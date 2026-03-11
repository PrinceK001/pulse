package org.dreamhorizon.pulseserver.tenant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.jsonwebtoken.Claims;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.UriInfo;
import java.io.IOException;
import org.dreamhorizon.pulseserver.context.ProjectContext;
import org.dreamhorizon.pulseserver.service.JwtService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TenantFilterTest {

  @Mock
  private ContainerRequestContext requestContext;

  @Mock
  private ContainerResponseContext responseContext;

  @Mock
  private UriInfo uriInfo;

  @Mock
  private JwtService jwtService;

  @Mock
  private Claims claims;

  private TenantFilter tenantFilter;

  @BeforeEach
  void setUp() {
    tenantFilter = new TenantFilter();
    TenantContext.clear();
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  @Nested
  class RequestFilterTests {

    @Test
    void shouldSetTenantIdFromHeader() throws IOException {
      when(requestContext.getUriInfo()).thenReturn(uriInfo);
      when(uriInfo.getPath()).thenReturn("/v1/some/path");
      // No Authorization header, so it falls back to X-Tenant-ID header
      when(requestContext.getHeaderString(HttpHeaders.AUTHORIZATION)).thenReturn(null);
      when(requestContext.getHeaderString(TenantFilter.PROJECT_HEADER)).thenReturn("test-tenant");

      tenantFilter.filter(requestContext);

      assertEquals("test-tenant", ProjectContext.getProjectId());
    }

    @Test
    void shouldTrimTenantIdFromHeader() throws IOException {
      when(requestContext.getUriInfo()).thenReturn(uriInfo);
      when(uriInfo.getPath()).thenReturn("/v1/some/path");
      when(requestContext.getHeaderString(HttpHeaders.AUTHORIZATION)).thenReturn(null);
      when(requestContext.getHeaderString(TenantFilter.PROJECT_HEADER)).thenReturn("  test-tenant  ");

      tenantFilter.filter(requestContext);

      assertEquals("test-tenant", ProjectContext.getProjectId());
    }
  }

  @Nested
  class ExcludedPathTests {

    @Test
    void shouldSkipFilterForHealthcheckPath() throws IOException {
      when(requestContext.getUriInfo()).thenReturn(uriInfo);
      when(uriInfo.getPath()).thenReturn("healthcheck");

      tenantFilter.filter(requestContext);

      assertNull(TenantContext.getTenantId());
      verify(requestContext, never()).getHeaderString(TenantFilter.API_KEY_HEADER);
    }

    @Test
    void shouldSkipFilterForHealthcheckPathWithLeadingSlash() throws IOException {
      when(requestContext.getUriInfo()).thenReturn(uriInfo);
      when(uriInfo.getPath()).thenReturn("/healthcheck");

      tenantFilter.filter(requestContext);

      assertNull(TenantContext.getTenantId());
      verify(requestContext, never()).getHeaderString(TenantFilter.API_KEY_HEADER);
    }

    @Test
    void shouldSkipFilterForHealthcheckSubPath() throws IOException {
      when(requestContext.getUriInfo()).thenReturn(uriInfo);
      when(uriInfo.getPath()).thenReturn("healthcheck/deep");

      tenantFilter.filter(requestContext);

      assertNull(TenantContext.getTenantId());
      verify(requestContext, never()).getHeaderString(TenantFilter.API_KEY_HEADER);
    }

    @Test
    void shouldSkipFilterForAuthPath() throws IOException {
      when(requestContext.getUriInfo()).thenReturn(uriInfo);
      when(uriInfo.getPath()).thenReturn("v1/auth/social/authenticate");

      tenantFilter.filter(requestContext);

      assertNull(TenantContext.getTenantId());
      verify(requestContext, never()).getHeaderString(TenantFilter.API_KEY_HEADER);
    }

    @Test
    void shouldSkipFilterForAuthPathWithLeadingSlash() throws IOException {
      when(requestContext.getUriInfo()).thenReturn(uriInfo);
      when(uriInfo.getPath()).thenReturn("/v1/auth/token/verify");

      tenantFilter.filter(requestContext);

      assertNull(TenantContext.getTenantId());
      verify(requestContext, never()).getHeaderString(TenantFilter.API_KEY_HEADER);
    }

    @Test
    void shouldSkipFilterForAlertsPath() throws IOException {
      when(requestContext.getUriInfo()).thenReturn(uriInfo);
      when(uriInfo.getPath()).thenReturn("alerts");

      tenantFilter.filter(requestContext);

      assertNull(TenantContext.getTenantId());
      verify(requestContext, never()).getHeaderString(TenantFilter.API_KEY_HEADER);
    }

    @Test
    void shouldSkipFilterForAlertsSubPath() throws IOException {
      when(requestContext.getUriInfo()).thenReturn(uriInfo);
      when(uriInfo.getPath()).thenReturn("/alerts/some/path");

      tenantFilter.filter(requestContext);

      assertNull(TenantContext.getTenantId());
      verify(requestContext, never()).getHeaderString(TenantFilter.API_KEY_HEADER);
    }

    @Test
    void shouldNotExcludeNullPath() throws IOException {
      when(requestContext.getUriInfo()).thenReturn(uriInfo);
      when(uriInfo.getPath()).thenReturn(null);
      when(requestContext.getHeaderString(TenantFilter.PROJECT_HEADER)).thenReturn(null);
      when(requestContext.getHeaderString(TenantFilter.API_KEY_HEADER)).thenReturn("test-tenant");

      tenantFilter.filter(requestContext);

      assertEquals("test-tenant", ProjectContext.getProjectId());
    }
  }

  @Nested
  class ResponseFilterTests {

    @Test
    void shouldClearTenantContextOnResponse() throws IOException {
      TenantContext.setTenantId("test-tenant");
      assertEquals("test-tenant", TenantContext.getTenantId());

      tenantFilter.filter(requestContext, responseContext);

      assertNull(TenantContext.getTenantId());
    }

    @Test
    void shouldClearTenantContextEvenWhenAlreadyNull() throws IOException {
      assertNull(TenantContext.getTenantId());

      tenantFilter.filter(requestContext, responseContext);

      assertNull(TenantContext.getTenantId());
    }
  }

  @Nested
  class EdgeCaseTests {

    @Test
    void shouldHandlePathWithOnlySlash() throws IOException {
      when(requestContext.getUriInfo()).thenReturn(uriInfo);
      when(uriInfo.getPath()).thenReturn("/");
      when(requestContext.getHeaderString(TenantFilter.PROJECT_HEADER)).thenReturn(null);
      when(requestContext.getHeaderString(TenantFilter.API_KEY_HEADER)).thenReturn("root-tenant");

      tenantFilter.filter(requestContext);

      assertEquals("root-tenant", ProjectContext.getProjectId());
    }

    @Test
    void shouldHandleEmptyPath() throws IOException {
      when(requestContext.getUriInfo()).thenReturn(uriInfo);
      when(uriInfo.getPath()).thenReturn("");
      when(requestContext.getHeaderString(TenantFilter.PROJECT_HEADER)).thenReturn(null);
      when(requestContext.getHeaderString(TenantFilter.API_KEY_HEADER)).thenReturn("empty-path-tenant");

      tenantFilter.filter(requestContext);

      assertEquals("empty-path-tenant", ProjectContext.getProjectId());
    }

    @Test
    void shouldNotExcludeHealthcheckPrefix() throws IOException {
      // "healthchecker" should NOT be excluded (it's not exactly "healthcheck")
      when(requestContext.getUriInfo()).thenReturn(uriInfo);
      when(uriInfo.getPath()).thenReturn("healthchecker");
      when(requestContext.getHeaderString(TenantFilter.PROJECT_HEADER)).thenReturn(null);
      when(requestContext.getHeaderString(TenantFilter.API_KEY_HEADER)).thenReturn("health-tenant");

      tenantFilter.filter(requestContext);

      assertEquals("health-tenant", ProjectContext.getProjectId());
    }

    @Test
    void shouldHandleRegularApiPath() throws IOException {
      when(requestContext.getUriInfo()).thenReturn(uriInfo);
      when(uriInfo.getPath()).thenReturn("v1/metrics/query");
      when(requestContext.getHeaderString(TenantFilter.PROJECT_HEADER)).thenReturn(null);
      when(requestContext.getHeaderString(TenantFilter.API_KEY_HEADER)).thenReturn("api-tenant");

      tenantFilter.filter(requestContext);

      assertEquals("api-tenant", ProjectContext.getProjectId());
    }

    @Test
    void shouldProcessNonAuthPathStartingWithV1() throws IOException {
      when(requestContext.getUriInfo()).thenReturn(uriInfo);
      when(uriInfo.getPath()).thenReturn("v1/configs");
      when(requestContext.getHeaderString(TenantFilter.PROJECT_HEADER)).thenReturn(null);
      when(requestContext.getHeaderString(TenantFilter.API_KEY_HEADER)).thenReturn("config-tenant");

      tenantFilter.filter(requestContext);

      assertEquals("config-tenant", ProjectContext.getProjectId());
    }
  }
}
