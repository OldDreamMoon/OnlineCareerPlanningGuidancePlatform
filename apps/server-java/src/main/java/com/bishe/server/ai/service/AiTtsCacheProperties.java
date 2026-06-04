package com.bishe.server.ai.service;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * AI TTS 临时缓存配置。
 */
@Component
@ConfigurationProperties(prefix = "ai.tts.cache")
public class AiTtsCacheProperties {

    private boolean enabled = true;
    private long ttlSeconds = 1800L;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public long getTtlSeconds() {
        return ttlSeconds;
    }

    public void setTtlSeconds(long ttlSeconds) {
        this.ttlSeconds = Math.max(ttlSeconds, 60L);
    }
}
