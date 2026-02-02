package org.dreamhorizon.pulseserver.service.tenant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class TenantAuditActionTest {

  @Nested
  class TestEnumValues {

    @Test
    void shouldHaveCredentialsCreatedAction() {
      TenantAuditAction action = TenantAuditAction.CREDENTIALS_CREATED;
      
      assertNotNull(action);
      assertEquals("CREDENTIALS_CREATED", action.getValue());
      assertEquals("CREDENTIALS_CREATED", action.name());
    }

    @Test
    void shouldHaveCredentialsUpdatedAction() {
      TenantAuditAction action = TenantAuditAction.CREDENTIALS_UPDATED;
      
      assertNotNull(action);
      assertEquals("CREDENTIALS_UPDATED", action.getValue());
      assertEquals("CREDENTIALS_UPDATED", action.name());
    }

    @Test
    void shouldHaveCredentialsDeactivatedAction() {
      TenantAuditAction action = TenantAuditAction.CREDENTIALS_DEACTIVATED;
      
      assertNotNull(action);
      assertEquals("CREDENTIALS_DEACTIVATED", action.getValue());
      assertEquals("CREDENTIALS_DEACTIVATED", action.name());
    }

    @Test
    void shouldHaveCredentialsReactivatedAction() {
      TenantAuditAction action = TenantAuditAction.CREDENTIALS_REACTIVATED;
      
      assertNotNull(action);
      assertEquals("CREDENTIALS_REACTIVATED", action.getValue());
      assertEquals("CREDENTIALS_REACTIVATED", action.name());
    }
  }

  @Nested
  class TestEnumMethods {

    @Test
    void shouldReturnAllValues() {
      TenantAuditAction[] actions = TenantAuditAction.values();
      
      assertNotNull(actions);
      assertEquals(4, actions.length);
    }

    @Test
    void shouldValueOfCredentialsCreated() {
      TenantAuditAction action = TenantAuditAction.valueOf("CREDENTIALS_CREATED");
      
      assertEquals(TenantAuditAction.CREDENTIALS_CREATED, action);
    }

    @Test
    void shouldValueOfCredentialsUpdated() {
      TenantAuditAction action = TenantAuditAction.valueOf("CREDENTIALS_UPDATED");
      
      assertEquals(TenantAuditAction.CREDENTIALS_UPDATED, action);
    }

    @Test
    void shouldValueOfCredentialsDeactivated() {
      TenantAuditAction action = TenantAuditAction.valueOf("CREDENTIALS_DEACTIVATED");
      
      assertEquals(TenantAuditAction.CREDENTIALS_DEACTIVATED, action);
    }

    @Test
    void shouldValueOfCredentialsReactivated() {
      TenantAuditAction action = TenantAuditAction.valueOf("CREDENTIALS_REACTIVATED");
      
      assertEquals(TenantAuditAction.CREDENTIALS_REACTIVATED, action);
    }
  }

  @Nested
  class TestOrdinal {

    @Test
    void shouldHaveCorrectOrdinals() {
      assertEquals(0, TenantAuditAction.CREDENTIALS_CREATED.ordinal());
      assertEquals(1, TenantAuditAction.CREDENTIALS_UPDATED.ordinal());
      assertEquals(2, TenantAuditAction.CREDENTIALS_DEACTIVATED.ordinal());
      assertEquals(3, TenantAuditAction.CREDENTIALS_REACTIVATED.ordinal());
    }
  }
}
