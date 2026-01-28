package org.dreamhorizon.pulseserver.tenant;

import com.google.inject.Inject;
import jakarta.annotation.Priority;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.ext.Provider;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.service.JwtService;

/**
 * JAX-RS filter that extracts tenant information from the request and sets it in the TenantContext.
 *
 * <p>Tenant resolution order:</p>
 * <ol>
 *   <li>X-Tenant-ID header (explicit override, useful for admin operations)</li>
 *   <li>tenant_id claim from JWT token</li>
 *   <li>Default tenant (fallback for backward compatibility)</li>
 * </ol>
 */
@Slf4j
@Provider
@Priority(Priorities.AUTHENTICATION + 10) // Run after authentication but before authorization
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class TenantFilter implements ContainerRequestFilter, ContainerResponseFilter {

  public static final String TENANT_HEADER = "X-Tenant-ID";
  public static final String AUTHORIZATION_HEADER = "Authorization";
  public static final String BEARER_PREFIX = "Bearer ";
  public static final String TENANT_CLAIM = "tenant_id";

  private final JwtService jwtService;

  @Override
  public void filter(ContainerRequestContext requestContext) throws IOException {
    String tenantId = resolveTenantId(requestContext);
    TenantContext.setTenantId(tenantId);
    log.debug("Request tenant context set to: {} for path: {}",
        tenantId, requestContext.getUriInfo().getPath());
  }

  @Override
  public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext)
      throws IOException {
    // Clear tenant context after request processing
    TenantContext.clear();
  }

  /**
   * Resolves the tenant ID from the request.
   *
   * @param requestContext the request context
   * @return the resolved tenant ID
   */
  private String resolveTenantId(ContainerRequestContext requestContext) {
    // Priority 1: Explicit X-Tenant-ID header
    String headerTenantId = requestContext.getHeaderString(TENANT_HEADER);
    if (headerTenantId != null && !headerTenantId.isBlank()) {
      log.debug("Tenant ID resolved from header: {}", headerTenantId);
      return headerTenantId.trim();
    }

//    // Priority 2: Extract from JWT token
//    String authorization = requestContext.getHeaderString(AUTHORIZATION_HEADER);
//    if (authorization != null && authorization.startsWith(BEARER_PREFIX)) {
//      String token = authorization.substring(BEARER_PREFIX.length());
//      try {
//        Claims claims = jwtService.verifyToken(token);
//        String tokenTenantId = claims.get(TENANT_CLAIM, String.class);
//        if (tokenTenantId != null && !tokenTenantId.isBlank()) {
//          log.debug("Tenant ID resolved from JWT: {}", tokenTenantId);
//          return tokenTenantId;
//        }
//      } catch (Exception e) {
//        log.debug("Could not extract tenant from JWT: {}", e.getMessage());
//      }
//    }

    // Priority 3: Default tenant (backward compatibility)
    log.debug("Using default tenant ID");
    return Tenant.DEFAULT_TENANT_ID;
  }
}

