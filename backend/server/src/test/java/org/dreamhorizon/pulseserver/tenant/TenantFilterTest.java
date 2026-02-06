package org.dreamhorizon.pulseserver.tenant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.core.UriInfo;
import java.io.IOException;
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
      when(requestContext.getHeaderString(TenantFilter.TENANT_HEADER)).thenReturn("test-tenant");

      tenantFilter.filter(requestContext);

      assertEquals("test-tenant", TenantContext.getTenantId());
    }

    @Test
    void shouldTrimTenantIdFromHeader() throws IOException {
      when(requestContext.getUriInfo()).thenReturn(uriInfo);
      when(uriInfo.getPath()).thenReturn("/v1/some/path");
      when(requestContext.getHeaderString(TenantFilter.TENANT_HEADER)).thenReturn("  test-tenant  ");

      tenantFilter.filter(requestContext);

      assertEquals("test-tenant", TenantContext.getTenantId());
    }

    @Test
    void shouldUseDefaultTenantWhenHeaderMissing() throws IOException {
      when(requestContext.getUriInfo()).thenReturn(uriInfo);
      when(uriInfo.getPath()).thenReturn("/v1/some/path");
      when(requestContext.getHeaderString(TenantFilter.TENANT_HEADER)).thenReturn(null);

      tenantFilter.filter(requestContext);

      assertEquals("default", TenantContext.getTenantId());
    }

    @Test
    void shouldUseDefaultTenantWhenHeaderBlank() throws IOException {
      when(requestContext.getUriInfo()).thenReturn(uriInfo);
      when(uriInfo.getPath()).thenReturn("/v1/some/path");
      when(requestContext.getHeaderString(TenantFilter.TENANT_HEADER)).thenReturn("   ");

      tenantFilter.filter(requestContext);

      assertEquals("default", TenantContext.getTenantId());
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
      verify(requestContext, never()).getHeaderString(TenantFilter.TENANT_HEADER);
    }

    @Test
    void shouldSkipFilterForHealthcheckPathWithLeadingSlash() throws IOException {
      when(requestContext.getUriInfo()).thenReturn(uriInfo);
      when(uriInfo.getPath()).thenReturn("/healthcheck");

      tenantFilter.filter(requestContext);

      assertNull(TenantContext.getTenantId());
      verify(requestContext, never()).getHeaderString(TenantFilter.TENANT_HEADER);
    }

    @Test
    void shouldSkipFilterForHealthcheckSubPath() throws IOException {
      when(requestContext.getUriInfo()).thenReturn(uriInfo);
      when(uriInfo.getPath()).thenReturn("healthcheck/deep");

      tenantFilter.filter(requestContext);

      assertNull(TenantContext.getTenantId());
      verify(requestContext, never()).getHeaderString(TenantFilter.TENANT_HEADER);
    }

    @Test
    void shouldSkipFilterForAuthPath() throws IOException {
      when(requestContext.getUriInfo()).thenReturn(uriInfo);
      when(uriInfo.getPath()).thenReturn("v1/auth/social/authenticate");

      tenantFilter.filter(requestContext);

      assertNull(TenantContext.getTenantId());
      verify(requestContext, never()).getHeaderString(TenantFilter.TENANT_HEADER);
    }

    @Test
    void shouldSkipFilterForAuthPathWithLeadingSlash() throws IOException {
      when(requestContext.getUriInfo()).thenReturn(uriInfo);
      when(uriInfo.getPath()).thenReturn("/v1/auth/token/verify");

      tenantFilter.filter(requestContext);

      assertNull(TenantContext.getTenantId());
      verify(requestContext, never()).getHeaderString(TenantFilter.TENANT_HEADER);
    }

    @Test
    void shouldSkipFilterForAlertsPath() throws IOException {
      when(requestContext.getUriInfo()).thenReturn(uriInfo);
      when(uriInfo.getPath()).thenReturn("alerts");

      tenantFilter.filter(requestContext);

      assertNull(TenantContext.getTenantId());
      verify(requestContext, never()).getHeaderString(TenantFilter.TENANT_HEADER);
    }

    @Test
    void shouldSkipFilterForAlertsSubPath() throws IOException {
      when(requestContext.getUriInfo()).thenReturn(uriInfo);
      when(uriInfo.getPath()).thenReturn("/alerts/some/path");

      tenantFilter.filter(requestContext);

      assertNull(TenantContext.getTenantId());
      verify(requestContext, never()).getHeaderString(TenantFilter.TENANT_HEADER);
    }

    @Test
    void shouldNotExcludeNullPath() throws IOException {
      when(requestContext.getUriInfo()).thenReturn(uriInfo);
      when(uriInfo.getPath()).thenReturn(null);
      when(requestContext.getHeaderString(TenantFilter.TENANT_HEADER)).thenReturn("test-tenant");

      tenantFilter.filter(requestContext);

      assertEquals("test-tenant", TenantContext.getTenantId());
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
  }
}

