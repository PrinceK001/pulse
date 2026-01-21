package org.dreamhorizon.pulseserver.service.churn;

import com.google.inject.Inject;
import io.reactivex.rxjava3.core.Single;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.client.ml.MLPredictionClient;
import org.dreamhorizon.pulseserver.service.churn.models.UserChurnFeatures;

/**
 * Calculates churn risk score using ML model with rule-based fallback
 * Tries ML service first, falls back to rule-based if ML unavailable
 */
@Slf4j
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class ChurnRiskCalculator {

  private final MLPredictionClient mlClient;
  private final RuleBasedChurnCalculator fallbackCalculator;

  /**
   * Calculate churn risk score (0-100) using ML model with fallback
   * Higher score = higher churn risk
   */
  public Single<Integer> calculateRiskScore(UserChurnFeatures features) {
    // Try ML service first
    return mlClient.predictChurn(features)
        .map(response -> {
          log.debug("ML prediction for user {}: risk_score={}", 
              features.getUserId(), response.getRiskScore());
          return response.getRiskScore();
        })
        .onErrorResumeNext(error -> {
          log.warn("ML prediction failed for user {}, using rule-based fallback: {}", 
              features.getUserId(), error.getMessage());
          // Fallback to rule-based calculation
          int fallbackScore = fallbackCalculator.calculateRiskScore(features);
          log.debug("Fallback prediction for user {}: risk_score={}", 
              features.getUserId(), fallbackScore);
          return Single.just(fallbackScore);
        });
  }

  /**
   * Synchronous version for backward compatibility
   * Note: This will block if ML service is slow. Use calculateRiskScore() for async.
   */
  public int calculateRiskScoreSync(UserChurnFeatures features) {
    try {
      return mlClient.predictChurn(features)
          .map(response -> response.getRiskScore())
          .onErrorReturn(error -> {
            log.warn("ML prediction failed, using rule-based fallback: {}", error.getMessage());
            return fallbackCalculator.calculateRiskScore(features);
          })
          .blockingGet();
    } catch (Exception e) {
      log.error("Error in synchronous risk calculation, using fallback", e);
      return fallbackCalculator.calculateRiskScore(features);
    }
  }
}

