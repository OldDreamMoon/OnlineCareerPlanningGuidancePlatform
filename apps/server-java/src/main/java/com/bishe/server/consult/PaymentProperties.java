package com.bishe.server.consult;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * 支付配置，当前优先支持 MOCK 闭环，同时预留 SANDBOX 链路配置。
 */
@Component
@ConfigurationProperties(prefix = "payment")
public class PaymentProperties {

    private String mode = PaymentMode.MOCK.name();
    private int unpaidTimeoutMinutes = 30;
    private final Sandbox sandbox = new Sandbox();

    public PaymentMode currentMode() {
        return PaymentMode.parse(mode == null ? PaymentMode.MOCK.name() : mode);
    }

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public int getUnpaidTimeoutMinutes() {
        return unpaidTimeoutMinutes;
    }

    public void setUnpaidTimeoutMinutes(int unpaidTimeoutMinutes) {
        this.unpaidTimeoutMinutes = unpaidTimeoutMinutes;
    }

    public Sandbox getSandbox() {
        return sandbox;
    }

    /**
     * 沙箱支付相关配置。
     */
    public static class Sandbox {

        private String payBaseUrl;
        private String gatewayUrl;
        private String appId;
        private String appPrivateKey;
        private String appPrivateKeyBase64;
        private String appPrivateKeyPath;
        private boolean verifyEnabled;
        private String alipayPublicKey;
        private String alipayPublicKeyBase64;
        private String alipayPublicKeyPath;
        private String signType = "RSA2";
        private String notifyUrl;
        private String returnUrl;
        private String charset = "utf-8";
        private String subjectPrefix = "职业规划咨询";
        private String timeoutExpress = "30m";
        private String productCode = "FAST_INSTANT_TRADE_PAY";

        public String getPayBaseUrl() {
            return payBaseUrl;
        }

        public void setPayBaseUrl(String payBaseUrl) {
            this.payBaseUrl = payBaseUrl;
        }

        public String getGatewayUrl() {
            return gatewayUrl;
        }

        public void setGatewayUrl(String gatewayUrl) {
            this.gatewayUrl = gatewayUrl;
        }

        public String getAppId() {
            return appId;
        }

        public void setAppId(String appId) {
            this.appId = appId;
        }

        public String getAppPrivateKey() {
            return appPrivateKey;
        }

        public void setAppPrivateKey(String appPrivateKey) {
            this.appPrivateKey = appPrivateKey;
        }

        public String getAppPrivateKeyPath() {
            return appPrivateKeyPath;
        }

        public void setAppPrivateKeyPath(String appPrivateKeyPath) {
            this.appPrivateKeyPath = appPrivateKeyPath;
        }

        public String getAppPrivateKeyBase64() {
            return appPrivateKeyBase64;
        }

        public void setAppPrivateKeyBase64(String appPrivateKeyBase64) {
            this.appPrivateKeyBase64 = appPrivateKeyBase64;
        }

        public boolean isVerifyEnabled() {
            return verifyEnabled;
        }

        public void setVerifyEnabled(boolean verifyEnabled) {
            this.verifyEnabled = verifyEnabled;
        }

        public String getAlipayPublicKey() {
            return alipayPublicKey;
        }

        public void setAlipayPublicKey(String alipayPublicKey) {
            this.alipayPublicKey = alipayPublicKey;
        }

        public String getAlipayPublicKeyPath() {
            return alipayPublicKeyPath;
        }

        public void setAlipayPublicKeyPath(String alipayPublicKeyPath) {
            this.alipayPublicKeyPath = alipayPublicKeyPath;
        }

        public String getAlipayPublicKeyBase64() {
            return alipayPublicKeyBase64;
        }

        public void setAlipayPublicKeyBase64(String alipayPublicKeyBase64) {
            this.alipayPublicKeyBase64 = alipayPublicKeyBase64;
        }

        public String getSignType() {
            return signType;
        }

        public void setSignType(String signType) {
            this.signType = signType;
        }

        public String getNotifyUrl() {
            return notifyUrl;
        }

        public void setNotifyUrl(String notifyUrl) {
            this.notifyUrl = notifyUrl;
        }

        public String getReturnUrl() {
            return returnUrl;
        }

        public void setReturnUrl(String returnUrl) {
            this.returnUrl = returnUrl;
        }

        public String getCharset() {
            return charset;
        }

        public void setCharset(String charset) {
            this.charset = charset;
        }

        public String getSubjectPrefix() {
            return subjectPrefix;
        }

        public void setSubjectPrefix(String subjectPrefix) {
            this.subjectPrefix = subjectPrefix;
        }

        public String getTimeoutExpress() {
            return timeoutExpress;
        }

        public void setTimeoutExpress(String timeoutExpress) {
            this.timeoutExpress = timeoutExpress;
        }

        public String getProductCode() {
            return productCode;
        }

        public void setProductCode(String productCode) {
            this.productCode = productCode;
        }

        public String resolveAppPrivateKey() {
            return resolveInlineBase64OrFile(appPrivateKey, appPrivateKeyBase64, appPrivateKeyPath);
        }

        public String resolveAlipayPublicKey() {
            return resolveInlineBase64OrFile(alipayPublicKey, alipayPublicKeyBase64, alipayPublicKeyPath);
        }

        public String resolveReturnUrl(String orderNo) {
            if (returnUrl == null || returnUrl.isBlank()) {
                return returnUrl;
            }
            return returnUrl.contains("{orderNo}") ? returnUrl.replace("{orderNo}", orderNo) : returnUrl;
        }

        public boolean hasSignedPagePayConfig() {
            return hasText(gatewayUrl)
                    && hasText(appId)
                    && hasText(resolveAppPrivateKey())
                    && hasText(notifyUrl)
                    && hasText(returnUrl);
        }

        private String resolveInlineBase64OrFile(String inlineValue, String base64Value, String pathValue) {
            if (hasText(inlineValue)) {
                return normalizeKeyMaterial(inlineValue);
            }
            if (hasText(base64Value)) {
                try {
                    byte[] decoded = Base64.getDecoder().decode(base64Value.replaceAll("\\s+", ""));
                    return normalizeKeyMaterial(new String(decoded, StandardCharsets.UTF_8));
                } catch (IllegalArgumentException ex) {
                    throw new IllegalStateException("failed to decode payment sandbox key base64", ex);
                }
            }
            if (!hasText(pathValue)) {
                return null;
            }
            try {
                return normalizeKeyMaterial(Files.readString(Path.of(pathValue.trim())));
            } catch (IOException ex) {
                throw new IllegalStateException("failed to read payment sandbox key file: " + pathValue, ex);
            }
        }

        private String normalizeKeyMaterial(String value) {
            if (!hasText(value)) {
                return value;
            }
            return value
                    .replace("\\r\\n", "\n")
                    .replace("\\n", "\n")
                    .replace("\\r", "\n")
                    .trim();
        }

        private boolean hasText(String value) {
            return value != null && !value.isBlank();
        }
    }
}
