package org.dreamhorizon.pulseserver.authz;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to require that the user is a member of the current tenant.
 * 
 * <p>This is a simpler authorization check that just verifies the user has access
 * to the tenant (is either admin or member). Use this for APIs where you just need
 * to verify the user is legitimate and belongs to the tenant.</p>
 *
 * <p>Usage example:</p>
 * <pre>
 * {@code
 * @GET
 * @Path("/alerts")
 * @RequiresTenantAccess
 * public Response getAlerts() {
 *     // Only users who are members of the current tenant can access
 * }
 * }
 * </pre>
 *
 * <p>The {@link AuthzFilter} intercepts requests to methods annotated with this annotation
 * and checks if the current user has 'can_view' permission on the current tenant.</p>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiresTenantAccess {
  // No parameters needed - uses current tenant from TenantContext
}

