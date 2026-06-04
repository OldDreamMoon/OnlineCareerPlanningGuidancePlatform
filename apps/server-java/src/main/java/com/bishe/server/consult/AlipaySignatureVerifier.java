package com.bishe.server.consult;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 支付宝请求签名与回调验签工具，兼容 RSA/RSA2 的最小能力。
 */
public final class AlipaySignatureVerifier {

    private AlipaySignatureVerifier() {
    }

    public static boolean verify(Map<String, String> form, String publicKeyPem, String signType) {
        return verifyContent(buildSignContent(form), form.get("sign"), publicKeyPem, signType);
    }

    public static boolean verifyRequest(Map<String, String> form, String publicKeyPem, String signType) {
        return verifyContent(buildRequestSignContent(form), form.get("sign"), publicKeyPem, signType);
    }

    public static String signRequest(Map<String, String> params, String privateKeyPem, String signType) {
        try {
            if (privateKeyPem == null || privateKeyPem.isBlank()) {
                throw new IllegalStateException("private key missing");
            }
            Signature signer = Signature.getInstance(resolveAlgorithm(signType));
            signer.initSign(parsePrivateKey(privateKeyPem));
            signer.update(buildRequestSignContent(params).getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(signer.sign());
        } catch (Exception ex) {
            throw new IllegalStateException("failed to sign alipay request", ex);
        }
    }

    public static String buildSignContent(Map<String, String> form) {
        return form.entrySet().stream()
                .filter(entry -> entry.getKey() != null && !entry.getKey().isBlank())
                .filter(entry -> entry.getValue() != null && !entry.getValue().isBlank())
                .filter(entry -> !"sign".equalsIgnoreCase(entry.getKey()))
                .filter(entry -> !"sign_type".equalsIgnoreCase(entry.getKey()))
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining("&"));
    }

    public static String buildRequestSignContent(Map<String, String> form) {
        return form.entrySet().stream()
                .filter(entry -> entry.getKey() != null && !entry.getKey().isBlank())
                .filter(entry -> entry.getValue() != null && !entry.getValue().isBlank())
                .filter(entry -> !"sign".equalsIgnoreCase(entry.getKey()))
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining("&"));
    }

    private static boolean verifyContent(String signContent, String sign, String publicKeyPem, String signType) {
        try {
            if (sign == null || sign.isBlank() || publicKeyPem == null || publicKeyPem.isBlank()) {
                return false;
            }
            Signature verifier = Signature.getInstance(resolveAlgorithm(signType));
            verifier.initVerify(parsePublicKey(publicKeyPem));
            verifier.update(signContent.getBytes(StandardCharsets.UTF_8));
            return verifier.verify(Base64.getDecoder().decode(sign));
        } catch (Exception ex) {
            return false;
        }
    }

    private static String resolveAlgorithm(String signType) {
        return "RSA".equalsIgnoreCase(signType) ? "SHA1withRSA" : "SHA256withRSA";
    }

    private static PublicKey parsePublicKey(String publicKeyPem) throws Exception {
        String normalized = normalizePem(publicKeyPem)
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "");
        byte[] decoded = Base64.getDecoder().decode(normalized);
        return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(decoded));
    }

    private static PrivateKey parsePrivateKey(String privateKeyPem) throws Exception {
        String normalized = normalizePem(privateKeyPem)
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "");
        byte[] decoded = Base64.getDecoder().decode(normalized);
        return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(decoded));
    }

    private static String normalizePem(String value) {
        return value
                .replace("\\r\\n", "\n")
                .replace("\\n", "\n")
                .replace("\\r", "\n")
                .replace("\r", "")
                .replaceAll("\\s+", "");
    }
}
