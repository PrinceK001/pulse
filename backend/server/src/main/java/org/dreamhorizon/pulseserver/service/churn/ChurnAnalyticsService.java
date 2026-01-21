package org.dreamhorizon.pulseserver.service.churn;

import com.google.inject.Inject;
import io.reactivex.rxjava3.core.Single;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.client.ml.*;
import org.dreamhorizon.pulseserver.service.churn.models.ChurnAnalyticsResponse;
import org.dreamhorizon.pulseserver.service.churn.models.ChurnPredictionRequest;
import org.dreamhorizon.pulseserver.service.churn.models.ChurnRiskUser;
import org.dreamhorizon.pulseserver.service.churn.models.UserChurnFeatures;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class ChurnAnalyticsService {

  private final ChurnFeatureExtractor featureExtractor;
  private final ChurnRiskCalculator riskCalculator;
  private final ChurnPredictionService predictionService;
  private final MLPredictionClient mlClient;

  // Cardinality control constants
  private static final int MAX_SEGMENTS_PER_DIMENSION = 15;
  private static final int MIN_SEGMENT_SIZE_ABSOLUTE = 50; // Minimum users per segment
  private static final double MIN_SEGMENT_PERCENTAGE = 0.5; // 0.5% of sample size
  private static final int MAX_RISK_FACTORS = 15;
  private static final int MIN_USERS_PER_RISK_FACTOR = 10; // At least 10 users
  private static final double MIN_RISK_FACTOR_PERCENTAGE = 0.1; // 0.1% of users
  private static final int MAX_SAMPLE_SIZE_FOR_ANALYTICS = 2000; // Reduced for ML analysis
  private static final int STRATIFIED_SAMPLING_THRESHOLD = 50000; // Use sampling if more than this
  private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

  /**
   * Get comprehensive analytics for large user base (100k+)
   * Returns aggregated insights, patterns, and actionable metrics
   * NOW USES ML-DRIVEN ANALYSIS instead of static rules
   */
  public Single<ChurnAnalyticsResponse> getChurnAnalytics(ChurnPredictionRequest request) {
    log.info("Getting churn analytics for large user base");

    // Sample users for ML analysis (1-2k is sufficient for statistical validity)
    int sampleSize = request.getLimit() != null ? 
        Math.min(request.getLimit(), MAX_SAMPLE_SIZE_FOR_ANALYTICS) : 
        MAX_SAMPLE_SIZE_FOR_ANALYTICS;
    
    ChurnPredictionRequest analyticsRequest = ChurnPredictionRequest.builder()
        .riskLevel(request.getRiskLevel())
        .minRiskScore(request.getMinRiskScore())
        .limit(sampleSize)
        .build();

    // Extract features first (needed for ML analysis)
    return featureExtractor.extractUserFeatures(analyticsRequest)
        .flatMap(features -> {
          if (features.isEmpty()) {
            return Single.just(buildEmptyResponse());
          }

          // Apply stratified sampling if needed
          final List<UserChurnFeatures> featuresToProcess;
          if (features.size() > STRATIFIED_SAMPLING_THRESHOLD) {
            log.info("Large dataset detected ({} users), applying stratified sampling to {}", 
                features.size(), MAX_SAMPLE_SIZE_FOR_ANALYTICS);
            featuresToProcess = stratifiedSampleFeatures(features, MAX_SAMPLE_SIZE_FOR_ANALYTICS);
          } else {
            featuresToProcess = features;
          }

          // Use ML batch prediction (1 API call instead of N)
          final List<UserChurnFeatures> finalFeaturesToProcess = featuresToProcess;
          return mlClient.predictChurnBatch(finalFeaturesToProcess)
              .flatMap(predictions -> {
                // Get ML-driven insights in parallel
                Single<MLRootCauseAnalysisResponse> rootCausesSingle = 
                    mlClient.analyzeRootCauses(finalFeaturesToProcess);
                Single<MLPatternDiscoveryResponse> patternsSingle = 
                    mlClient.discoverPatterns(finalFeaturesToProcess);
                
                // Combine root causes and patterns
                return Single.zip(
                    rootCausesSingle,
                    patternsSingle,
                    (rootCauses, patterns) -> {
                      // Try to get trend analysis, but don't fail if unavailable
                      try {
                        // For now, skip historical data (can be added later with date filtering)
                        return buildEnhancedResponse(
                            finalFeaturesToProcess, predictions, rootCauses, patterns, null, null
                        );
                      } catch (Exception e) {
                        log.warn("Error building enhanced response: {}", e.getMessage());
                        return buildEnhancedResponse(
                            finalFeaturesToProcess, predictions, rootCauses, patterns, null, null
                        );
                      }
                    }
                );
              })
              .onErrorResumeNext(error -> {
                log.warn("ML analysis failed, falling back to basic analytics: {}", error.getMessage());
                // Fallback to basic analytics without ML insights
                return mlClient.predictChurnBatch(finalFeaturesToProcess)
                    .map(predictions -> {
                      return buildBasicResponse(finalFeaturesToProcess, predictions);
                    });
              });
        })
        .doOnError(error -> log.error("Error getting churn analytics", error));
  }
  
  /**
   * Build enhanced response with ML-driven insights
   */
  private ChurnAnalyticsResponse buildEnhancedResponse(
      List<UserChurnFeatures> features,
      List<MLPredictionResponse> predictions,
      MLRootCauseAnalysisResponse rootCauses,
      MLPatternDiscoveryResponse patterns,
      MLTrendAnalysisResponse trends,
      List<MLPredictionResponse> historicalPredictions) {
    
    // Convert ML predictions to ChurnRiskUser for compatibility
    List<ChurnRiskUser> users = new ArrayList<>();
    for (int i = 0; i < features.size() && i < predictions.size(); i++) {
      UserChurnFeatures f = features.get(i);
      MLPredictionResponse p = predictions.get(i);
      
      users.add(ChurnRiskUser.builder()
          .userId(f.getUserId())
          .riskScore(p.getRiskScore())
          .riskLevel(p.getRiskLevel())
          .churnProbability(p.getChurnProbability())
          .daysSinceLastSession(f.getDaysSinceLastSession())
          .sessionsLast7Days(f.getSessionsLast7Days())
          .sessionsLast30Days(f.getSessionsLast30Days())
          .crashCountLast7Days(f.getCrashCountLast7Days())
          .anrCountLast7Days(f.getAnrCountLast7Days())
          .frozenFrameRate(f.getFrozenFrameRate())
          .avgSessionDuration(f.getAvgSessionDuration())
          .uniqueScreensLast7Days(f.getUniqueScreensLast7Days())
          .deviceModel(f.getDeviceModel())
          .osVersion(f.getOsVersion())
          .appVersion(f.getAppVersion())
          .primaryRiskFactors(List.of()) // Will be populated from ML insights
          .build());
    }
    
    // Calculate basic metrics
    double avgRiskScore = predictions.stream()
        .mapToInt(MLPredictionResponse::getRiskScore)
        .average()
        .orElse(0.0);
    
    long highRiskCount = predictions.stream()
        .filter(p -> p.getRiskScore() >= 70)
        .count();
    long mediumRiskCount = predictions.stream()
        .filter(p -> p.getRiskScore() >= 40 && p.getRiskScore() < 70)
        .count();
    long lowRiskCount = predictions.stream()
        .filter(p -> p.getRiskScore() < 40)
        .count();
    
    // Build ML-driven root cause analysis
    ChurnAnalyticsResponse.RootCauseAnalysis rootCauseAnalysis = null;
    if (rootCauses != null) {
      List<ChurnAnalyticsResponse.RootCause> causes = rootCauses.getRootCauses().stream()
          .map(rc -> {
            // Map ML feature names to human-readable causes
            String cause = mapFeatureToCause(rc.getFeature());
            int affectedUsers = estimateAffectedUsers(features, rc.getFeature());
            
            return ChurnAnalyticsResponse.RootCause.builder()
                .cause(cause)
                .affectedUserCount(affectedUsers)
                .averageSeverity(rc.getImportance() * 100) // Convert to 0-100 scale
                .impactScore(affectedUsers * rc.getImportance() * 100)
                .importance(rc.getImportance())
                .correlationWithHighRisk(rc.getCorrelationWithHighRisk())
                .affectedSegments(identifyAffectedSegments(features, rc.getFeature()))
                .recommendedFix(generateFixRecommendation(rc.getFeature()))
                .estimatedChurnReduction(rc.getImportance() * 30.0) // Estimate 30% reduction if fixed
                .build();
          })
          .sorted((a, b) -> Double.compare(b.getImpactScore(), a.getImpactScore()))
          .limit(10)
          .collect(Collectors.toList());
      
      rootCauseAnalysis = ChurnAnalyticsResponse.RootCauseAnalysis.builder()
          .primaryCauses(causes)
          .aggregateFeatureImportance(convertMap(rootCauses.getAggregateImportance()))
          .correlations(convertMap(rootCauses.getCorrelations()))
          .totalAtRiskUsers((int) highRiskCount)
          .build();
    }
    
    // Build priority fixes from root causes
    List<ChurnAnalyticsResponse.PriorityFix> priorityFixes = new ArrayList<>();
    if (rootCauseAnalysis != null) {
      priorityFixes = rootCauseAnalysis.getPrimaryCauses().stream()
          .map(cause -> {
            int priority = calculatePriority(cause);
            return ChurnAnalyticsResponse.PriorityFix.builder()
                .issue(cause.getCause())
                .priority(priority)
                .estimatedAffectedUsers(cause.getAffectedUserCount())
                .impactScore(cause.getImpactScore())
                .fixDescription(cause.getRecommendedFix())
                .estimatedEffort(estimateEffort(cause.getCause()))
                .estimatedChurnReduction(cause.getEstimatedChurnReduction())
                .affectedSegments(cause.getAffectedSegments())
                .pattern(cause.getCause())
                .build();
          })
          .sorted((a, b) -> Integer.compare(b.getPriority(), a.getPriority()))
          .limit(10)
          .collect(Collectors.toList());
    }
    
    // Build trend analysis
    ChurnAnalyticsResponse.TrendAnalysis trendAnalysis = null;
    if (trends != null) {
      ChurnAnalyticsResponse.TrendMetrics currentMetrics = ChurnAnalyticsResponse.TrendMetrics.builder()
          .averageRiskScore(avgRiskScore)
          .highRiskUserCount((int) highRiskCount)
          .totalUsers(features.size())
          .riskDistribution(calculateRiskDistribution(users))
          .topRiskFactors(calculateTopRiskFactorsMap(users))
          .build();
      
      ChurnAnalyticsResponse.TrendMetrics previousMetrics = null;
      if (historicalPredictions != null && !historicalPredictions.isEmpty()) {
        double prevAvgRisk = historicalPredictions.stream()
            .mapToInt(MLPredictionResponse::getRiskScore)
            .average()
            .orElse(0.0);
        
        previousMetrics = ChurnAnalyticsResponse.TrendMetrics.builder()
            .averageRiskScore(prevAvgRisk)
            .highRiskUserCount((int) historicalPredictions.stream()
                .filter(p -> p.getRiskScore() >= 70)
                .count())
            .totalUsers(historicalPredictions.size())
            .build();
      }
      
      double trendDirection = previousMetrics != null ? 
          ((currentMetrics.getAverageRiskScore() - previousMetrics.getAverageRiskScore()) / 
           previousMetrics.getAverageRiskScore() * 100) : 0.0;
      
      List<ChurnAnalyticsResponse.Anomaly> anomalies = new ArrayList<>();
      if (trends.getIsAnomaly()) {
        anomalies.add(ChurnAnalyticsResponse.Anomaly.builder()
            .type("SPIKE")
            .detectedAt(LocalDateTime.now().format(DATE_FORMATTER))
            .description(String.format("Churn risk %s by %.1f%%", 
                trends.getTrendDirection(), Math.abs(trendDirection)))
            .severity(trends.getTrendStrength())
            .potentialCause("Detected by ML anomaly detection")
            .build());
      }
      
      trendAnalysis = ChurnAnalyticsResponse.TrendAnalysis.builder()
          .currentPeriod(currentMetrics)
          .previousPeriod(previousMetrics)
          .trendDirection(trendDirection)
          .anomalies(anomalies)
          .isAnomaly(trends.getIsAnomaly())
          .trendDirectionLabel(trends.getTrendDirection())
          .trendStrength(trends.getTrendStrength())
          .statisticalSignificance(trends.getStatisticalSignificance())
          .build();
    }
    
    // Build pattern insights
    ChurnAnalyticsResponse.PatternInsights patternInsights = null;
    if (patterns != null && patterns.getPatterns() != null) {
      List<ChurnAnalyticsResponse.ChurnPattern> churnPatterns = patterns.getPatterns().stream()
          .map(p -> {
            // Extract key indicators from characteristics
            List<String> indicators = new ArrayList<>();
            if (p.getCharacteristics() != null) {
              Map<String, Object> chars = p.getCharacteristics();
              if (chars.containsKey("key_indicators")) {
                @SuppressWarnings("unchecked")
                List<String> keys = (List<String>) chars.get("key_indicators");
                if (keys != null) {
                  indicators.addAll(keys);
                }
              }
            }
            
            return ChurnAnalyticsResponse.ChurnPattern.builder()
                .pattern(String.join(" + ", indicators.isEmpty() ? List.of("unknown") : indicators))
                .userCount(p.getUserCount())
                .averageRiskScore(p.getAvgRiskScore())
                .churnProbability(p.getAvgChurnProbability())
                .commonSegments(identifyCommonSegments(features, p.getPatternId()))
                .characteristics(p.getCharacteristics())
                .build();
          })
          .collect(Collectors.toList());
      
      patternInsights = ChurnAnalyticsResponse.PatternInsights.builder()
          .commonPatterns(churnPatterns)
          .segmentRiskPatterns(calculateSegmentRiskPatterns(users))
          .build();
    }
    
    // Build final response
    return ChurnAnalyticsResponse.builder()
        .totalUsers(features.size())
        .highRiskCount((int) highRiskCount)
        .mediumRiskCount((int) mediumRiskCount)
        .lowRiskCount((int) lowRiskCount)
        .averageRiskScore(avgRiskScore)
        .overallChurnProbability(predictions.stream()
            .mapToDouble(MLPredictionResponse::getChurnProbability)
            .average()
            .orElse(0.0))
        .riskDistribution(calculateRiskDistribution(users))
        .topRiskFactors(calculateTopRiskFactors(users))
        .deviceSegments(calculateSegmentStats(users, u -> u.getDeviceModel()))
        .osSegments(calculateSegmentStats(users, u -> u.getOsVersion()))
        .appVersionSegments(calculateSegmentStats(users, u -> u.getAppVersion()))
        .performanceImpact(calculatePerformanceImpact(users))
        .engagementPatterns(calculateEngagementPatterns(users))
        .rootCauseAnalysis(rootCauseAnalysis)
        .priorityFixes(priorityFixes)
        .trendAnalysis(trendAnalysis)
        .patternInsights(patternInsights)
        .build();
  }
  
  // Helper methods for ML-driven analysis
  private String mapFeatureToCause(String feature) {
    Map<String, String> mapping = Map.of(
        "days_since", "No session in extended period",
        "engagement_decline", "Declining engagement",
        "performance_score", "Performance issues",
        "crash_count", "High crash rate",
        "anr_count", "ANR issues",
        "frozen_rate", "High frozen frame rate",
        "sessions_7d", "Low session frequency",
        "sessions_30d", "Low overall engagement"
    );
    return mapping.getOrDefault(feature, feature);
  }
  
  private int estimateAffectedUsers(List<UserChurnFeatures> features, String featureName) {
    // Estimate based on feature values
    return (int) features.stream()
        .filter(f -> hasHighFeatureValue(f, featureName))
        .count();
  }
  
  private boolean hasHighFeatureValue(UserChurnFeatures f, String featureName) {
    switch (featureName) {
      case "days_since":
        return f.getDaysSinceLastSession() != null && f.getDaysSinceLastSession() >= 14;
      case "crash_count":
        return f.getCrashCountLast7Days() != null && f.getCrashCountLast7Days() >= 2;
      case "anr_count":
        return f.getAnrCountLast7Days() != null && f.getAnrCountLast7Days() >= 1;
      case "frozen_rate":
        return f.getFrozenFrameRate() != null && f.getFrozenFrameRate() > 0.2;
      case "sessions_7d":
        return f.getSessionsLast7Days() != null && f.getSessionsLast7Days() == 0;
      default:
        return false;
    }
  }
  
  private List<String> identifyAffectedSegments(List<UserChurnFeatures> features, String featureName) {
    Set<String> segments = new HashSet<>();
    features.stream()
        .filter(f -> hasHighFeatureValue(f, featureName))
        .forEach(f -> {
          if (f.getAppVersion() != null) segments.add(f.getAppVersion());
          if (f.getDeviceModel() != null) segments.add(f.getDeviceModel());
          if (f.getOsVersion() != null) segments.add(f.getOsVersion());
        });
    return new ArrayList<>(segments).subList(0, Math.min(5, segments.size()));
  }
  
  private String generateFixRecommendation(String featureName) {
    Map<String, String> recommendations = Map.of(
        "crash_count", "Fix crashes in affected app versions",
        "anr_count", "Optimize app performance to reduce ANRs",
        "frozen_rate", "Improve UI rendering performance",
        "days_since", "Launch re-engagement campaign",
        "sessions_7d", "Improve onboarding and feature discovery",
        "engagement_decline", "Analyze user journey and optimize key flows"
    );
    return recommendations.getOrDefault(featureName, "Investigate and address root cause");
  }
  
  private int calculatePriority(ChurnAnalyticsResponse.RootCause cause) {
    // Priority based on impact score (1-10)
    double normalizedImpact = cause.getImpactScore() / 10000.0; // Normalize
    return Math.min(10, Math.max(1, (int) (normalizedImpact * 10)));
  }
  
  private String estimateEffort(String cause) {
    if (cause.contains("crash") || cause.contains("ANR")) return "Medium";
    if (cause.contains("frozen")) return "High";
    if (cause.contains("engagement") || cause.contains("session")) return "Low";
    return "Medium";
  }
  
  private List<String> identifyCommonSegments(List<UserChurnFeatures> features, Integer patternId) {
    // This would need pattern ID mapping - simplified for now
    return List.of();
  }
  
  private Map<String, Double> calculateSegmentRiskPatterns(List<ChurnRiskUser> users) {
    // Group by segment and calculate average risk
    return users.stream()
        .collect(Collectors.groupingBy(
            u -> u.getDeviceModel() != null ? u.getDeviceModel() : "Unknown",
            Collectors.averagingInt(ChurnRiskUser::getRiskScore)
        ));
  }
  
  private Map<String, Double> calculateTopRiskFactorsMap(List<ChurnRiskUser> users) {
    Map<String, Long> factorCounts = new HashMap<>();
    for (ChurnRiskUser user : users) {
      if (user.getPrimaryRiskFactors() != null) {
        for (String factor : user.getPrimaryRiskFactors()) {
          factorCounts.merge(factor, 1L, Long::sum);
        }
      }
    }
    
    int total = users.size();
    return factorCounts.entrySet().stream()
        .collect(Collectors.toMap(
            Map.Entry::getKey,
            e -> (double) e.getValue() / total * 100
        ));
  }
  
  private Map<String, Double> convertMap(Map<String, Object> input) {
    return input.entrySet().stream()
        .collect(Collectors.toMap(
            Map.Entry::getKey,
            e -> {
              Object value = e.getValue();
              if (value instanceof Number) {
                return ((Number) value).doubleValue();
              }
              return 0.0;
            }
        ));
  }
  
  private List<UserChurnFeatures> stratifiedSampleFeatures(
      List<UserChurnFeatures> features, int sampleSize) {
    // Simplified stratified sampling - in production, would group by risk level first
    Collections.shuffle(features);
    return features.subList(0, Math.min(sampleSize, features.size()));
  }
  
  /**
   * Build basic response without ML insights (fallback)
   */
  private ChurnAnalyticsResponse buildBasicResponse(
      List<UserChurnFeatures> features,
      List<MLPredictionResponse> predictions) {
    
    List<ChurnRiskUser> users = new ArrayList<>();
    for (int i = 0; i < features.size() && i < predictions.size(); i++) {
      UserChurnFeatures f = features.get(i);
      MLPredictionResponse p = predictions.get(i);
      
      users.add(ChurnRiskUser.builder()
          .userId(f.getUserId())
          .riskScore(p.getRiskScore())
          .riskLevel(p.getRiskLevel())
          .churnProbability(p.getChurnProbability())
          .daysSinceLastSession(f.getDaysSinceLastSession())
          .sessionsLast7Days(f.getSessionsLast7Days())
          .sessionsLast30Days(f.getSessionsLast30Days())
          .crashCountLast7Days(f.getCrashCountLast7Days())
          .anrCountLast7Days(f.getAnrCountLast7Days())
          .frozenFrameRate(f.getFrozenFrameRate())
          .avgSessionDuration(f.getAvgSessionDuration())
          .uniqueScreensLast7Days(f.getUniqueScreensLast7Days())
          .deviceModel(f.getDeviceModel())
          .osVersion(f.getOsVersion())
          .appVersion(f.getAppVersion())
          .primaryRiskFactors(List.of())
          .build());
    }
    
    double avgRiskScore = predictions.stream()
        .mapToInt(MLPredictionResponse::getRiskScore)
        .average()
        .orElse(0.0);
    
    long highRiskCount = predictions.stream()
        .filter(p -> p.getRiskScore() >= 70)
        .count();
    long mediumRiskCount = predictions.stream()
        .filter(p -> p.getRiskScore() >= 40 && p.getRiskScore() < 70)
        .count();
    long lowRiskCount = predictions.stream()
        .filter(p -> p.getRiskScore() < 40)
        .count();
    
    return ChurnAnalyticsResponse.builder()
        .totalUsers(features.size())
        .highRiskCount((int) highRiskCount)
        .mediumRiskCount((int) mediumRiskCount)
        .lowRiskCount((int) lowRiskCount)
        .averageRiskScore(avgRiskScore)
        .overallChurnProbability(predictions.stream()
            .mapToDouble(MLPredictionResponse::getChurnProbability)
            .average()
            .orElse(0.0))
        .riskDistribution(calculateRiskDistribution(users))
        .topRiskFactors(calculateTopRiskFactors(users))
        .deviceSegments(calculateSegmentStats(users, u -> u.getDeviceModel()))
        .osSegments(calculateSegmentStats(users, u -> u.getOsVersion()))
        .appVersionSegments(calculateSegmentStats(users, u -> u.getAppVersion()))
        .performanceImpact(calculatePerformanceImpact(users))
        .engagementPatterns(calculateEngagementPatterns(users))
        .build();
  }
  
  // Keep existing methods for backward compatibility
  public Single<ChurnAnalyticsResponse> getChurnAnalyticsLegacy(ChurnPredictionRequest request) {
    return predictionService.getChurnPredictions(request)
        .map(response -> {
          List<ChurnRiskUser> users = response.getUsers();
          
          if (users.isEmpty()) {
            return buildEmptyResponse();
          }

          // Apply stratified sampling if dataset is very large
          List<ChurnRiskUser> usersToProcess = users;
          if (users.size() > STRATIFIED_SAMPLING_THRESHOLD) {
            log.info("Large dataset detected ({} users), applying stratified sampling to {}", 
                users.size(), MAX_SAMPLE_SIZE_FOR_ANALYTICS);
            usersToProcess = stratifiedSample(users, MAX_SAMPLE_SIZE_FOR_ANALYTICS);
          }

          // Calculate overall metrics (use original count for totals, processed for calculations)
          double avgRiskScore = usersToProcess.stream()
              .mapToInt(ChurnRiskUser::getRiskScore)
              .average()
              .orElse(0.0);
          
          double avgChurnProbability = usersToProcess.stream()
              .mapToDouble(u -> u.getChurnProbability() != null ? u.getChurnProbability() : 0.0)
              .average()
              .orElse(0.0);

          // Risk distribution
          Map<String, Integer> riskDistribution = calculateRiskDistribution(usersToProcess);

          // Top risk factors (aggregated)
          List<ChurnAnalyticsResponse.RiskFactorFrequency> topRiskFactors = 
              calculateTopRiskFactors(usersToProcess);

          // Segment analysis with cardinality control
          Map<String, ChurnAnalyticsResponse.SegmentStats> deviceSegments = 
              calculateSegmentStats(usersToProcess, u -> u.getDeviceModel());
          Map<String, ChurnAnalyticsResponse.SegmentStats> osSegments = 
              calculateSegmentStats(usersToProcess, u -> u.getOsVersion());
          Map<String, ChurnAnalyticsResponse.SegmentStats> appVersionSegments = 
              calculateSegmentStats(usersToProcess, u -> u.getAppVersion());

          // Performance impact
          ChurnAnalyticsResponse.PerformanceImpactMetrics performanceImpact = 
              calculatePerformanceImpact(usersToProcess);

          // Engagement patterns
          ChurnAnalyticsResponse.EngagementPatternMetrics engagementPatterns = 
              calculateEngagementPatterns(usersToProcess);

          return ChurnAnalyticsResponse.builder()
              .totalUsers(response.getTotalUsers())
              .highRiskCount(response.getHighRiskCount())
              .mediumRiskCount(response.getMediumRiskCount())
              .lowRiskCount(response.getLowRiskCount())
              .averageRiskScore(avgRiskScore)
              .overallChurnProbability(avgChurnProbability)
              .riskDistribution(riskDistribution)
              .topRiskFactors(topRiskFactors)
              .deviceSegments(deviceSegments)
              .osSegments(osSegments)
              .appVersionSegments(appVersionSegments)
              .performanceImpact(performanceImpact)
              .engagementPatterns(engagementPatterns)
              .build();
        })
        .doOnError(error -> log.error("Error getting churn analytics", error));
  }

  private Map<String, Integer> calculateRiskDistribution(List<ChurnRiskUser> users) {
    Map<String, Integer> distribution = new LinkedHashMap<>();
    distribution.put("0-20", 0);
    distribution.put("20-40", 0);
    distribution.put("40-60", 0);
    distribution.put("60-80", 0);
    distribution.put("80-100", 0);

    for (ChurnRiskUser user : users) {
      int score = user.getRiskScore();
      if (score < 20) {
        distribution.put("0-20", distribution.get("0-20") + 1);
      } else if (score < 40) {
        distribution.put("20-40", distribution.get("20-40") + 1);
      } else if (score < 60) {
        distribution.put("40-60", distribution.get("40-60") + 1);
      } else if (score < 80) {
        distribution.put("60-80", distribution.get("60-80") + 1);
      } else {
        distribution.put("80-100", distribution.get("80-100") + 1);
      }
    }

    return distribution;
  }

  private List<ChurnAnalyticsResponse.RiskFactorFrequency> calculateTopRiskFactors(
      List<ChurnRiskUser> users) {
    Map<String, Integer> factorCounts = new HashMap<>();
    Map<String, Double> factorSeveritySum = new HashMap<>();
    Map<String, Integer> factorOccurrences = new HashMap<>();

    for (ChurnRiskUser user : users) {
      if (user.getPrimaryRiskFactors() != null) {
        for (String factor : user.getPrimaryRiskFactors()) {
          // Normalize similar risk factors to reduce cardinality
          String normalizedFactor = normalizeRiskFactor(factor);
          
          factorCounts.put(normalizedFactor, factorCounts.getOrDefault(normalizedFactor, 0) + 1);
          factorSeveritySum.put(normalizedFactor, 
              factorSeveritySum.getOrDefault(normalizedFactor, 0.0) + user.getRiskScore());
          factorOccurrences.put(normalizedFactor, 
              factorOccurrences.getOrDefault(normalizedFactor, 0) + 1);
        }
      }
    }

    int totalUsers = users.size();
    int minUsersForFactor = Math.max(MIN_USERS_PER_RISK_FACTOR, 
        (int) (totalUsers * MIN_RISK_FACTOR_PERCENTAGE / 100.0));
    
    return factorCounts.entrySet().stream()
        .filter(entry -> entry.getValue() >= minUsersForFactor) // Filter low-frequency factors
        .map(entry -> {
          String factor = entry.getKey();
          int count = entry.getValue();
          double avgRisk = factorSeveritySum.get(factor) / factorOccurrences.get(factor);
          String severity = avgRisk >= 70 ? "HIGH" : avgRisk >= 40 ? "MEDIUM" : "LOW";
          
          return ChurnAnalyticsResponse.RiskFactorFrequency.builder()
              .factor(factor)
              .userCount(count)
              .percentage((double) count / totalUsers * 100)
              .severity(severity)
              .build();
        })
        .sorted((a, b) -> {
          // Sort by user count first, then by severity (HIGH > MEDIUM > LOW)
          int countCompare = Integer.compare(b.getUserCount(), a.getUserCount());
          if (countCompare != 0) return countCompare;
          return b.getSeverity().compareTo(a.getSeverity());
        })
        .limit(MAX_RISK_FACTORS)
        .collect(Collectors.toList());
  }

  /**
   * Normalize risk factors to reduce cardinality
   * Groups similar factors together (e.g., "No session in 30 days" and "No session in 31 days")
   */
  private String normalizeRiskFactor(String factor) {
    if (factor == null || factor.trim().isEmpty()) {
      return "Unknown";
    }
    
    String normalized = factor.trim();
    
    // Normalize "No session in X days" patterns
    if (normalized.contains("No session in") || normalized.contains("no session")) {
      String numberStr = normalized.replaceAll("[^0-9]", "");
      if (!numberStr.isEmpty()) {
        try {
          int days = Integer.parseInt(numberStr);
          if (days >= 30) {
            return "No session in 30+ days";
          } else if (days >= 14) {
            return "No session in 14-29 days";
          } else if (days >= 7) {
            return "No session in 7-13 days";
          } else if (days >= 3) {
            return "No session in 3-6 days";
          }
        } catch (NumberFormatException e) {
          // Keep original if parsing fails
        }
      }
    }
    
    // Normalize crash counts
    if (normalized.toLowerCase().contains("crash")) {
      String numberStr = normalized.replaceAll("[^0-9]", "");
      if (!numberStr.isEmpty()) {
        try {
          int crashes = Integer.parseInt(numberStr);
          if (crashes >= 3) {
            return "3+ crashes in last 7 days";
          } else if (crashes >= 2) {
            return "2 crashes in last 7 days";
          } else if (crashes >= 1) {
            return "1 crash in last 7 days";
          }
        } catch (NumberFormatException e) {
          // Keep original if parsing fails
        }
      }
    }
    
    // Normalize ANR counts
    if (normalized.toLowerCase().contains("anr")) {
      String numberStr = normalized.replaceAll("[^0-9]", "");
      if (!numberStr.isEmpty()) {
        try {
          int anrs = Integer.parseInt(numberStr);
          if (anrs >= 3) {
            return "3+ ANRs in last 7 days";
          } else if (anrs >= 2) {
            return "2 ANRs in last 7 days";
          } else if (anrs >= 1) {
            return "1 ANR in last 7 days";
          }
        } catch (NumberFormatException e) {
          // Keep original if parsing fails
        }
      }
    }
    
    // Normalize session frequency decline
    if (normalized.contains("declined by") || normalized.contains("decline")) {
      String numberStr = normalized.replaceAll("[^0-9]", "");
      if (!numberStr.isEmpty()) {
        try {
          int declinePercent = Integer.parseInt(numberStr);
          if (declinePercent >= 70) {
            return "Session frequency declined by 70%+";
          } else if (declinePercent >= 50) {
            return "Session frequency declined by 50-69%";
          } else if (declinePercent >= 30) {
            return "Session frequency declined by 30-49%";
          }
        } catch (NumberFormatException e) {
          // Keep original if parsing fails
        }
      }
    }
    
    // Normalize frozen frame rate
    if (normalized.toLowerCase().contains("frozen frame")) {
      String numberStr = normalized.replaceAll("[^0-9.]", "");
      if (!numberStr.isEmpty()) {
        try {
          double rate = Double.parseDouble(numberStr);
          if (rate >= 20) {
            return "High frozen frame rate: 20%+";
          } else if (rate >= 10) {
            return "High frozen frame rate: 10-19%";
          } else if (rate >= 5) {
            return "Moderate frozen frame rate: 5-9%";
          }
        } catch (NumberFormatException e) {
          // Keep original if parsing fails
        }
      }
    }
    
    // Normalize session duration
    if (normalized.toLowerCase().contains("session duration")) {
      String numberStr = normalized.replaceAll("[^0-9]", "");
      if (!numberStr.isEmpty()) {
        try {
          int seconds = Integer.parseInt(numberStr);
          if (seconds < 10) {
            return "Very short session duration: <10s";
          } else if (seconds < 30) {
            return "Short session duration: 10-29s";
          } else if (seconds < 60) {
            return "Short session duration: 30-59s";
          }
        } catch (NumberFormatException e) {
          // Keep original if parsing fails
        }
      }
    }
    
    return normalized;
  }

  private Map<String, ChurnAnalyticsResponse.SegmentStats> calculateSegmentStats(
      List<ChurnRiskUser> users,
      java.util.function.Function<ChurnRiskUser, String> segmentKey) {
    
    int totalUsers = users.size();
    int minUsersForSegment = Math.max(
        MIN_SEGMENT_SIZE_ABSOLUTE, 
        (int) (totalUsers * MIN_SEGMENT_PERCENTAGE / 100.0)
    );
    
    log.debug("Calculating segment stats: totalUsers={}, minUsersForSegment={}", 
        totalUsers, minUsersForSegment);
    
    // Normalize segment keys and group
    Map<String, List<ChurnRiskUser>> grouped = users.stream()
        .collect(Collectors.groupingBy(
            u -> {
              String key = segmentKey.apply(u);
              return normalizeSegmentKey(key);
            }
        ));

    Map<String, ChurnAnalyticsResponse.SegmentStats> stats = new HashMap<>();
    List<ChurnRiskUser> otherUsers = new ArrayList<>();
    
    // Process segments and collect small ones into "Other" bucket
    grouped.forEach((segment, segmentUsers) -> {
      if (segmentUsers.isEmpty()) return;
      
      // If segment is too small, add to "Other" bucket
      if (segmentUsers.size() < minUsersForSegment) {
        otherUsers.addAll(segmentUsers);
        return;
      }

      ChurnAnalyticsResponse.SegmentStats segmentStats = buildSegmentStats(segmentUsers);
      stats.put(segment, segmentStats);
    });
    
    // Add "Other" bucket if there are small segments
    if (!otherUsers.isEmpty()) {
      log.debug("Aggregating {} small segments into 'Other' bucket ({} users)", 
          grouped.size() - stats.size(), otherUsers.size());
      stats.put("Other", buildSegmentStats(otherUsers));
    }

    // Sort by user count, then by risk score (prioritize high-risk segments)
    return stats.entrySet().stream()
        .sorted((a, b) -> {
          // First sort by user count (descending)
          int countCompare = Integer.compare(b.getValue().getUserCount(), a.getValue().getUserCount());
          if (countCompare != 0) return countCompare;
          // Then by average risk score (descending) - prioritize high-risk segments
          return Double.compare(b.getValue().getAverageRiskScore(), a.getValue().getAverageRiskScore());
        })
        .limit(MAX_SEGMENTS_PER_DIMENSION)
        .collect(Collectors.toMap(
            Map.Entry::getKey,
            Map.Entry::getValue,
            (a, b) -> a,
            LinkedHashMap::new
        ));
  }

  /**
   * Normalize segment keys to reduce cardinality
   * Groups similar devices/versions together
   */
  private String normalizeSegmentKey(String key) {
    if (key == null || key.trim().isEmpty()) {
      return "Unknown";
    }
    
    String normalized = key.trim();
    
    // Normalize device models - group iPhone variants
    if (normalized.toLowerCase().contains("iphone")) {
      // Extract major version (e.g., "iPhone 12" from "iPhone 12 Pro Max")
      String[] parts = normalized.split(" ");
      if (parts.length >= 2) {
        try {
          // Check if second part is a number (version)
          Integer.parseInt(parts[1]);
          return parts[0] + " " + parts[1] + " Series";
        } catch (NumberFormatException e) {
          // Not a numbered version, keep original
        }
      }
    }
    
    // Normalize Android devices - group by manufacturer and series
    if (normalized.toLowerCase().contains("samsung")) {
      // Group Galaxy S series (S10, S20, S21, etc.)
      if (normalized.toLowerCase().contains("galaxy s")) {
        String[] parts = normalized.split(" ");
        for (int i = 0; i < parts.length; i++) {
          if (parts[i].equalsIgnoreCase("S") && i + 1 < parts.length) {
            String version = parts[i + 1];
            // Extract major version number
            String majorVersion = version.replaceAll("[^0-9]", "");
            if (!majorVersion.isEmpty()) {
              return "Samsung Galaxy S" + majorVersion.charAt(0) + "0 Series";
            }
          }
        }
      }
      // Group Galaxy Note series
      if (normalized.toLowerCase().contains("galaxy note")) {
        return "Samsung Galaxy Note Series";
      }
    }
    
    // Normalize app versions - group by major.minor (e.g., "1.2.3" -> "1.2.x")
    if (normalized.matches("^v?\\d+\\.\\d+.*")) {
      String version = normalized.replaceAll("^v?", "");
      String[] parts = version.split("\\.");
      if (parts.length >= 2) {
        try {
          Integer.parseInt(parts[0]);
          Integer.parseInt(parts[1]);
          return "v" + parts[0] + "." + parts[1] + ".x";
        } catch (NumberFormatException e) {
          // Keep original if parsing fails
        }
      }
    }
    
    // Normalize OS versions - group by major version (e.g., "Android 11.0.1" -> "Android 11")
    if (normalized.toLowerCase().contains("android")) {
      String[] parts = normalized.split(" ");
      if (parts.length >= 2) {
        try {
          String version = parts[1];
          String majorVersion = version.split("\\.")[0];
          Integer.parseInt(majorVersion);
          return parts[0] + " " + majorVersion;
        } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
          // Keep original if parsing fails
        }
      }
    }
    
    // Normalize iOS versions
    if (normalized.toLowerCase().contains("ios")) {
      String[] parts = normalized.split(" ");
      if (parts.length >= 2) {
        try {
          String version = parts[1];
          String majorVersion = version.split("\\.")[0];
          Integer.parseInt(majorVersion);
          return parts[0] + " " + majorVersion;
        } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
          // Keep original if parsing fails
        }
      }
    }
    
    return normalized;
  }

  /**
   * Build segment statistics from a list of users
   */
  private ChurnAnalyticsResponse.SegmentStats buildSegmentStats(List<ChurnRiskUser> segmentUsers) {
    double avgRisk = segmentUsers.stream()
        .mapToInt(ChurnRiskUser::getRiskScore)
        .average()
        .orElse(0.0);

    long highRiskCount = segmentUsers.stream()
        .filter(u -> u.getRiskScore() >= 70)
        .count();

    double avgChurnProb = segmentUsers.stream()
        .mapToDouble(u -> u.getChurnProbability() != null ? u.getChurnProbability() : 0.0)
        .average()
        .orElse(0.0);

    // Get top risk factors for this segment (limit to avoid high cardinality)
    List<String> topFactors = calculateTopRiskFactors(segmentUsers).stream()
        .map(ChurnAnalyticsResponse.RiskFactorFrequency::getFactor)
        .limit(3)
        .collect(Collectors.toList());

    return ChurnAnalyticsResponse.SegmentStats.builder()
        .userCount(segmentUsers.size())
        .averageRiskScore(avgRisk)
        .highRiskCount((int) highRiskCount)
        .highRiskPercentage((double) highRiskCount / segmentUsers.size() * 100)
        .churnProbability(avgChurnProb)
        .topRiskFactors(topFactors)
        .build();
  }

  /**
   * Stratified sampling to ensure representation across risk levels
   * Used when dataset is very large (>50k users)
   */
  private List<ChurnRiskUser> stratifiedSample(List<ChurnRiskUser> users, int sampleSize) {
    // Group by risk level
    Map<String, List<ChurnRiskUser>> byRiskLevel = users.stream()
        .collect(Collectors.groupingBy(u -> {
          if (u.getRiskScore() >= 70) return "HIGH";
          if (u.getRiskScore() >= 40) return "MEDIUM";
          return "LOW";
        }));
    
    List<ChurnRiskUser> sampled = new ArrayList<>();
    int perLevel = sampleSize / 3; // Distribute across 3 risk levels
    
    byRiskLevel.forEach((level, levelUsers) -> {
      if (levelUsers.size() <= perLevel) {
        sampled.addAll(levelUsers);
      } else {
        // Random sample from this level
        List<ChurnRiskUser> shuffled = new ArrayList<>(levelUsers);
        Collections.shuffle(shuffled);
        sampled.addAll(shuffled.subList(0, perLevel));
      }
    });
    
    // If we have room, fill remaining slots proportionally
    if (sampled.size() < sampleSize) {
      int remaining = sampleSize - sampled.size();
      List<ChurnRiskUser> remainingUsers = users.stream()
          .filter(u -> !sampled.contains(u))
          .collect(Collectors.toList());
      Collections.shuffle(remainingUsers);
      sampled.addAll(remainingUsers.subList(0, Math.min(remaining, remainingUsers.size())));
    }
    
    log.info("Stratified sampling: {} users sampled from {} total", sampled.size(), users.size());
    return sampled;
  }

  private ChurnAnalyticsResponse.PerformanceImpactMetrics calculatePerformanceImpact(
      List<ChurnRiskUser> users) {
    
    long usersWithCrashes = users.stream()
        .filter(u -> u.getCrashCountLast7Days() != null && u.getCrashCountLast7Days() > 0)
        .count();
    
    long usersWithAnrs = users.stream()
        .filter(u -> u.getAnrCountLast7Days() != null && u.getAnrCountLast7Days() > 0)
        .count();
    
    long usersWithFrozenFrames = users.stream()
        .filter(u -> u.getFrozenFrameRate() != null && u.getFrozenFrameRate() > 0.1)
        .count();

    double avgCrashRate = users.stream()
        .mapToInt(u -> u.getCrashCountLast7Days() != null ? u.getCrashCountLast7Days() : 0)
        .average()
        .orElse(0.0);

    double avgAnrRate = users.stream()
        .mapToInt(u -> u.getAnrCountLast7Days() != null ? u.getAnrCountLast7Days() : 0)
        .average()
        .orElse(0.0);

    double avgFrozenFrameRate = users.stream()
        .mapToDouble(u -> u.getFrozenFrameRate() != null ? u.getFrozenFrameRate() : 0.0)
        .average()
        .orElse(0.0);

    // Calculate correlation: average risk score of users with performance issues vs without
    double riskWithIssues = users.stream()
        .filter(u -> (u.getCrashCountLast7Days() != null && u.getCrashCountLast7Days() > 0) ||
                     (u.getAnrCountLast7Days() != null && u.getAnrCountLast7Days() > 0) ||
                     (u.getFrozenFrameRate() != null && u.getFrozenFrameRate() > 0.1))
        .mapToInt(ChurnRiskUser::getRiskScore)
        .average()
        .orElse(0.0);

    double riskWithoutIssues = users.stream()
        .filter(u -> (u.getCrashCountLast7Days() == null || u.getCrashCountLast7Days() == 0) &&
                     (u.getAnrCountLast7Days() == null || u.getAnrCountLast7Days() == 0) &&
                     (u.getFrozenFrameRate() == null || u.getFrozenFrameRate() <= 0.1))
        .mapToInt(ChurnRiskUser::getRiskScore)
        .average()
        .orElse(0.0);

    double correlation = riskWithIssues > 0 ? (riskWithIssues - riskWithoutIssues) / riskWithIssues * 100 : 0.0;

    return ChurnAnalyticsResponse.PerformanceImpactMetrics.builder()
        .usersWithCrashes((int) usersWithCrashes)
        .usersWithAnrs((int) usersWithAnrs)
        .usersWithFrozenFrames((int) usersWithFrozenFrames)
        .avgCrashRate(avgCrashRate)
        .avgAnrRate(avgAnrRate)
        .avgFrozenFrameRate(avgFrozenFrameRate)
        .performanceRiskCorrelation(correlation)
        .build();
  }

  private ChurnAnalyticsResponse.EngagementPatternMetrics calculateEngagementPatterns(
      List<ChurnRiskUser> users) {
    
    long inactiveUsers = users.stream()
        .filter(u -> u.getDaysSinceLastSession() != null && u.getDaysSinceLastSession() >= 7)
        .count();

    long decliningUsers = users.stream()
        .filter(u -> {
          if (u.getSessionsLast7Days() == null || u.getSessionsLast30Days() == null) return false;
          if (u.getSessionsLast30Days() == 0) return false;
          double expected7d = u.getSessionsLast30Days() / 4.0;
          if (expected7d == 0) return false;
          double decline = 1.0 - (u.getSessionsLast7Days() / expected7d);
          return decline > 0.5;
        })
        .count();

    double avgDaysSince = users.stream()
        .mapToInt(u -> u.getDaysSinceLastSession() != null ? u.getDaysSinceLastSession() : 0)
        .average()
        .orElse(0.0);

    double avgSessions7d = users.stream()
        .mapToInt(u -> u.getSessionsLast7Days() != null ? u.getSessionsLast7Days() : 0)
        .average()
        .orElse(0.0);

    double avgSessions30d = users.stream()
        .mapToInt(u -> u.getSessionsLast30Days() != null ? u.getSessionsLast30Days() : 0)
        .average()
        .orElse(0.0);

    double avgDuration = users.stream()
        .mapToLong(u -> u.getAvgSessionDuration() != null ? u.getAvgSessionDuration() : 0)
        .average()
        .orElse(0.0);

    return ChurnAnalyticsResponse.EngagementPatternMetrics.builder()
        .inactiveUsers((int) inactiveUsers)
        .decliningUsers((int) decliningUsers)
        .avgDaysSinceLastSession(avgDaysSince)
        .avgSessionsLast7Days(avgSessions7d)
        .avgSessionsLast30Days(avgSessions30d)
        .avgSessionDuration(avgDuration)
        .build();
  }

  private ChurnAnalyticsResponse buildEmptyResponse() {
    return ChurnAnalyticsResponse.builder()
        .totalUsers(0)
        .highRiskCount(0)
        .mediumRiskCount(0)
        .lowRiskCount(0)
        .averageRiskScore(0.0)
        .overallChurnProbability(0.0)
        .riskDistribution(new LinkedHashMap<>())
        .topRiskFactors(new ArrayList<>())
        .deviceSegments(new LinkedHashMap<>())
        .osSegments(new LinkedHashMap<>())
        .appVersionSegments(new LinkedHashMap<>())
        .build();
  }
}

