package org.dreamhorizon.pulseserver.authz;

import io.jsonwebtoken.Claims;
import jakarta.annotation.Priority;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ResourceInfo;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.config.OpenFgaConfig;
import org.dreamhorizon.pulseserver.guice.GuiceInjector;
import org.dreamhorizon.pulseserver.service.JwtService;
import org.dreamhorizon.pulseserver.tenant.TenantContext;

import java.io.IOException;
import java.lang.reflect.Method;

/**
 * JAX-RS filter that enforces authorization based on {@link RequiresPermission} annotations.
 *
 * <p>This filter runs after authentication (TenantFilter) and checks if the current user
 * has the required permission to access the requested resource using OpenFGA.</p>
 *
 * <p>The filter extracts the user ID from the JWT token and the object ID from path parameters,
 * then queries OpenFGA to verify the permission.</p>
 */
@Slf4j
@Provider
@Priority(Priorities.AUTHORIZATION)
public class AuthzFilter implements ContainerRequestFilter {

  private static final String BEARER_PREFIX = "Bearer ";
  private static final String CLAIM_USER_ID = "userId";
  private static final String CLAIM_EMAIL = "email";
  private static final String USER_ID_PROPERTY = "pulse.userId";

  @Context
  private ResourceInfo resourceInfo;

  private OpenFgaService openFgaService;
  private OpenFgaConfig openFgaConfig;
  private JwtService jwtService;

  @Override
  public void filter(ContainerRequestContext requestContext) throws IOException {
    Method method = resourceInfo.getResourceMethod();
    if (method == null) {
      return;
    }

    // Check for @RequiresPermission annotation
    RequiresPermission permission = method.getAnnotation(RequiresPermission.class);
    if (permission == null) {
      return; // No authorization required for this method
    }

    // Check if OpenFGA is enabled
    OpenFgaConfig config = getOpenFgaConfig();
    if (config == null || !config.isEnabled()) {
      log.debug("OpenFGA authorization is disabled, skipping permission check");
      return;
    }

    // Extract user ID from JWT token
    String userId = extractUserId(requestContext);
    if (userId == null || userId.isBlank()) {
      log.warn("Authorization failed: User ID not found in token for path: {}",
          requestContext.getUriInfo().getPath());
      abortUnauthorized(requestContext, "User ID not found in token");
      return;
    }

    // Store userId in request context for downstream use
    requestContext.setProperty(USER_ID_PROPERTY, userId);

    // Extract object ID
    String objectId = extractObjectId(requestContext, permission);
    if (objectId == null || objectId.isBlank()) {
      log.warn("Authorization failed: Object ID not found for param '{}' on path: {}",
          permission.objectIdParam(), requestContext.getUriInfo().getPath());
      abortBadRequest(requestContext, "Missing resource ID: " + permission.objectIdParam());
      return;
    }

    // Perform permission check
    try {
      OpenFgaService service = getOpenFgaService();
      Boolean allowed = service.check(
          userId,
          permission.relation(),
          permission.objectType(),
          objectId
      ).blockingGet();

      if (!Boolean.TRUE.equals(allowed)) {
        log.warn("Access denied: user '{}' cannot '{}' on {}:{}",
            userId, permission.relation(), permission.objectType(), objectId);
        abortForbidden(requestContext);
      } else {
        log.debug("Access granted: user '{}' can '{}' on {}:{}",
            userId, permission.relation(), permission.objectType(), objectId);
      }
    } catch (Exception e) {
      log.error("Authorization check failed for user '{}' on {}:{}: {}",
          userId, permission.objectType(), objectId, e.getMessage());
      abortInternalError(requestContext, "Authorization check failed");
    }
  }

  /**
   * Extract user ID from the JWT token in the Authorization header.
   */
  private String extractUserId(ContainerRequestContext requestContext) {
    String authHeader = requestContext.getHeaderString(HttpHeaders.AUTHORIZATION);
    if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
      return null;
    }

    String token = authHeader.substring(BEARER_PREFIX.length()).trim();
    if (token.isBlank()) {
      return null;
    }

    try {
      JwtService service = getJwtService();
      if (service == null) {
        log.warn("JwtService not available");
        return null;
      }

      Claims claims = service.verifyToken(token);
      
      // Try userId claim first, then fall back to email
      String userId = claims.get(CLAIM_USER_ID, String.class);
      if (userId == null || userId.isBlank()) {
        userId = claims.get(CLAIM_EMAIL, String.class);
      }
      if (userId == null || userId.isBlank()) {
        userId = claims.getSubject();
      }
      
      return userId;
    } catch (Exception e) {
      log.debug("Failed to extract userId from token: {}", e.getMessage());
      return null;
    }
  }

  /**
   * Extract the object ID from the request based on the annotation configuration.
   */
  private String extractObjectId(ContainerRequestContext requestContext, RequiresPermission permission) {
    if (permission.useTenantContext()) {
      return TenantContext.getTenantId();
    }
    return requestContext.getUriInfo().getPathParameters().getFirst(permission.objectIdParam());
  }

  private void abortUnauthorized(ContainerRequestContext ctx, String message) {
    ctx.abortWith(Response.status(Response.Status.UNAUTHORIZED)
        .entity("{\"error\": \"" + message + "\"}")
        .type(MediaType.APPLICATION_JSON)
        .build());
  }

  private void abortForbidden(ContainerRequestContext ctx) {
    ctx.abortWith(Response.status(Response.Status.FORBIDDEN)
        .entity("{\"error\": \"Access denied. You do not have permission to perform this action.\"}")
        .type(MediaType.APPLICATION_JSON)
        .build());
  }

  private void abortBadRequest(ContainerRequestContext ctx, String message) {
    ctx.abortWith(Response.status(Response.Status.BAD_REQUEST)
        .entity("{\"error\": \"" + message + "\"}")
        .type(MediaType.APPLICATION_JSON)
        .build());
  }

  private void abortInternalError(ContainerRequestContext ctx, String message) {
    ctx.abortWith(Response.status(Response.Status.INTERNAL_SERVER_ERROR)
        .entity("{\"error\": \"" + message + "\"}")
        .type(MediaType.APPLICATION_JSON)
        .build());
  }

  private OpenFgaService getOpenFgaService() {
    if (openFgaService == null) {
      try {
        openFgaService = GuiceInjector.getGuiceInjector().getInstance(OpenFgaService.class);
      } catch (Exception e) {
        log.error("Failed to get OpenFgaService: {}", e.getMessage());
      }
    }
    return openFgaService;
  }

  private OpenFgaConfig getOpenFgaConfig() {
    if (openFgaConfig == null) {
      try {
        openFgaConfig = GuiceInjector.getGuiceInjector().getInstance(OpenFgaConfig.class);
      } catch (Exception e) {
        log.error("Failed to get OpenFgaConfig: {}", e.getMessage());
      }
    }
    return openFgaConfig;
  }

  private JwtService getJwtService() {
    if (jwtService == null) {
      try {
        jwtService = GuiceInjector.getGuiceInjector().getInstance(JwtService.class);
      } catch (Exception e) {
        log.error("Failed to get JwtService: {}", e.getMessage());
      }
    }
    return jwtService;
  }

  // For testing purposes
  void setOpenFgaService(OpenFgaService service) {
    this.openFgaService = service;
  }

  void setOpenFgaConfig(OpenFgaConfig config) {
    this.openFgaConfig = config;
  }

  void setJwtService(JwtService service) {
    this.jwtService = service;
  }
}

