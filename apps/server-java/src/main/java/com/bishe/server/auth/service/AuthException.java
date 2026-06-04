package com.bishe.server.auth.service;

import com.bishe.server.common.exception.ApiException;
import org.springframework.http.HttpStatus;

/**
 * 鉴权域异常工厂，集中维护 AUTH 错误码。
 */
public class AuthException extends ApiException {

    private AuthException(String code, String message, HttpStatus status) {
        super(code, message, status);
    }

    public static AuthException emailExists() {
        return new AuthException("AUTH-1001", "email already exists", HttpStatus.CONFLICT);
    }

    public static AuthException invalidCredentials() {
        return new AuthException("AUTH-1002", "invalid credentials", HttpStatus.UNAUTHORIZED);
    }

    public static AuthException tokenExpiredOrInvalid() {
        return new AuthException("AUTH-1003", "token expired", HttpStatus.UNAUTHORIZED);
    }

    public static AuthException permissionDenied() {
        return new AuthException("AUTH-1004", "permission denied", HttpStatus.FORBIDDEN);
    }

    public static AuthException roleNotAllowedForRegister() {
        return new AuthException("AUTH-1005", "role not allowed for self register", HttpStatus.BAD_REQUEST);
    }

    public static AuthException accountNotActive() {
        return new AuthException("AUTH-1006", "account is not active", HttpStatus.FORBIDDEN);
    }

    public static AuthException captchaRequired() {
        return new AuthException("AUTH-1007", "captcha verification is required", HttpStatus.BAD_REQUEST);
    }

    public static AuthException captchaInvalidOrExpired() {
        return new AuthException("AUTH-1008", "captcha verification is invalid or expired", HttpStatus.BAD_REQUEST);
    }

    public static AuthException captchaUnavailable() {
        return new AuthException("AUTH-1009", "captcha verification service is unavailable", HttpStatus.SERVICE_UNAVAILABLE);
    }

    public static AuthException captchaVerifyFailed(String reason) {
        String message = (reason == null || reason.isBlank())
                ? "captcha verification failed"
                : "captcha verification failed: " + reason;
        return new AuthException("AUTH-1010", message, HttpStatus.BAD_REQUEST);
    }

    public static AuthException emailVerificationUnavailable() {
        return new AuthException("AUTH-1011", "email verification service is unavailable", HttpStatus.SERVICE_UNAVAILABLE);
    }

    public static AuthException emailVerificationSendTooFrequent(long waitSeconds) {
        return new AuthException("AUTH-1012", "email verification code resend is too frequent, retry after " + waitSeconds + " seconds", HttpStatus.TOO_MANY_REQUESTS);
    }

    public static AuthException emailVerificationCodeInvalidOrExpired() {
        return new AuthException("AUTH-1013", "email verification code is invalid or expired", HttpStatus.BAD_REQUEST);
    }

    public static AuthException emailVerificationRequired() {
        return new AuthException("AUTH-1014", "email verification is required", HttpStatus.BAD_REQUEST);
    }

    public static AuthException emailVerificationTokenInvalidOrExpired() {
        return new AuthException("AUTH-1015", "email verification token is invalid or expired", HttpStatus.BAD_REQUEST);
    }

    public static AuthException emailNotRegistered() {
        return new AuthException("AUTH-1016", "email is not registered", HttpStatus.NOT_FOUND);
    }

    public static AuthException reservedEmailDomain() {
        return new AuthException("AUTH-1017", "email domain is reserved for system accounts", HttpStatus.BAD_REQUEST);
    }
}
