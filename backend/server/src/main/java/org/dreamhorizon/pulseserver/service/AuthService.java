package org.dreamhorizon.pulseserver.service;

import com.google.inject.Inject;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.SignedJWT;
import io.jsonwebtoken.Claims;
import io.reactivex.rxjava3.core.Single;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Date;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.dreamhorizon.pulseserver.config.ApplicationConfig;
import org.dreamhorizon.pulseserver.dto.request.GetAccessTokenFromRefreshTokenRequestDto;
import org.dreamhorizon.pulseserver.resources.v1.auth.models.AuthenticateResponseDto;
import org.dreamhorizon.pulseserver.resources.v1.auth.models.GetAccessTokenFromRefreshTokenResponseDto;
import org.dreamhorizon.pulseserver.resources.v1.auth.models.VerifyAuthTokenResponseDto;

@Slf4j
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class AuthService {

  private static final String FIREBASE_JWKS_URL =
      "https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com";

  private final ApplicationConfig applicationConfig;
  private final JwtService jwtService;
  private volatile String firebaseJwksCache;
  private volatile long firebaseJwksCacheExpiry;
  private static final long JWKS_CACHE_TTL_MS = 3600_000L;

  // Development mode constants
  private static final String DEV_USER_ID = "dev-user";
  private static final String DEV_EMAIL = "dev-user@localhost.local";
  private static final String DEV_NAME = "Development User";
  private static final String DEV_FIRST_NAME = "Development";
  private static final String DEV_LAST_NAME = "User";
  private static final String DEV_PROFILE_PICTURE = "";

  // Response constants
  private static final String TOKEN_TYPE_BEARER = "Bearer";

  // Claim keys (matching JwtService)
  private static final String CLAIM_EMAIL = "email";
  private static final String CLAIM_NAME = "name";

  public boolean isGoogleSignInEnabled() {
    // Check explicit environment variable first
    Boolean oauthEnabled = applicationConfig.getGoogleOAuthEnabled();
    if (oauthEnabled != null) {
      return oauthEnabled;
    }

    // Fallback: if client ID is not set, disable Google OAuth
    String clientId = applicationConfig.getGoogleOAuthClientId();
    if (clientId == null || clientId.trim().isEmpty()) {
      log.info("Google OAuth is disabled: client ID is not configured");
      return false;
    }

    // Default to enabled if client ID is present and no explicit flag is set
    return true;
  }

  private AuthenticateResponseDto createDevelopmentUser() {
    String accessToken = jwtService.generateAccessToken(DEV_USER_ID, DEV_EMAIL, DEV_NAME);
    String refreshToken = jwtService.generateRefreshToken(DEV_USER_ID, DEV_EMAIL, DEV_NAME);
    String idToken = jwtService.generateIdToken(DEV_USER_ID, DEV_EMAIL, DEV_FIRST_NAME, DEV_LAST_NAME, DEV_PROFILE_PICTURE);

    return AuthenticateResponseDto.builder()
        .accessToken(accessToken)
        .refreshToken(refreshToken)
        .idToken(idToken)
        .tokenType(TOKEN_TYPE_BEARER)
        .expiresIn(JwtService.ACCESS_TOKEN_VALIDITY_SECONDS)
        .build();
  }


  private boolean isFirebaseConfigured() {
    String projectId = applicationConfig.getFirebaseProjectId();
    return projectId != null && !projectId.trim().isEmpty();
  }

  private String fetchFirebaseJwks() throws IOException {
    long now = System.currentTimeMillis();
    if (firebaseJwksCache != null && now < firebaseJwksCacheExpiry) {
      return firebaseJwksCache;
    }
    synchronized (this) {
      if (firebaseJwksCache != null && System.currentTimeMillis() < firebaseJwksCacheExpiry) {
        return firebaseJwksCache;
      }
      var conn = new URL(FIREBASE_JWKS_URL).openConnection();
      conn.setConnectTimeout(5000);
      conn.setReadTimeout(5000);
      try (var in = conn.getInputStream()) {
        firebaseJwksCache = new String(in.readAllBytes(), StandardCharsets.UTF_8);
      }
      firebaseJwksCacheExpiry = System.currentTimeMillis() + JWKS_CACHE_TTL_MS;
      return firebaseJwksCache;
    }
  }

  private static String JwtIssuer(String idTokenString) {
    if (idTokenString == null || idTokenString.isEmpty()) {
      return null;
    }
    String[] parts = idTokenString.split("\\.");
    if (parts.length != 3) {
      return null;
    }
    try {
      String payloadJson = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
      int issStart = payloadJson.indexOf("\"iss\"");
      if (issStart == -1) {
        return null;
      }
      int colon = payloadJson.indexOf(':', issStart);
      int valueStart = payloadJson.indexOf('"', colon + 1) + 1;
      int valueEnd = payloadJson.indexOf('"', valueStart);
      if (valueStart <= 0 || valueEnd <= valueStart) {
        return null;
      }
      return payloadJson.substring(valueStart, valueEnd);
    } catch (Exception e) {
      return null;
    }
  }

  private static boolean isFirebaseIssuer(String iss) {
    return iss != null && iss.contains("securetoken.google.com");
  }

  private AuthenticateResponseDto verifyFirebaseIdToken(String idTokenString, String requestTenantId) {
    if (!isFirebaseConfigured()) {
      throw new IllegalArgumentException("Firebase is not configured. Set CONFIG_SERVICE_APPLICATION_FIREBASEPROJECTID.");
    }
    String projectId = applicationConfig.getFirebaseProjectId().trim();
    String expectedIssuer = "https://securetoken.google.com/" + projectId;
    try {
      SignedJWT signedJWT = SignedJWT.parse(idTokenString);
      String kid = signedJWT.getHeader().getKeyID();
      if (kid == null) {
        log.error("Firebase token missing kid");
        throw new IllegalArgumentException("Invalid Firebase token: missing key ID.");
      }
      String jwksJson = fetchFirebaseJwks();
      JWKSet jwkSet = JWKSet.parse(jwksJson);
      JWK jwk = jwkSet.getKeys().stream()
          .filter(k -> kid.equals(k.getKeyID()))
          .findFirst()
          .orElse(null);
      if (!(jwk instanceof RSAKey rsaKey)) {
        log.error("No RSA key found for kid: {}", kid);
        throw new IllegalArgumentException("Invalid Firebase token: unable to verify signature.");
      }
      JWSVerifier verifier = new RSASSAVerifier(rsaKey);
      if (!signedJWT.verify(verifier)) {
        log.error("Firebase token signature verification failed");
        throw new IllegalArgumentException("Invalid Firebase token: signature verification failed.");
      }
      var claims = signedJWT.getJWTClaimsSet();
      if (!expectedIssuer.equals(claims.getIssuer())) {
        log.error("Firebase token issuer mismatch: expected {} got {}", expectedIssuer, claims.getIssuer());
        throw new IllegalArgumentException("Invalid Firebase token: issuer mismatch. Check your Firebase project configuration.");
      }
      var audience = claims.getAudience();
      if (audience == null || !audience.contains(projectId)) {
        log.error("Firebase token audience mismatch: expected {} got {}", projectId, audience);
        throw new IllegalArgumentException("Invalid Firebase token: audience mismatch. Check your Firebase project configuration.");
      }
      Date exp = claims.getExpirationTime();
      if (exp == null || exp.before(new Date())) {
        log.error("Firebase token expired or missing exp");
        throw new IllegalArgumentException("Firebase token has expired. Please re-authenticate.");
      }
      String tokenTenant = getFirebaseTenantFromClaims(claims);
      if (tokenTenant == null || tokenTenant.isBlank()) {
        log.error("Firebase token missing tenant claim");
        throw new IllegalArgumentException("Firebase token missing tenant. Multi-tenant authentication requires a tenant.");
      }
      if (!tokenTenant.trim().equals(requestTenantId.trim())) {
        log.error("tenant-id header does not match token tenant: header={} token={}", requestTenantId, tokenTenant);
        throw new IllegalArgumentException("Tenant mismatch: tenant-id header does not match the token tenant.");
      }
      String userId = claims.getSubject();
      if (userId == null || userId.isEmpty()) {
        log.error("Firebase token missing subject (user ID)");
        throw new IllegalArgumentException("Invalid Firebase token: missing user ID.");
      }
      String email = claims.getStringClaim("email");
      String name = claims.getStringClaim("name");
      if (email == null) {
        email = "";
      }
      if (name == null) {
        name = "";
      }
      String accessToken = jwtService.generateAccessToken(userId, email, name);
      String refreshToken = jwtService.generateRefreshToken(userId, email, name);
      return AuthenticateResponseDto.builder()
          .accessToken(accessToken)
          .refreshToken(refreshToken)
          .idToken(idTokenString)
          .tokenType(TOKEN_TYPE_BEARER)
          .expiresIn(JwtService.ACCESS_TOKEN_VALIDITY_SECONDS)
          .build();
    } catch (IllegalArgumentException e) {
      throw e;
    } catch (Exception e) {
      log.error("Firebase ID token verification failed: {}", e.getMessage(), e);
      throw new IllegalArgumentException("Firebase token verification failed: " + e.getMessage(), e);
    }
  }

  private static String getFirebaseTenantFromClaims(com.nimbusds.jwt.JWTClaimsSet claims) {
    try {
      Object firebase = claims.getClaim("firebase");
      if (firebase instanceof java.util.Map) {
        @SuppressWarnings("unchecked")
        Object tenant = ((java.util.Map<String, Object>) firebase).get("tenant");
        return tenant != null ? tenant.toString() : null;
      }
      return null;
    } catch (Exception e) {
      return null;
    }
  }

  public Single<AuthenticateResponseDto> verifyGoogleIdToken(String idTokenString, String requestTenantId) {
    if (!isGoogleSignInEnabled()) {
      return Single.just(createDevelopmentUser());
    }

    if (StringUtils.isEmpty(requestTenantId)) {
      throw new IllegalArgumentException(
          "tenant-id header is required for Firebase (multi-tenant) authentication.");
    }

    return Single.fromCallable(() -> {
      if (!isFirebaseIssuer(JwtIssuer(idTokenString))) {
        throw new IllegalArgumentException(
            "Only Firebase ID tokens are supported. Please authenticate using Firebase Authentication.");
      }
      return verifyFirebaseIdToken(idTokenString, requestTenantId);
    });
  }

  public Single<VerifyAuthTokenResponseDto> verifyAuthToken(String authorization) {
    return Single.fromCallable(() -> {
      try {
        String token = extractTokenFromHeader(authorization);

        if (token == null || token.trim().isEmpty()) {
          log.warn("Empty or null token provided");
          return VerifyAuthTokenResponseDto.builder()
              .isAuthTokenValid(false)
              .build();
        }

        boolean isValid = jwtService.isAccessToken(token);

        return VerifyAuthTokenResponseDto.builder()
            .isAuthTokenValid(isValid)
            .build();

      } catch (Exception e) {
        log.error("Error verifying token", e);
        return VerifyAuthTokenResponseDto.builder()
            .isAuthTokenValid(false)
            .build();
      }
    });
  }


  public Single<GetAccessTokenFromRefreshTokenResponseDto> getAccessTokenFromRefreshToken(
      GetAccessTokenFromRefreshTokenRequestDto request) {

    return Single.fromCallable(() -> {
      try {
        String refreshToken = request.getRefreshToken();

        if (refreshToken == null || refreshToken.trim().isEmpty()) {
          throw new IllegalArgumentException("Refresh token is required");
        }

        if (!jwtService.isRefreshToken(refreshToken)) {
          log.error("Invalid token type. Expected refresh token.");
          throw new IllegalArgumentException("Invalid token type. Expected refresh token.");
        }


        Claims claims = jwtService.verifyToken(refreshToken);
        String userId = claims.getSubject();
        String email = claims.get(CLAIM_EMAIL, String.class);
        String name = claims.get(CLAIM_NAME, String.class);

        String newAccessToken = jwtService.generateAccessToken(userId, email, name);

        log.info("Successfully refreshed access token for user: {}", userId);

        return GetAccessTokenFromRefreshTokenResponseDto.builder()
            .accessToken(newAccessToken)
            .refreshToken(refreshToken)
            .tokenType(TOKEN_TYPE_BEARER)
            .expiresIn(JwtService.ACCESS_TOKEN_VALIDITY_SECONDS)
            .build();

      } catch (Exception e) {
        log.error("Error refreshing access token: {}", e.getMessage());
        throw new RuntimeException("Failed to refresh access token", e);
      }
    });
  }


  private String extractTokenFromHeader(String authorization) {
    if (authorization == null || authorization.trim().isEmpty()) {
      return null;
    }

    if (authorization.toLowerCase().startsWith("bearer ")) {
      return authorization.substring(7).trim();
    }

    return authorization.trim();
  }
}
