package com.bishe.server.governance;

import java.time.Instant;

/**
 * 敏感词配置快照：供缓存层与治理服务读取。
 */
public record SensitiveTermRow(
        long id,
        String term,
        String termType,
        String riskLevel,
        String action,
        String sourceScope,
        boolean whitelist,
        boolean enabled,
        Instant createdAt,
        Instant updatedAt
) {
}
