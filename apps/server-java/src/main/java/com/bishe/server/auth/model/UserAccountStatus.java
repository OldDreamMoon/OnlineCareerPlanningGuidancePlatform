package com.bishe.server.auth.model;

/**
 * 用户账号状态。
 */
public enum UserAccountStatus {
    ACTIVE,
    PENDING,
    SUSPENDED;

    public static UserAccountStatus parse(String value) {
        return UserAccountStatus.valueOf(value.trim().toUpperCase());
    }
}
