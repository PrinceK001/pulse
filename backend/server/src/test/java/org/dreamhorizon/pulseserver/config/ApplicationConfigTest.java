package org.dreamhorizon.pulseserver.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

class ApplicationConfigTest {

  @Test
  void noArgsConstructorCreatesInstance() {
    ApplicationConfig config = new ApplicationConfig();
    assertNotNull(config);
  }

  @Test
  void settersAndGetters() {
    ApplicationConfig config = new ApplicationConfig();
    config.setCronManagerBaseUrl("cronUrl");
    config.setServiceUrl("serviceUrl");
    config.setShutdownGracePeriod(30);
    config.setGoogleOAuthClientId("newClientId");
    config.setGoogleOAuthEnabled(false);
    config.setFirebaseProjectId("proj1");
    config.setJwtSecret("secret");
    config.setOtelCollectorUrl("otel");
    config.setS3BucketName("bucket");
    assertEquals("cronUrl", config.getCronManagerBaseUrl());
    assertEquals("serviceUrl", config.getServiceUrl());
    assertEquals(30, config.getShutdownGracePeriod());
    assertEquals("newClientId", config.getGoogleOAuthClientId());
    assertEquals(false, config.getGoogleOAuthEnabled());
    assertEquals("proj1", config.getFirebaseProjectId());
    assertEquals("secret", config.getJwtSecret());
    assertEquals("bucket", config.getS3BucketName());
  }
}
