package org.dreamhorizon.pulseserver.client.chclient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.dreamhorizon.pulseserver.config.ClickhouseConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ClickhouseTenantConnectionPoolManagerTest {

  @Mock
  private ClickhouseConfig clickhouseConfig;

  private ClickhouseTenantConnectionPoolManager poolManager;

  @BeforeEach
  void setup() {
    poolManager = new ClickhouseTenantConnectionPoolManager(clickhouseConfig);
  }

  @Nested
  class TestGetPoolStatistics {

    @Test
    void shouldReturnZeroStatsForNonExistentTenant() {
      ClickhouseTenantConnectionPoolManager.PoolStatistics stats = 
          poolManager.getPoolStatistics("non_existent_tenant");

      assertNotNull(stats);
      assertEquals("non_existent_tenant", stats.tenantId);
      assertEquals(0, stats.activeConnections);
      assertEquals(0, stats.maxConnections);
      assertFalse(stats.isActive);
    }

    @Test
    void shouldReturnStatsWithCorrectTenantId() {
      ClickhouseTenantConnectionPoolManager.PoolStatistics stats = 
          poolManager.getPoolStatistics("test_tenant");

      assertEquals("test_tenant", stats.tenantId);
    }
  }

  @Nested
  class TestPoolStatistics {

    @Test
    void shouldCreatePoolStatisticsWithAllFields() {
      ClickhouseTenantConnectionPoolManager.PoolStatistics stats = 
          new ClickhouseTenantConnectionPoolManager.PoolStatistics(
              "tenant_1", 5, 10, true
          );

      assertEquals("tenant_1", stats.tenantId);
      assertEquals(5, stats.activeConnections);
      assertEquals(10, stats.maxConnections);
      assertTrue(stats.isActive);
    }

    @Test
    void shouldCreatePoolStatisticsWithInactivePool() {
      ClickhouseTenantConnectionPoolManager.PoolStatistics stats = 
          new ClickhouseTenantConnectionPoolManager.PoolStatistics(
              "tenant_2", 0, 10, false
          );

      assertEquals("tenant_2", stats.tenantId);
      assertEquals(0, stats.activeConnections);
      assertEquals(10, stats.maxConnections);
      assertFalse(stats.isActive);
    }
  }

  @Nested
  class TestInitializeAdminPool {

    @Test
    void shouldThrowExceptionWhenConfigIsInvalid() {
      when(clickhouseConfig.getR2dbcUrl()).thenReturn("invalid://url");
      when(clickhouseConfig.getUsername()).thenReturn("admin");
      when(clickhouseConfig.getPassword()).thenReturn("password");

      assertThrows(RuntimeException.class, () -> poolManager.initializeAdminPool());
    }
  }

  @Nested
  class TestGetAdminPool {

    @Test
    void shouldThrowExceptionWhenAdminPoolNotInitialized() {
      RuntimeException exception = assertThrows(RuntimeException.class, 
          () -> poolManager.getAdminPool());

      assertEquals("Admin pool not available", exception.getMessage());
    }
  }

  @Nested
  class TestGetPoolForTenant {

    @Test
    void shouldThrowExceptionForInvalidConfig() {
      when(clickhouseConfig.getR2dbcUrl()).thenReturn("invalid://url");

      assertThrows(RuntimeException.class, 
          () -> poolManager.getPoolForTenant("tenant_1", "user", "password"));
    }
  }

  @Nested
  class TestClosePoolForTenant {

    @Test
    void shouldHandleClosingNonExistentPool() {
      // Should not throw exception for non-existent tenant
      poolManager.closePoolForTenant("non_existent_tenant");
      
      ClickhouseTenantConnectionPoolManager.PoolStatistics stats = 
          poolManager.getPoolStatistics("non_existent_tenant");
      assertFalse(stats.isActive);
    }
  }

  @Nested
  class TestCloseAllPools {

    @Test
    void shouldHandleClosingWhenNoPools() {
      // Should not throw exception when no pools exist
      poolManager.closeAllPools();
      
      ClickhouseTenantConnectionPoolManager.PoolStatistics stats = 
          poolManager.getPoolStatistics("any_tenant");
      assertFalse(stats.isActive);
    }
  }

  @Nested
  class TestConstructor {

    @Test
    void shouldCreateWithConfig() {
      ClickhouseConfig config = mock(ClickhouseConfig.class);
      ClickhouseTenantConnectionPoolManager manager = 
          new ClickhouseTenantConnectionPoolManager(config);

      assertNotNull(manager);
    }
  }

  @Nested
  class TestPoolStatisticsFields {

    @Test
    void shouldAccessAllPublicFields() {
      ClickhouseTenantConnectionPoolManager.PoolStatistics stats = 
          new ClickhouseTenantConnectionPoolManager.PoolStatistics(
              "test_tenant", 3, 15, true
          );

      // Verify all public fields are accessible
      assertEquals("test_tenant", stats.tenantId);
      assertEquals(3, stats.activeConnections);
      assertEquals(15, stats.maxConnections);
      assertTrue(stats.isActive);
    }

    @Test
    void shouldHandleZeroConnections() {
      ClickhouseTenantConnectionPoolManager.PoolStatistics stats = 
          new ClickhouseTenantConnectionPoolManager.PoolStatistics(
              "empty_tenant", 0, 0, false
          );

      assertEquals(0, stats.activeConnections);
      assertEquals(0, stats.maxConnections);
      assertFalse(stats.isActive);
    }

    @Test
    void shouldHandleNullTenantId() {
      ClickhouseTenantConnectionPoolManager.PoolStatistics stats = 
          new ClickhouseTenantConnectionPoolManager.PoolStatistics(
              null, 0, 0, false
          );

      assertEquals(null, stats.tenantId);
    }
  }

  @Nested
  class TestMultipleOperations {

    @Test
    void shouldHandleMultipleClosePoolCalls() {
      // Multiple close calls should not throw
      poolManager.closePoolForTenant("tenant_1");
      poolManager.closePoolForTenant("tenant_1");
      poolManager.closePoolForTenant("tenant_2");
      
      assertFalse(poolManager.getPoolStatistics("tenant_1").isActive);
    }

    @Test
    void shouldHandleCloseAllPoolsMultipleTimes() {
      poolManager.closeAllPools();
      poolManager.closeAllPools();
      
      assertFalse(poolManager.getPoolStatistics("any").isActive);
    }

    @Test
    void shouldReturnDifferentStatsForDifferentTenants() {
      ClickhouseTenantConnectionPoolManager.PoolStatistics stats1 = 
          poolManager.getPoolStatistics("tenant_1");
      ClickhouseTenantConnectionPoolManager.PoolStatistics stats2 = 
          poolManager.getPoolStatistics("tenant_2");
      
      assertEquals("tenant_1", stats1.tenantId);
      assertEquals("tenant_2", stats2.tenantId);
    }
  }

  @Nested
  class TestExceptionMessages {

    @Test
    void shouldHaveCorrectMessageForAdminPoolNotAvailable() {
      RuntimeException ex = assertThrows(RuntimeException.class, 
          () -> poolManager.getAdminPool());
      
      assertTrue(ex.getMessage().contains("Admin pool not available"));
    }

    @Test
    void shouldThrowExceptionForNullR2dbcUrl() {
      when(clickhouseConfig.getR2dbcUrl()).thenReturn(null);
      
      assertThrows(Exception.class, 
          () -> poolManager.getPoolForTenant("tenant", "user", "pass"));
    }

    @Test
    void shouldThrowExceptionForEmptyR2dbcUrl() {
      when(clickhouseConfig.getR2dbcUrl()).thenReturn("");
      
      assertThrows(Exception.class, 
          () -> poolManager.getPoolForTenant("tenant", "user", "pass"));
    }
  }

  @Nested
  class TestInitializationErrors {

    @Test
    void shouldThrowExceptionForNullUsername() {
      when(clickhouseConfig.getR2dbcUrl()).thenReturn("r2dbc:clickhouse://localhost:8123");
      when(clickhouseConfig.getUsername()).thenReturn(null);
      when(clickhouseConfig.getPassword()).thenReturn("password");
      
      assertThrows(RuntimeException.class, () -> poolManager.initializeAdminPool());
    }

    @Test
    void shouldThrowExceptionForNullPassword() {
      when(clickhouseConfig.getR2dbcUrl()).thenReturn("r2dbc:clickhouse://localhost:8123");
      when(clickhouseConfig.getUsername()).thenReturn("admin");
      when(clickhouseConfig.getPassword()).thenReturn(null);
      
      assertThrows(RuntimeException.class, () -> poolManager.initializeAdminPool());
    }
  }
}
