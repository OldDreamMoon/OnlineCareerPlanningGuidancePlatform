package com.bishe.server.profile.service;

import com.bishe.server.auth.SystemUserPolicy;
import com.bishe.server.auth.email.EmailVerificationProperties;
import com.bishe.server.auth.model.AppUser;
import com.bishe.server.auth.repository.UserRepository;
import com.bishe.server.common.TimePayloads;
import com.bishe.server.common.exception.ApiException;
import com.bishe.server.demo.DemoModeProperties;
import com.bishe.server.profile.dto.StudentProfileEmailChangeRequest;
import com.bishe.server.profile.dto.StudentProfileEmailChangeResponse;
import com.bishe.server.profile.dto.StudentProfilePasswordChangeRequest;
import com.bishe.server.profile.dto.StudentProfilePasswordChangeResponse;
import com.bishe.server.profile.dto.StudentProfileSecurityEmailSendCodeRequest;
import com.bishe.server.profile.dto.StudentProfileSecuritySendCodeResponse;
import com.bishe.server.profile.dto.StudentProfileSecurityVerifyCodeResponse;
import com.bishe.server.profile.repository.StudentProfileSecurityCodeRepository;
import com.bishe.server.security.JwtProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 学生资料中心安全能力：邮箱换绑与密码重置。
 */
@Service
public class StudentProfileSecurityService {

    private static final Logger log = LoggerFactory.getLogger(StudentProfileSecurityService.class);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Pattern CODE_PATTERN = Pattern.compile("^\\d{6}$");
    private static final HexFormat HEX_FORMAT = HexFormat.of();
    private static final String HMAC_SHA256 = "HmacSHA256";
    private static final String BRAND_NAME = "大学生职业规划平台";
    private static final String STAGE_CURRENT = "CURRENT";
    private static final String STAGE_NEW = "NEW";
    private static final String STAGE_PASSWORD_RESET = "PASSWORD_RESET";
    private static final String PURPOSE_EMAIL_CURRENT_CODE = "PROFILE_EMAIL_CURRENT_CODE";
    private static final String PURPOSE_EMAIL_NEW_CODE = "PROFILE_EMAIL_NEW_CODE";
    private static final String PURPOSE_PASSWORD_RESET_CODE = "PROFILE_PASSWORD_RESET_CODE";
    private static final String PURPOSE_EMAIL_CURRENT_TOKEN = "PROFILE_EMAIL_CURRENT_VERIFIED";
    private static final String PURPOSE_PASSWORD_RESET_TOKEN = "PROFILE_PASSWORD_RESET_VERIFIED";

    public enum PasswordResetEmailScene {
        PROFILE_CENTER,
        LOGIN_RECOVERY
    }

    private final UserRepository userRepository;
    private final StudentProfileSecurityCodeRepository securityCodeRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailVerificationProperties emailVerificationProperties;
    private final JwtProperties jwtProperties;
    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper;
    private final DemoModeProperties demoModeProperties;

    public StudentProfileSecurityService(
            UserRepository userRepository,
            StudentProfileSecurityCodeRepository securityCodeRepository,
            PasswordEncoder passwordEncoder,
            EmailVerificationProperties emailVerificationProperties,
            JwtProperties jwtProperties,
            WebClient.Builder webClientBuilder,
            ObjectMapper objectMapper,
            DemoModeProperties demoModeProperties
    ) {
        this.userRepository = userRepository;
        this.securityCodeRepository = securityCodeRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailVerificationProperties = emailVerificationProperties;
        this.jwtProperties = jwtProperties;
        this.webClientBuilder = webClientBuilder;
        this.objectMapper = objectMapper;
        this.demoModeProperties = demoModeProperties;
    }

    public StudentProfileSecuritySendCodeResponse sendEmailCode(long userId, StudentProfileSecurityEmailSendCodeRequest request) {
        AppUser user = requireUser(userId);
        String stage = normalizeStage(request.stage());
        // CURRENT 验证当前邮箱，NEW 验证目标邮箱，两段都通过后才允许换绑。
        String targetEmail = switch (stage) {
            case STAGE_CURRENT -> user.email();
            case STAGE_NEW -> resolveNewEmailForSend(user, request.newEmail());
            default -> throw new ApiException("BIZ-1401", "unsupported email verification stage", HttpStatus.BAD_REQUEST);
        };
        String purpose = STAGE_CURRENT.equals(stage) ? PURPOSE_EMAIL_CURRENT_CODE : PURPOSE_EMAIL_NEW_CODE;
        DeliveryResult deliveryResult = dispatchVerificationCode(userId, purpose, stage, targetEmail);
        return new StudentProfileSecuritySendCodeResponse(
                true,
                stage,
                targetEmail,
                deliveryResult.deliveryChannel(),
                TimePayloads.toEpochMillis(deliveryResult.expiresAt()),
                TimePayloads.toEpochMillis(deliveryResult.nextSendAt()),
                deliveryResult.debugCode()
        );
    }

    public StudentProfileSecuritySendCodeResponse sendPasswordResetCode(long userId) {
        return sendPasswordResetCode(userId, PasswordResetEmailScene.PROFILE_CENTER);
    }

    public StudentProfileSecuritySendCodeResponse sendPasswordResetCode(long userId, PasswordResetEmailScene scene) {
        AppUser user = requireUser(userId);
        DeliveryResult deliveryResult = dispatchVerificationCode(
                userId,
                PURPOSE_PASSWORD_RESET_CODE,
                STAGE_PASSWORD_RESET,
                user.email(),
                scene
        );
        return new StudentProfileSecuritySendCodeResponse(
                true,
                STAGE_PASSWORD_RESET,
                user.email(),
                deliveryResult.deliveryChannel(),
                TimePayloads.toEpochMillis(deliveryResult.expiresAt()),
                TimePayloads.toEpochMillis(deliveryResult.nextSendAt()),
                deliveryResult.debugCode()
        );
    }

    public StudentProfileSecurityVerifyCodeResponse verifyCurrentEmailCode(long userId, String code) {
        AppUser user = requireUser(userId);
        verifyCode(userId, PURPOSE_EMAIL_CURRENT_CODE, user.email(), code);
        Instant expiresAt = Instant.now().plusSeconds(emailVerificationProperties.getProofTtlSeconds());
        // proof token 只证明“当前邮箱已验证”，不直接携带可修改的新邮箱。
        return new StudentProfileSecurityVerifyCodeResponse(
                true,
                buildProofToken(new SecurityProofPayload(PURPOSE_EMAIL_CURRENT_TOKEN, userId, user.email(), expiresAt.getEpochSecond())),
                TimePayloads.toEpochMillis(expiresAt)
        );
    }

    public StudentProfileSecurityVerifyCodeResponse verifyPasswordResetCode(long userId, String code) {
        AppUser user = requireUser(userId);
        verifyCode(userId, PURPOSE_PASSWORD_RESET_CODE, user.email(), code);
        Instant expiresAt = Instant.now().plusSeconds(emailVerificationProperties.getProofTtlSeconds());
        return new StudentProfileSecurityVerifyCodeResponse(
                true,
                buildProofToken(new SecurityProofPayload(PURPOSE_PASSWORD_RESET_TOKEN, userId, user.email(), expiresAt.getEpochSecond())),
                TimePayloads.toEpochMillis(expiresAt)
        );
    }

    public StudentProfileEmailChangeResponse changeEmail(long userId, StudentProfileEmailChangeRequest request) {
        AppUser user = requireUser(userId);
        // 换绑前必须先校验当前邮箱 proof，再校验新邮箱验证码。
        assertProofToken(userId, PURPOSE_EMAIL_CURRENT_TOKEN, user.email(), request.currentEmailVerificationToken());
        String normalizedNewEmail = normalizeEmail(request.newEmail());
        SystemUserPolicy.assertPublicEmailAllowed(normalizedNewEmail);
        if (normalizedNewEmail.equals(user.email())) {
            throw new ApiException("BIZ-1402", "new email must be different from current email", HttpStatus.BAD_REQUEST);
        }
        userRepository.findByEmail(normalizedNewEmail)
                .filter(existing -> existing.id() != userId)
                .ifPresent(existing -> {
                    throw new ApiException("BIZ-1403", "email already exists", HttpStatus.CONFLICT);
                });
        verifyCode(userId, PURPOSE_EMAIL_NEW_CODE, normalizedNewEmail, request.newEmailCode());
        userRepository.updateEmail(userId, normalizedNewEmail);
        return new StudentProfileEmailChangeResponse(true, normalizedNewEmail);
    }

    public StudentProfilePasswordChangeResponse changePassword(long userId, StudentProfilePasswordChangeRequest request) {
        AppUser user = requireUser(userId);
        // 改密只接受 verifyPasswordResetCode 生成的短期 proof token。
        assertProofToken(userId, PURPOSE_PASSWORD_RESET_TOKEN, user.email(), request.passwordResetToken());
        userRepository.updatePasswordHash(userId, passwordEncoder.encode(request.newPassword()));
        return new StudentProfilePasswordChangeResponse(true);
    }

    private AppUser requireUser(long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ApiException("BIZ-1002", "user not found", HttpStatus.NOT_FOUND));
    }

    private String resolveNewEmailForSend(AppUser user, String newEmail) {
        if (!StringUtils.hasText(newEmail)) {
            throw new ApiException("BIZ-1404", "new email is required", HttpStatus.BAD_REQUEST);
        }
        String normalizedNewEmail = normalizeEmail(newEmail);
        SystemUserPolicy.assertPublicEmailAllowed(normalizedNewEmail);
        if (normalizedNewEmail.equals(user.email())) {
            throw new ApiException("BIZ-1402", "new email must be different from current email", HttpStatus.BAD_REQUEST);
        }
        userRepository.findByEmail(normalizedNewEmail)
                .filter(existing -> existing.id() != user.id())
                .ifPresent(existing -> {
                    throw new ApiException("BIZ-1403", "email already exists", HttpStatus.CONFLICT);
                });
        return normalizedNewEmail;
    }

    private DeliveryResult dispatchVerificationCode(long userId, String purpose, String stage, String targetEmail) {
        return dispatchVerificationCode(userId, purpose, stage, targetEmail, PasswordResetEmailScene.PROFILE_CENTER);
    }

    private DeliveryResult dispatchVerificationCode(long userId, String purpose, String stage, String targetEmail, PasswordResetEmailScene scene) {
        Instant now = Instant.now();
        StudentProfileSecurityCodeRepository.SecurityCodeRow currentRecord = securityCodeRepository
                .findByIdentity(userId, purpose, targetEmail)
                .orElse(null);
        if (currentRecord != null && currentRecord.nextSendAt().isAfter(now)) {
            // 同一用户、用途和邮箱维度限流，防止验证码邮件被重复刷。
            long waitSeconds = Math.max(1L, Duration.between(now, currentRecord.nextSendAt()).getSeconds());
            throw new ApiException(
                    "BIZ-1405",
                    "verification code resend is too frequent, retry after " + waitSeconds + " seconds",
                    HttpStatus.TOO_MANY_REQUESTS
            );
        }

        boolean demoBypassEnabled = demoModeProperties.isPasswordResetBypassEnabled();
        String code = demoBypassEnabled ? demoModeProperties.resolveFixedEmailCode() : generateCode();
        Instant expiresAt = now.plusSeconds(emailVerificationProperties.getCodeTtlSeconds());
        Instant nextSendAt = now.plusSeconds(emailVerificationProperties.getResendCooldownSeconds());
        DeliveryChannel deliveryChannel = deliverCode(stage, targetEmail, code, scene, demoBypassEnabled);

        // 数据库只保存验证码 hash，mock 环境的明文只通过响应 debugCode 暴露给前端。
        securityCodeRepository.saveOrUpdate(userId, purpose, targetEmail, hashCode(code), expiresAt, nextSendAt);
        return new DeliveryResult(
                deliveryChannel.channel(),
                expiresAt,
                nextSendAt,
                deliveryChannel.debugCode()
        );
    }

    private DeliveryChannel deliverCode(String stage, String targetEmail, String code, PasswordResetEmailScene scene, boolean demoBypassEnabled) {
        if (demoBypassEnabled) {
            log.info("profile security code(demo): stage={}, targetEmail={}, code={}", stage, targetEmail, code);
            return new DeliveryChannel("DEMO", code);
        }
        if (!emailVerificationProperties.isReady()) {
            // 本地/答辩环境没有 Resend 配置时走 MOCK，避免阻断安全流程演示。
            log.info("profile security code(mock): stage={}, targetEmail={}, code={}", stage, targetEmail, code);
            return new DeliveryChannel("MOCK", code);
        }

        ResendEmailRequest requestBody = new ResendEmailRequest(
                buildFromAddress(),
                List.of(targetEmail),
                buildSubject(stage, scene),
                buildHtmlContent(stage, code, scene),
                buildTextContent(stage, code, scene)
        );

        WebClient client = webClientBuilder.build();
        ResendHttpResponse response;
        try {
            response = client.post()
                    .uri(emailVerificationProperties.getResendApiUrl().trim())
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + emailVerificationProperties.getResendApiKey().trim())
                    .bodyValue(requestBody)
                    .exchangeToMono(clientResponse ->
                            clientResponse.bodyToMono(String.class)
                                    .defaultIfEmpty("")
                                    .map(body -> new ResendHttpResponse(
                                            clientResponse.statusCode(),
                                            clientResponse.headers().contentType().orElse(null),
                                            body
                                    )))
                    .timeout(Duration.ofSeconds(10))
                    .block();
        } catch (Exception ex) {
            log.warn("profile security resend request failed: type={}, message={}", ex.getClass().getName(), ex.getMessage(), ex);
            throw new ApiException("BIZ-1406", "email verification service is unavailable", HttpStatus.SERVICE_UNAVAILABLE);
        }

        if (response == null || response.statusCode().isError()) {
            log.warn(
                    "profile security resend failed: stage={}, targetEmail={}, status={}, body={}",
                    stage,
                    targetEmail,
                    response == null ? "null" : response.statusCode().value(),
                    response == null ? "" : response.body()
            );
            throw new ApiException("BIZ-1406", "email verification service is unavailable", HttpStatus.SERVICE_UNAVAILABLE);
        }

        return new DeliveryChannel("EMAIL", null);
    }

    private void verifyCode(long userId, String purpose, String targetEmail, String code) {
        String normalizedCode = code == null ? "" : code.trim();
        if (!CODE_PATTERN.matcher(normalizedCode).matches()) {
            throw new ApiException("BIZ-1407", "verification code is invalid or expired", HttpStatus.BAD_REQUEST);
        }

        StudentProfileSecurityCodeRepository.SecurityCodeRow record = securityCodeRepository
                .findByIdentity(userId, purpose, targetEmail)
                .orElse(null);
        if (record == null) {
            throw new ApiException("BIZ-1407", "verification code is invalid or expired", HttpStatus.BAD_REQUEST);
        }

        Instant now = Instant.now();
        if (record.expiresAt().isBefore(now)) {
            securityCodeRepository.deleteByIdentity(userId, purpose, targetEmail);
            throw new ApiException("BIZ-1407", "verification code is invalid or expired", HttpStatus.BAD_REQUEST);
        }

        String expectedCodeHash = hashCode(normalizedCode);
        if (!demoModeProperties.isPasswordResetBypassEnabled() && !MessageDigest.isEqual(
                record.codeHash().getBytes(StandardCharsets.UTF_8),
                expectedCodeHash.getBytes(StandardCharsets.UTF_8)
        )) {
            throw new ApiException("BIZ-1407", "verification code is invalid or expired", HttpStatus.BAD_REQUEST);
        }

        // 验证码成功后立即删除，保证一次性使用。
        securityCodeRepository.deleteByIdentity(userId, purpose, targetEmail);
    }

    private void assertProofToken(long userId, String expectedPurpose, String expectedEmail, String verificationToken) {
        if (!StringUtils.hasText(verificationToken)) {
            throw new ApiException("BIZ-1408", "verification token is required", HttpStatus.BAD_REQUEST);
        }

        String[] tokenParts = verificationToken.split("\\.");
        if (tokenParts.length != 2) {
            throw new ApiException("BIZ-1409", "verification token is invalid or expired", HttpStatus.BAD_REQUEST);
        }

        String payloadSegment = tokenParts[0];
        String signatureSegment = tokenParts[1];
        String expectedSignature = buildProofSignature(payloadSegment);
        // proof token 使用 HMAC 校验，不落库也能判断是否被篡改。
        if (!MessageDigest.isEqual(
                signatureSegment.getBytes(StandardCharsets.UTF_8),
                expectedSignature.getBytes(StandardCharsets.UTF_8)
        )) {
            throw new ApiException("BIZ-1409", "verification token is invalid or expired", HttpStatus.BAD_REQUEST);
        }

        SecurityProofPayload payload = readProofPayload(payloadSegment);
        if (!expectedPurpose.equals(payload.purpose())
                || payload.userId() != userId
                || !expectedEmail.equals(payload.email())
                || Instant.now().getEpochSecond() > payload.expiresAtEpochSecond()) {
            throw new ApiException("BIZ-1409", "verification token is invalid or expired", HttpStatus.BAD_REQUEST);
        }
    }

    private String normalizeStage(String stage) {
        return stage == null ? "" : stage.trim().toUpperCase();
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase();
    }

    private String generateCode() {
        return "%06d".formatted(RANDOM.nextInt(1_000_000));
    }

    private String hashCode(String code) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HEX_FORMAT.formatHex(digest.digest(code.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("failed to hash verification code", ex);
        }
    }

    private String buildProofToken(SecurityProofPayload payload) {
        try {
            String payloadJson = objectMapper.writeValueAsString(payload);
            String payloadSegment = Base64.getUrlEncoder()
                    .withoutPadding()
                    .encodeToString(payloadJson.getBytes(StandardCharsets.UTF_8));
            return payloadSegment + "." + buildProofSignature(payloadSegment);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("failed to build verification token", ex);
        }
    }

    private SecurityProofPayload readProofPayload(String payloadSegment) {
        try {
            byte[] payloadBytes = Base64.getUrlDecoder().decode(payloadSegment);
            return objectMapper.readValue(payloadBytes, SecurityProofPayload.class);
        } catch (Exception ex) {
            throw new ApiException("BIZ-1409", "verification token is invalid or expired", HttpStatus.BAD_REQUEST);
        }
    }

    private String buildProofSignature(String payloadSegment) {
        byte[] digest = hmacSha256(payloadSegment, jwtProperties.getSecret());
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
    }

    private byte[] hmacSha256(String message, String secret) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_SHA256));
            return mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ex) {
            throw new IllegalStateException("failed to sign verification token", ex);
        }
    }

    private String buildFromAddress() {
        String configuredFromEmail = emailVerificationProperties.getResendFromEmail().trim();
        if (configuredFromEmail.contains("<") && configuredFromEmail.contains(">")) {
            return configuredFromEmail;
        }
        return BRAND_NAME + " <" + configuredFromEmail + ">";
    }

    private String buildSubject(String stage, PasswordResetEmailScene scene) {
        return switch (stage) {
            case STAGE_CURRENT -> "【" + BRAND_NAME + "】验证当前邮箱，继续修改账号信息";
            case STAGE_NEW -> "【" + BRAND_NAME + "】验证新邮箱，完成邮箱换绑";
            case STAGE_PASSWORD_RESET -> scene == PasswordResetEmailScene.LOGIN_RECOVERY
                    ? "【" + BRAND_NAME + "】找回登录密码验证码"
                    : "【" + BRAND_NAME + "】验证身份，继续修改登录密码";
            default -> "【" + BRAND_NAME + "】安全验证码";
        };
    }

    private String buildHtmlContent(String stage, String code, PasswordResetEmailScene scene) {
        String actionLabel = switch (stage) {
            case STAGE_CURRENT -> "验证当前邮箱";
            case STAGE_NEW -> "验证新邮箱";
            case STAGE_PASSWORD_RESET -> scene == PasswordResetEmailScene.LOGIN_RECOVERY ? "找回登录密码" : "验证身份并修改密码";
            default -> "完成安全校验";
        };
        String sceneBadge = switch (stage) {
            case STAGE_PASSWORD_RESET -> scene == PasswordResetEmailScene.LOGIN_RECOVERY ? "登录页密码找回" : "资料中心安全验证";
            default -> "资料中心安全验证";
        };
        String sceneDescription = switch (stage) {
            case STAGE_PASSWORD_RESET -> scene == PasswordResetEmailScene.LOGIN_RECOVERY
                    ? "你正在通过登录页找回密码，请在页面中输入下面的 6 位验证码，继续完成密码重置。"
                    : "你正在进行资料中心安全操作，请在页面中输入下面的 6 位验证码，继续完成后续步骤。";
            default -> "你正在进行资料中心安全操作，请在页面中输入下面的 6 位验证码，继续完成后续步骤。";
        };
        String sceneFooter = switch (stage) {
            case STAGE_PASSWORD_RESET -> scene == PasswordResetEmailScene.LOGIN_RECOVERY
                    ? "如果这不是你的操作，请忽略本邮件，并检查账号安全。"
                    : "如果这不是你的操作，请忽略本邮件，并及时修改账号密码。";
            default -> "如果这不是你的操作，请忽略本邮件，并及时修改账号密码。";
        };
        long ttlMinutes = Math.max(1L, emailVerificationProperties.getCodeTtlSeconds() / 60L);
        return """
                <!DOCTYPE html>
                <html lang="zh-CN">
                  <body style="margin:0;padding:24px;background:#f8fafc;color:#0f172a;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI','PingFang SC','Microsoft YaHei',sans-serif;">
                    <div style="max-width:560px;margin:0 auto;background:#ffffff;border-radius:24px;padding:32px;box-shadow:0 24px 64px rgba(15,23,42,0.12);">
                      <div style="display:inline-block;padding:8px 14px;border-radius:999px;background:#eef2ff;color:#4338ca;font-size:12px;font-weight:700;">
                        %s
                      </div>
                      <h1 style="margin:18px 0 10px;font-size:28px;line-height:1.35;">%s</h1>
                      <p style="margin:0 0 24px;font-size:15px;line-height:1.8;color:#475569;">
                        %s
                      </p>
                      <div style="padding:24px;border-radius:20px;background:#f8fafc;border:1px solid #e2e8f0;text-align:center;">
                        <div style="font-size:34px;font-weight:800;letter-spacing:8px;color:#0f172a;">%s</div>
                        <p style="margin:14px 0 0;font-size:13px;color:#64748b;">验证码将在 %d 分钟内有效</p>
                      </div>
                      <p style="margin:24px 0 0;font-size:13px;line-height:1.8;color:#64748b;">
                        %s
                      </p>
                    </div>
                  </body>
                </html>
                """.formatted(sceneBadge, actionLabel, sceneDescription, code, ttlMinutes, sceneFooter);
    }

    private String buildTextContent(String stage, String code, PasswordResetEmailScene scene) {
        String actionLabel = switch (stage) {
            case STAGE_CURRENT -> "验证当前邮箱";
            case STAGE_NEW -> "验证新邮箱";
            case STAGE_PASSWORD_RESET -> scene == PasswordResetEmailScene.LOGIN_RECOVERY ? "找回登录密码" : "验证身份并修改密码";
            default -> "完成安全校验";
        };
        String scenePageLabel = switch (stage) {
            case STAGE_PASSWORD_RESET -> scene == PasswordResetEmailScene.LOGIN_RECOVERY ? "登录页找回密码弹窗" : "资料中心页面";
            default -> "资料中心页面";
        };
        String sceneFooter = switch (stage) {
            case STAGE_PASSWORD_RESET -> scene == PasswordResetEmailScene.LOGIN_RECOVERY
                    ? "如果这不是你的操作，请忽略本邮件，并检查账号安全。"
                    : "如果这不是你的操作，请忽略本邮件。";
            default -> "如果这不是你的操作，请忽略本邮件。";
        };
        long ttlMinutes = Math.max(1L, emailVerificationProperties.getCodeTtlSeconds() / 60L);
        return """
                %s

                你的验证码是：%s

                请回到%s输入验证码，继续%s。验证码将在 %d 分钟内有效。
                %s
                """.formatted(BRAND_NAME, code, scenePageLabel, actionLabel, ttlMinutes, sceneFooter);
    }

    private record DeliveryChannel(
            String channel,
            String debugCode
    ) {
    }

    private record DeliveryResult(
            String deliveryChannel,
            Instant expiresAt,
            Instant nextSendAt,
            String debugCode
    ) {
    }

    private record SecurityProofPayload(
            String purpose,
            long userId,
            String email,
            long expiresAtEpochSecond
    ) {
    }

    private record ResendEmailRequest(
            String from,
            List<String> to,
            String subject,
            String html,
            String text
    ) {
    }

    private record ResendHttpResponse(
            HttpStatusCode statusCode,
            MediaType contentType,
            String body
    ) {
    }
}
