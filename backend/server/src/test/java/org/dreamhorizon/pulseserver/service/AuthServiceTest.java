package org.dreamhorizon.pulseserver.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.jsonwebtoken.Claims;
import io.reactivex.rxjava3.core.Single;
import org.dreamhorizon.pulseserver.config.ApplicationConfig;
import org.dreamhorizon.pulseserver.dto.request.GetAccessTokenFromRefreshTokenRequestDto;
import org.dreamhorizon.pulseserver.resources.v1.auth.models.AuthenticateResponseDto;
import org.dreamhorizon.pulseserver.resources.v1.auth.models.GetAccessTokenFromRefreshTokenResponseDto;
import org.dreamhorizon.pulseserver.resources.v1.auth.models.VerifyAuthTokenResponseDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthServiceTest {

  @Mock
  ApplicationConfig applicationConfig;

  @Mock
  JwtService jwtService;

  AuthService authService;

  @BeforeEach
  void setUp() {
    authService = new AuthService(applicationConfig, jwtService);
  }

  @Nested
  class IsGoogleSignInEnabled {

    @Test
    void returnsTrueWhenOAuthEnabledIsTrue() {
      when(applicationConfig.getGoogleOAuthEnabled()).thenReturn(true);
      assertTrue(authService.isGoogleSignInEnabled());
    }

    @Test
    void returnsFalseWhenOAuthEnabledIsFalse() {
      when(applicationConfig.getGoogleOAuthEnabled()).thenReturn(false);
      assertFalse(authService.isGoogleSignInEnabled());
    }

    @Test
    void returnsFalseWhenClientIdNullAndOAuthEnabledNull() {
      when(applicationConfig.getGoogleOAuthEnabled()).thenReturn(null);
      when(applicationConfig.getGoogleOAuthClientId()).thenReturn(null);
      assertFalse(authService.isGoogleSignInEnabled());
    }

    @Test
    void returnsFalseWhenClientIdEmptyAndOAuthEnabledNull() {
      when(applicationConfig.getGoogleOAuthEnabled()).thenReturn(null);
      when(applicationConfig.getGoogleOAuthClientId()).thenReturn("   ");
      assertFalse(authService.isGoogleSignInEnabled());
    }

    @Test
    void returnsTrueWhenClientIdSetAndOAuthEnabledNull() {
      when(applicationConfig.getGoogleOAuthEnabled()).thenReturn(null);
      when(applicationConfig.getGoogleOAuthClientId()).thenReturn("client-id");
      assertTrue(authService.isGoogleSignInEnabled());
    }
  }

  @Nested
  class VerifyGoogleIdToken {

    @Test
    void returnsDevelopmentUserWhenGoogleSignInDisabled() throws Exception {
      when(applicationConfig.getGoogleOAuthEnabled()).thenReturn(false);
      when(jwtService.generateAccessToken(anyString(), anyString(), anyString())).thenReturn("access");
      when(jwtService.generateRefreshToken(anyString(), anyString(), anyString())).thenReturn("refresh");
      when(jwtService.generateIdToken(anyString(), anyString(), anyString(), anyString(), anyString()))
          .thenReturn("id");

      Single<AuthenticateResponseDto> single = authService.verifyGoogleIdToken("any-token", null);
      AuthenticateResponseDto result = single.blockingGet();

      assertNotNull(result);
      assertEquals("access", result.getAccessToken());
      assertEquals("refresh", result.getRefreshToken());
      assertEquals("id", result.getIdToken());
      assertEquals("Bearer", result.getTokenType());
      assertEquals(JwtService.ACCESS_TOKEN_VALIDITY_SECONDS, result.getExpiresIn());
    }

    @Test
    void throwsWhenFirebaseIssuerButTenantIdNull() {
      when(applicationConfig.getGoogleOAuthEnabled()).thenReturn(true);
      String payloadBase64 = java.util.Base64.getUrlEncoder().withoutPadding()
          .encodeToString("{\"iss\":\"https://securetoken.google.com/proj1\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
      String firebaseToken = "header." + payloadBase64 + ".signature";

      Throwable t = assertThrows(RuntimeException.class, () ->
          authService.verifyGoogleIdToken(firebaseToken, null).blockingGet());

      IllegalArgumentException iae = t instanceof IllegalArgumentException
          ? (IllegalArgumentException) t
          : (IllegalArgumentException) t.getCause();
      assertTrue(iae.getMessage().contains("tenant-id header is required"));
    }

    @Test
    void throwsWhenFirebaseIssuerButTenantIdBlank() {
      when(applicationConfig.getGoogleOAuthEnabled()).thenReturn(true);
      String payloadBase64 = java.util.Base64.getUrlEncoder().withoutPadding()
          .encodeToString("{\"iss\":\"https://securetoken.google.com/proj1\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
      String firebaseToken = "header." + payloadBase64 + ".signature";

      Throwable t = assertThrows(RuntimeException.class, () ->
          authService.verifyGoogleIdToken(firebaseToken, "   ").blockingGet());

      IllegalArgumentException iae = t instanceof IllegalArgumentException
          ? (IllegalArgumentException) t
          : (IllegalArgumentException) t.getCause();
      assertTrue(iae.getMessage().contains("tenant-id header is required"));
    }
  }

  @Nested
  class VerifyAuthToken {

    @Test
    void returnsInvalidWhenAuthorizationNull() throws Exception {
      Single<VerifyAuthTokenResponseDto> single = authService.verifyAuthToken(null);
      VerifyAuthTokenResponseDto result = single.blockingGet();
      assertNotNull(result);
      assertFalse(result.getIsAuthTokenValid());
    }

    @Test
    void returnsInvalidWhenAuthorizationEmpty() throws Exception {
      Single<VerifyAuthTokenResponseDto> single = authService.verifyAuthToken("   ");
      VerifyAuthTokenResponseDto result = single.blockingGet();
      assertFalse(result.getIsAuthTokenValid());
    }

    @Test
    void returnsValidWhenJwtServiceSaysAccessToken() throws Exception {
      when(jwtService.isAccessToken("valid-token")).thenReturn(true);
      Single<VerifyAuthTokenResponseDto> single = authService.verifyAuthToken("Bearer valid-token");
      VerifyAuthTokenResponseDto result = single.blockingGet();
      assertTrue(result.getIsAuthTokenValid());
    }

    @Test
    void returnsInvalidWhenJwtServiceSaysNotAccessToken() throws Exception {
      when(jwtService.isAccessToken("bad-token")).thenReturn(false);
      Single<VerifyAuthTokenResponseDto> single = authService.verifyAuthToken("Bearer bad-token");
      VerifyAuthTokenResponseDto result = single.blockingGet();
      assertFalse(result.getIsAuthTokenValid());
    }
  }

  @Nested
  class GetAccessTokenFromRefreshToken {

    @Test
    void throwsWhenRefreshTokenNull() {
      GetAccessTokenFromRefreshTokenRequestDto request = new GetAccessTokenFromRefreshTokenRequestDto();
      request.setRefreshToken(null);

      assertThrows(RuntimeException.class, () ->
          authService.getAccessTokenFromRefreshToken(request).blockingGet());
    }

    @Test
    void throwsWhenRefreshTokenEmpty() {
      GetAccessTokenFromRefreshTokenRequestDto request = new GetAccessTokenFromRefreshTokenRequestDto();
      request.setRefreshToken("   ");

      assertThrows(RuntimeException.class, () ->
          authService.getAccessTokenFromRefreshToken(request).blockingGet());
    }

    @Test
    void throwsWhenNotRefreshToken() {
      GetAccessTokenFromRefreshTokenRequestDto request = new GetAccessTokenFromRefreshTokenRequestDto();
      request.setRefreshToken("not-refresh");
      when(jwtService.isRefreshToken("not-refresh")).thenReturn(false);

      assertThrows(RuntimeException.class, () ->
          authService.getAccessTokenFromRefreshToken(request).blockingGet());
    }

    @Test
    void returnsNewAccessTokenWhenValidRefreshToken() throws Exception {
      GetAccessTokenFromRefreshTokenRequestDto request = new GetAccessTokenFromRefreshTokenRequestDto();
      request.setRefreshToken("valid-refresh");
      when(jwtService.isRefreshToken("valid-refresh")).thenReturn(true);
      Claims claims = mock(Claims.class);
      when(claims.getSubject()).thenReturn("user1");
      when(claims.get(eq("email"), eq(String.class))).thenReturn("e@x.com");
      when(claims.get(eq("name"), eq(String.class))).thenReturn("Name");
      when(jwtService.verifyToken("valid-refresh")).thenReturn(claims);
      when(jwtService.generateAccessToken("user1", "e@x.com", "Name")).thenReturn("new-access");

      Single<GetAccessTokenFromRefreshTokenResponseDto> single =
          authService.getAccessTokenFromRefreshToken(request);
      GetAccessTokenFromRefreshTokenResponseDto result = single.blockingGet();

      assertNotNull(result);
      assertEquals("new-access", result.getAccessToken());
      assertEquals("valid-refresh", result.getRefreshToken());
      assertEquals("Bearer", result.getTokenType());
      assertEquals(JwtService.ACCESS_TOKEN_VALIDITY_SECONDS, result.getExpiresIn());
      verify(jwtService).generateAccessToken("user1", "e@x.com", "Name");
    }
  }
}
