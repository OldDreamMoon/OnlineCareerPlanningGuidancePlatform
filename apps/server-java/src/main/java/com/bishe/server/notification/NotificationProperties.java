package com.bishe.server.notification;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 通知模块运行参数。
 */
@Component
@ConfigurationProperties(prefix = "notification")
public class NotificationProperties {

    private String appBaseUrl = "";
    private final Dispatch dispatch = new Dispatch();
    private final Websocket websocket = new Websocket();
    private final Email email = new Email();

    public String getAppBaseUrl() {
        return appBaseUrl;
    }

    public void setAppBaseUrl(String appBaseUrl) {
        this.appBaseUrl = appBaseUrl;
    }

    public Dispatch getDispatch() {
        return dispatch;
    }

    public Websocket getWebsocket() {
        return websocket;
    }

    public Email getEmail() {
        return email;
    }

    public static class Dispatch {
        private int batchSize = 8;
        private long leaseMs = 30_000L;
        private long pollIntervalMs = 2_000L;
        private final Retention retention = new Retention();

        public int getBatchSize() {
            return batchSize;
        }

        public void setBatchSize(int batchSize) {
            this.batchSize = batchSize;
        }

        public long getLeaseMs() {
            return leaseMs;
        }

        public void setLeaseMs(long leaseMs) {
            this.leaseMs = leaseMs;
        }

        public long getPollIntervalMs() {
            return pollIntervalMs;
        }

        public void setPollIntervalMs(long pollIntervalMs) {
            this.pollIntervalMs = pollIntervalMs;
        }

        public Retention getRetention() {
            return retention;
        }
    }

    public static class Retention {
        private boolean enabled = true;
        private long maxAgeMs = 2_592_000_000L;
        private int batchSize = 200;
        private long initialDelayMs = 3_600_000L;
        private long fixedDelayMs = 3_600_000L;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public long getMaxAgeMs() {
            return maxAgeMs;
        }

        public void setMaxAgeMs(long maxAgeMs) {
            this.maxAgeMs = maxAgeMs;
        }

        public int getBatchSize() {
            return batchSize;
        }

        public void setBatchSize(int batchSize) {
            this.batchSize = batchSize;
        }

        public long getInitialDelayMs() {
            return initialDelayMs;
        }

        public void setInitialDelayMs(long initialDelayMs) {
            this.initialDelayMs = initialDelayMs;
        }

        public long getFixedDelayMs() {
            return fixedDelayMs;
        }

        public void setFixedDelayMs(long fixedDelayMs) {
            this.fixedDelayMs = fixedDelayMs;
        }
    }

    public static class Websocket {
        private long ticketTtlSeconds = 180L;
        private int dispatchMaxAttempts = 10;
        private long offlineRetryDelaySeconds = 30L;

        public long getTicketTtlSeconds() {
            return ticketTtlSeconds;
        }

        public void setTicketTtlSeconds(long ticketTtlSeconds) {
            this.ticketTtlSeconds = ticketTtlSeconds;
        }

        public int getDispatchMaxAttempts() {
            return dispatchMaxAttempts;
        }

        public void setDispatchMaxAttempts(int dispatchMaxAttempts) {
            this.dispatchMaxAttempts = dispatchMaxAttempts;
        }

        public long getOfflineRetryDelaySeconds() {
            return offlineRetryDelaySeconds;
        }

        public void setOfflineRetryDelaySeconds(long offlineRetryDelaySeconds) {
            this.offlineRetryDelaySeconds = offlineRetryDelaySeconds;
        }
    }

    public static class Email {
        private String resendApiKey = "";
        private String resendFromEmail = "";
        private String resendApiUrl = "https://api.resend.com/emails";

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

        public boolean isReady() {
            return StringUtils.hasText(resendApiKey)
                    && StringUtils.hasText(resendFromEmail)
                    && StringUtils.hasText(resendApiUrl);
        }
    }
}
