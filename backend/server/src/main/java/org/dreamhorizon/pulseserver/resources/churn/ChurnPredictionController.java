package org.dreamhorizon.pulseserver.resources.churn;

import com.google.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.resources.churn.models.ChurnPredictionRestRequest;
import org.dreamhorizon.pulseserver.resources.churn.models.ChurnPredictionRestResponse;
import org.dreamhorizon.pulseserver.resources.churn.models.ChurnRiskUserRestResponse;
import org.dreamhorizon.pulseserver.rest.io.Response;
import org.dreamhorizon.pulseserver.rest.io.RestResponse;
import org.dreamhorizon.pulseserver.service.churn.ChurnAnalyticsService;
import org.dreamhorizon.pulseserver.service.churn.ChurnPredictionService;
import org.dreamhorizon.pulseserver.service.churn.models.ChurnAnalyticsResponse;
import org.dreamhorizon.pulseserver.service.churn.models.ChurnPredictionRequest;
import org.dreamhorizon.pulseserver.service.churn.models.ChurnPredictionResponse;
import org.dreamhorizon.pulseserver.service.churn.models.ChurnRiskUser;

@Slf4j
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
@Path("/api/v1/churn")
public class ChurnPredictionController {

  private final ChurnPredictionService churnPredictionService;
  private final ChurnAnalyticsService churnAnalyticsService;
  private static final ChurnPredictionMapper mapper = ChurnPredictionMapper.INSTANCE;

  @POST
  @Path("/predictions")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public CompletionStage<Response<ChurnPredictionRestResponse>> getChurnPredictions(
      ChurnPredictionRestRequest request
  ) {
    log.info("Getting churn predictions with request: {}", request);

    ChurnPredictionRequest serviceRequest = mapper.toServiceRequest(request);
    return churnPredictionService.getChurnPredictions(serviceRequest)
        .map(mapper::toRestResponse)
        .to(RestResponse.jaxrsRestHandler());
  }

  @GET
  @Path("/predictions/user/{userId}")
  @Consumes(MediaType.WILDCARD)
  @Produces(MediaType.APPLICATION_JSON)
  public CompletionStage<Response<ChurnRiskUserRestResponse>> getChurnPredictionForUser(
      @PathParam("userId") String userId
  ) {
    log.info("Getting churn prediction for user: {}", userId);

    return churnPredictionService.getChurnPredictionForUser(userId)
        .map(mapper::toRestUserResponse)
        .to(RestResponse.jaxrsRestHandler());
  }

  @GET
  @Path("/predictions/segments")
  @Consumes(MediaType.WILDCARD)
  @Produces(MediaType.APPLICATION_JSON)
  public CompletionStage<Response<Map<String, Object>>> getChurnRiskBySegment(
      @QueryParam("deviceModel") String deviceModel,
      @QueryParam("osVersion") String osVersion,
      @QueryParam("appVersion") String appVersion,
      @QueryParam("limit") Integer limit
  ) {
    log.info("Getting churn risk by segment");

    ChurnPredictionRequest request = ChurnPredictionRequest.builder()
        .deviceModel(deviceModel)
        .osVersion(osVersion)
        .appVersion(appVersion)
        .limit(limit)
        .build();

    return churnPredictionService.getChurnRiskBySegment(request)
        .to(RestResponse.jaxrsRestHandler());
  }

  @POST
  @Path("/analytics")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public CompletionStage<Response<ChurnAnalyticsResponse>> getChurnAnalytics(
      ChurnPredictionRestRequest request
  ) {
    log.info("Getting churn analytics with request: {}", request);

    ChurnPredictionRequest serviceRequest = mapper.toServiceRequest(request);
    return churnAnalyticsService.getChurnAnalytics(serviceRequest)
        .to(RestResponse.jaxrsRestHandler());
  }
}

