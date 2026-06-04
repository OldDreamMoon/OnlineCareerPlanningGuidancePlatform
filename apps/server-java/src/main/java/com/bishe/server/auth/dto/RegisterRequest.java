package com.bishe.server.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 注册请求。
 */
public record RegisterRequest(
        @NotBlank(message = "role is required")
        String role,
        @NotBlank(message = "email is required")
        @Email(message = "email format is invalid")
        String email,
        @NotBlank(message = "password is required")
        @Size(min = 8, max = 64, message = "password length must be between 8 and 64")
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$", message = "password must include letters and numbers")
        String password,
        @NotBlank(message = "displayName is required")
        @Size(max = 100, message = "displayName too long")
        String displayName,
        @Size(max = 100, message = "realName too long")
        String realName,
        @Size(max = 200, message = "companyName too long")
        String companyName,
        @Size(max = 100, message = "jobTitle too long")
        String jobTitle,
        @Size(max = 2048, message = "emailVerificationToken too long")
        String emailVerificationToken
) {
}
