package org.dreamhorizon.pulseserver.authz;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to declare permission requirements on JAX-RS resource methods.
 *
 * <p>Usage example:</p>
 * <pre>
 * {@code
 * @GET
 * @Path("/{alertId}")
 * @RequiresPermission(objectType = "alert", relation = "can_view", objectIdParam = "alertId")
 * public Response getAlert(@PathParam("alertId") String alertId) {
 *     // Method implementation
 * }
 * }
 * </pre>
 *
 * <p>The {@link AuthzFilter} intercepts requests to methods annotated with this annotation
 * and checks if the current user has the required permission.</p>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiresPermission {

  /**
   * The object type in OpenFGA (e.g., "tenant", "project", "alert", "sdk_config").
   *
   * @return the object type
   */
  String objectType();

  /**
   * The relation/permission to check (e.g., "can_view", "can_edit", "can_delete").
   *
   * @return the relation to check
   */
  String relation();

  /**
   * The name of the path parameter containing the object ID.
   * This parameter will be extracted from the request URI.
   *
   * @return the path parameter name
   */
  String objectIdParam();

  /**
   * Optional: Whether to use the tenant ID from context instead of a path parameter.
   * When true, the current tenant from TenantContext is used as the object ID.
   *
   * @return true if tenant context should be used
   */
  boolean useTenantContext() default false;
}

