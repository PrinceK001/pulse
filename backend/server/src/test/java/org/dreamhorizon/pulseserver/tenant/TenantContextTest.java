package org.dreamhorizon.pulseserver.tenant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class TenantContextTest {

  @BeforeEach
  void setUp() {
    TenantContext.clear();
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  @Nested
  class SetAndGetTenantIdTests {

    @Test
    void shouldSetAndGetTenantId() {
      TenantContext.setTenantId("test-tenant");

      assertEquals("test-tenant", TenantContext.getTenantId());
    }

    @Test
    void shouldReturnNullWhenTenantIdNotSet() {
      assertNull(TenantContext.getTenantId());
    }

    @Test
    void shouldOverwritePreviousTenantId() {
      TenantContext.setTenantId("tenant-1");
      TenantContext.setTenantId("tenant-2");

      assertEquals("tenant-2", TenantContext.getTenantId());
    }
  }

  @Nested
  class GetCurrentTenantIdTests {

    @Test
    void shouldReturnOptionalWithTenantId() {
      TenantContext.setTenantId("test-tenant");

      Optional<String> result = TenantContext.getCurrentTenantId();

      assertTrue(result.isPresent());
      assertEquals("test-tenant", result.get());
    }

    @Test
    void shouldReturnEmptyOptionalWhenNotSet() {
      Optional<String> result = TenantContext.getCurrentTenantId();

      assertTrue(result.isEmpty());
    }
  }

  @Nested
  class SetAndGetTenantTests {

    @Test
    void shouldSetAndGetTenant() {
      Tenant tenant = Tenant.builder()
          .tenantId("test-tenant")
          .name("Test Tenant")
          .build();

      TenantContext.setTenant(tenant);

      Optional<Tenant> result = TenantContext.getCurrentTenant();
      assertTrue(result.isPresent());
      assertEquals("test-tenant", result.get().getTenantId());
      assertEquals("Test Tenant", result.get().getName());
    }

    @Test
    void shouldSetTenantIdWhenSettingTenant() {
      Tenant tenant = Tenant.builder()
          .tenantId("test-tenant")
          .name("Test Tenant")
          .build();

      TenantContext.setTenant(tenant);

      assertEquals("test-tenant", TenantContext.getTenantId());
    }

    @Test
    void shouldReturnEmptyOptionalWhenTenantNotSet() {
      Optional<Tenant> result = TenantContext.getCurrentTenant();

      assertTrue(result.isEmpty());
    }

    @Test
    void shouldHandleNullTenant() {
      TenantContext.setTenant(null);

      Optional<Tenant> result = TenantContext.getCurrentTenant();
      assertTrue(result.isEmpty());
    }
  }

  @Nested
  class ClearTests {

    @Test
    void shouldClearTenantId() {
      TenantContext.setTenantId("test-tenant");
      assertEquals("test-tenant", TenantContext.getTenantId());

      TenantContext.clear();

      assertNull(TenantContext.getTenantId());
    }

    @Test
    void shouldClearTenant() {
      Tenant tenant = Tenant.builder()
          .tenantId("test-tenant")
          .build();
      TenantContext.setTenant(tenant);

      TenantContext.clear();

      assertTrue(TenantContext.getCurrentTenant().isEmpty());
    }

    @Test
    void shouldNotFailWhenClearingEmptyContext() {
      TenantContext.clear();
      TenantContext.clear(); // Should not throw

      assertNull(TenantContext.getTenantId());
    }
  }

  @Nested
  class IsSetTests {

    @Test
    void shouldReturnTrueWhenTenantIdIsSet() {
      TenantContext.setTenantId("test-tenant");

      assertTrue(TenantContext.isSet());
    }

    @Test
    void shouldReturnFalseWhenTenantIdNotSet() {
      assertFalse(TenantContext.isSet());
    }

    @Test
    void shouldReturnFalseAfterClear() {
      TenantContext.setTenantId("test-tenant");
      TenantContext.clear();

      assertFalse(TenantContext.isSet());
    }
  }

  @Nested
  class RequireTenantIdTests {

    @Test
    void shouldReturnTenantIdWhenSet() {
      TenantContext.setTenantId("test-tenant");

      String result = TenantContext.requireTenantId();

      assertEquals("test-tenant", result);
    }

    @Test
    void shouldThrowExceptionWhenNotSet() {
      IllegalStateException exception = assertThrows(
          IllegalStateException.class,
          TenantContext::requireTenantId
      );

      assertEquals("No tenant context is set", exception.getMessage());
    }
  }
}

