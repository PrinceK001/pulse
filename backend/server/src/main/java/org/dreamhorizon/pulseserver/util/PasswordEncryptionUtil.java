package org.dreamhorizon.pulseserver.util;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.config.ClickhouseConfig;

@Slf4j
@Singleton
public class PasswordEncryptionUtil {
  private static final String ALGORITHM = "AES";
  private static final String DIGEST_ALGORITHM = "SHA-256";

  private final SecretKey secretKey;

  @Inject
  public PasswordEncryptionUtil(ClickhouseConfig clickhouseConfig) {
    try {
      String encryptionKey = clickhouseConfig.getEncryptionMasterKey();
      if (encryptionKey == null || encryptionKey.isBlank()) {
        throw new IllegalStateException("encryptionMasterKey is not configured in ClickhouseConfig");
      }
      byte[] decodedKey = Base64.getDecoder().decode(encryptionKey);
      this.secretKey = new SecretKeySpec(decodedKey, 0, decodedKey.length, ALGORITHM);
      log.info("Encryption key initialized successfully from ClickhouseConfig");
    } catch (Exception e) {
      log.error("Failed to initialize encryption key", e);
      throw new RuntimeException("Encryption key initialization failed", e);
    }
  }

  public EncryptedPassword encryptPassword(String plainPassword) {
    try {
      SecureRandom secureRandom = new SecureRandom();
      byte[] salt = new byte[16];
      secureRandom.nextBytes(salt);
      String saltBase64 = Base64.getEncoder().encodeToString(salt);

      Cipher cipher = Cipher.getInstance(ALGORITHM);
      cipher.init(Cipher.ENCRYPT_MODE, secretKey);
      byte[] encryptedPassword = cipher.doFinal(plainPassword.getBytes());
      String encryptedBase64 = Base64.getEncoder().encodeToString(encryptedPassword);

      String digest = generateDigest(plainPassword + saltBase64);

      return EncryptedPassword.builder()
          .encryptedPassword(encryptedBase64)
          .salt(saltBase64)
          .digest(digest)
          .build();
    } catch (Exception e) {
      log.error("Password encryption failed", e);
      throw new RuntimeException("Password encryption failed", e);
    }
  }

  public String decryptPassword(String encryptedPasswordBase64) {
    try {
      Cipher cipher = Cipher.getInstance(ALGORITHM);
      cipher.init(Cipher.DECRYPT_MODE, secretKey);
      byte[] decodedPassword = Base64.getDecoder().decode(encryptedPasswordBase64);
      byte[] decrypted = cipher.doFinal(decodedPassword);
      return new String(decrypted);
    } catch (Exception e) {
      log.error("Password decryption failed", e);
      throw new RuntimeException("Password decryption failed", e);
    }
  }

  public String generateDigest(String input) {
    try {
      MessageDigest digest = MessageDigest.getInstance(DIGEST_ALGORITHM);
      byte[] hash = digest.digest(input.getBytes());
      return Base64.getEncoder().encodeToString(hash);
    } catch (Exception e) {
      log.error("Digest generation failed", e);
      throw new RuntimeException("Digest generation failed", e);
    }
  }

  public boolean verifyPassword(String plainPassword, String salt, String storedDigest) {
    try {
      String computedDigest = generateDigest(plainPassword + salt);
      return computedDigest.equals(storedDigest);
    } catch (Exception e) {
      log.error("Password verification failed", e);
      return false;
    }
  }

  @Data
  @Builder
  public static class EncryptedPassword {
    private String encryptedPassword;
    private String salt;
    private String digest;
  }
}
