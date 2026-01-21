package org.dreamhorizon.pulseserver.module;

import com.google.inject.AbstractModule;
import com.google.inject.Singleton;
import org.dreamhorizon.pulseserver.client.ml.MLPredictionClient;
import org.dreamhorizon.pulseserver.service.churn.ChurnAnalyticsService;
import org.dreamhorizon.pulseserver.service.churn.ChurnFeatureExtractor;
import org.dreamhorizon.pulseserver.service.churn.ChurnPredictionService;
import org.dreamhorizon.pulseserver.service.churn.ChurnRiskCalculator;
import org.dreamhorizon.pulseserver.service.churn.RuleBasedChurnCalculator;

public class ChurnModule extends AbstractModule {

  @Override
  protected void configure() {
    bind(MLPredictionClient.class).in(Singleton.class);
    bind(RuleBasedChurnCalculator.class).in(Singleton.class);
    bind(ChurnFeatureExtractor.class).in(Singleton.class);
    bind(ChurnRiskCalculator.class).in(Singleton.class);
    bind(ChurnPredictionService.class).in(Singleton.class);
    bind(ChurnAnalyticsService.class).in(Singleton.class);
  }
}

