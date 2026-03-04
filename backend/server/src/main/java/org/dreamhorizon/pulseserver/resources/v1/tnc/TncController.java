package org.dreamhorizon.pulseserver.resources.v1.tnc;

import com.google.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import java.util.List;
import java.util.concurrent.CompletionStage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.resources.v1.tnc.models.AcceptTncRequest;
import org.dreamhorizon.pulseserver.resources.v1.tnc.models.AcceptTncResponse;
import org.dreamhorizon.pulseserver.resources.v1.tnc.models.TncHistoryResponse;
import org.dreamhorizon.pulseserver.resources.v1.tnc.models.TncStatusResponse;
import org.dreamhorizon.pulseserver.rest.io.Response;
import org.dreamhorizon.pulseserver.rest.io.RestResponse;
import org.dreamhorizon.pulseserver.service.OpenFgaService;
import org.dreamhorizon.pulseserver.service.tnc.TncService;
import org.dreamhorizon.pulseserver.tenant.TenantContext;

import static org.dreamhorizon.pulseserver.util.AuthenticationUtil.extractUserId;

/**
 * Tenant-facing TnC endpoints.
 * Tenant ID is resolved from the JWT token via TenantFilter.
 */
@Slf4j
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
@Path("/v1/tnc")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class TncController {

  private final TncService tncService;
  private final OpenFgaService openFgaService;

  /**
   * Public endpoint - returns active TnC documents without requiring auth.
   * Used by the onboarding page before the user has a tenant/token.
   */
  @GET
  @Path("/documents")
  public CompletionStage<Response<TncStatusResponse>> getDocuments() {
    log.info("Getting TnC documents (public, no auth required)");

    return tncService.getTncStatus(null)
        .map(result -> {
          var version = result.getVersion();
          return TncStatusResponse.builder()
              .accepted(false)
              .version(version.getVersion())
              .versionId(version.getId())
              .documents(TncStatusResponse.TncDocumentUrls.builder()
                  .tos(tncService.generatePresignedUrl(version.getTosS3Url()))
                  .aup(tncService.generatePresignedUrl(version.getAupS3Url()))
                  .privacyPolicy(tncService.generatePresignedUrl(version.getPrivacyPolicyS3Url()))
                  .build())
              .build();
        })
        .to(RestResponse.jaxrsRestHandler());
  }

  @GET
  @Path("/status")
  public CompletionStage<Response<TncStatusResponse>> getStatus() {
    String tenantId = TenantContext.getTenantId();
    log.info("Getting TnC status for tenant: {}", tenantId);

    return tncService.getTncStatus(tenantId)
        .map(result -> {
          var version = result.getVersion();
          return TncStatusResponse.builder()
              .accepted(result.isAccepted())
              .version(version.getVersion())
              .versionId(version.getId())
              .documents(TncStatusResponse.TncDocumentUrls.builder()
                  .tos(tncService.generatePresignedUrl(version.getTosS3Url()))
                  .aup(tncService.generatePresignedUrl(version.getAupS3Url()))
                  .privacyPolicy(tncService.generatePresignedUrl(version.getPrivacyPolicyS3Url()))
                  .build())
              .acceptedBy(result.getAcceptedByEmail())
              .acceptedAt(result.getAcceptedAt())
              .build();
        })
        .to(RestResponse.jaxrsRestHandler());
  }

  @POST
  @Path("/accept")
  public CompletionStage<Response<AcceptTncResponse>> acceptTnc(
      @HeaderParam(HttpHeaders.AUTHORIZATION) String authorization,
      @HeaderParam("User-Agent") String userAgent,
      @HeaderParam("X-Forwarded-For") String forwardedFor,
      @NotNull @Valid AcceptTncRequest request) {

    String tenantId = TenantContext.getTenantId();
    String userId = extractUserId(authorization);
    String userEmail = extractEmailFromAuth(authorization);

    log.info("TnC acceptance attempt: tenant={}, user={}, email={}, version={}", tenantId, userId, userEmail, request.getVersionId());

    return openFgaService.checkPermission(userId, "can_accept_tnc", "tenant", tenantId)
        .flatMap(allowed -> {
          if (!allowed) {
            return io.reactivex.rxjava3.core.Single.error(
                new SecurityException("Only tenant admins can accept Terms & Conditions"));
          }

          String ipAddress = forwardedFor != null ? forwardedFor : "unknown";

          return tncService.acceptTnc(tenantId, request.getVersionId(), userEmail, ipAddress, userAgent);
        })
        .map(acceptance -> AcceptTncResponse.builder()
            .status("accepted")
            .tenantId(acceptance.getTenantId())
            .version(String.valueOf(acceptance.getTncVersionId()))
            .acceptedBy(acceptance.getAcceptedByEmail())
            .acceptedAt(acceptance.getAcceptedAt())
            .build())
        .to(RestResponse.jaxrsRestHandler());
  }

  @GET
  @Path("/history")
  public CompletionStage<Response<TncHistoryResponse>> getHistory() {
    String tenantId = TenantContext.getTenantId();
    log.info("Getting TnC history for tenant: {}", tenantId);

    return tncService.getAcceptanceHistory(tenantId)
        .toList()
        .map(acceptances -> {
          List<TncHistoryResponse.TncHistoryEntry> entries = acceptances.stream()
              .map(a -> TncHistoryResponse.TncHistoryEntry.builder()
                  .versionId(a.getTncVersionId())
                  .acceptedBy(a.getAcceptedByEmail())
                  .acceptedAt(a.getAcceptedAt())
                  .ipAddress(a.getIpAddress())
                  .build())
              .toList();
          return TncHistoryResponse.builder()
              .history(entries)
              .totalCount(entries.size())
              .build();
        })
        .to(RestResponse.jaxrsRestHandler());
  }

  private String extractEmailFromAuth(String authorization) {
    try {
      String token = authorization.substring("Bearer ".length());
      com.nimbusds.jwt.SignedJWT jwt = com.nimbusds.jwt.SignedJWT.parse(token);
      return jwt.getJWTClaimsSet().getStringClaim("email");
    } catch (Exception e) {
      log.warn("Failed to extract email from JWT", e);
      return "unknown";
    }
  }
}
