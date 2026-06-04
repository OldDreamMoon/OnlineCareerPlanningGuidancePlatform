package com.bishe.server.certification;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 正式认证资料链路配置。
 */
@Component
@ConfigurationProperties(prefix = "certification")
public class CertificationProperties {

    private long maxFileSizeBytes = 10 * 1024 * 1024L;

    public long getMaxFileSizeBytes() {
        return maxFileSizeBytes;
    }

    public void setMaxFileSizeBytes(long maxFileSizeBytes) {
        this.maxFileSizeBytes = maxFileSizeBytes;
    }
}
