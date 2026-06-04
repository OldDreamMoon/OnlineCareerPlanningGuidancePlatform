package com.bishe.server.auth;

import com.bishe.server.auth.model.AppUser;
import com.bishe.server.auth.service.AuthException;

/**
 * 系统账号策略：统一约束内部保留邮箱域、交互登录与后台可见性。
 */
public final class SystemUserPolicy {

    public static final String ADMIN_SQL_EXCLUDE_SYSTEM_USERS = " AND u.email NOT LIKE '%@system.local'";
    private static final String SYSTEM_EMAIL_DOMAIN = "@system.local";

    private SystemUserPolicy() {
    }

    public static boolean isSystemUserEmail(String email) {
        if (email == null) {
            return false;
        }
        return email.trim().toLowerCase().endsWith(SYSTEM_EMAIL_DOMAIN);
    }

    public static boolean isSystemUser(AppUser user) {
        return user != null && isSystemUserEmail(user.email());
    }

    public static void assertPublicEmailAllowed(String email) {
        if (isSystemUserEmail(email)) {
            throw AuthException.reservedEmailDomain();
        }
    }

    public static void assertInteractiveLoginAllowed(AppUser user) {
        if (isSystemUser(user)) {
            throw AuthException.invalidCredentials();
        }
    }

    public static void assertInteractiveSessionAllowed(AppUser user) {
        if (isSystemUser(user)) {
            throw AuthException.tokenExpiredOrInvalid();
        }
    }

    public static void assertPasswordResetAllowed(AppUser user) {
        if (isSystemUser(user)) {
            throw AuthException.emailNotRegistered();
        }
    }
}
