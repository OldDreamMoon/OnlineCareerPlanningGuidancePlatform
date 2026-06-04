package com.bishe.server.auth.email;

import com.bishe.server.auth.SystemUserPolicy;
import com.bishe.server.auth.captcha.GeetestCaptchaService;
import com.bishe.server.auth.dto.SendEmailVerificationCodeResponse;
import com.bishe.server.auth.dto.VerifyEmailVerificationCodeResponse;
import com.bishe.server.auth.repository.UserRepository;
import com.bishe.server.auth.service.AuthException;
import com.bishe.server.common.TimePayloads;
import com.bishe.server.demo.DemoModeProperties;
import com.bishe.server.security.JwtProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
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
import java.util.List;
import java.util.regex.Pattern;

/**
 * 注册邮箱验证码发送、校验与短时注册凭证服务。
 */
@Service
public class EmailVerificationService {

    private static final String BRAND_NAME = "大学生职业规划平台";
    private static final Logger log = LoggerFactory.getLogger(EmailVerificationService.class);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Pattern CODE_PATTERN = Pattern.compile("^\\d{6}$");
    private static final String HMAC_SHA256 = "HmacSHA256";
    private static final String PROVIDER = "RESEND_EMAIL_CODE";
    private static final String DEMO_PROVIDER = "DEMO_EMAIL_CODE";

    private final EmailVerificationProperties properties;
    private final GeetestCaptchaService geetestCaptchaService;
    private final UserRepository userRepository;
    private final DemoModeProperties demoModeProperties;
    private final JwtProperties jwtProperties;
    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper;
    private final EmailVerificationCodeStoreService codeStoreService;

    public EmailVerificationService(
            EmailVerificationProperties properties,
            GeetestCaptchaService geetestCaptchaService,
            UserRepository userRepository,
            DemoModeProperties demoModeProperties,
            JwtProperties jwtProperties,
            WebClient.Builder webClientBuilder,
            ObjectMapper objectMapper,
            EmailVerificationCodeStoreService codeStoreService
    ) {
        this.properties = properties;
        this.geetestCaptchaService = geetestCaptchaService;
        this.userRepository = userRepository;
        this.demoModeProperties = demoModeProperties;
        this.jwtProperties = jwtProperties;
        this.webClientBuilder = webClientBuilder;
        this.objectMapper = objectMapper;
        this.codeStoreService = codeStoreService;
    }

    public SendEmailVerificationCodeResponse sendCode(String email, String captchaVerificationToken) {
        String normalizedEmail = normalizeEmail(email);
        SystemUserPolicy.assertPublicEmailAllowed(normalizedEmail);
        if (userRepository.findByEmail(normalizedEmail).isPresent()) {
            throw AuthException.emailExists();
        }
        if (!geetestCaptchaService.isReady()) {
            throw AuthException.captchaUnavailable();
        }
        geetestCaptchaService.assertRegistrationProof(normalizedEmail, captchaVerificationToken);

        Instant now = Instant.now();
        EmailVerificationCodeStoreService.EmailCodeRecord currentRecord = codeStoreService.get(normalizedEmail).orElse(null);
        if (currentRecord != null && currentRecord.nextSendAt().isAfter(now)) {
            long waitSeconds = Math.max(1L, Duration.between(now, currentRecord.nextSendAt()).getSeconds());
            throw AuthException.emailVerificationSendTooFrequent(waitSeconds);
        }

        boolean demoBypassEnabled = demoModeProperties.isRegisterEmailVerificationBypassEnabled();
        String code = demoBypassEnabled ? demoModeProperties.resolveFixedEmailCode() : generateCode();
        String provider = PROVIDER;
        String debugCode = null;
        if (demoBypassEnabled) {
            provider = DEMO_PROVIDER;
            debugCode = code;
            log.info("register email verification(demo): email={}, code={}", normalizedEmail, code);
        } else {
            requireEmailVerificationReady();
            sendWithResend(normalizedEmail, code);
        }

        Instant expiresAt = now.plusSeconds(properties.getCodeTtlSeconds());
        Instant nextSendAt = now.plusSeconds(properties.getResendCooldownSeconds());
        codeStoreService.put(normalizedEmail, hashCode(code), expiresAt, nextSendAt);
        return new SendEmailVerificationCodeResponse(
                true,
                provider,
                normalizedEmail,
                TimePayloads.toEpochMillis(expiresAt),
                TimePayloads.toEpochMillis(nextSendAt),
                debugCode
        );
    }

    public VerifyEmailVerificationCodeResponse verifyCode(String email, String code) {
        boolean demoBypassEnabled = demoModeProperties.isRegisterEmailVerificationBypassEnabled();
        if (!demoBypassEnabled) {
            requireEmailVerificationReady();
        }
        String normalizedEmail = normalizeEmail(email);
        String normalizedCode = code == null ? "" : code.trim();
        if (!CODE_PATTERN.matcher(normalizedCode).matches()) {
            throw AuthException.emailVerificationCodeInvalidOrExpired();
        }

        EmailVerificationCodeStoreService.EmailCodeRecord record = codeStoreService.get(normalizedEmail).orElse(null);
        if (record == null) {
            throw AuthException.emailVerificationCodeInvalidOrExpired();
        }

        Instant now = Instant.now();
        if (record.expiresAt().isBefore(now)) {
            codeStoreService.delete(normalizedEmail);
            throw AuthException.emailVerificationCodeInvalidOrExpired();
        }

        if (!demoBypassEnabled && !MessageDigest.isEqual(record.codeHash(), hashCode(normalizedCode))) {
            throw AuthException.emailVerificationCodeInvalidOrExpired();
        }

        codeStoreService.delete(normalizedEmail);
        Instant expiresAt = now.plusSeconds(properties.getProofTtlSeconds());
        String verificationToken = buildProofToken(new EmailVerificationProofPayload(PROVIDER, normalizedEmail, expiresAt.getEpochSecond()));
        return new VerifyEmailVerificationCodeResponse(true, PROVIDER, verificationToken, TimePayloads.toEpochMillis(expiresAt));
    }

    public void assertEmailVerificationProof(String email, String verificationToken) {
        if (!properties.isReady()) {
            return;
        }
        if (!StringUtils.hasText(verificationToken)) {
            throw AuthException.emailVerificationRequired();
        }

        String[] tokenParts = verificationToken.split("\\.");
        if (tokenParts.length != 2) {
            throw AuthException.emailVerificationTokenInvalidOrExpired();
        }

        String payloadSegment = tokenParts[0];
        String signatureSegment = tokenParts[1];
        String expectedSignature = buildProofSignature(payloadSegment);
        if (!MessageDigest.isEqual(
                signatureSegment.getBytes(StandardCharsets.UTF_8),
                expectedSignature.getBytes(StandardCharsets.UTF_8)
        )) {
            throw AuthException.emailVerificationTokenInvalidOrExpired();
        }

        EmailVerificationProofPayload payload = readProofPayload(payloadSegment);
        if (!PROVIDER.equals(payload.provider())) {
            throw AuthException.emailVerificationTokenInvalidOrExpired();
        }
        if (!normalizeEmail(email).equals(payload.email())) {
            throw AuthException.emailVerificationTokenInvalidOrExpired();
        }
        if (Instant.now().getEpochSecond() > payload.expiresAtEpochSecond()) {
            throw AuthException.emailVerificationTokenInvalidOrExpired();
        }
    }

    private void requireEmailVerificationReady() {
        if (!properties.isReady()) {
            throw AuthException.emailVerificationUnavailable();
        }
    }

    private void sendWithResend(String email, String code) {
        WebClient client = webClientBuilder.build();
        ResendEmailRequest requestBody = new ResendEmailRequest(
                buildFromAddress(),
                List.of(email),
                "【" + BRAND_NAME + "】继续完成注册 | 你的邮箱验证码",
                buildHtmlContent(code),
                buildTextContent(code)
        );

        ResendHttpResponse response;
        try {
            response = client.post()
                    .uri(properties.getResendApiUrl().trim())
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + properties.getResendApiKey().trim())
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
            log.warn(
                    "Resend email send request failed due to transport error: type={}, message={}",
                    ex.getClass().getName(),
                    ex.getMessage(),
                    ex
            );
            throw AuthException.emailVerificationUnavailable();
        }

        if (response == null) {
            log.warn("Resend email send returned null response");
            throw AuthException.emailVerificationUnavailable();
        }

        if (response.statusCode().isError()) {
            String reason = extractResendReason(response.body());
            log.warn(
                    "Resend email send failed with status={}, contentType={}, reason={}, body={}",
                    response.statusCode().value(),
                    response.contentType(),
                    reason,
                    sanitizeForLog(response.body())
            );
            throw AuthException.emailVerificationUnavailable();
        }

        log.info(
                "Resend email send succeeded: status={}, requestId={}, contentType={}",
                response.statusCode().value(),
                extractResendRequestId(response.body()),
                response.contentType()
        );
    }

    private String buildProofToken(EmailVerificationProofPayload payload) {
        String payloadJson;
        try {
            payloadJson = objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            throw AuthException.emailVerificationUnavailable();
        }

        String payloadSegment = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(payloadJson.getBytes(StandardCharsets.UTF_8));
        return payloadSegment + "." + buildProofSignature(payloadSegment);
    }

    private EmailVerificationProofPayload readProofPayload(String payloadSegment) {
        try {
            byte[] payloadBytes = Base64.getUrlDecoder().decode(payloadSegment);
            return objectMapper.readValue(payloadBytes, EmailVerificationProofPayload.class);
        } catch (Exception ex) {
            throw AuthException.emailVerificationTokenInvalidOrExpired();
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
            throw AuthException.emailVerificationUnavailable();
        }
    }

    private byte[] hashCode(String code) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(code.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ex) {
            throw AuthException.emailVerificationUnavailable();
        }
    }

    private String buildFromAddress() {
        String configuredFromEmail = properties.getResendFromEmail().trim();
        if (configuredFromEmail.contains("<") && configuredFromEmail.contains(">")) {
            return configuredFromEmail;
        }
        return BRAND_NAME + " <" + configuredFromEmail + ">";
    }

    private String buildHtmlContent(String code) {
        long ttlMinutes = Math.max(1L, properties.getCodeTtlSeconds() / 60L);
        return """
                <!DOCTYPE html>
                <html lang="zh-CN">
                  <body style="margin:0;padding:0;background:#f3f7fb;color:#0f172a;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI','PingFang SC','Hiragino Sans GB','Microsoft YaHei',sans-serif;">
                    <div style="display:none;max-height:0;overflow:hidden;opacity:0;color:transparent;">
                      你的注册验证码是 %s，%d 分钟内有效，请尽快完成邮箱验证。
                    </div>

                    <table role="presentation" cellpadding="0" cellspacing="0" border="0" width="100{pct}" style="width:100{pct};background:#f3f7fb;padding:24px 0;">
                      <tr>
                        <td align="center">
                          <table role="presentation" cellpadding="0" cellspacing="0" border="0" width="100{pct}" style="width:100{pct};max-width:560px;">
                            <tr>
                              <td style="padding:0 12px;">
                                <table role="presentation" cellpadding="0" cellspacing="0" border="0" width="100{pct}" style="width:100{pct};background:#ffffff;border-radius:20px;overflow:hidden;border:1px solid #e2e8f0;">
                                  <tr>
                                    <td style="padding:0;background:linear-gradient(135deg,#0f172a,#1d4ed8,#38bdf8);">
                                      <table role="presentation" cellpadding="0" cellspacing="0" border="0" width="100{pct}" style="width:100{pct};">
                                        <tr>
                                          <td style="padding:24px 24px 22px;">
                                            <div style="display:inline-block;padding:6px 12px;border-radius:999px;background:rgba(255,255,255,0.14);font-size:11px;font-weight:700;letter-spacing:0.5px;color:#dbeafe;">
                                              %s
                                            </div>
                                            <h1 style="margin:16px 0 8px;font-size:22px;line-height:1.45;font-weight:800;color:#ffffff;">
                                              完成邮箱验证，继续创建账号
                                            </h1>
                                            <p style="margin:0;font-size:14px;line-height:1.8;color:rgba(255,255,255,0.9);">
                                              你正在进行平台注册流程，请在注册页面中输入下面的 6 位验证码，继续完成下一步身份配置。
                                            </p>
                                          </td>
                                        </tr>
                                      </table>
                                    </td>
                                  </tr>

                                  <tr>
                                    <td style="padding:24px 24px 12px;">
                                      <table role="presentation" cellpadding="0" cellspacing="0" border="0" width="100{pct}" style="width:100{pct};border:1px solid #dbeafe;border-radius:18px;background:linear-gradient(180deg,#f8fbff,#eef4ff);">
                                        <tr>
                                          <td align="center" style="padding:22px 18px;">
                                            <p style="margin:0 0 10px;font-size:12px;font-weight:700;letter-spacing:0.5px;color:#475569;">
                                              邮箱验证码
                                            </p>
                                            <div style="display:inline-block;max-width:100{pct};padding:14px 18px;border-radius:16px;background:#ffffff;border:1px solid #c7d2fe;font-size:28px;font-weight:800;letter-spacing:6px;line-height:1.2;color:#1e1b4b;box-sizing:border-box;">
                                              %s
                                            </div>
                                            <p style="margin:14px 0 0;font-size:13px;line-height:1.75;color:#475569;">
                                              该验证码将在 <span style="font-weight:800;color:#0f172a;">%d 分钟</span> 后失效，请尽快完成验证。
                                            </p>
                                          </td>
                                        </tr>
                                      </table>
                                    </td>
                                  </tr>

                                  <tr>
                                    <td style="padding:0 24px 10px;">
                                      <table role="presentation" cellpadding="0" cellspacing="0" border="0" width="100{pct}" style="width:100{pct};">
                                        <tr>
                                          <td style="padding-bottom:10px;">
                                            <div style="border-radius:16px;border:1px solid #e2e8f0;background:#f8fafc;padding:16px 16px;">
                                              <p style="margin:0 0 8px;font-size:13px;font-weight:800;color:#0f172a;">
                                                接下来这样操作
                                              </p>
                                              <p style="margin:0;font-size:13px;line-height:1.8;color:#475569;">
                                                1. 返回注册页面并输入验证码<br />
                                                2. 点击“继续下一步”完成邮箱验证<br />
                                                3. 继续补充学生、导师或企业身份信息
                                              </p>
                                            </div>
                                          </td>
                                        </tr>
                                        <tr>
                                          <td>
                                            <div style="border-radius:16px;border:1px solid #fed7aa;background:#fff7ed;padding:16px 16px;">
                                              <p style="margin:0 0 8px;font-size:13px;font-weight:800;color:#9a3412;">
                                                安全提醒
                                              </p>
                                              <p style="margin:0;font-size:13px;line-height:1.8;color:#7c2d12;">
                                                验证码仅用于本次注册验证，请不要泄露给任何人。如果这不是你的操作，请直接忽略本邮件。
                                              </p>
                                            </div>
                                          </td>
                                        </tr>
                                      </table>
                                    </td>
                                  </tr>

                                  <tr>
                                    <td style="padding:8px 24px 24px;">
                                      <p style="margin:0;font-size:12px;line-height:1.8;color:#94a3b8;">
                                        这是一封系统自动发送的验证邮件，请勿直接回复。若你在几分钟内未收到邮件，请检查垃圾邮件箱，或返回注册页面重新发送验证码。
                                      </p>
                                    </td>
                                  </tr>
                                </table>
                              </td>
                            </tr>

                            <tr>
                              <td align="center" style="padding:14px 20px 0;">
                                <p style="margin:0;font-size:11px;line-height:1.8;color:#94a3b8;">
                                  © 2026 %s · AI 赋能的职业成长与就业指导服务
                                </p>
                              </td>
                            </tr>
                          </table>
                        </td>
                      </tr>
                    </table>
                  </body>
                </html>
                """.formatted(code, ttlMinutes, BRAND_NAME, code, ttlMinutes, BRAND_NAME).replace("{pct}", "%");
    }

    private String buildTextContent(String code) {
        long ttlMinutes = Math.max(1L, properties.getCodeTtlSeconds() / 60L);
        return """
                %s 邮箱验证

                你的注册验证码是：%s

                请返回注册页面输入该验证码，继续完成账号创建。验证码将在 %d 分钟内有效。
                如果这不是你的操作，请忽略本邮件，且不要将验证码透露给任何人。
                """.formatted(BRAND_NAME, code, ttlMinutes);
    }

    private String generateCode() {
        return "%06d".formatted(RANDOM.nextInt(1_000_000));
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase();
    }

    private String extractResendReason(String responseBody) {
        if (!StringUtils.hasText(responseBody)) {
            return null;
        }
        try {
            JsonNode payload = objectMapper.readTree(responseBody);
            String message = payload.path("message").asText(null);
            if (StringUtils.hasText(message)) {
                return message;
            }
            JsonNode errorNode = payload.path("error");
            if (errorNode.isObject()) {
                String detail = errorNode.path("message").asText(null);
                if (StringUtils.hasText(detail)) {
                    return detail;
                }
            }
            return payload.path("name").asText(null);
        } catch (Exception ex) {
            return null;
        }
    }

    private String extractResendRequestId(String responseBody) {
        if (!StringUtils.hasText(responseBody)) {
            return null;
        }
        try {
            JsonNode payload = objectMapper.readTree(responseBody);
            return payload.path("id").asText(null);
        } catch (Exception ex) {
            return null;
        }
    }

    private String sanitizeForLog(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.replaceAll("[\\r\\n]+", " ").trim();
    }

    private record EmailVerificationProofPayload(
            String provider,
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
