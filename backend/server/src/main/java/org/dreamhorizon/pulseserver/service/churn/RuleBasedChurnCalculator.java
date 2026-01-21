package org.dreamhorizon.pulseserver.service.churn;

import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.service.churn.models.UserChurnFeatures;

/**
 * Rule-based churn calculator (fallback when ML service unavailable)
 * This is the original implementation moved to a separate class
 */
@Slf4j
public class RuleBasedChurnCalculator {
  
  /**
   * Calculate churn risk score (0-100) based on user features
   * Higher score = higher churn risk
   */
  public int calculateRiskScore(UserChurnFeatures features) {
    int score = 0;

    // Days since last session (0-40 points)
    int daysSinceLastSession = features.getDaysSinceLastSession();
    if (daysSinceLastSession >= 30) {
      score += 40;
    } else if (daysSinceLastSession >= 14) {
      score += 30;
    } else if (daysSinceLastSession >= 7) {
      score += 20;
    } else if (daysSinceLastSession >= 3) {
      score += 10;
    }

    // Session frequency decline (0-25 points)
    int sessions7d = features.getSessionsLast7Days();
    int sessions30d = features.getSessionsLast30Days();
    if (sessions30d > 0) {
      double expected7d = sessions30d / 4.0; // Expected sessions in 7 days based on 30d average
      if (sessions7d == 0 && expected7d > 0) {
        score += 25; // Complete drop-off
      } else if (sessions7d > 0 && expected7d > 0) {
        double decline = 1.0 - (sessions7d / expected7d);
        if (decline > 0.7) {
          score += 20;
        } else if (decline > 0.5) {
          score += 15;
        } else if (decline > 0.3) {
          score += 10;
        }
      }
    } else if (sessions7d == 0) {
      score += 20; // No sessions at all
    }

    // Zero sessions in last 7 days (0-15 points)
    if (sessions7d == 0 && daysSinceLastSession < 7) {
      score += 15;
    }

    // Performance issues (0-15 points)
    int crashCount = features.getCrashCountLast7Days();
    int anrCount = features.getAnrCountLast7Days();
    double frozenFrameRate = features.getFrozenFrameRate();

    if (crashCount >= 3) {
      score += 15;
    } else if (crashCount >= 2) {
      score += 10;
    } else if (crashCount >= 1) {
      score += 5;
    }

    if (anrCount >= 3) {
      score += 10;
    } else if (anrCount >= 2) {
      score += 7;
    } else if (anrCount >= 1) {
      score += 3;
    }

    if (frozenFrameRate > 0.2) {
      score += 10;
    } else if (frozenFrameRate > 0.1) {
      score += 5;
    }

    // Short session duration (0-5 points)
    long avgSessionDuration = features.getAvgSessionDuration();
    if (avgSessionDuration > 0 && avgSessionDuration < 30000) { // Less than 30 seconds
      score += 5;
    }

    // Limited screen diversity (0-5 points)
    int uniqueScreens = features.getUniqueScreensLast7Days();
    if (uniqueScreens <= 1 && sessions7d > 0) {
      score += 5;
    }

    // Cap at 100
    return Math.min(score, 100);
  }
}

