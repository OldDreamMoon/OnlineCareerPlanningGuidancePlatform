package com.bishe.server.demo;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 本地答辩演示模式配置。
 */
@Component
@ConfigurationProperties(prefix = "demo")
public class DemoModeProperties {

    private boolean enabled;
    private final Auth auth = new Auth();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Auth getAuth() {
        return auth;
    }

    public boolean isRegisterEmailVerificationBypassEnabled() {
        return enabled && auth.registerSkipEmailVerification;
    }

    public boolean isPasswordResetBypassEnabled() {
        return enabled;
    }

    public boolean isCertificationBypassEnabled() {
        return enabled;
    }

    public String resolveFixedEmailCode() {
        String configured = auth.fixedEmailCode == null ? "" : auth.fixedEmailCode.trim();
        return configured.matches("\\d{6}") ? configured : "000000";
    }

    public static class Auth {

        private boolean registerSkipEmailVerification;
        private String fixedEmailCode = "000000";

        public boolean isRegisterSkipEmailVerification() {
            return registerSkipEmailVerification;
        }

        public void setRegisterSkipEmailVerification(boolean registerSkipEmailVerification) {
            this.registerSkipEmailVerification = registerSkipEmailVerification;
        }

        public String getFixedEmailCode() {
            return fixedEmailCode;
        }

        public void setFixedEmailCode(String fixedEmailCode) {
            this.fixedEmailCode = fixedEmailCode;
        }
    }
}
