package com.bishe.server.auth.captcha;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 极验 v4 人机验证配置。
 */
@Component
@ConfigurationProperties(prefix = "auth.captcha.geetest")
public class GeetestCaptchaProperties {

    private boolean enabled = false;
    private String captchaId = "";
    private String captchaKey = "";
    private String verifyUrl = "https://gcaptcha4.geetest.com/validate";
    private long timeoutMs = 5000L;
    private long proofTtlSeconds = 600L;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getCaptchaId() {
        return captchaId;
    }

    public void setCaptchaId(String captchaId) {
        this.captchaId = captchaId;
    }

    public String getCaptchaKey() {
        return captchaKey;
    }

    public void setCaptchaKey(String captchaKey) {
        this.captchaKey = captchaKey;
    }

    public String getVerifyUrl() {
        return verifyUrl;
    }

    public void setVerifyUrl(String verifyUrl) {
        this.verifyUrl = verifyUrl;
    }

    public long getTimeoutMs() {
        return timeoutMs;
    }

    public void setTimeoutMs(long timeoutMs) {
        this.timeoutMs = timeoutMs;
    }

    public long getProofTtlSeconds() {
        return proofTtlSeconds;
    }

    public void setProofTtlSeconds(long proofTtlSeconds) {
        this.proofTtlSeconds = proofTtlSeconds;
    }

    public boolean isReady() {
        return enabled
                && StringUtils.hasText(captchaId)
                && StringUtils.hasText(captchaKey)
                && StringUtils.hasText(verifyUrl);
    }
}
