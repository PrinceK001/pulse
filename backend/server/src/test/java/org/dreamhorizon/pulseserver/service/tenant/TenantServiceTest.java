package org.dreamhorizon.pulseserver.service.tenant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import java.util.List;
import org.dreamhorizon.pulseserver.client.chclient.ClickhouseTenantConnectionPoolManager;
import org.dreamhorizon.pulseserver.dao.clickhousecredentials.models.ClickhouseCredentials;
import org.dreamhorizon.pulseserver.dao.clickhousecredentials.models.ClickhouseTenantCredentialAudit;
import org.dreamhorizon.pulseserver.dao.tenant.TenantDao;
import org.dreamhorizon.pulseserver.dao.tenant.models.Tenant;
import org.dreamhorizon.pulseserver.service.tenant.models.CreateTenantRequest;
import org.dreamhorizon.pulseserver.service.tenant.models.UpdateTenantRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
class TenantServiceTest {

  @Mock
  TenantDao tenantDao;


  @Mock
  ClickhouseTenantConnectionPoolManager poolManager;

  @Mock
  org.dreamhorizon.pulseserver.service.OpenFgaService openFgaService;

  TenantService tenantService;

  @BeforeEach
  void setup() {
    tenantService = new TenantService(tenantDao, credentialsDao, poolManager, openFgaService);
    tenantService = new TenantService(tenantDao, credentialsDao, poolManager, openFgaService);
  }

  private Tenant createMockTenant() {
    return Tenant.builder()
        .tenantId("test_tenant")
        .name("Test Tenant")
        .description("Test Description")
        .gcpTenantId("gcp-test-123")
        .domainName("test.example.com")
        .isActive(true)
        .createdAt("2026-01-01T00:00:00")
        .updatedAt("2026-01-01T00:00:00")
        .build();
  }

  private ClickhouseCredentials createMockCredentials() {
    return ClickhouseCredentials.builder()
        .id(1L)
        .tenantId("test_tenant")
        .clickhouseUsername("tenant_test_tenant")
        .clickhousePassword("decrypted_password")
        .isActive(true)
        .createdAt("2026-01-01T00:00:00")
        .updatedAt("2026-01-01T00:00:00")
        .build();
  }

  private ClickhouseTenantCredentialAudit createMockAudit() {
    return ClickhouseTenantCredentialAudit.builder()
        .id(1L)
        .projectId("test_project")
        .action("CREDENTIALS_CREATED")
        .performedBy("admin@example.com")
        .details("{\"action\":\"test\"}")
        .createdAt("2026-01-01T00:00:00")
        .build();
  }

  // ==================== TENANT CRUD TESTS ====================

  @Nested
  class TestCreateTenant {

    @Test
    void shouldCreateTenantSuccessfully() {
      CreateTenantRequest request = CreateTenantRequest.builder()
          .tenantId("test_tenant")
          .name("Test Tenant")
          .description("Test Description")
          .gcpTenantId("gcp-test-123")
          .domainName("test.example.com")
          .build();

      Tenant mockTenant = createMockTenant();
      when(tenantDao.createTenant(any(Tenant.class))).thenReturn(Single.just(mockTenant));

      Tenant result = tenantService.createTenant(request).blockingGet();

      assertNotNull(result);
      assertEquals("test_tenant", result.getTenantId());
      assertEquals("Test Tenant", result.getName());
      verify(tenantDao).createTenant(any(Tenant.class));
    }

    @Test
    void shouldThrowExceptionOnDaoError() {
      CreateTenantRequest request = CreateTenantRequest.builder()
          .tenantId("test_tenant")
          .name("Test Tenant")
          .build();

      when(tenantDao.createTenant(any(Tenant.class)))
          .thenReturn(Single.error(new RuntimeException("Database error")));

      Exception ex = assertThrows(RuntimeException.class,
          () -> tenantService.createTenant(request).blockingGet());
      assertTrue(ex.getMessage().contains("Database error"));
    }
  }

  @Nested
  class TestGetTenant {

    @Test
    void shouldGetTenantSuccessfully() {
      Tenant mockTenant = createMockTenant();
      when(tenantDao.getTenantById("test_tenant")).thenReturn(Maybe.just(mockTenant));

      Tenant result = tenantService.getTenant("test_tenant").blockingGet();

      assertNotNull(result);
      assertEquals("test_tenant", result.getTenantId());
    }

    @Test
    void shouldReturnEmptyWhenTenantNotFound() {
      when(tenantDao.getTenantById("non_existent")).thenReturn(Maybe.empty());

      Tenant result = tenantService.getTenant("non_existent").blockingGet();

      assertEquals(null, result);
    }

    @Test
    void shouldThrowExceptionOnDaoError() {
      when(tenantDao.getTenantById(anyString()))
          .thenReturn(Maybe.error(new RuntimeException("Database error")));

      Exception ex = assertThrows(RuntimeException.class,
          () -> tenantService.getTenant("test_tenant").blockingGet());
      assertTrue(ex.getMessage().contains("Database error"));
    }
  }

  @Nested
  class TestGetAllActiveTenants {

    @Test
    void shouldGetAllActiveTenantsSuccessfully() {
      Tenant mockTenant = createMockTenant();
      when(tenantDao.getAllActiveTenants()).thenReturn(Flowable.just(mockTenant));

      List<Tenant> result = tenantService.getAllActiveTenants().toList().blockingGet();

      assertNotNull(result);
      assertEquals(1, result.size());
      assertEquals("test_tenant", result.get(0).getTenantId());
    }

    @Test
    void shouldReturnEmptyListWhenNoTenants() {
      when(tenantDao.getAllActiveTenants()).thenReturn(Flowable.empty());

      List<Tenant> result = tenantService.getAllActiveTenants().toList().blockingGet();

      assertNotNull(result);
      assertTrue(result.isEmpty());
    }

    @Test
    void shouldThrowExceptionOnDaoError() {
      when(tenantDao.getAllActiveTenants())
          .thenReturn(Flowable.error(new RuntimeException("Database error")));

      Exception ex = assertThrows(RuntimeException.class,
          () -> tenantService.getAllActiveTenants().toList().blockingGet());
      assertTrue(ex.getMessage().contains("Database error"));
    }
  }

  @Nested
  class TestGetAllTenants {

    @Test
    void shouldGetAllTenantsSuccessfully() {
      Tenant mockTenant = createMockTenant();
      when(tenantDao.getAllTenants()).thenReturn(Flowable.just(mockTenant));

      List<Tenant> result = tenantService.getAllTenants().toList().blockingGet();

      assertNotNull(result);
      assertEquals(1, result.size());
    }

    @Test
    void shouldThrowExceptionOnDaoError() {
      when(tenantDao.getAllTenants())
          .thenReturn(Flowable.error(new RuntimeException("Database error")));

      Exception ex = assertThrows(RuntimeException.class,
          () -> tenantService.getAllTenants().toList().blockingGet());
      assertTrue(ex.getMessage().contains("Database error"));
    }
  }

  @Nested
  class TestUpdateTenant {

    @Test
    void shouldUpdateTenantSuccessfully() {
      UpdateTenantRequest request = UpdateTenantRequest.builder()
          .tenantId("test_tenant")
          .name("Updated Name")
          .description("Updated Description")
          .build();

      Tenant updatedTenant = createMockTenant();
      updatedTenant.setName("Updated Name");
      when(tenantDao.updateTenant(any()))
          .thenReturn(Single.just(updatedTenant));

      Tenant result = tenantService.updateTenant(request).blockingGet();

      assertNotNull(result);
      assertEquals("Updated Name", result.getName());
    }

    @Test
    void shouldThrowExceptionOnDaoError() {
      UpdateTenantRequest request = UpdateTenantRequest.builder()
          .tenantId("test_tenant")
          .name("Updated Name")
          .build();

      when(tenantDao.updateTenant(any()))
          .thenReturn(Single.error(new RuntimeException("Tenant not found")));

      Exception ex = assertThrows(RuntimeException.class,
          () -> tenantService.updateTenant(request).blockingGet());
      assertTrue(ex.getMessage().contains("Tenant not found"));
    }
  }

  @Nested
  class TestDeactivateTenant {

    @Test
    void shouldDeactivateTenantSuccessfully() {
      when(tenantDao.deactivateTenant("test_tenant")).thenReturn(Completable.complete());

      tenantService.deactivateTenant("test_tenant").blockingAwait();

      verify(tenantDao).deactivateTenant("test_tenant");
    }

    @Test
    void shouldThrowExceptionOnDaoError() {
      when(tenantDao.deactivateTenant(anyString()))
          .thenReturn(Completable.error(new RuntimeException("Database error")));

      Exception ex = assertThrows(RuntimeException.class,
          () -> tenantService.deactivateTenant("test_tenant").blockingAwait());
      assertTrue(ex.getMessage().contains("Database error"));
    }
  }

  @Nested
  class TestActivateTenant {

    @Test
    void shouldActivateTenantSuccessfully() {
      when(tenantDao.activateTenant("test_tenant")).thenReturn(Completable.complete());

      tenantService.activateTenant("test_tenant").blockingAwait();

      verify(tenantDao).activateTenant("test_tenant");
    }

    @Test
    void shouldThrowExceptionOnDaoError() {
      when(tenantDao.activateTenant(anyString()))
          .thenReturn(Completable.error(new RuntimeException("Tenant not found")));

      Exception ex = assertThrows(RuntimeException.class,
          () -> tenantService.activateTenant("test_tenant").blockingAwait());
      assertTrue(ex.getMessage().contains("Tenant not found"));
    }
  }

}
