package com.bishe.server.ai.gateway;

import com.bishe.server.common.exception.ApiException;
import com.bishe.server.security.JwtProperties;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AI 网关敏感配置加解密服务。
 */
@Service
public class AiConfigCryptoService {

    private static final String AES_TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH = 128;
    private static final int IV_LENGTH = 12;

    private final AiGatewayProperties properties;
    private final JwtProperties jwtProperties;
    private final SecureRandom secureRandom = new SecureRandom();

    public AiConfigCryptoService(AiGatewayProperties properties, JwtProperties jwtProperties) {
        this.properties = properties;
        this.jwtProperties = jwtProperties;
    }

    public String encrypt(String plainText) {
        if (plainText == null || plainText.isBlank()) {
            return null;
        }
        try {
            byte[] iv = new byte[IV_LENGTH];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(AES_TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey(), new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            byte[] encrypted = cipher.doFinal(plainText.trim().getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(iv) + "." + Base64.getEncoder().encodeToString(encrypted);
        } catch (Exception ex) {
            throw new ApiException("AI-2001", "ai config encrypt failed", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    public String decrypt(String cipherText) {
        if (cipherText == null || cipherText.isBlank()) {
            return "";
        }
        try {
            String[] parts = cipherText.split("\\.", 2);
            if (parts.length != 2) {
                throw new IllegalArgumentException("cipher text invalid");
            }
            byte[] iv = Base64.getDecoder().decode(parts[0]);
            byte[] encrypted = Base64.getDecoder().decode(parts[1]);
            Cipher cipher = Cipher.getInstance(AES_TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (Exception ex) {
            throw new ApiException("AI-2001", "ai config decrypt failed", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    public String mask(String secret) {
        if (secret == null || secret.isBlank()) {
            return "";
        }
        String trimmed = secret.trim();
        int visible = Math.min(4, trimmed.length());
        return "***" + trimmed.substring(trimmed.length() - visible) + "(len=" + trimmed.length() + ")";
    }

    private SecretKeySpec secretKey() throws Exception {
        String secretMaterial = properties.getConfigSecret();
        if (secretMaterial == null || secretMaterial.isBlank()) {
            secretMaterial = jwtProperties.getSecret();
        }
        if (secretMaterial == null || secretMaterial.isBlank()) {
            throw new IllegalStateException("ai config secret missing");
        }
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(secretMaterial.getBytes(StandardCharsets.UTF_8));
        return new SecretKeySpec(digest, "AES");
    }
}
