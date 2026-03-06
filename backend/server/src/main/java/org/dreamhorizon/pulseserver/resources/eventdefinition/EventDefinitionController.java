package org.dreamhorizon.pulseserver.resources.eventdefinition;

import com.google.inject.Inject;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.resources.eventdefinition.models.RestBulkUploadResponse;
import org.dreamhorizon.pulseserver.resources.eventdefinition.models.RestEventAttribute;
import org.dreamhorizon.pulseserver.resources.eventdefinition.models.RestEventDefinition;
import org.dreamhorizon.pulseserver.resources.eventdefinition.models.RestEventDefinitionListResponse;
import org.dreamhorizon.pulseserver.rest.io.Response;
import org.dreamhorizon.pulseserver.rest.io.RestResponse;
import org.dreamhorizon.pulseserver.service.eventdefinition.EventDefinitionService;
import org.dreamhorizon.pulseserver.service.eventdefinition.models.CreateEventDefinitionRequest;
import org.dreamhorizon.pulseserver.service.eventdefinition.models.EventAttributeDefinition;
import org.dreamhorizon.pulseserver.service.eventdefinition.models.EventDefinition;
import org.dreamhorizon.pulseserver.service.eventdefinition.models.EventDefinitionPage;
import org.dreamhorizon.pulseserver.service.eventdefinition.models.UpdateEventDefinitionRequest;
import org.jboss.resteasy.annotations.providers.multipart.MultipartForm;
import org.jboss.resteasy.plugins.providers.multipart.MultipartFormDataInput;

@Slf4j
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
@Path("/v1/event-definitions")
public class EventDefinitionController {

  private final EventDefinitionService eventDefinitionService;

  @GET
  @Consumes(MediaType.WILDCARD)
  @Produces(MediaType.APPLICATION_JSON)
  public CompletionStage<Response<RestEventDefinitionListResponse>> getAllEventDefinitions(
      @QueryParam("limit") @DefaultValue("50") int limit,
      @QueryParam("offset") @DefaultValue("0") int offset,
      @QueryParam("search") String search,
      @QueryParam("category") String category
  ) {
    return eventDefinitionService.queryEventDefinitions(search, category, limit, offset)
        .map(this::toRestListResponse)
        .to(RestResponse.jaxrsRestHandler());
  }

  @GET
  @Path("/categories")
  @Consumes(MediaType.WILDCARD)
  @Produces(MediaType.APPLICATION_JSON)
  public CompletionStage<Response<List<String>>> getCategories() {
    return eventDefinitionService.getDistinctCategories()
        .to(RestResponse.jaxrsRestHandler());
  }

  @GET
  @Path("/{id}")
  @Consumes(MediaType.WILDCARD)
  @Produces(MediaType.APPLICATION_JSON)
  public CompletionStage<Response<RestEventDefinition>> getEventDefinitionById(
      @PathParam("id") Long id
  ) {
    return eventDefinitionService.getEventDefinitionById(id)
        .map(this::toRestModel)
        .to(RestResponse.jaxrsRestHandler());
  }

  @POST
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public CompletionStage<Response<RestEventDefinition>> createEventDefinition(
      @NotNull @HeaderParam("user-email") String userEmail,
      @NotNull RestEventDefinition restRequest
  ) {
    CreateEventDefinitionRequest request = CreateEventDefinitionRequest.builder()
        .eventName(restRequest.getEventName())
        .displayName(restRequest.getDisplayName())
        .description(restRequest.getDescription())
        .category(restRequest.getCategory())
        .attributes(toServiceAttributes(restRequest.getAttributes()))
        .user(userEmail)
        .build();

    return eventDefinitionService.createEventDefinition(request)
        .map(this::toRestModel)
        .to(RestResponse.jaxrsRestHandler());
  }

  @PUT
  @Path("/{id}")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public CompletionStage<Response<RestEventDefinition>> updateEventDefinition(
      @NotNull @HeaderParam("user-email") String userEmail,
      @PathParam("id") Long id,
      @NotNull RestEventDefinition restRequest
  ) {
    UpdateEventDefinitionRequest request = UpdateEventDefinitionRequest.builder()
        .id(id)
        .displayName(restRequest.getDisplayName())
        .description(restRequest.getDescription())
        .category(restRequest.getCategory())
        .attributes(toServiceAttributes(restRequest.getAttributes()))
        .user(userEmail)
        .build();

    return eventDefinitionService.updateEventDefinition(request)
        .andThen(eventDefinitionService.getEventDefinitionById(id))
        .map(this::toRestModel)
        .to(RestResponse.jaxrsRestHandler());
  }

  @DELETE
  @Path("/{id}")
  @Consumes(MediaType.WILDCARD)
  @Produces(MediaType.APPLICATION_JSON)
  public CompletionStage<Response<RestEventDefinition>> archiveEventDefinition(
      @NotNull @HeaderParam("user-email") String userEmail,
      @PathParam("id") Long id
  ) {
    return eventDefinitionService.archiveEventDefinition(id, userEmail)
        .andThen(eventDefinitionService.getEventDefinitionById(id))
        .map(this::toRestModel)
        .to(RestResponse.jaxrsRestHandler());
  }

  @POST
  @Path("/bulk")
  @Consumes(MediaType.MULTIPART_FORM_DATA)
  @Produces(MediaType.APPLICATION_JSON)
  public CompletionStage<Response<RestBulkUploadResponse>> bulkUpload(
      @NotNull @HeaderParam("user-email") String userEmail,
      @MultipartForm MultipartFormDataInput input
  ) {
    return extractCsvInputStream(input)
        .flatMap(csvStream -> eventDefinitionService.bulkUploadFromCsv(csvStream, userEmail))
        .map(result -> RestBulkUploadResponse.builder()
            .created(result.getCreated())
            .updated(result.getUpdated())
            .skipped(result.getSkipped())
            .errors(result.getErrors() == null ? Collections.emptyList()
                : result.getErrors().stream()
                .map(e -> RestBulkUploadResponse.RowError.builder()
                    .line(e.getLine())
                    .eventName(e.getEventName())
                    .message(e.getMessage())
                    .build())
                .collect(Collectors.toList()))
            .build())
        .to(RestResponse.jaxrsRestHandler());
  }

  private io.reactivex.rxjava3.core.Single<InputStream> extractCsvInputStream(
      MultipartFormDataInput input) {
    try {
      var formDataMap = input.getFormDataMap();
      var fileParts = formDataMap.get("file");
      if (fileParts == null || fileParts.isEmpty()) {
        return io.reactivex.rxjava3.core.Single.error(
            new IllegalArgumentException("No file found in request. Use form field name 'file'."));
      }
      return io.reactivex.rxjava3.core.Single.just(
          fileParts.get(0).getBody(InputStream.class, null));
    } catch (Exception e) {
      return io.reactivex.rxjava3.core.Single.error(e);
    }
  }

  private RestEventDefinition toRestModel(EventDefinition def) {
    return RestEventDefinition.builder()
        .id(def.getId())
        .eventName(def.getEventName())
        .displayName(def.getDisplayName())
        .description(def.getDescription())
        .category(def.getCategory())
        .isArchived(def.isArchived())
        .createdBy(def.getCreatedBy())
        .updatedBy(def.getUpdatedBy())
        .createdAt(def.getCreatedAt())
        .updatedAt(def.getUpdatedAt())
        .attributes(def.getAttributes() == null ? Collections.emptyList()
            : def.getAttributes().stream()
            .map(attr -> RestEventAttribute.builder()
                .id(attr.getId())
                .attributeName(attr.getAttributeName())
                .description(attr.getDescription())
                .dataType(attr.getDataType())
                .isRequired(attr.isRequired())
                .isArchived(attr.isArchived())
                .build())
            .collect(Collectors.toList()))
        .build();
  }

  private RestEventDefinitionListResponse toRestListResponse(EventDefinitionPage page) {
    return RestEventDefinitionListResponse.builder()
        .eventDefinitions(page.getDefinitions().stream()
            .map(this::toRestModel).collect(Collectors.toList()))
        .totalCount(page.getTotalCount())
        .build();
  }

  private List<EventAttributeDefinition> toServiceAttributes(
      List<RestEventAttribute> restAttrs) {
    if (restAttrs == null) {
      return null;
    }
    return restAttrs.stream()
        .map(attr -> EventAttributeDefinition.builder()
            .attributeName(attr.getAttributeName())
            .description(attr.getDescription())
            .dataType(attr.getDataType())
            .required(Boolean.TRUE.equals(attr.getIsRequired()))
            .build())
        .collect(Collectors.toList());
  }
}
