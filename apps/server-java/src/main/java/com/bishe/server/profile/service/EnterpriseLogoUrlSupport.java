package com.bishe.server.profile.service;

import com.bishe.server.common.TimePayloads;

import java.time.Instant;

/**
 * 企业 Logo 公开地址辅助方法。
 */
public final class EnterpriseLogoUrlSupport {

    private EnterpriseLogoUrlSupport() {
    }

    public static String buildPublicLogoUrl(long enterpriseUserId, Instant updatedAt) {
        if (enterpriseUserId <= 0) {
            return null;
        }
        Long version = TimePayloads.toEpochMillis(updatedAt);
        String versionToken = version == null ? "latest" : String.valueOf(version);
        return "/api/v1/profiles/enterprises/%d/logo?v=%s".formatted(enterpriseUserId, versionToken);
    }
}
