package org.dreamhorizon.pulseserver.service.firebase;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.multitenancy.TenantManager;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.config.ApplicationConfig;

/**
 * Service for initializing and providing access to Firebase Admin SDK.
 *
 * <p>This service handles the initialization of Firebase Admin SDK using
 * Application Default Credentials (ADC), which works with:</p>
 * <ul>
 *   <li>Workload Identity Federation with AWS (using credential config file)</li>
 *   <li>Workload Identity Pools (GKE, Cloud Run, etc.)</li>
 *   <li>Service Account attached to GCE instances</li>
 *   <li>GOOGLE_APPLICATION_CREDENTIALS environment variable</li>
 * </ul>
 *
 * <p>For AWS Workload Identity Federation, set GOOGLE_APPLICATION_CREDENTIALS
 * to the path of your credential configuration file (not a service account key).</p>
 */
@Slf4j
@Singleton
public class FirebaseAdminService {

  private static final String FIREBASE_APP_NAME = "pulse-server";

  private final ApplicationConfig applicationConfig;
  private volatile FirebaseApp firebaseApp;
  private volatile boolean initialized = false;

  @Inject
  public FirebaseAdminService(ApplicationConfig applicationConfig) {
    this.applicationConfig = applicationConfig;
    if (!Objects.equals(System.getenv("ENV"), "dev")) {
      initialize();
    }
  }

  /**
   * Initializes the Firebase Admin SDK.
   *
   * <p>This method is idempotent - subsequent calls will be ignored if already initialized.</p>
   */
  private synchronized void initialize() {
    if (initialized) {
      log.debug("Firebase Admin SDK already initialized");
      return;
    }

    try {
      FirebaseOptions options = buildFirebaseOptions();

      // Check if app with this name already exists (useful for testing/reloads)
      try {
        firebaseApp = FirebaseApp.getInstance(FIREBASE_APP_NAME);
        log.info("Reusing existing Firebase App instance: {}", FIREBASE_APP_NAME);
      } catch (IllegalStateException e) {
        firebaseApp = FirebaseApp.initializeApp(options, FIREBASE_APP_NAME);
        log.info("Firebase Admin SDK initialized successfully with app name: {}", FIREBASE_APP_NAME);
      }

      initialized = true;
    } catch (Exception e) {
      log.error("Failed to initialize Firebase Admin SDK: {}", e.getMessage(), e);
      throw new RuntimeException("Firebase Admin SDK initialization failed", e);
    }
  }

  private FirebaseOptions buildFirebaseOptions() throws IOException {
    String projectId = applicationConfig.getFirebaseProjectId();

    if (projectId == null || projectId.isBlank()) {
      throw new IllegalStateException(
          "Firebase project ID is required. Set CONFIG_SERVICE_APPLICATION_FIREBASEPROJECTID.");
    }

    GoogleCredentials credentials = loadCredentials();

    return FirebaseOptions.builder()
        .setCredentials(credentials)
        .setProjectId(projectId.trim())
        .build();
  }

  /**
   * Loads credentials using Application Default Credentials (ADC).
   *
   * <p>For AWS Workload Identity Federation:</p>
   * <ol>
   *   <li>Set GOOGLE_APPLICATION_CREDENTIALS to your credential configuration file path</li>
   *   <li>The file contains Workload Identity Pool config, NOT a service account key</li>
   *   <li>Google auth library automatically exchanges AWS credentials for GCP credentials</li>
   * </ol>
   */
  private GoogleCredentials loadCredentials() throws IOException {
    String credentialsPath = System.getenv("GOOGLE_APPLICATION_CREDENTIALS");

    if (credentialsPath != null && !credentialsPath.isBlank()) {
      log.info("Loading credentials from GOOGLE_APPLICATION_CREDENTIALS: {}", credentialsPath);
      // This works for both:
      // - Service account key files (for local dev)
      // - Workload Identity Federation credential config files (for AWS/Azure)
      try (FileInputStream fis = new FileInputStream(credentialsPath)) {
        return GoogleCredentials.fromStream(fis);
      }
    }

    // Fallback to Application Default Credentials
    // Works on GCP-hosted environments (GKE with Workload Identity, Cloud Run, GCE)
    log.info("Using Application Default Credentials (no GOOGLE_APPLICATION_CREDENTIALS set)");
    return GoogleCredentials.getApplicationDefault();
  }

  /**
   * Gets the Firebase Auth instance for authentication operations.
   *
   * @return FirebaseAuth instance
   * @throws IllegalStateException if Firebase Admin SDK is not initialized
   */
  public FirebaseAuth getFirebaseAuth() {
    ensureInitialized();
    return FirebaseAuth.getInstance(firebaseApp);
  }

  /**
   * Gets the Tenant Manager for multi-tenancy operations.
   *
   * <p>Use this to create, update, delete, and list tenants in Firebase Identity Platform.</p>
   *
   * @return TenantManager instance
   * @throws IllegalStateException if Firebase Admin SDK is not initialized
   */
  public TenantManager getTenantManager() {
    ensureInitialized();
    return getFirebaseAuth().getTenantManager();
  }

  /**
   * Checks if Firebase Admin SDK is properly initialized.
   *
   * @return true if initialized, false otherwise
   */
  public boolean isInitialized() {
    return initialized && firebaseApp != null;
  }

  private void ensureInitialized() {
    if (!initialized || firebaseApp == null) {
      throw new IllegalStateException(
          "Firebase Admin SDK is not initialized. Check credentials and project configuration.");
    }
  }

  /**
   * Gets the Firebase project ID.
   *
   * @return the configured Firebase project ID
   */
  public String getProjectId() {
    return applicationConfig.getFirebaseProjectId();
  }
}

