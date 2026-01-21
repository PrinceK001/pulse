package org.dreamhorizon.pulseserver.service.churn;

import com.google.inject.Inject;
import io.reactivex.rxjava3.core.Single;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.client.chclient.ClickhouseQueryService;
import org.dreamhorizon.pulseserver.model.QueryConfiguration;
import org.dreamhorizon.pulseserver.service.churn.models.ChurnPredictionRequest;
import org.dreamhorizon.pulseserver.service.churn.models.UserChurnFeatures;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class ChurnFeatureExtractor {

  private final ClickhouseQueryService clickhouseQueryService;
  private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

  /**
   * Extract churn prediction features for users from ClickHouse
   */
  public Single<List<UserChurnFeatures>> extractUserFeatures(ChurnPredictionRequest request) {
    log.info("Extracting churn features for users");

    LocalDateTime now = LocalDateTime.now();
    LocalDateTime sevenDaysAgo = now.minusDays(7);
    LocalDateTime thirtyDaysAgo = now.minusDays(30);

    // Build the feature extraction query
    String query = buildFeatureExtractionQuery(request, sevenDaysAgo, thirtyDaysAgo, now);

    return clickhouseQueryService.executeQueryOrCreateJob(
        QueryConfiguration.newQuery(query).build(),
        UserChurnFeatures.class
    )
        .map(result -> {
          List<UserChurnFeatures> features = result.getRows();
          log.info("Extracted features for {} users", features.size());
          return features;
        })
        .doOnError(error -> log.error("Error extracting churn features", error));
  }

  private String buildFeatureExtractionQuery(
      ChurnPredictionRequest request,
      LocalDateTime sevenDaysAgo,
      LocalDateTime thirtyDaysAgo,
      LocalDateTime now
  ) {
    StringBuilder query = new StringBuilder();
    query.append("SELECT\n");
    query.append("  COALESCE(t.user_id, '') as user_id,\n");
    query.append("  COALESCE(t.device_model, '') as device_model,\n");
    query.append("  COALESCE(t.os_version, '') as os_version,\n");
    query.append("  COALESCE(t.app_version, '') as app_version,\n");
    
    // Engagement features
    query.append("  COALESCE(t.sessions_7d, 0) as sessions_last_7_days,\n");
    query.append("  COALESCE(t.sessions_30d, 0) as sessions_last_30_days,\n");
    query.append("  COALESCE(t.days_since_last_session, 0) as days_since_last_session,\n");
    query.append("  COALESCE(t.avg_session_duration, 0) as avg_session_duration,\n");
    query.append("  COALESCE(t.unique_screens_7d, 0) as unique_screens_last_7_days,\n");
    
    // Performance features
    query.append("  COALESCE(t.crash_count_7d, 0) as crash_count_last_7_days,\n");
    query.append("  COALESCE(t.anr_count_7d, 0) as anr_count_last_7_days,\n");
    query.append("  COALESCE(t.frozen_frame_rate_7d, 0.0) as frozen_frame_rate\n");
    
    query.append("FROM (\n");
    query.append("  SELECT DISTINCT\n");
    query.append("    UserId as user_id,\n");
    query.append("    DeviceModel as device_model,\n");
    query.append("    OsVersion as os_version,\n");
    query.append("    AppVersion as app_version\n");
    query.append("  FROM otel.otel_logs\n");
    query.append("  WHERE Timestamp >= '").append(thirtyDaysAgo.format(DATE_FORMATTER)).append("'\n");
    query.append("    AND UserId != ''\n");
    
    if (request.getUserId() != null && !request.getUserId().isEmpty()) {
      query.append("    AND UserId = '").append(request.getUserId()).append("'\n");
    }
    
    query.append(") t\n");
    query.append("LEFT JOIN (\n");
    
    // Sessions in last 7 days
    query.append("  SELECT\n");
    query.append("    UserId as user_id,\n");
    query.append("    COUNT(DISTINCT SessionId) as sessions_7d\n");
    query.append("  FROM otel.otel_logs\n");
    query.append("  WHERE PulseType = 'session.start'\n");
    query.append("    AND Timestamp >= '").append(sevenDaysAgo.format(DATE_FORMATTER)).append("'\n");
    query.append("    AND Timestamp < '").append(now.format(DATE_FORMATTER)).append("'\n");
    query.append("    AND UserId != ''\n");
    query.append("  GROUP BY UserId\n");
    query.append(") s7d ON t.user_id = s7d.user_id\n");
    
    // Sessions in last 30 days
    query.append("LEFT JOIN (\n");
    query.append("  SELECT\n");
    query.append("    UserId as user_id,\n");
    query.append("    COUNT(DISTINCT SessionId) as sessions_30d\n");
    query.append("  FROM otel.otel_logs\n");
    query.append("  WHERE PulseType = 'session.start'\n");
    query.append("    AND Timestamp >= '").append(thirtyDaysAgo.format(DATE_FORMATTER)).append("'\n");
    query.append("    AND Timestamp < '").append(now.format(DATE_FORMATTER)).append("'\n");
    query.append("    AND UserId != ''\n");
    query.append("  GROUP BY UserId\n");
    query.append(") s30d ON t.user_id = s30d.user_id\n");
    
    // Days since last session
    query.append("LEFT JOIN (\n");
    query.append("  SELECT\n");
    query.append("    UserId as user_id,\n");
    query.append("    dateDiff('day', MAX(Timestamp), now()) as days_since_last_session\n");
    query.append("  FROM otel.otel_logs\n");
    query.append("  WHERE PulseType = 'session.start'\n");
    query.append("    AND UserId != ''\n");
    query.append("  GROUP BY UserId\n");
    query.append(") dsls ON t.user_id = dsls.user_id\n");
    
    // Average session duration
    query.append("LEFT JOIN (\n");
    query.append("  SELECT\n");
    query.append("    UserId as user_id,\n");
    query.append("    AVG(Duration) as avg_session_duration\n");
    query.append("  FROM otel.otel_traces\n");
    query.append("  WHERE PulseType = 'screen_session'\n");
    query.append("    AND Timestamp >= '").append(sevenDaysAgo.format(DATE_FORMATTER)).append("'\n");
    query.append("    AND UserId != ''\n");
    query.append("    AND Duration > 0\n");
    query.append("  GROUP BY UserId\n");
    query.append(") asd ON t.user_id = asd.user_id\n");
    
    // Unique screens in last 7 days
    query.append("LEFT JOIN (\n");
    query.append("  SELECT\n");
    query.append("    UserId as user_id,\n");
    query.append("    COUNT(DISTINCT SpanAttributes['screen.name']) as unique_screens_7d\n");
    query.append("  FROM otel.otel_traces\n");
    query.append("  WHERE PulseType IN ('screen_session', 'screen_load')\n");
    query.append("    AND Timestamp >= '").append(sevenDaysAgo.format(DATE_FORMATTER)).append("'\n");
    query.append("    AND UserId != ''\n");
    query.append("  GROUP BY UserId\n");
    query.append(") us7d ON t.user_id = us7d.user_id\n");
    
    // Crash count in last 7 days
    query.append("LEFT JOIN (\n");
    query.append("  SELECT\n");
    query.append("    UserId as user_id,\n");
    query.append("    COUNT(*) as crash_count_7d\n");
    query.append("  FROM otel.stack_trace_events\n");
    query.append("  WHERE PulseType = 'device.crash'\n");
    query.append("    AND Timestamp >= '").append(sevenDaysAgo.format(DATE_FORMATTER)).append("'\n");
    query.append("    AND UserId != ''\n");
    query.append("  GROUP BY UserId\n");
    query.append(") c7d ON t.user_id = c7d.user_id\n");
    
    // ANR count in last 7 days
    query.append("LEFT JOIN (\n");
    query.append("  SELECT\n");
    query.append("    UserId as user_id,\n");
    query.append("    COUNT(*) as anr_count_7d\n");
    query.append("  FROM otel.stack_trace_events\n");
    query.append("  WHERE PulseType = 'device.anr'\n");
    query.append("    AND Timestamp >= '").append(sevenDaysAgo.format(DATE_FORMATTER)).append("'\n");
    query.append("    AND UserId != ''\n");
    query.append("  GROUP BY UserId\n");
    query.append(") a7d ON t.user_id = a7d.user_id\n");
    
    // Frozen frame rate in last 7 days
    query.append("LEFT JOIN (\n");
    query.append("  SELECT\n");
    query.append("    UserId as user_id,\n");
    query.append("    CASE\n");
    query.append("      WHEN COUNT(*) > 0 THEN\n");
    query.append("        COUNT(CASE WHEN PulseType = 'app.jank.frozen' THEN 1 END) / COUNT(*)\n");
    query.append("      ELSE 0.0\n");
    query.append("    END as frozen_frame_rate_7d\n");
    query.append("  FROM otel.stack_trace_events\n");
    query.append("  WHERE PulseType IN ('app.jank.frozen', 'app.jank.slow')\n");
    query.append("    AND Timestamp >= '").append(sevenDaysAgo.format(DATE_FORMATTER)).append("'\n");
    query.append("    AND UserId != ''\n");
    query.append("  GROUP BY UserId\n");
    query.append(") ffr ON t.user_id = ffr.user_id\n");
    
    query.append("WHERE t.user_id != ''\n");
    
    // Apply filters
    if (request.getDeviceModel() != null && !request.getDeviceModel().isEmpty()) {
      query.append("  AND t.device_model = '").append(request.getDeviceModel()).append("'\n");
    }
    if (request.getOsVersion() != null && !request.getOsVersion().isEmpty()) {
      query.append("  AND t.os_version = '").append(request.getOsVersion()).append("'\n");
    }
    if (request.getAppVersion() != null && !request.getAppVersion().isEmpty()) {
      query.append("  AND t.app_version = '").append(request.getAppVersion()).append("'\n");
    }
    
    query.append("LIMIT ").append(request.getLimit() != null ? request.getLimit() : 1000);

    return query.toString();
  }
}

