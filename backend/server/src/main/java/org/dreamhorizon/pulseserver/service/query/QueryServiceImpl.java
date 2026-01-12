package org.dreamhorizon.pulseserver.service.query;

import com.google.inject.Inject;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Single;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.client.query.QueryClient;
import org.dreamhorizon.pulseserver.client.query.models.QueryStatus;
import org.dreamhorizon.pulseserver.constant.Constants;
import org.dreamhorizon.pulseserver.dao.query.QueryJobDao;
import org.dreamhorizon.pulseserver.service.query.models.QueryJob;
import org.dreamhorizon.pulseserver.service.query.models.QueryJobStatus;
import org.dreamhorizon.pulseserver.util.QueryTimestampEnricher;
import java.sql.Timestamp;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class QueryServiceImpl implements QueryService {

  private final QueryClient queryClient;
  private final QueryJobDao queryJobDao;

  @Override
  public Single<QueryJob> submitQuery(String queryString, List<String> parameters, String timestampString, String userEmail) {
    if (userEmail == null || userEmail.trim().isEmpty()) {
      return Single.error(new IllegalArgumentException("User email is required and cannot be null or empty"));
    }

    if (!hasTimestampInWhereClause(queryString)) {
      return Single.error(new IllegalArgumentException("Query must contain a TIMESTAMP in the WHERE clause"));
    }

    final String enrichedQuery = QueryTimestampEnricher.enrichQueryWithTimestamp(queryString, timestampString);

    if (!enrichedQuery.equals(queryString)) {
      log.debug("Enriched query with partition filters. Original: {}, Enriched: {}", queryString, enrichedQuery);
    } else {
      log.debug("Query was not enriched (no timestamp found or partition filters already present)");
    }

    return queryJobDao.createJob(enrichedQuery, queryString, userEmail.trim())
        .flatMap(jobId -> queryClient.submitQuery(enrichedQuery, parameters)
            .flatMap(queryExecutionId -> queryClient.getQueryExecution(queryExecutionId)
                .flatMap(execution -> {
                  Long initialDataScannedBytes = execution.getDataScannedInBytes();
                  Timestamp submissionDateTime = execution.getSubmissionDateTime();

                  return queryJobDao.updateJobWithExecutionId(jobId, queryExecutionId, QueryJobStatus.RUNNING, submissionDateTime)
                      .flatMap(result -> waitForCompletionWithTimeout(queryExecutionId, Constants.QUERY_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                          .flatMap(status -> handleQueryState(jobId, queryExecutionId, status, initialDataScannedBytes))
                          .onErrorResumeNext(error -> {
                            log.debug("Error or timeout waiting for query completion for job: {}, returning job ID only", jobId);
                            return queryJobDao.getJobById(jobId)
                                .map(job -> buildJobWithDataScanned(job, initialDataScannedBytes));
                          }));
                }))
            .onErrorResumeNext(error -> {
              log.error("Error submitting query for job: {}", jobId, error);
              return queryJobDao.updateJobFailed(jobId, "Failed to submit query: " + error.getMessage(), null)
                  .flatMap(v -> Single.error(error));
            }));
  }

  private Single<QueryJob> handleQueryState(String jobId, String queryExecutionId, QueryStatus status, Long initialDataScannedBytes) {
    if (status == QueryStatus.SUCCEEDED) {
      log.info("Query completed within {} seconds for job: {}", Constants.QUERY_TIMEOUT_SECONDS, jobId);
      return fetchResultsForJob(jobId, queryExecutionId, initialDataScannedBytes);
    } else if (status == QueryStatus.FAILED || status == QueryStatus.CANCELLED) {
      log.warn("Query failed within {} seconds for job: {}, status: {}", Constants.QUERY_TIMEOUT_SECONDS, jobId, status);
      return handleFailedQuery(jobId, queryExecutionId, status, initialDataScannedBytes);
    } else {
      log.debug("Query still running after {} seconds for job: {}, returning job ID only", Constants.QUERY_TIMEOUT_SECONDS, jobId);
      return queryJobDao.getJobById(jobId)
          .map(job -> buildJobWithDataScanned(job, initialDataScannedBytes));
    }
  }

  private Single<QueryJob> handleFailedQuery(String jobId, String queryExecutionId, QueryStatus status, Long fallbackDataScannedBytes) {
    return queryClient.getQueryExecution(queryExecutionId)
        .flatMap(execution -> {
          String errorMessage = execution.getStateChangeReason() != null
              ? execution.getStateChangeReason()
              : "Query " + status.name().toLowerCase();
          Long dataScannedBytes = execution.getDataScannedInBytes() != null
              ? execution.getDataScannedInBytes()
              : fallbackDataScannedBytes;
          Timestamp completionDateTime = execution.getCompletionDateTime();
          return queryJobDao.updateJobFailed(jobId, errorMessage, completionDateTime)
              .flatMap(v -> queryJobDao.getJobById(jobId)
                  .map(job -> buildJobWithDataScanned(job, dataScannedBytes)));
        });
  }

  private Single<QueryStatus> waitForCompletionWithTimeout(String queryExecutionId, long timeout, TimeUnit unit) {
    long startTime = System.currentTimeMillis();

    return queryClient.getQueryStatus(queryExecutionId)
        .flatMap(initialStatus -> {
          if (isQueryStatusFinal(initialStatus)) {
            long elapsed = System.currentTimeMillis() - startTime;
            log.debug("Query already in final state: {} (checked in {}ms)", initialStatus, elapsed);
            return Single.just(initialStatus);
          }

          long elapsed = System.currentTimeMillis() - startTime;
          long remainingTimeout = unit.toMillis(timeout) - elapsed;

          if (remainingTimeout <= 0) {
            log.debug("Timeout already exceeded after initial check, returning current state: {}", initialStatus);
            return Single.just(initialStatus);
          }

          log.debug("Query not in final state yet ({}), starting to poll every {}ms for up to {}ms",
              initialStatus, Constants.QUERY_POLL_INTERVAL_MS, remainingTimeout);

          return Observable.interval(Constants.QUERY_POLL_INTERVAL_MS, TimeUnit.MILLISECONDS)
              .flatMapSingle(tick -> queryClient.getQueryStatus(queryExecutionId))
              .filter(this::isQueryStatusFinal)
              .firstOrError()
              .timeout(remainingTimeout, TimeUnit.MILLISECONDS)
              .onErrorResumeNext(error -> {
                long totalElapsed = System.currentTimeMillis() - startTime;
                log.debug("Timeout waiting for query completion after {}ms (requested {} {}), checking current status",
                    totalElapsed, timeout, unit);
                return queryClient.getQueryStatus(queryExecutionId);
              });
        });
  }

  private Single<QueryJob> fetchResultsForJob(String jobId, String queryExecutionId, Long fallbackDataScannedBytes) {
    return queryClient.getQueryExecution(queryExecutionId)
        .flatMap(execution -> {
          String resultLocation = execution.getResultLocation();
          Long dataScannedBytes = execution.getDataScannedInBytes() != null
              ? execution.getDataScannedInBytes()
              : fallbackDataScannedBytes;
          Timestamp completionDateTime = execution.getCompletionDateTime();

          return queryJobDao.updateJobCompleted(jobId, resultLocation, completionDateTime)
              .flatMap(v -> queryJobDao.updateJobStatistics(jobId, dataScannedBytes,
                  execution.getExecutionTimeMillis(), execution.getEngineExecutionTimeMillis(), execution.getQueryQueueTimeMillis(), completionDateTime))
              .flatMap(v -> Single.timer(Constants.RESULT_FETCH_DELAY_MS, TimeUnit.MILLISECONDS)
                  .flatMap(tick -> fetchResultsWithRetry(queryExecutionId, Constants.MAX_RESULT_FETCH_RETRIES, Constants.RESULT_FETCH_RETRY_DELAY_MS)
                      .flatMap(resultSet -> {
                        log.info("Successfully fetched {} result rows for job: {}", 
                            resultSet.getResultData() != null ? resultSet.getResultData().size() : 0, jobId);

                        return queryJobDao.getJobById(jobId)
                            .map(job -> buildCompletedJob(job, resultSet.getResultData(), resultSet.getNextToken(), dataScannedBytes));
                      })));
        })
        .onErrorResumeNext(error -> {
          log.error("Error fetching results for job: {} after retries. Error: {}", jobId, error.getMessage(), error);
          return queryClient.getQueryExecution(queryExecutionId)
              .flatMap(execution -> {
                Long dataScannedBytes = execution.getDataScannedInBytes() != null
                    ? execution.getDataScannedInBytes()
                    : fallbackDataScannedBytes;
                Timestamp completionDateTime = execution.getCompletionDateTime();
                return queryJobDao.updateJobStatistics(jobId, dataScannedBytes,
                    execution.getExecutionTimeMillis(), execution.getEngineExecutionTimeMillis(), execution.getQueryQueueTimeMillis(), completionDateTime)
                    .flatMap(v -> queryJobDao.getJobById(jobId))
                    .map(job -> buildCompletedJob(job, null, null, dataScannedBytes));
              })
              .onErrorResumeNext(fallbackError -> queryJobDao.getJobById(jobId)
                  .map(job -> buildCompletedJob(job, null, null, fallbackDataScannedBytes)));
        });
  }

  private Single<org.dreamhorizon.pulseserver.client.query.models.QueryResultSet> fetchResultsWithRetry(
      String queryExecutionId, int maxRetries, long delayMs) {
    return queryClient.getQueryResults(queryExecutionId, Constants.MAX_QUERY_RESULTS, null)
        .onErrorResumeNext(error -> {
          if (maxRetries > 0) {
            log.warn("Failed to fetch results for query {}: {}. Retrying in {}ms ({} retries left)",
                queryExecutionId, error.getMessage(), delayMs, maxRetries);
            return Single.timer(delayMs, TimeUnit.MILLISECONDS)
                .flatMap(tick -> {
                  log.debug("Retrying to fetch results for query: {}", queryExecutionId);
                  return fetchResultsWithRetry(queryExecutionId, maxRetries - 1, delayMs);
                });
          } else {
            log.error("Failed to fetch results for query {} after all retries. Last error: {}",
                queryExecutionId, error.getMessage(), error);
            return Single.error(error);
          }
        });
  }

  @Override
  public Single<QueryJob> getJobStatus(String jobId, Integer maxResults, String nextToken) {
    return queryJobDao.getJobById(jobId)
        .flatMap(job -> {
          if (job == null) {
            return Single.error(new RuntimeException("Job not found: " + jobId));
          }

          if (isFinalState(job.getStatus())) {
            if (job.getStatus() == QueryJobStatus.COMPLETED && job.getQueryExecutionId() != null) {
              return fetchPaginatedResults(job, maxResults, nextToken);
            }
            return Single.just(job);
          }

          if (job.getQueryExecutionId() == null) {
            return Single.just(job);
          }

          return queryClient.getQueryStatus(job.getQueryExecutionId())
              .flatMap(status -> {
                QueryJobStatus newStatus = mapQueryStatusToJobStatus(status);

                if (newStatus != job.getStatus()) {
                  return handleStatusChange(jobId, job.getQueryExecutionId(), newStatus, job, maxResults, nextToken);
                }

                if (newStatus == QueryJobStatus.COMPLETED && (maxResults != null || nextToken != null)) {
                  return fetchPaginatedResults(job, maxResults, nextToken);
                }

                return Single.just(job);
              });
        });
  }

  private Single<QueryJob> handleStatusChange(String jobId, String queryExecutionId, QueryJobStatus newStatus,
      QueryJob job, Integer maxResults, String nextToken) {
    if (newStatus == QueryJobStatus.COMPLETED) {
      return fetchAndUpdateJobResults(jobId, queryExecutionId)
          .flatMap(updatedJob -> {
            if (maxResults != null || nextToken != null) {
              return fetchPaginatedResults(updatedJob, maxResults, nextToken);
            }
            return Single.just(updatedJob);
          });
    } else if (newStatus == QueryJobStatus.FAILED || newStatus == QueryJobStatus.CANCELLED) {
      return handleFailedQueryInStatusCheck(jobId, queryExecutionId, newStatus);
    } else {
      return queryClient.getQueryExecution(queryExecutionId)
          .flatMap(execution -> {
            Timestamp updatedAt = execution.getCompletionDateTime() != null 
                ? execution.getCompletionDateTime() 
                : execution.getSubmissionDateTime();
            return queryJobDao.updateJobStatus(jobId, newStatus, updatedAt)
                .flatMap(v -> queryJobDao.getJobById(jobId));
          });
    }
  }

  private Single<QueryJob> handleFailedQueryInStatusCheck(String jobId, String queryExecutionId, QueryJobStatus status) {
    return queryClient.getQueryExecution(queryExecutionId)
        .flatMap(execution -> {
          String errorMessage = execution.getStateChangeReason() != null
              ? execution.getStateChangeReason()
              : "Query " + status.name().toLowerCase();
          Long dataScannedBytes = execution.getDataScannedInBytes();
          Timestamp completionDateTime = execution.getCompletionDateTime();
          return queryJobDao.updateJobFailed(jobId, errorMessage, completionDateTime)
              .flatMap(v -> queryJobDao.getJobById(jobId)
                  .map(dbJob -> buildJobWithDataScanned(dbJob, dataScannedBytes)));
        });
  }

  private Single<QueryJob> fetchPaginatedResults(QueryJob job, Integer maxResults, String nextToken) {
    if (job.getQueryExecutionId() == null) {
      return Single.just(job);
    }

    Integer queryMaxResults = maxResults != null ? Math.min(maxResults + 1, Constants.MAX_QUERY_RESULTS) : null;

    return queryClient.getQueryResults(job.getQueryExecutionId(), queryMaxResults, nextToken)
        .map(resultSet -> {
          return buildJobWithResults(job, resultSet.getResultData(), resultSet.getNextToken());
        });
  }

  @Override
  public Single<QueryJob> waitForJobCompletion(String jobId) {
    return queryJobDao.getJobById(jobId)
        .flatMap(job -> {
          if (job == null) {
            return Single.error(new RuntimeException("Job not found: " + jobId));
          }

          if (isFinalState(job.getStatus())) {
            return Single.just(job);
          }

          if (job.getQueryExecutionId() == null) {
            return Single.error(new RuntimeException("No query execution ID for job: " + jobId));
          }

          return queryClient.waitForQueryCompletion(job.getQueryExecutionId())
              .flatMap(status -> {
                if (status == QueryStatus.SUCCEEDED) {
                  return fetchAndUpdateJobResults(jobId, job.getQueryExecutionId());
                } else {
                  return handleFailedQueryInStatusCheck(jobId, job.getQueryExecutionId(),
                      mapQueryStatusToJobStatus(status));
                }
              });
        });
  }

  private Single<QueryJob> fetchAndUpdateJobResults(String jobId, String queryExecutionId) {
    return queryClient.getQueryExecution(queryExecutionId)
        .flatMap(execution -> {
          String resultLocation = execution.getResultLocation();
          Long dataScannedBytes = execution.getDataScannedInBytes();
          Timestamp completionDateTime = execution.getCompletionDateTime();

          return queryJobDao.updateJobCompleted(jobId, resultLocation, completionDateTime)
              .flatMap(v -> queryJobDao.getJobById(jobId)
                  .map(job -> buildJobWithDataScanned(job, dataScannedBytes)));
        })
        .onErrorResumeNext(error -> {
          log.error("Error updating job results location for job: {}", jobId, error);
          return queryClient.getQueryExecution(queryExecutionId)
              .flatMap(execution -> {
                Timestamp completionDateTime = execution.getCompletionDateTime();
                return queryJobDao.updateJobFailed(jobId, "Failed to update result location: " + error.getMessage(), completionDateTime)
                    .flatMap(v -> queryJobDao.getJobById(jobId));
              })
              .onErrorResumeNext(fallbackError -> {
                return queryJobDao.updateJobFailed(jobId, "Failed to update result location: " + error.getMessage(), null)
                    .flatMap(v -> queryJobDao.getJobById(jobId));
              });
        });
  }

  private QueryJob buildJobWithDataScanned(QueryJob job, Long dataScannedBytes) {
    return QueryJob.builder()
        .jobId(job.getJobId())
        .queryString(job.getQueryString())
        .queryExecutionId(job.getQueryExecutionId())
        .status(job.getStatus())
        .resultLocation(job.getResultLocation())
        .errorMessage(job.getErrorMessage())
        .dataScannedInBytes(dataScannedBytes)
        .createdAt(job.getCreatedAt())
        .updatedAt(job.getUpdatedAt())
        .completedAt(job.getCompletedAt())
        .build();
  }

  private QueryJob buildCompletedJob(QueryJob job, io.vertx.core.json.JsonArray resultData, String nextToken, Long dataScannedBytes) {
    return QueryJob.builder()
        .jobId(job.getJobId())
        .queryString(job.getQueryString())
        .queryExecutionId(job.getQueryExecutionId())
        .status(QueryJobStatus.COMPLETED)
        .resultLocation(job.getResultLocation())
        .errorMessage(job.getErrorMessage())
        .resultData(resultData)
        .nextToken(nextToken)
        .dataScannedInBytes(dataScannedBytes)
        .createdAt(job.getCreatedAt())
        .updatedAt(job.getUpdatedAt())
        .completedAt(job.getCompletedAt())
        .build();
  }

  private QueryJob buildJobWithResults(QueryJob job, io.vertx.core.json.JsonArray resultData, String nextToken) {
    return QueryJob.builder()
        .jobId(job.getJobId())
        .queryString(job.getQueryString())
        .queryExecutionId(job.getQueryExecutionId())
        .status(job.getStatus())
        .resultLocation(job.getResultLocation())
        .errorMessage(job.getErrorMessage())
        .resultData(resultData)
        .createdAt(job.getCreatedAt())
        .updatedAt(job.getUpdatedAt())
        .completedAt(job.getCompletedAt())
        .nextToken(nextToken)
        .dataScannedInBytes(job.getDataScannedInBytes())
        .build();
  }

  private boolean isQueryStatusFinal(QueryStatus status) {
    return status == QueryStatus.SUCCEEDED
        || status == QueryStatus.FAILED
        || status == QueryStatus.CANCELLED;
  }

  private boolean isFinalState(QueryJobStatus status) {
    return status == QueryJobStatus.COMPLETED
        || status == QueryJobStatus.FAILED
        || status == QueryJobStatus.CANCELLED;
  }

  @Override
  public Single<QueryJob> cancelQuery(String jobId) {
    return queryJobDao.getJobById(jobId)
        .flatMap(job -> {
          if (job == null) {
            return Single.error(new RuntimeException("Job not found: " + jobId));
          }

          if (isFinalState(job.getStatus())) {
            log.warn("Cannot cancel job {} - already in final state: {}", jobId, job.getStatus());
            return Single.just(job);
          }

          if (job.getQueryExecutionId() == null) {
            log.warn("Cannot cancel job {} - no query execution ID available", jobId);
            Timestamp now = new Timestamp(System.currentTimeMillis());
            return queryJobDao.updateJobStatus(jobId, QueryJobStatus.CANCELLED, now)
                .flatMap(v -> queryJobDao.getJobById(jobId));
          }

          return queryClient.cancelQuery(job.getQueryExecutionId())
              .flatMap(cancelled -> {
                if (cancelled) {
                  log.info("Successfully cancelled query execution {} for job {}", job.getQueryExecutionId(), jobId);
                  return queryClient.getQueryExecution(job.getQueryExecutionId())
                      .flatMap(execution -> {
                        Timestamp updatedAt = execution.getCompletionDateTime() != null 
                            ? execution.getCompletionDateTime() 
                            : new Timestamp(System.currentTimeMillis());
                        return queryJobDao.updateJobStatus(jobId, QueryJobStatus.CANCELLED, updatedAt)
                            .flatMap(v -> queryJobDao.getJobById(jobId));
                      })
                      .onErrorResumeNext(error -> {
                        Timestamp now = new Timestamp(System.currentTimeMillis());
                        return queryJobDao.updateJobStatus(jobId, QueryJobStatus.CANCELLED, now)
                            .flatMap(v -> queryJobDao.getJobById(jobId));
                      });
                } else {
                  log.warn("Failed to cancel query execution {} for job {}", job.getQueryExecutionId(), jobId);
                  return queryClient.getQueryExecution(job.getQueryExecutionId())
                      .flatMap(execution -> {
                        QueryJobStatus newStatus = mapQueryStatusToJobStatus(execution.getStatus());
                        Timestamp updatedAt = execution.getCompletionDateTime() != null 
                            ? execution.getCompletionDateTime() 
                            : (execution.getSubmissionDateTime() != null ? execution.getSubmissionDateTime() : new Timestamp(System.currentTimeMillis()));
                        if (newStatus == QueryJobStatus.CANCELLED) {
                          return queryJobDao.updateJobStatus(jobId, QueryJobStatus.CANCELLED, updatedAt)
                              .flatMap(v -> queryJobDao.getJobById(jobId));
                        }
                        return Single.error(new RuntimeException("Failed to cancel query execution"));
                      });
                }
              })
              .onErrorResumeNext(error -> {
                log.error("Error cancelling query for job: {}", jobId, error);
                return queryClient.getQueryExecution(job.getQueryExecutionId())
                    .flatMap(execution -> {
                      QueryJobStatus newStatus = mapQueryStatusToJobStatus(execution.getStatus());
                      Timestamp updatedAt = execution.getCompletionDateTime() != null 
                          ? execution.getCompletionDateTime() 
                          : (execution.getSubmissionDateTime() != null ? execution.getSubmissionDateTime() : new Timestamp(System.currentTimeMillis()));
                      if (newStatus == QueryJobStatus.CANCELLED) {
                        return queryJobDao.updateJobStatus(jobId, QueryJobStatus.CANCELLED, updatedAt)
                            .flatMap(v -> queryJobDao.getJobById(jobId));
                      }
                      return Single.error(error);
                    })
                    .onErrorResumeNext(fallbackError -> Single.error(
                        new RuntimeException("Failed to cancel query: " + error.getMessage(), error)));
              });
        });
  }

  private QueryJobStatus mapQueryStatusToJobStatus(QueryStatus status) {
    switch (status) {
      case QUEUED:
      case RUNNING:
        return QueryJobStatus.RUNNING;
      case SUCCEEDED:
        return QueryJobStatus.COMPLETED;
      case FAILED:
        return QueryJobStatus.FAILED;
      case CANCELLED:
        return QueryJobStatus.CANCELLED;
      default:
        return QueryJobStatus.SUBMITTED;
    }
  }

  private boolean hasTimestampInWhereClause(String query) {
    if (query == null || query.trim().isEmpty()) {
      return false;
    }

    Pattern wherePattern = Pattern.compile("\\bWHERE\\b", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    Matcher whereMatcher = wherePattern.matcher(query);
    if (!whereMatcher.find()) {
      return false;
    }

    int whereEnd = whereMatcher.end();
    String whereClause = query.substring(whereEnd);

    Pattern timestampPattern = Pattern.compile(
        "TIMESTAMP\\s+['\"](\\d{4}-\\d{2}-\\d{2}\\s+\\d{1,2}:\\d{2}:\\d{2})['\"]",
        Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );

    return timestampPattern.matcher(whereClause).find();
  }

  @Override
  public Single<List<QueryJob>> getQueryHistory(String userEmail, Integer limit, Integer offset) {
    if (userEmail == null || userEmail.trim().isEmpty()) {
      return Single.error(new IllegalArgumentException("User email is required and cannot be null or empty"));
    }

    int queryLimit = limit != null && limit > 0 ? Math.min(limit, Constants.MAX_QUERY_RESULTS) : 20;
    int queryOffset = offset != null && offset >= 0 ? offset : 0;

    return queryJobDao.getQueryHistory(userEmail.trim(), queryLimit, queryOffset);
  }
}

