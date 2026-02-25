package org.dreamhorizon.pulseserver.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class PasswordEncryptionUtilTest {

  private PasswordEncryptionUtil encryptionUtil;

  @BeforeEach
  void setup() {
    encryptionUtil = new PasswordEncryptionUtil();
  }

  @Nested
  class TestConstructor {

    @Test
    void shouldInitializeSuccessfully() {
      PasswordEncryptionUtil util = new PasswordEncryptionUtil();
      assertNotNull(util);
    }

    @Test
    void shouldGenerateKeyWhenEnvVarNotSet() {
      PasswordEncryptionUtil util = new PasswordEncryptionUtil();
      assertNotNull(util.getKeyAsBase64());
      assertFalse(util.getKeyAsBase64().isEmpty());
    }
  }

  @Nested
  class TestEncrypt {

    @Test
    void shouldEncryptPasswordSuccessfully() {
      String plainPassword = "TestPassword123!";
      String encrypted = encryptionUtil.encrypt(plainPassword);

      assertNotNull(encrypted);
      assertNotEquals(plainPassword, encrypted);
    }

    @Test
    void shouldProduceSameOutputForSameInput() {
      String plainPassword = "TestPassword123!";
      String encrypted1 = encryptionUtil.encrypt(plainPassword);
      String encrypted2 = encryptionUtil.encrypt(plainPassword);

      assertEquals(encrypted1, encrypted2);
    }

    @Test
    void shouldHandleEmptyPassword() {
      String encrypted = encryptionUtil.encrypt("");
      assertNotNull(encrypted);
    }

    @Test
    void shouldHandleSpecialCharacters() {
      String specialPassword = "P@$$w0rd!#$%^&*()_+-=[]{}|;':\",./<>?";
      String encrypted = encryptionUtil.encrypt(specialPassword);
      assertNotNull(encrypted);
    }

    @Test
    void shouldHandleUnicodeCharacters() {
      String unicodePassword = "密码测试123पासवर्ड";
      String encrypted = encryptionUtil.encrypt(unicodePassword);
      assertNotNull(encrypted);
    }

    @Test
    void shouldHandleLongPassword() {
      String longPassword = "A".repeat(1000);
      String encrypted = encryptionUtil.encrypt(longPassword);
      assertNotNull(encrypted);
    }

    @Test
    void shouldProduceDifferentOutputForDifferentInput() {
      String encrypted1 = encryptionUtil.encrypt("password1");
      String encrypted2 = encryptionUtil.encrypt("password2");
      assertNotEquals(encrypted1, encrypted2);
    }
  }

  @Nested
  class TestDecrypt {

    @Test
    void shouldDecryptPasswordSuccessfully() {
      String plainPassword = "TestPassword123!";
      String encrypted = encryptionUtil.encrypt(plainPassword);
      String decrypted = encryptionUtil.decrypt(encrypted);
      assertEquals(plainPassword, decrypted);
    }

    @Test
    void shouldDecryptEmptyPassword() {
      String encrypted = encryptionUtil.encrypt("");
      String decrypted = encryptionUtil.decrypt(encrypted);
      assertEquals("", decrypted);
    }

    @Test
    void shouldDecryptSpecialCharacters() {
      String specialPassword = "P@$$w0rd!#$%^&*()";
      String encrypted = encryptionUtil.encrypt(specialPassword);
      String decrypted = encryptionUtil.decrypt(encrypted);
      assertEquals(specialPassword, decrypted);
    }

    @Test
    void shouldDecryptUnicodeCharacters() {
      String unicodePassword = "密码测试123पासवर्ड";
      String encrypted = encryptionUtil.encrypt(unicodePassword);
      String decrypted = encryptionUtil.decrypt(encrypted);
      assertEquals(unicodePassword, decrypted);
    }

    @Test
    void shouldThrowExceptionForInvalidEncryptedPassword() {
      assertThrows(RuntimeException.class,
          () -> encryptionUtil.decrypt("invalid_base64_data!!!"));
    }

    @Test
    void shouldThrowExceptionForCorruptedData() {
      String corruptedData = "dGhpcyBpcyBub3QgZW5jcnlwdGVk";
      assertThrows(RuntimeException.class,
          () -> encryptionUtil.decrypt(corruptedData));
    }
  }

  @Nested
  class TestGenerateDigest {

    @Test
    void shouldGenerateDigestSuccessfully() {
      String digest = encryptionUtil.generateDigest("TestInput123");
      assertNotNull(digest);
      assertFalse(digest.isEmpty());
    }

    @Test
    void shouldGenerateSameDigestForSameInput() {
      String digest1 = encryptionUtil.generateDigest("TestInput123");
      String digest2 = encryptionUtil.generateDigest("TestInput123");
      assertEquals(digest1, digest2);
    }

    @Test
    void shouldGenerateDifferentDigestForDifferentInput() {
      String digest1 = encryptionUtil.generateDigest("TestInput1");
      String digest2 = encryptionUtil.generateDigest("TestInput2");
      assertNotEquals(digest1, digest2);
    }

    @Test
    void shouldHandleEmptyInput() {
      String digest = encryptionUtil.generateDigest("");
      assertNotNull(digest);
      assertFalse(digest.isEmpty());
    }

    @Test
    void shouldHandleSpecialCharacters() {
      String digest = encryptionUtil.generateDigest("!@#$%^&*()_+-=[]{}|;':\",./<>?");
      assertNotNull(digest);
    }

    @Test
    void shouldGenerateHexEncodedDigest() {
      String digest = encryptionUtil.generateDigest("test");
      assertTrue(digest.matches("[0-9a-f]+"));
      assertEquals(64, digest.length());
    }
  }

  @Nested
  class TestGenerateSalt {

    @Test
    void shouldGenerateNonNullSalt() {
      String salt = encryptionUtil.generateSalt();
      assertNotNull(salt);
      assertFalse(salt.isEmpty());
    }

    @Test
    void shouldGenerateDifferentSaltsEachTime() {
      String salt1 = encryptionUtil.generateSalt();
      String salt2 = encryptionUtil.generateSalt();
      assertNotEquals(salt1, salt2);
    }

    @Test
    void shouldGenerateBase64EncodedSalt() {
      String salt = encryptionUtil.generateSalt();
      java.util.Base64.getDecoder().decode(salt);
    }
  }

  @Nested
  class TestVerifyPassword {

    @Test
    void shouldVerifyCorrectPassword() {
      String password = "TestPassword123!";
      String digest = encryptionUtil.generateDigest(password);
      assertTrue(encryptionUtil.verifyPassword(password, digest));
    }

    @Test
    void shouldRejectIncorrectPassword() {
      String digest = encryptionUtil.generateDigest("CorrectPassword123!");
      assertFalse(encryptionUtil.verifyPassword("WrongPassword456!", digest));
    }

    @Test
    void shouldRejectWrongDigest() {
      assertFalse(encryptionUtil.verifyPassword("TestPassword123!", "wrongdigest"));
    }

    @Test
    void shouldVerifyEmptyPassword() {
      String digest = encryptionUtil.generateDigest("");
      assertTrue(encryptionUtil.verifyPassword("", digest));
    }

    @Test
    void shouldVerifySpecialCharacterPassword() {
      String password = "P@$$w0rd!#$%";
      String digest = encryptionUtil.generateDigest(password);
      assertTrue(encryptionUtil.verifyPassword(password, digest));
    }
  }

  @Nested
  class TestGetKeyAsBase64 {

    @Test
    void shouldReturnNonNullKey() {
      String key = encryptionUtil.getKeyAsBase64();
      assertNotNull(key);
      assertFalse(key.isEmpty());
    }

    @Test
    void shouldReturnConsistentKey() {
      String key1 = encryptionUtil.getKeyAsBase64();
      String key2 = encryptionUtil.getKeyAsBase64();
      assertEquals(key1, key2);
    }

    @Test
    void shouldReturnValidBase64Key() {
      String key = encryptionUtil.getKeyAsBase64();
      byte[] decoded = java.util.Base64.getDecoder().decode(key);
      assertTrue(decoded.length > 0);
    }
  }

  @Nested
  class TestEndToEndEncryption {

    @Test
    void shouldPerformCompleteEncryptionDecryptionCycle() {
      String originalPassword = "MySecurePassword123!@#";
      String encrypted = encryptionUtil.encrypt(originalPassword);
      String decrypted = encryptionUtil.decrypt(encrypted);
      assertEquals(originalPassword, decrypted);
    }

    @Test
    void shouldHandleMultiplePasswordsIndependently() {
      String password1 = "Password1";
      String password2 = "Password2";
      String password3 = "Password3";

      String enc1 = encryptionUtil.encrypt(password1);
      String enc2 = encryptionUtil.encrypt(password2);
      String enc3 = encryptionUtil.encrypt(password3);

      assertEquals(password1, encryptionUtil.decrypt(enc1));
      assertEquals(password2, encryptionUtil.decrypt(enc2));
      assertEquals(password3, encryptionUtil.decrypt(enc3));
    }
  }
}
