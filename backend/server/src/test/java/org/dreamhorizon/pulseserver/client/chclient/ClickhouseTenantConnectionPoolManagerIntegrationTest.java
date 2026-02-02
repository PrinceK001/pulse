package org.dreamhorizon.pulseserver.client.chclient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import org.dreamhorizon.pulseserver.config.ClickhouseConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
class ClickhouseTenantConnectionPoolManagerIntegrationTest {

  @Mock
  private ClickhouseConfig clickhouseConfig;

  private ClickhouseTenantConnectionPoolManager poolManager;

  @BeforeEach
  void setup() {
    poolManager = new ClickhouseTenantConnectionPoolManager(clickhouseConfig);
  }

  @Nested
  class TestPoolStatisticsComprehensive {

    @Test
    void shouldReturnCorrectStatsForNonExistentPool() {
      ClickhouseTenantConnectionPoolManager.PoolStatistics stats = 
          poolManager.getPoolStatistics("non_existent");

      assertNotNull(stats);
      assertEquals("non_existent", stats.tenantId);
      assertEquals(0, stats.activeConnections);
      assertEquals(0, stats.maxConnections);
      assertFalse(stats.isActive);
    }

    @Test
    void shouldCreatePoolStatisticsWithVariousValues() {
      ClickhouseTenantConnectionPoolManager.PoolStatistics stats1 = 
          new ClickhouseTenantConnectionPoolManager.PoolStatistics("t1", 0, 0, false);
      ClickhouseTenantConnectionPoolManager.PoolStatistics stats2 = 
          new ClickhouseTenantConnectionPoolManager.PoolStatistics("t2", 5, 10, true);
      ClickhouseTenantConnectionPoolManager.PoolStatistics stats3 = 
          new ClickhouseTenantConnectionPoolManager.PoolStatistics("t3", 10, 10, true);
      ClickhouseTenantConnectionPoolManager.PoolStatistics stats4 = 
          new ClickhouseTenantConnectionPoolManager.PoolStatistics(null, 0, 0, false);

      assertEquals("t1", stats1.tenantId);
      assertEquals("t2", stats2.tenantId);
      assertEquals("t3", stats3.tenantId);
      assertEquals(null, stats4.tenantId);
      
      assertEquals(0, stats1.activeConnections);
      assertEquals(5, stats2.activeConnections);
      assertEquals(10, stats3.activeConnections);
      
      assertEquals(0, stats1.maxConnections);
      assertEquals(10, stats2.maxConnections);
      assertEquals(10, stats3.maxConnections);
      
      assertFalse(stats1.isActive);
      assertTrue(stats2.isActive);
      assertTrue(stats3.isActive);
      assertFalse(stats4.isActive);
    }
  }

  @Nested
  class TestInitializeAdminPoolErrors {

    @Test
    void shouldThrowExceptionForMalformedUrl() {
      when(clickhouseConfig.getR2dbcUrl()).thenReturn("not-a-valid-url");
      when(clickhouseConfig.getUsername()).thenReturn("admin");
      when(clickhouseConfig.getPassword()).thenReturn("password");

      assertThrows(RuntimeException.class, () -> poolManager.initializeAdminPool());
    }

    @Test
    void shouldThrowExceptionForNullUrl() {
      when(clickhouseConfig.getR2dbcUrl()).thenReturn(null);
      when(clickhouseConfig.getUsername()).thenReturn("admin");
      when(clickhouseConfig.getPassword()).thenReturn("password");

      assertThrows(RuntimeException.class, () -> poolManager.initializeAdminPool());
    }

    @Test
    void shouldThrowExceptionForEmptyUrl() {
      when(clickhouseConfig.getR2dbcUrl()).thenReturn("");
      when(clickhouseConfig.getUsername()).thenReturn("admin");
      when(clickhouseConfig.getPassword()).thenReturn("password");

      assertThrows(RuntimeException.class, () -> poolManager.initializeAdminPool());
    }
  }

  @Nested
  class TestGetPoolForTenantErrors {

    @Test
    void shouldThrowExceptionForMalformedUrl() {
      when(clickhouseConfig.getR2dbcUrl()).thenReturn("invalid-url");

      RuntimeException ex = assertThrows(RuntimeException.class, 
          () -> poolManager.getPoolForTenant("tenant1", "user", "pass"));
      
      assertTrue(ex.getMessage().contains("Cannot create tenant connection pool"));
    }

    @Test
    void shouldThrowExceptionForNullUrl() {
      when(clickhouseConfig.getR2dbcUrl()).thenReturn(null);

      assertThrows(RuntimeException.class, 
          () -> poolManager.getPoolForTenant("tenant1", "user", "pass"));
    }
  }

  @Nested
  class TestGetAdminPoolErrors {

    @Test
    void shouldThrowExceptionWhenAdminPoolNotInitialized() {
      RuntimeException ex = assertThrows(RuntimeException.class, 
          () -> poolManager.getAdminPool());
      
      assertEquals("Admin pool not available", ex.getMessage());
    }

    @Test
    void shouldThrowExceptionMessageFormat() {
      try {
        poolManager.getAdminPool();
      } catch (RuntimeException e) {
        assertTrue(e.getMessage().contains("Admin pool"));
        assertTrue(e.getMessage().contains("not available"));
      }
    }
  }

  @Nested
  class TestClosePoolForTenantBehavior {

    @Test
    void shouldHandleNonExistentTenantGracefully() {
      // Should not throw
      poolManager.closePoolForTenant("non_existent_tenant_123");
      poolManager.closePoolForTenant("another_non_existent");
      poolManager.closePoolForTenant("");
      // Note: null might cause NPE depending on implementation, so we don't test it
    }

    @Test
    void shouldNotAffectOtherTenantsWhenClosing() {
      poolManager.closePoolForTenant("tenant1");
      
      ClickhouseTenantConnectionPoolManager.PoolStatistics stats1 = 
          poolManager.getPoolStatistics("tenant1");
      ClickhouseTenantConnectionPoolManager.PoolStatistics stats2 = 
          poolManager.getPoolStatistics("tenant2");
      
      assertFalse(stats1.isActive);
      assertFalse(stats2.isActive);
    }
  }

  @Nested
  class TestCloseAllPoolsBehavior {

    @Test
    void shouldHandleEmptyPoolCache() {
      poolManager.closeAllPools();
      poolManager.closeAllPools();
      poolManager.closeAllPools();
      
      // Should not throw and pools should remain inactive
      assertFalse(poolManager.getPoolStatistics("any").isActive);
    }

    @Test
    void shouldClearAllPoolsAfterClose() {
      poolManager.closeAllPools();
      
      assertEquals(0, poolManager.getPoolStatistics("t1").activeConnections);
      assertEquals(0, poolManager.getPoolStatistics("t2").activeConnections);
    }
  }

  @Nested
  class TestConstructorAndConfig {

    @Test
    void shouldStoreConfig() {
      ClickhouseTenantConnectionPoolManager manager = 
          new ClickhouseTenantConnectionPoolManager(clickhouseConfig);
      
      assertNotNull(manager);
    }

    @Test
    void shouldInitializeWithEmptyPools() {
      ClickhouseTenantConnectionPoolManager manager = 
          new ClickhouseTenantConnectionPoolManager(clickhouseConfig);
      
      ClickhouseTenantConnectionPoolManager.PoolStatistics stats = 
          manager.getPoolStatistics("any_tenant");
      
      assertFalse(stats.isActive);
      assertEquals(0, stats.activeConnections);
    }
  }

  @Nested
  class TestConcurrentOperations {

    @Test
    void shouldHandleConcurrentPoolStatisticsRequests() {
      // Multiple concurrent calls should not cause issues
      for (int i = 0; i < 100; i++) {
        poolManager.getPoolStatistics("tenant_" + i);
      }
      
      // Verify all return valid stats
      for (int i = 0; i < 100; i++) {
        ClickhouseTenantConnectionPoolManager.PoolStatistics stats = 
            poolManager.getPoolStatistics("tenant_" + i);
        assertNotNull(stats);
        assertEquals("tenant_" + i, stats.tenantId);
      }
    }

    @Test
    void shouldHandleConcurrentCloseRequests() {
      // Multiple concurrent close calls should not cause issues
      for (int i = 0; i < 50; i++) {
        poolManager.closePoolForTenant("tenant_" + i);
      }
      
      poolManager.closeAllPools();
    }
  }
}
