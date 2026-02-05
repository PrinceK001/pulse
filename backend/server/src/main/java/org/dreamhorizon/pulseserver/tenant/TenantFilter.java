package org.dreamhorizon.pulseserver.tenant;

import jakarta.annotation.Priority;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.ext.Provider;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;

/**
 * JAX-RS filter that extracts tenant information from the request and sets it in the TenantContext.
 *
 * <p>Tenant resolution order:</p>
 * <ol>
 *   <li>X-Tenant-ID header (explicit override, useful for admin operations)</li>
 *   <li>Default tenant (fallback for backward compatibility)</li>
 * </ol>
 *
 * <p>Note: JWT-based tenant extraction can be enabled by uncommenting the relevant code
 * and adding back the JwtService dependency.</p>
 */
@Slf4j
@Provider
@Priority(Priorities.AUTHENTICATION + 10) // Run after authentication but before authorization
public class TenantFilter implements ContainerRequestFilter, ContainerResponseFilter {

  public static final String TENANT_HEADER = "X-Tenant-ID";
  private static final String HEALTHCHECK_PATH = "healthcheck";
  private static final String DEFAULT_TENANT_ID = "default";

  @Override
  public void filter(ContainerRequestContext requestContext) throws IOException {
    String path = requestContext.getUriInfo().getPath();

    // Skip tenant check for healthcheck endpoint
    if (isExcludedPath(path)) {
      log.debug("Skipping tenant filter for excluded path: {}", path);
      return;
    }

    String tenantId = resolveTenantId(requestContext);
    TenantContext.setTenantId(tenantId);
    log.debug("Request tenant context set to: {} for path: {}",
        tenantId, path);
  }

  private boolean isExcludedPath(String path) {
    if (path == null) {
      return false;
    }
    String normalizedPath = path.startsWith("/") ? path.substring(1) : path;
    return normalizedPath.equals(HEALTHCHECK_PATH)
        || normalizedPath.startsWith(HEALTHCHECK_PATH + "/");
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
   * @return the resolved tenant ID, or default if header not present
   */
  private String resolveTenantId(ContainerRequestContext requestContext) {
    // Priority 1: Explicit X-Tenant-ID header
    String headerTenantId = requestContext.getHeaderString(TENANT_HEADER);
    if (headerTenantId != null && !headerTenantId.isBlank()) {
      log.debug("Tenant ID resolved from header: {}", headerTenantId);
      return headerTenantId.trim();
    }

    // Priority 2: Use default tenant
    log.debug("No X-Tenant-ID header found, using default tenant: {}", DEFAULT_TENANT_ID);
    return DEFAULT_TENANT_ID;
  }
}

