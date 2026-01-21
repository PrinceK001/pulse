package org.dreamhorizon.pulseserver.resources.churn;

import org.dreamhorizon.pulseserver.resources.churn.models.ChurnPredictionRestRequest;
import org.dreamhorizon.pulseserver.resources.churn.models.ChurnPredictionRestResponse;
import org.dreamhorizon.pulseserver.resources.churn.models.ChurnRiskUserRestResponse;
import org.dreamhorizon.pulseserver.service.churn.models.ChurnPredictionRequest;
import org.dreamhorizon.pulseserver.service.churn.models.ChurnPredictionResponse;
import org.dreamhorizon.pulseserver.service.churn.models.ChurnRiskUser;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

@Mapper
public interface ChurnPredictionMapper {
  ChurnPredictionMapper INSTANCE = Mappers.getMapper(ChurnPredictionMapper.class);

  ChurnPredictionRequest toServiceRequest(ChurnPredictionRestRequest request);

  ChurnPredictionRestResponse toRestResponse(ChurnPredictionResponse response);

  ChurnRiskUserRestResponse toRestUserResponse(ChurnRiskUser user);
}

