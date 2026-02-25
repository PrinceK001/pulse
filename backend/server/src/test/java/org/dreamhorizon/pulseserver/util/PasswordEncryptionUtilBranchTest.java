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

class PasswordEncryptionUtilBranchTest {

  private PasswordEncryptionUtil util;

  @BeforeEach
  void setup() {
    util = new PasswordEncryptionUtil();
  }

  @Nested
  class TestEncryptDecryptCycle {

    @Test
    void shouldEncryptAndDecryptSuccessfully() {
      String password = "testPassword123!";
      String encrypted = util.encrypt(password);
      String decrypted = util.decrypt(encrypted);
      assertEquals(password, decrypted);
    }

    @Test
    void shouldHandleEmptyPassword() {
      String password = "";
      String encrypted = util.encrypt(password);
      String decrypted = util.decrypt(encrypted);
      assertEquals(password, decrypted);
    }

    @Test
    void shouldHandleSpecialCharacters() {
      String password = "p@$$w0rd!#$%^&*()_+{}|:<>?~`";
      String encrypted = util.encrypt(password);
      String decrypted = util.decrypt(encrypted);
      assertEquals(password, decrypted);
    }

    @Test
    void shouldHandleUnicodeCharacters() {
      String password = "密码тест";
      String encrypted = util.encrypt(password);
      String decrypted = util.decrypt(encrypted);
      assertEquals(password, decrypted);
    }

    @Test
    void shouldHandleLongPassword() {
      String password = "a".repeat(1000);
      String encrypted = util.encrypt(password);
      String decrypted = util.decrypt(encrypted);
      assertEquals(password, decrypted);
    }

    @Test
    void shouldProduceSameEncryptedOutputForSameInput() {
      String password = "testPassword";
      String encrypted1 = util.encrypt(password);
      String encrypted2 = util.encrypt(password);
      assertEquals(encrypted1, encrypted2);
    }

    @Test
    void shouldProduceDifferentEncryptedOutputForDifferentInput() {
      String encrypted1 = util.encrypt("password1");
      String encrypted2 = util.encrypt("password2");
      assertNotEquals(encrypted1, encrypted2);
    }

    @Test
    void shouldReturnNonNullEncryptedValue() {
      String encrypted = util.encrypt("test");
      assertNotNull(encrypted);
      assertFalse(encrypted.isEmpty());
    }
  }

  @Nested
  class TestDigest {

    @Test
    void shouldGenerateConsistentDigest() {
      String input = "testInput";
      String digest1 = util.generateDigest(input);
      String digest2 = util.generateDigest(input);
      assertEquals(digest1, digest2);
    }

    @Test
    void shouldGenerateDifferentDigestsForDifferentInputs() {
      String digest1 = util.generateDigest("input1");
      String digest2 = util.generateDigest("input2");
      assertNotEquals(digest1, digest2);
    }

    @Test
    void shouldGenerateDigestForEmptyString() {
      String digest = util.generateDigest("");
      assertNotNull(digest);
      assertTrue(digest.length() > 0);
    }

    @Test
    void shouldGenerateDigestForSpecialCharacters() {
      String digest = util.generateDigest("!@#$%^&*()");
      assertNotNull(digest);
    }

    @Test
    void shouldGenerateDigestForUnicode() {
      String digest = util.generateDigest("密码тест");
      assertNotNull(digest);
    }

    @Test
    void shouldGenerateHexEncodedDigest() {
      String digest = util.generateDigest("test");
      assertNotNull(digest);
      assertTrue(digest.matches("[0-9a-f]+"));
      assertEquals(64, digest.length());
    }
  }

  @Nested
  class TestVerifyPassword {

    @Test
    void shouldVerifyMatchingPassword() {
      String password = "testPassword";
      String digest = util.generateDigest(password);
      assertTrue(util.verifyPassword(password, digest));
    }

    @Test
    void shouldRejectNonMatchingPassword() {
      String password = "testPassword";
      String digest = util.generateDigest(password);
      assertFalse(util.verifyPassword("wrongPassword", digest));
    }

    @Test
    void shouldHandleEmptyPasswordVerification() {
      String digest = util.generateDigest("");
      assertTrue(util.verifyPassword("", digest));
      assertFalse(util.verifyPassword("nonEmpty", digest));
    }

    @Test
    void shouldHandleSpecialCharacterVerification() {
      String password = "!@#$%^&*()";
      String digest = util.generateDigest(password);
      assertTrue(util.verifyPassword(password, digest));
    }

    @Test
    void shouldReturnFalseForInvalidDigest() {
      String password = "testPassword";
      assertFalse(util.verifyPassword(password, "invalidDigest"));
    }
  }

  @Nested
  class TestSalt {

    @Test
    void shouldGenerateNonNullSalt() {
      String salt = util.generateSalt();
      assertNotNull(salt);
      assertFalse(salt.isEmpty());
    }

    @Test
    void shouldGenerateDifferentSaltsEachTime() {
      String salt1 = util.generateSalt();
      String salt2 = util.generateSalt();
      assertNotEquals(salt1, salt2);
    }

    @Test
    void shouldGenerateBase64EncodedSalt() {
      String salt = util.generateSalt();
      assertNotNull(salt);
      assertFalse(salt.isEmpty());
      // Should be valid Base64
      java.util.Base64.getDecoder().decode(salt);
    }
  }

  @Nested
  class TestGetKeyAsBase64 {

    @Test
    void shouldReturnNonNullKey() {
      String key = util.getKeyAsBase64();
      assertNotNull(key);
      assertFalse(key.isEmpty());
    }

    @Test
    void shouldReturnConsistentKey() {
      String key1 = util.getKeyAsBase64();
      String key2 = util.getKeyAsBase64();
      assertEquals(key1, key2);
    }

    @Test
    void shouldReturnValidBase64Key() {
      String key = util.getKeyAsBase64();
      byte[] decoded = java.util.Base64.getDecoder().decode(key);
      assertNotNull(decoded);
      assertTrue(decoded.length > 0);
    }
  }

  @Nested
  class TestDecryptWithInvalidInput {

    @Test
    void shouldThrowExceptionForInvalidEncryptedPassword() {
      assertThrows(RuntimeException.class, () -> util.decrypt("invalid"));
    }

    @Test
    void shouldThrowExceptionForNullEncryptedPassword() {
      assertThrows(Exception.class, () -> util.decrypt(null));
    }
  }
}
