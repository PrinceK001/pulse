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

    // Priority 2: Default tenant (backward compatibility)
    log.debug("Using default tenant ID");
    return Tenant.DEFAULT_TENANT_ID;
  }
}

