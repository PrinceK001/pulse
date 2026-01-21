package org.dreamhorizon.pulseserver.client.ml;

import com.google.inject.Inject;
import io.reactivex.rxjava3.core.Single;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.rxjava3.ext.web.client.WebClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.config.ApplicationConfig;
import org.dreamhorizon.pulseserver.service.churn.models.UserChurnFeatures;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class MLPredictionClient {
  
  private final WebClient webClient;
  private final ApplicationConfig config;
  
  private static final String DEFAULT_ML_SERVICE_URL = "http://localhost:8000";
  private static final int TIMEOUT_MS = 5000; // 5 second timeout
  
  private String getMLServiceUrl() {
    // Get from config or use default
    String url = config.getMlServiceBaseUrl();
    if (url == null || url.trim().isEmpty()) {
      log.warn("ML service URL not configured, using default: {}", DEFAULT_ML_SERVICE_URL);
      return DEFAULT_ML_SERVICE_URL;
    }
    return url;
  }
  
  /**
   * Predict churn risk using ML service
   */
  public Single<MLPredictionResponse> predictChurn(UserChurnFeatures features) {
    String url = getMLServiceUrl() + "/predict";
    
    log.debug("Calling ML service at {} for user: {}", url, features.getUserId());
    
    JsonObject requestBody = new JsonObject()
        .put("user_id", features.getUserId() != null ? features.getUserId() : "")
        .put("sessions_last_7_days", features.getSessionsLast7Days() != null ? features.getSessionsLast7Days() : 0)
        .put("sessions_last_30_days", features.getSessionsLast30Days() != null ? features.getSessionsLast30Days() : 0)
        .put("days_since_last_session", features.getDaysSinceLastSession() != null ? features.getDaysSinceLastSession() : 0)
        .put("avg_session_duration", features.getAvgSessionDuration() != null ? features.getAvgSessionDuration() : 0L)
        .put("unique_screens_last_7_days", features.getUniqueScreensLast7Days() != null ? features.getUniqueScreensLast7Days() : 0)
        .put("crash_count_last_7_days", features.getCrashCountLast7Days() != null ? features.getCrashCountLast7Days() : 0)
        .put("anr_count_last_7_days", features.getAnrCountLast7Days() != null ? features.getAnrCountLast7Days() : 0)
        .put("frozen_frame_rate", features.getFrozenFrameRate() != null ? features.getFrozenFrameRate() : 0.0)
        .put("device_model", features.getDeviceModel() != null ? features.getDeviceModel() : "")
        .put("os_version", features.getOsVersion() != null ? features.getOsVersion() : "")
        .put("app_version", features.getAppVersion() != null ? features.getAppVersion() : "");
    
    return webClient
        .postAbs(url)
        .timeout(TIMEOUT_MS)
        .rxSendJson(requestBody)
        .map(response -> {
          if (response.statusCode() == 200) {
            JsonObject body = response.bodyAsJsonObject();
            return MLPredictionResponse.builder()
                .riskScore(body.getInteger("risk_score"))
                .churnProbability(body.getDouble("churn_probability"))
                .riskLevel(body.getString("risk_level"))
                .build();
          } else {
            String errorMsg = String.format("ML service returned status %d: %s", 
                response.statusCode(), response.bodyAsString());
            log.error(errorMsg);
            throw new RuntimeException(errorMsg);
          }
        })
        .doOnSuccess(response -> 
            log.debug("ML prediction for user {}: risk_score={}, probability={}", 
                features.getUserId(), response.getRiskScore(), response.getChurnProbability()))
        .doOnError(error -> 
            log.warn("Error calling ML service for user {}: {}", 
                features.getUserId(), error.getMessage()));
  }
  
  /**
   * Batch predict churn risk for multiple users (optimized for large datasets)
   */
  public Single<List<MLPredictionResponse>> predictChurnBatch(List<UserChurnFeatures> featuresList) {
    String url = getMLServiceUrl() + "/predict/batch";
    
    log.debug("Calling ML service batch prediction for {} users", featuresList.size());
    
    JsonArray usersArray = new JsonArray();
    for (UserChurnFeatures features : featuresList) {
      usersArray.add(mapFeaturesToJson(features));
    }
    
    JsonObject requestBody = new JsonObject().put("users", usersArray);
    
    return webClient
        .postAbs(url)
        .timeout(TIMEOUT_MS * 2) // Longer timeout for batch
        .rxSendJson(requestBody)
        .map(response -> {
          if (response.statusCode() == 200) {
            JsonObject body = response.bodyAsJsonObject();
            JsonArray predictionsArray = body.getJsonArray("predictions");
            
            return predictionsArray.stream()
                .map(obj -> (JsonObject) obj)
                .map(json -> MLPredictionResponse.builder()
                    .riskScore(json.getInteger("risk_score"))
                    .churnProbability(json.getDouble("churn_probability"))
                    .riskLevel(json.getString("risk_level"))
                    .build())
                .collect(Collectors.toList());
          } else {
            String errorMsg = String.format("ML service batch returned status %d: %s", 
                response.statusCode(), response.bodyAsString());
            log.error(errorMsg);
            throw new RuntimeException(errorMsg);
          }
        })
        .doOnSuccess(responses -> 
            log.debug("ML batch prediction completed: {} predictions", responses.size()))
        .doOnError(error -> 
            log.warn("Error calling ML service batch prediction: {}", error.getMessage()));
  }
  
  /**
   * Discover churn patterns using ML clustering
   */
  public Single<MLPatternDiscoveryResponse> discoverPatterns(List<UserChurnFeatures> featuresList) {
    String url = getMLServiceUrl() + "/analyze/patterns";
    
    log.debug("Discovering patterns for {} users", featuresList.size());
    
    JsonArray usersArray = new JsonArray();
    for (UserChurnFeatures features : featuresList) {
      usersArray.add(mapFeaturesToJson(features));
    }
    
    JsonObject requestBody = new JsonObject().put("users", usersArray);
    
    return webClient
        .postAbs(url)
        .timeout(TIMEOUT_MS * 3) // Longer timeout for analysis
        .rxSendJson(requestBody)
        .map(response -> {
          if (response.statusCode() == 200) {
            JsonObject body = response.bodyAsJsonObject();
            JsonArray patternsArray = body.getJsonArray("patterns");
            
            List<MLPatternDiscoveryResponse.Pattern> patterns = patternsArray.stream()
                .map(obj -> (JsonObject) obj)
                .map(json -> {
                  JsonObject characteristics = json.getJsonObject("characteristics");
                  return MLPatternDiscoveryResponse.Pattern.builder()
                      .patternId(json.getInteger("pattern_id"))
                      .userCount(json.getInteger("user_count"))
                      .avgRiskScore(json.getDouble("avg_risk_score"))
                      .avgChurnProbability(json.getDouble("avg_churn_probability"))
                      .characteristics(characteristics != null ? 
                          characteristics.getMap() : Map.of())
                      .build();
                })
                .collect(Collectors.toList());
            
            return MLPatternDiscoveryResponse.builder()
                .patterns(patterns)
                .build();
          } else {
            throw new RuntimeException("Pattern discovery failed: " + response.statusCode());
          }
        });
  }
  
  /**
   * Analyze root causes using ML feature importance
   */
  public Single<MLRootCauseAnalysisResponse> analyzeRootCauses(List<UserChurnFeatures> featuresList) {
    String url = getMLServiceUrl() + "/analyze/root-causes";
    
    log.debug("Analyzing root causes for {} users", featuresList.size());
    
    JsonArray usersArray = new JsonArray();
    for (UserChurnFeatures features : featuresList) {
      usersArray.add(mapFeaturesToJson(features));
    }
    
    JsonObject requestBody = new JsonObject().put("users", usersArray);
    
    return webClient
        .postAbs(url)
        .timeout(TIMEOUT_MS * 3)
        .rxSendJson(requestBody)
        .map(response -> {
          if (response.statusCode() == 200) {
            JsonObject body = response.bodyAsJsonObject();
            JsonArray rootCausesArray = body.getJsonArray("root_causes");
            
            List<MLRootCauseAnalysisResponse.RootCause> rootCauses = rootCausesArray.stream()
                .map(obj -> (JsonObject) obj)
                .map(json -> MLRootCauseAnalysisResponse.RootCause.builder()
                    .feature(json.getString("feature"))
                    .importance(json.getDouble("importance"))
                    .correlationWithHighRisk(json.getDouble("correlation_with_high_risk"))
                    .build())
                .collect(Collectors.toList());
            
            JsonObject aggregateImportance = body.getJsonObject("aggregate_importance");
            JsonObject correlations = body.getJsonObject("correlations");
            
            return MLRootCauseAnalysisResponse.builder()
                .rootCauses(rootCauses)
                .aggregateImportance(aggregateImportance != null ? aggregateImportance.getMap() : Map.of())
                .correlations(correlations != null ? correlations.getMap() : Map.of())
                .build();
          } else {
            throw new RuntimeException("Root cause analysis failed: " + response.statusCode());
          }
        });
  }
  
  /**
   * Analyze trends using ML time series regression
   */
  public Single<MLTrendAnalysisResponse> analyzeTrends(
      List<UserChurnFeatures> currentFeatures,
      List<UserChurnFeatures> historicalFeatures) {
    String url = getMLServiceUrl() + "/analyze/trends";
    
    log.debug("Analyzing trends: {} current, {} historical", 
        currentFeatures.size(), historicalFeatures != null ? historicalFeatures.size() : 0);
    
    JsonArray currentUsersArray = new JsonArray();
    for (UserChurnFeatures features : currentFeatures) {
      currentUsersArray.add(mapFeaturesToJson(features));
    }
    
    JsonObject requestBody = new JsonObject().put("current_users", currentUsersArray);
    
    if (historicalFeatures != null && !historicalFeatures.isEmpty()) {
      JsonArray historicalUsersArray = new JsonArray();
      for (UserChurnFeatures features : historicalFeatures) {
        historicalUsersArray.add(mapFeaturesToJson(features));
      }
      requestBody.put("historical_users", historicalUsersArray);
    }
    
    return webClient
        .postAbs(url)
        .timeout(TIMEOUT_MS * 3)
        .rxSendJson(requestBody)
        .map(response -> {
          if (response.statusCode() == 200) {
            JsonObject body = response.bodyAsJsonObject();
            return MLTrendAnalysisResponse.builder()
                .trendDirection(body.getString("trend_direction"))
                .trendStrength(body.getDouble("trend_strength"))
                .statisticalSignificance(body.getBoolean("statistical_significance"))
                .deviationFromExpected(body.getDouble("deviation_from_expected"))
                .isAnomaly(body.getBoolean("is_anomaly"))
                .currentMean(body.getDouble("current_mean"))
                .expectedValue(body.containsKey("expected_value") ? 
                    body.getDouble("expected_value") : null)
                .pValue(body.containsKey("p_value") ? 
                    body.getDouble("p_value") : null)
                .build();
          } else {
            throw new RuntimeException("Trend analysis failed: " + response.statusCode());
          }
        });
  }
  
  private JsonObject mapFeaturesToJson(UserChurnFeatures features) {
    return new JsonObject()
        .put("user_id", features.getUserId() != null ? features.getUserId() : "")
        .put("sessions_last_7_days", features.getSessionsLast7Days() != null ? features.getSessionsLast7Days() : 0)
        .put("sessions_last_30_days", features.getSessionsLast30Days() != null ? features.getSessionsLast30Days() : 0)
        .put("days_since_last_session", features.getDaysSinceLastSession() != null ? features.getDaysSinceLastSession() : 0)
        .put("avg_session_duration", features.getAvgSessionDuration() != null ? features.getAvgSessionDuration() : 0L)
        .put("unique_screens_last_7_days", features.getUniqueScreensLast7Days() != null ? features.getUniqueScreensLast7Days() : 0)
        .put("crash_count_last_7_days", features.getCrashCountLast7Days() != null ? features.getCrashCountLast7Days() : 0)
        .put("anr_count_last_7_days", features.getAnrCountLast7Days() != null ? features.getAnrCountLast7Days() : 0)
        .put("frozen_frame_rate", features.getFrozenFrameRate() != null ? features.getFrozenFrameRate() : 0.0)
        .put("device_model", features.getDeviceModel() != null ? features.getDeviceModel() : "")
        .put("os_version", features.getOsVersion() != null ? features.getOsVersion() : "")
        .put("app_version", features.getAppVersion() != null ? features.getAppVersion() : "");
  }
  
  /**
   * Check if ML service is healthy
   */
  public Single<Boolean> healthCheck() {
    String url = getMLServiceUrl() + "/health";
    
    return webClient
        .getAbs(url)
        .timeout(2000) // 2 second timeout for health check
        .rxSend()
        .map(response -> {
          if (response.statusCode() == 200) {
            JsonObject body = response.bodyAsJsonObject();
            return body.getBoolean("model_loaded", false);
          }
          return false;
        })
        .onErrorReturn(error -> {
          log.warn("ML service health check failed: {}", error.getMessage());
          return false;
        });
  }
}

