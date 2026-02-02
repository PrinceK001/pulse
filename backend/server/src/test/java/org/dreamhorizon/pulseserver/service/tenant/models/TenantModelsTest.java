package org.dreamhorizon.pulseserver.service.tenant.models;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class TenantModelsTest {

  @Nested
  class TestCreateTenantRequest {

    @Test
    void shouldBuildWithAllFields() {
      CreateTenantRequest request = CreateTenantRequest.builder()
          .tenantId("test_tenant")
          .name("Test Tenant")
          .description("Test Description")
          .clickhousePassword("password123")
          .gcpTenantId("gcp-123")
          .domainName("test.example.com")
          .build();

      assertEquals("test_tenant", request.getTenantId());
      assertEquals("Test Tenant", request.getName());
      assertEquals("Test Description", request.getDescription());
      assertEquals("password123", request.getClickhousePassword());
      assertEquals("gcp-123", request.getGcpTenantId());
      assertEquals("test.example.com", request.getDomainName());
    }

    @Test
    void shouldSetAndGetAllFields() {
      CreateTenantRequest request = new CreateTenantRequest();
      request.setTenantId("tenant_abc");
      request.setName("Tenant ABC");
      request.setDescription("Description");
      request.setClickhousePassword("pass");
      request.setGcpTenantId("gcp");
      request.setDomainName("domain");

      assertEquals("tenant_abc", request.getTenantId());
      assertEquals("Tenant ABC", request.getName());
      assertEquals("Description", request.getDescription());
      assertEquals("pass", request.getClickhousePassword());
      assertEquals("gcp", request.getGcpTenantId());
      assertEquals("domain", request.getDomainName());
    }

    @Test
    void shouldTestEqualsAndHashCode() {
      CreateTenantRequest req1 = CreateTenantRequest.builder()
          .tenantId("test")
          .name("Test")
          .build();

      CreateTenantRequest req2 = CreateTenantRequest.builder()
          .tenantId("test")
          .name("Test")
          .build();

      assertEquals(req1, req2);
      assertEquals(req1.hashCode(), req2.hashCode());
    }

    @Test
    void shouldTestToString() {
      CreateTenantRequest request = CreateTenantRequest.builder()
          .tenantId("test")
          .build();

      assertNotNull(request.toString());
      assertTrue(request.toString().contains("test"));
    }

    @Test
    void shouldCreateWithNoArgsConstructor() {
      CreateTenantRequest request = new CreateTenantRequest();
      assertNull(request.getTenantId());
    }

    @Test
    void shouldCreateWithAllArgsConstructor() {
      CreateTenantRequest request = new CreateTenantRequest(
          "tenant", "name", "desc", "pass", "gcp", "domain"
      );
      assertEquals("tenant", request.getTenantId());
    }
  }

  @Nested
  class TestCreateCredentialsRequest {

    @Test
    void shouldBuildWithAllFields() {
      CreateCredentialsRequest request = CreateCredentialsRequest.builder()
          .tenantId("test_tenant")
          .clickhousePassword("password123")
          .build();

      assertEquals("test_tenant", request.getTenantId());
      assertEquals("password123", request.getClickhousePassword());
    }

    @Test
    void shouldSetAndGetAllFields() {
      CreateCredentialsRequest request = new CreateCredentialsRequest();
      request.setTenantId("tenant_abc");
      request.setClickhousePassword("pass");

      assertEquals("tenant_abc", request.getTenantId());
      assertEquals("pass", request.getClickhousePassword());
    }

    @Test
    void shouldTestEqualsAndHashCode() {
      CreateCredentialsRequest req1 = CreateCredentialsRequest.builder()
          .tenantId("test")
          .clickhousePassword("pass")
          .build();

      CreateCredentialsRequest req2 = CreateCredentialsRequest.builder()
          .tenantId("test")
          .clickhousePassword("pass")
          .build();

      assertEquals(req1, req2);
      assertEquals(req1.hashCode(), req2.hashCode());
    }

    @Test
    void shouldTestToString() {
      CreateCredentialsRequest request = CreateCredentialsRequest.builder()
          .tenantId("test")
          .build();

      assertNotNull(request.toString());
    }

    @Test
    void shouldCreateWithNoArgsConstructor() {
      CreateCredentialsRequest request = new CreateCredentialsRequest();
      assertNull(request.getTenantId());
    }

    @Test
    void shouldCreateWithAllArgsConstructor() {
      CreateCredentialsRequest request = new CreateCredentialsRequest("tenant", "pass");
      assertEquals("tenant", request.getTenantId());
      assertEquals("pass", request.getClickhousePassword());
    }
  }

  @Nested
  class TestUpdateTenantRequest {

    @Test
    void shouldBuildWithAllFields() {
      UpdateTenantRequest request = UpdateTenantRequest.builder()
          .tenantId("test_tenant")
          .name("Updated Name")
          .description("Updated Description")
          .build();

      assertEquals("test_tenant", request.getTenantId());
      assertEquals("Updated Name", request.getName());
      assertEquals("Updated Description", request.getDescription());
    }

    @Test
    void shouldSetAndGetAllFields() {
      UpdateTenantRequest request = new UpdateTenantRequest();
      request.setTenantId("tenant_abc");
      request.setName("Name");
      request.setDescription("Desc");

      assertEquals("tenant_abc", request.getTenantId());
      assertEquals("Name", request.getName());
      assertEquals("Desc", request.getDescription());
    }

    @Test
    void shouldTestEqualsAndHashCode() {
      UpdateTenantRequest req1 = UpdateTenantRequest.builder()
          .tenantId("test")
          .name("Test")
          .build();

      UpdateTenantRequest req2 = UpdateTenantRequest.builder()
          .tenantId("test")
          .name("Test")
          .build();

      assertEquals(req1, req2);
      assertEquals(req1.hashCode(), req2.hashCode());
    }

    @Test
    void shouldTestToString() {
      UpdateTenantRequest request = UpdateTenantRequest.builder()
          .tenantId("test")
          .build();

      assertNotNull(request.toString());
    }

    @Test
    void shouldCreateWithNoArgsConstructor() {
      UpdateTenantRequest request = new UpdateTenantRequest();
      assertNull(request.getTenantId());
    }

    @Test
    void shouldCreateWithAllArgsConstructor() {
      UpdateTenantRequest request = new UpdateTenantRequest("tenant", "name", "desc");
      assertEquals("tenant", request.getTenantId());
      assertEquals("name", request.getName());
      assertEquals("desc", request.getDescription());
    }
  }

  @Nested
  class TestUpdateCredentialsRequest {

    @Test
    void shouldBuildWithAllFields() {
      UpdateCredentialsRequest request = UpdateCredentialsRequest.builder()
          .tenantId("test_tenant")
          .newPassword("newPassword123")
          .reason("Password rotation")
          .build();

      assertEquals("test_tenant", request.getTenantId());
      assertEquals("newPassword123", request.getNewPassword());
      assertEquals("Password rotation", request.getReason());
    }

    @Test
    void shouldSetAndGetAllFields() {
      UpdateCredentialsRequest request = new UpdateCredentialsRequest();
      request.setTenantId("tenant_abc");
      request.setNewPassword("newPass");
      request.setReason("Reason");

      assertEquals("tenant_abc", request.getTenantId());
      assertEquals("newPass", request.getNewPassword());
      assertEquals("Reason", request.getReason());
    }

    @Test
    void shouldTestEqualsAndHashCode() {
      UpdateCredentialsRequest req1 = UpdateCredentialsRequest.builder()
          .tenantId("test")
          .newPassword("pass")
          .build();

      UpdateCredentialsRequest req2 = UpdateCredentialsRequest.builder()
          .tenantId("test")
          .newPassword("pass")
          .build();

      assertEquals(req1, req2);
      assertEquals(req1.hashCode(), req2.hashCode());
    }

    @Test
    void shouldTestToString() {
      UpdateCredentialsRequest request = UpdateCredentialsRequest.builder()
          .tenantId("test")
          .build();

      assertNotNull(request.toString());
    }

    @Test
    void shouldCreateWithNoArgsConstructor() {
      UpdateCredentialsRequest request = new UpdateCredentialsRequest();
      assertNull(request.getTenantId());
    }

    @Test
    void shouldCreateWithAllArgsConstructor() {
      UpdateCredentialsRequest request = new UpdateCredentialsRequest("tenant", "pass", "reason");
      assertEquals("tenant", request.getTenantId());
      assertEquals("pass", request.getNewPassword());
      assertEquals("reason", request.getReason());
    }
  }

  @Nested
  class TestTenantInfo {

    @Test
    void shouldBuildWithAllFields() {
      TenantInfo info = TenantInfo.builder()
          .tenantId("test_tenant")
          .name("Test Tenant")
          .description("Description")
          .clickhouseUsername("user")
          .clickhousePassword("pass")
          .isActive(true)
          .createdAt("2026-01-01")
          .updatedAt("2026-01-02")
          .message("Success")
          .build();

      assertEquals("test_tenant", info.getTenantId());
      assertEquals("Test Tenant", info.getName());
      assertEquals("Description", info.getDescription());
      assertEquals("user", info.getClickhouseUsername());
      assertEquals("pass", info.getClickhousePassword());
      assertTrue(info.getIsActive());
      assertEquals("2026-01-01", info.getCreatedAt());
      assertEquals("2026-01-02", info.getUpdatedAt());
      assertEquals("Success", info.getMessage());
    }

    @Test
    void shouldSetAndGetAllFields() {
      TenantInfo info = new TenantInfo();
      info.setTenantId("tenant_abc");
      info.setName("Name");
      info.setDescription("Desc");
      info.setClickhouseUsername("user");
      info.setClickhousePassword("pass");
      info.setIsActive(false);
      info.setCreatedAt("created");
      info.setUpdatedAt("updated");
      info.setMessage("msg");

      assertEquals("tenant_abc", info.getTenantId());
      assertEquals("Name", info.getName());
      assertEquals("Desc", info.getDescription());
      assertEquals("user", info.getClickhouseUsername());
      assertEquals("pass", info.getClickhousePassword());
      assertFalse(info.getIsActive());
      assertEquals("created", info.getCreatedAt());
      assertEquals("updated", info.getUpdatedAt());
      assertEquals("msg", info.getMessage());
    }

    @Test
    void shouldTestEqualsAndHashCode() {
      TenantInfo info1 = TenantInfo.builder()
          .tenantId("test")
          .name("Test")
          .build();

      TenantInfo info2 = TenantInfo.builder()
          .tenantId("test")
          .name("Test")
          .build();

      assertEquals(info1, info2);
      assertEquals(info1.hashCode(), info2.hashCode());
    }

    @Test
    void shouldTestNotEquals() {
      TenantInfo info1 = TenantInfo.builder()
          .tenantId("test1")
          .build();

      TenantInfo info2 = TenantInfo.builder()
          .tenantId("test2")
          .build();

      assertNotEquals(info1, info2);
    }

    @Test
    void shouldTestToString() {
      TenantInfo info = TenantInfo.builder()
          .tenantId("test")
          .build();

      assertNotNull(info.toString());
      assertTrue(info.toString().contains("test"));
    }

    @Test
    void shouldCreateWithNoArgsConstructor() {
      TenantInfo info = new TenantInfo();
      assertNull(info.getTenantId());
    }

    @Test
    void shouldCreateWithAllArgsConstructor() {
      TenantInfo info = new TenantInfo(
          "tenant", "name", "desc", "user", "pass", true, "created", "updated", "msg"
      );
      assertEquals("tenant", info.getTenantId());
      assertEquals("name", info.getName());
      assertEquals("desc", info.getDescription());
      assertEquals("user", info.getClickhouseUsername());
      assertEquals("pass", info.getClickhousePassword());
      assertTrue(info.getIsActive());
      assertEquals("created", info.getCreatedAt());
      assertEquals("updated", info.getUpdatedAt());
      assertEquals("msg", info.getMessage());
    }
  }
}
