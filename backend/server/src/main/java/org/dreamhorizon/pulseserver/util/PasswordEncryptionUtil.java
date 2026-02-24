package org.dreamhorizon.pulseserver.util;

import com.google.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Utility for encrypting and decrypting sensitive data (e.g., ClickHouse passwords).
 * Uses AES-256 encryption with salting and SHA-256 digest for verification.
 */
@Slf4j
@Singleton
public class PasswordEncryptionUtil {
    
    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int KEY_SIZE = 256;
    private static final int SALT_LENGTH = 16;
    private static final int GCM_IV_LENGTH = 12; // 12 bytes recommended for GCM
    private static final int GCM_TAG_LENGTH = 128; // 128-bit authentication tag
    
    private final SecretKey secretKey;
    private final SecureRandom secureRandom;
    
    /**
     * Initialize with encryption key from environment variable.
     * In production, this should come from a secure key management service.
     */
    public PasswordEncryptionUtil() {
        String keyString = System.getenv("ENCRYPTION_KEY");
        
        if (keyString == null || keyString.isEmpty()) {
            log.warn("ENCRYPTION_KEY not set, generating random key (NOT SUITABLE FOR PRODUCTION)");
            this.secretKey = generateKey();
        } else {
            this.secretKey = new SecretKeySpec(
                Base64.getDecoder().decode(keyString), 
                ALGORITHM
            );
        }
        
        this.secureRandom = new SecureRandom();
        log.info("PasswordEncryptionUtil initialized");
    }
    
    /**
     * Encrypt plaintext password using AES-GCM mode.
     * GCM provides both encryption and authentication, preventing tampering.
     * The IV is prepended to the ciphertext for storage.
     * 
     * @param plaintext Plain text password
     * @return Base64 encoded encrypted password with IV prepended
     */
    public String encrypt(String plaintext) {
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            
            // Generate random IV for GCM mode
            byte[] iv = new byte[GCM_IV_LENGTH];
            secureRandom.nextBytes(iv);
            javax.crypto.spec.GCMParameterSpec gcmSpec = new javax.crypto.spec.GCMParameterSpec(GCM_TAG_LENGTH, iv);
            
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, gcmSpec);
            byte[] encryptedBytes = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            
            // Prepend IV to encrypted data for storage
            byte[] combined = new byte[iv.length + encryptedBytes.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(encryptedBytes, 0, combined, iv.length, encryptedBytes.length);
            
            return Base64.getEncoder().encodeToString(combined);
            
        } catch (Exception e) {
            log.error("Encryption failed", e);
            throw new RuntimeException("Failed to encrypt password", e);
        }
    }
    
    /**
     * Decrypt encrypted password using AES-GCM mode.
     * Extracts the IV from the beginning of the ciphertext.
     * 
     * @param ciphertext Base64 encoded encrypted password with IV prepended
     * @return Decrypted plain text password
     */
    public String decrypt(String ciphertext) {
        try {
            byte[] decoded = Base64.getDecoder().decode(ciphertext);
            
            // Extract IV from beginning
            byte[] iv = new byte[GCM_IV_LENGTH];
            byte[] encryptedBytes = new byte[decoded.length - GCM_IV_LENGTH];
            System.arraycopy(decoded, 0, iv, 0, GCM_IV_LENGTH);
            System.arraycopy(decoded, GCM_IV_LENGTH, encryptedBytes, 0, encryptedBytes.length);
            
            javax.crypto.spec.GCMParameterSpec gcmSpec = new javax.crypto.spec.GCMParameterSpec(GCM_TAG_LENGTH, iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec);
            
            byte[] decryptedBytes = cipher.doFinal(encryptedBytes);
            return new String(decryptedBytes, StandardCharsets.UTF_8);
            
        } catch (Exception e) {
            log.error("Decryption failed", e);
            throw new RuntimeException("Failed to decrypt password", e);
        }
    }
    
    /**
     * Generate random salt for password hashing.
     * 
     * @return Base64 encoded salt
     */
    public String generateSalt() {
        byte[] salt = new byte[SALT_LENGTH];
        secureRandom.nextBytes(salt);
        return Base64.getEncoder().encodeToString(salt);
    }
    
    /**
     * Generate SHA-256 digest of password for verification.
     * 
     * @param password Plain text password
     * @return Hex encoded digest
     */
    public String generateDigest(String password) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(password.getBytes(StandardCharsets.UTF_8));
            
            // Convert to hex string
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            
            return hexString.toString();
            
        } catch (Exception e) {
            log.error("Digest generation failed", e);
            throw new RuntimeException("Failed to generate digest", e);
        }
    }
    
    /**
     * Verify password against stored digest.
     * 
     * @param password Plain text password
     * @param storedDigest Stored digest to verify against
     * @return true if password matches
     */
    public boolean verifyPassword(String password, String storedDigest) {
        String passwordDigest = generateDigest(password);
        return passwordDigest.equals(storedDigest);
    }
    
    /**
     * Generate a new encryption key (for initialization only).
     */
    private SecretKey generateKey() {
        try {
            KeyGenerator keyGenerator = KeyGenerator.getInstance(ALGORITHM);
            keyGenerator.init(KEY_SIZE, secureRandom);
            return keyGenerator.generateKey();
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate encryption key", e);
        }
    }
    
    /**
     * Get the encryption key as Base64 string (for backup/storage).
     * Only use this during initial setup to save the key securely.
     */
    public String getKeyAsBase64() {
        return Base64.getEncoder().encodeToString(secretKey.getEncoded());
    }
}
