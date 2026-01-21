package org.dreamhorizon.pulseserver.service.churn;

import com.google.inject.Inject;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.core.Observable;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.client.chclient.ClickhouseQueryService;
import org.dreamhorizon.pulseserver.model.QueryConfiguration;
import org.dreamhorizon.pulseserver.service.churn.models.ChurnPredictionRequest;
import org.dreamhorizon.pulseserver.service.churn.models.ChurnPredictionResponse;
import org.dreamhorizon.pulseserver.service.churn.models.ChurnRiskUser;
import org.dreamhorizon.pulseserver.service.churn.models.UserChurnFeatures;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class ChurnPredictionService {

  private final ClickhouseQueryService clickhouseQueryService;
  private final ChurnFeatureExtractor featureExtractor;
  private final ChurnRiskCalculator riskCalculator;
  private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

  /**
   * Get churn predictions for users based on their engagement patterns
   */
  public Single<ChurnPredictionResponse> getChurnPredictions(ChurnPredictionRequest request) {
    log.info("Getting churn predictions with filters: {}", request);

    return featureExtractor.extractUserFeatures(request)
        .flatMap(features -> {
          // Convert each feature to ChurnRiskUser using async ML prediction
          // Use Observable for better performance with many items
          return Observable.fromIterable(features)
              .flatMapSingle(feature -> 
                  riskCalculator.calculateRiskScore(feature)
                      .map(riskScore -> {
                        String riskLevel = determineRiskLevel(riskScore);
                        List<String> riskFactors = identifyRiskFactors(feature, riskScore);

                        return ChurnRiskUser.builder()
                            .userId(feature.getUserId())
                            .riskScore(riskScore)
                            .riskLevel(riskLevel)
                            .daysSinceLastSession(feature.getDaysSinceLastSession())
                            .sessionsLast7Days(feature.getSessionsLast7Days())
                            .sessionsLast30Days(feature.getSessionsLast30Days())
                            .crashCountLast7Days(feature.getCrashCountLast7Days())
                            .anrCountLast7Days(feature.getAnrCountLast7Days())
                            .frozenFrameRate(feature.getFrozenFrameRate())
                            .avgSessionDuration(feature.getAvgSessionDuration())
                            .uniqueScreensLast7Days(feature.getUniqueScreensLast7Days())
                            .deviceModel(feature.getDeviceModel())
                            .osVersion(feature.getOsVersion())
                            .appVersion(feature.getAppVersion())
                            .primaryRiskFactors(riskFactors)
                            .churnProbability(riskScore / 100.0)
                            .build();
                      })
              )
              .toList()
              .toSingle();
        })
        .map(riskUsers -> {
          // Apply filters
          List<ChurnRiskUser> filtered = riskUsers.stream()
              .filter(user -> {
                // Apply risk level filter if specified
                if (request.getRiskLevel() != null && !request.getRiskLevel().isEmpty()) {
                  return user.getRiskLevel().equalsIgnoreCase(request.getRiskLevel());
                }
                return true;
              })
              .filter(user -> {
                // Apply minimum risk score filter
                return user.getRiskScore() >= request.getMinRiskScore();
              })
              .sorted((a, b) -> Integer.compare(b.getRiskScore(), a.getRiskScore()))
              .limit(request.getLimit() != null ? request.getLimit() : 100)
              .collect(Collectors.toList());
          
          return filtered;
        })
        .map(riskUsers -> {
          // Calculate summary statistics
          long highRiskCount = riskUsers.stream()
              .filter(u -> "HIGH".equalsIgnoreCase(u.getRiskLevel()))
              .count();
          long mediumRiskCount = riskUsers.stream()
              .filter(u -> "MEDIUM".equalsIgnoreCase(u.getRiskLevel()))
              .count();
          long lowRiskCount = riskUsers.stream()
              .filter(u -> "LOW".equalsIgnoreCase(u.getRiskLevel()))
              .count();

          return ChurnPredictionResponse.builder()
              .users(riskUsers)
              .totalUsers(riskUsers.size())
              .highRiskCount((int) highRiskCount)
              .mediumRiskCount((int) mediumRiskCount)
              .lowRiskCount((int) lowRiskCount)
              .predictionDate(LocalDateTime.now())
              .build();
        })
        .doOnError(error -> log.error("Error getting churn predictions", error));
  }

  /**
   * Get churn prediction for a specific user
   */
  public Single<ChurnRiskUser> getChurnPredictionForUser(String userId) {
    log.info("Getting churn prediction for user: {}", userId);

    ChurnPredictionRequest request = ChurnPredictionRequest.builder()
        .userId(userId)
        .limit(1)
        .build();

    return featureExtractor.extractUserFeatures(request)
        .flatMap(features -> {
          if (features.isEmpty()) {
            return Single.error(new RuntimeException("User not found: " + userId));
          }
          UserChurnFeatures feature = features.get(0);
          
          return riskCalculator.calculateRiskScore(feature)
              .map(riskScore -> {
                String riskLevel = determineRiskLevel(riskScore);
                List<String> riskFactors = identifyRiskFactors(feature, riskScore);

                return ChurnRiskUser.builder()
                    .userId(feature.getUserId())
                    .riskScore(riskScore)
                    .riskLevel(riskLevel)
                    .daysSinceLastSession(feature.getDaysSinceLastSession())
                    .sessionsLast7Days(feature.getSessionsLast7Days())
                    .sessionsLast30Days(feature.getSessionsLast30Days())
                    .crashCountLast7Days(feature.getCrashCountLast7Days())
                    .anrCountLast7Days(feature.getAnrCountLast7Days())
                    .frozenFrameRate(feature.getFrozenFrameRate())
                    .avgSessionDuration(feature.getAvgSessionDuration())
                    .uniqueScreensLast7Days(feature.getUniqueScreensLast7Days())
                    .deviceModel(feature.getDeviceModel())
                    .osVersion(feature.getOsVersion())
                    .appVersion(feature.getAppVersion())
                    .primaryRiskFactors(riskFactors)
                    .churnProbability(riskScore / 100.0)
                    .build();
              });
        })
        .doOnError(error -> log.error("Error getting churn prediction for user: {}", userId, error));
  }

  /**
   * Get churn risk statistics by segment
   */
  public Single<Map<String, Object>> getChurnRiskBySegment(ChurnPredictionRequest request) {
    log.info("Getting churn risk by segment");

    return featureExtractor.extractUserFeatures(request)
        .map(features -> {
          Map<String, Object> segmentStats = new HashMap<>();

          // Group by device model
          Map<String, List<UserChurnFeatures>> byDevice = features.stream()
              .collect(Collectors.groupingBy(f -> f.getDeviceModel() != null ? f.getDeviceModel() : "Unknown"));

          Map<String, Object> deviceStats = new HashMap<>();
          byDevice.forEach((device, deviceFeatures) -> {
            // Use synchronous fallback for segment analysis (batch processing)
            double avgRisk = deviceFeatures.stream()
                .mapToInt(f -> riskCalculator.calculateRiskScoreSync(f))
                .average()
                .orElse(0.0);
            long highRiskCount = deviceFeatures.stream()
                .filter(f -> riskCalculator.calculateRiskScoreSync(f) >= 70)
                .count();

            deviceStats.put(device, Map.of(
                "avgRiskScore", avgRisk,
                "userCount", deviceFeatures.size(),
                "highRiskCount", highRiskCount,
                "highRiskPercentage", (double) highRiskCount / deviceFeatures.size() * 100
            ));
          });
          segmentStats.put("byDevice", deviceStats);

          // Group by OS version
          Map<String, List<UserChurnFeatures>> byOs = features.stream()
              .collect(Collectors.groupingBy(f -> f.getOsVersion() != null ? f.getOsVersion() : "Unknown"));

          Map<String, Object> osStats = new HashMap<>();
          byOs.forEach((os, osFeatures) -> {
            double avgRisk = osFeatures.stream()
                .mapToInt(f -> riskCalculator.calculateRiskScoreSync(f))
                .average()
                .orElse(0.0);
            long highRiskCount = osFeatures.stream()
                .filter(f -> riskCalculator.calculateRiskScoreSync(f) >= 70)
                .count();

            osStats.put(os, Map.of(
                "avgRiskScore", avgRisk,
                "userCount", osFeatures.size(),
                "highRiskCount", highRiskCount,
                "highRiskPercentage", (double) highRiskCount / osFeatures.size() * 100
            ));
          });
          segmentStats.put("byOsVersion", osStats);

          // Group by app version
          Map<String, List<UserChurnFeatures>> byAppVersion = features.stream()
              .collect(Collectors.groupingBy(f -> f.getAppVersion() != null ? f.getAppVersion() : "Unknown"));

          Map<String, Object> appVersionStats = new HashMap<>();
          byAppVersion.forEach((appVersion, appFeatures) -> {
            double avgRisk = appFeatures.stream()
                .mapToInt(f -> riskCalculator.calculateRiskScoreSync(f))
                .average()
                .orElse(0.0);
            long highRiskCount = appFeatures.stream()
                .filter(f -> riskCalculator.calculateRiskScoreSync(f) >= 70)
                .count();

            appVersionStats.put(appVersion, Map.of(
                "avgRiskScore", avgRisk,
                "userCount", appFeatures.size(),
                "highRiskCount", highRiskCount,
                "highRiskPercentage", (double) highRiskCount / appFeatures.size() * 100
            ));
          });
          segmentStats.put("byAppVersion", appVersionStats);

          return segmentStats;
        })
        .doOnError(error -> log.error("Error getting churn risk by segment", error));
  }

  private String determineRiskLevel(int riskScore) {
    if (riskScore >= 70) {
      return "HIGH";
    } else if (riskScore >= 40) {
      return "MEDIUM";
    } else {
      return "LOW";
    }
  }

  private List<String> identifyRiskFactors(UserChurnFeatures feature, int riskScore) {
    List<String> factors = new ArrayList<>();

    if (feature.getDaysSinceLastSession() >= 7) {
      factors.add("No session in " + feature.getDaysSinceLastSession() + " days");
    }

    if (feature.getSessionsLast7Days() == 0) {
      factors.add("Zero sessions in last 7 days");
    }

    if (feature.getSessionsLast7Days() > 0 && feature.getSessionsLast30Days() > 0) {
      double decline = 1.0 - ((double) feature.getSessionsLast7Days() / 4.0) / feature.getSessionsLast30Days();
      if (decline > 0.5) {
        factors.add("Session frequency declined by " + String.format("%.0f%%", decline * 100));
      }
    }

    if (feature.getCrashCountLast7Days() > 0) {
      factors.add(feature.getCrashCountLast7Days() + " crash(es) in last 7 days");
    }

    if (feature.getAnrCountLast7Days() > 0) {
      factors.add(feature.getAnrCountLast7Days() + " ANR(s) in last 7 days");
    }

    if (feature.getFrozenFrameRate() > 0.1) {
      factors.add("High frozen frame rate: " + String.format("%.1f%%", feature.getFrozenFrameRate() * 100));
    }

    if (feature.getAvgSessionDuration() > 0 && feature.getAvgSessionDuration() < 30000) {
      factors.add("Short average session duration: " + (feature.getAvgSessionDuration() / 1000) + "s");
    }

    if (feature.getUniqueScreensLast7Days() <= 1 && feature.getSessionsLast7Days() > 0) {
      factors.add("Limited screen diversity");
    }

    return factors.isEmpty() ? List.of("Low engagement trend") : factors;
  }
}

