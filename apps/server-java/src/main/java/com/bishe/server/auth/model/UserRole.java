package com.bishe.server.auth.model;

import java.util.EnumSet;

/**
 * 系统角色枚举，需与 SSOT 保持一致。
 */
public enum UserRole {
    STUDENT,
    MENTOR,
    ENTERPRISE,
    ADMIN;

    public static UserRole parse(String value) {
        return UserRole.valueOf(value.trim().toUpperCase());
    }

    public static boolean canSelfRegister(UserRole role) {
        return EnumSet.of(STUDENT, MENTOR, ENTERPRISE).contains(role);
    }
}
