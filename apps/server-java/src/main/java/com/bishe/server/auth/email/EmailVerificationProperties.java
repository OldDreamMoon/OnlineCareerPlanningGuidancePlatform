package com.bishe.server.auth.email;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 注册邮箱验证码与 Resend 发信配置。
 */
@Component
@ConfigurationProperties(prefix = "auth.email")
public class EmailVerificationProperties {

    private String resendApiKey = "";
    private String resendFromEmail = "";
    private String resendApiUrl = "https://api.resend.com/emails";
    private long codeTtlSeconds = 600L;
    private long resendCooldownSeconds = 60L;
    private long proofTtlSeconds = 1800L;

    public String getResendApiKey() {
        return resendApiKey;
    }

    public void setResendApiKey(String resendApiKey) {
        this.resendApiKey = resendApiKey;
    }

    public String getResendFromEmail() {
        return resendFromEmail;
    }

    public void setResendFromEmail(String resendFromEmail) {
        this.resendFromEmail = resendFromEmail;
    }

    public String getResendApiUrl() {
        return resendApiUrl;
    }

    public void setResendApiUrl(String resendApiUrl) {
        this.resendApiUrl = resendApiUrl;
    }

    public long getCodeTtlSeconds() {
        return codeTtlSeconds;
    }

    public void setCodeTtlSeconds(long codeTtlSeconds) {
        this.codeTtlSeconds = codeTtlSeconds;
    }

    public long getResendCooldownSeconds() {
        return resendCooldownSeconds;
    }

    public void setResendCooldownSeconds(long resendCooldownSeconds) {
        this.resendCooldownSeconds = resendCooldownSeconds;
    }

    public long getProofTtlSeconds() {
        return proofTtlSeconds;
    }

    public void setProofTtlSeconds(long proofTtlSeconds) {
        this.proofTtlSeconds = proofTtlSeconds;
    }

    public boolean isReady() {
        return StringUtils.hasText(resendApiKey)
                && StringUtils.hasText(resendFromEmail)
                && StringUtils.hasText(resendApiUrl);
    }
}
