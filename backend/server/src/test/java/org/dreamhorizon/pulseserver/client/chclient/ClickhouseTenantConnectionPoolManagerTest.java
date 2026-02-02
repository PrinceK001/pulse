package org.dreamhorizon.pulseserver.client.chclient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import org.dreamhorizon.pulseserver.config.ClickhouseConfig;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
class ClickhouseTenantConnectionPoolManagerTest {

  @Mock
  private ClickhouseConfig clickhouseConfig;

  @Nested
  class TestConstructor {

    @Test
    void shouldThrowExceptionWhenConfigIsInvalid() {
      // Constructor now calls initializeAdminPool(), so invalid config should throw
      when(clickhouseConfig.getR2dbcUrl()).thenReturn("invalid://url");
      when(clickhouseConfig.getUsername()).thenReturn("admin");
      when(clickhouseConfig.getPassword()).thenReturn("password");

      assertThrows(RuntimeException.class, 
          () -> new ClickhouseTenantConnectionPoolManager(clickhouseConfig));
    }

    @Test
    void shouldThrowExceptionForNullUrl() {
      when(clickhouseConfig.getR2dbcUrl()).thenReturn(null);
      when(clickhouseConfig.getUsername()).thenReturn("admin");
      when(clickhouseConfig.getPassword()).thenReturn("password");

      assertThrows(RuntimeException.class, 
          () -> new ClickhouseTenantConnectionPoolManager(clickhouseConfig));
    }

    @Test
    void shouldThrowExceptionForNullUsername() {
      when(clickhouseConfig.getR2dbcUrl()).thenReturn("r2dbc:clickhouse://localhost:8123");
      when(clickhouseConfig.getUsername()).thenReturn(null);
      when(clickhouseConfig.getPassword()).thenReturn("password");

      assertThrows(RuntimeException.class, 
          () -> new ClickhouseTenantConnectionPoolManager(clickhouseConfig));
    }

    @Test
    void shouldThrowExceptionForNullPassword() {
      when(clickhouseConfig.getR2dbcUrl()).thenReturn("r2dbc:clickhouse://localhost:8123");
      when(clickhouseConfig.getUsername()).thenReturn("admin");
      when(clickhouseConfig.getPassword()).thenReturn(null);

      assertThrows(RuntimeException.class, 
          () -> new ClickhouseTenantConnectionPoolManager(clickhouseConfig));
    }

    @Test
    void shouldThrowExceptionForEmptyUrl() {
      when(clickhouseConfig.getR2dbcUrl()).thenReturn("");
      when(clickhouseConfig.getUsername()).thenReturn("admin");
      when(clickhouseConfig.getPassword()).thenReturn("password");

      assertThrows(RuntimeException.class, 
          () -> new ClickhouseTenantConnectionPoolManager(clickhouseConfig));
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
    void shouldCreatePoolStatisticsWithVariousValues() {
      ClickhouseTenantConnectionPoolManager.PoolStatistics stats1 = 
          new ClickhouseTenantConnectionPoolManager.PoolStatistics("t1", 0, 0, false);
      ClickhouseTenantConnectionPoolManager.PoolStatistics stats2 = 
          new ClickhouseTenantConnectionPoolManager.PoolStatistics("t2", 5, 10, true);
      ClickhouseTenantConnectionPoolManager.PoolStatistics stats3 = 
          new ClickhouseTenantConnectionPoolManager.PoolStatistics("t3", 10, 10, true);

      assertEquals("t1", stats1.tenantId);
      assertEquals("t2", stats2.tenantId);
      assertEquals("t3", stats3.tenantId);
      
      assertEquals(0, stats1.activeConnections);
      assertEquals(5, stats2.activeConnections);
      assertEquals(10, stats3.activeConnections);
      
      assertFalse(stats1.isActive);
      assertTrue(stats2.isActive);
      assertTrue(stats3.isActive);
    }
  }

  @Nested
  class TestGetPoolForTenantErrors {

    @Test
    void shouldThrowExceptionForInvalidConfig() {
      // When constructor fails, we can't test getPoolForTenant on the instance
      // So we test that constructor throws with invalid config
      when(clickhouseConfig.getR2dbcUrl()).thenReturn("invalid://url");
      when(clickhouseConfig.getUsername()).thenReturn("admin");
      when(clickhouseConfig.getPassword()).thenReturn("password");

      assertThrows(RuntimeException.class, 
          () -> new ClickhouseTenantConnectionPoolManager(clickhouseConfig));
    }
  }

  @Nested
  class TestExceptionMessages {

    @Test
    void shouldThrowWithCannotInitializeMessage() {
      when(clickhouseConfig.getR2dbcUrl()).thenReturn("invalid://url");
      when(clickhouseConfig.getUsername()).thenReturn("admin");
      when(clickhouseConfig.getPassword()).thenReturn("password");

      RuntimeException ex = assertThrows(RuntimeException.class, 
          () -> new ClickhouseTenantConnectionPoolManager(clickhouseConfig));
      
      assertTrue(ex.getMessage().contains("Cannot initialize admin pool"));
    }
  }
}
