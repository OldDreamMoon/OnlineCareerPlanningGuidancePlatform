package com.bishe.server.auth.captcha;

import com.bishe.server.auth.dto.AuthCaptchaConfigResponse;
import com.bishe.server.auth.dto.GeetestVerifyRequest;
import com.bishe.server.auth.dto.GeetestVerifyResponse;
import com.bishe.server.auth.service.AuthException;
import com.bishe.server.common.TimePayloads;
import com.bishe.server.demo.DemoModeProperties;
import com.bishe.server.security.JwtProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

/**
 * 极验 v4 二次校验与短时注册凭证服务。
 */
@Service
public class GeetestCaptchaService {

    private static final Logger log = LoggerFactory.getLogger(GeetestCaptchaService.class);
    private static final String PROVIDER = "GEETEST_V4";
    private static final String PRODUCT = "bind";
    private static final String HMAC_SHA256 = "HmacSHA256";

    private final GeetestCaptchaProperties properties;
    private final JwtProperties jwtProperties;
    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper;
    private final AuthCaptchaConfigCacheService authCaptchaConfigCacheService;
    private final DemoModeProperties demoModeProperties;

    public GeetestCaptchaService(
            GeetestCaptchaProperties properties,
            JwtProperties jwtProperties,
            WebClient.Builder webClientBuilder,
            ObjectMapper objectMapper,
            AuthCaptchaConfigCacheService authCaptchaConfigCacheService,
            DemoModeProperties demoModeProperties
    ) {
        this.properties = properties;
        this.jwtProperties = jwtProperties;
        this.webClientBuilder = webClientBuilder;
        this.objectMapper = objectMapper;
        this.authCaptchaConfigCacheService = authCaptchaConfigCacheService;
        this.demoModeProperties = demoModeProperties;
    }

    public AuthCaptchaConfigResponse getPublicConfig() {
        return authCaptchaConfigCacheService.getPublicConfig(() -> new AuthCaptchaConfigResponse(
                properties.isReady(),
                PROVIDER,
                properties.isReady() ? properties.getCaptchaId() : null,
                PRODUCT,
                properties.getProofTtlSeconds(),
                demoModeProperties.isEnabled(),
                demoModeProperties.isRegisterEmailVerificationBypassEnabled(),
                demoModeProperties.isPasswordResetBypassEnabled(),
                demoModeProperties.isCertificationBypassEnabled()
        ));
    }

    public boolean isReady() {
        return properties.isReady();
    }

    public GeetestVerifyResponse verifyAndIssueToken(GeetestVerifyRequest request) {
        if (!properties.isReady()) {
            throw AuthException.captchaUnavailable();
        }

        String normalizedEmail = normalizeEmail(request.email());
        String reason = verifyWithGeetest(request);
        Instant expiresAt = Instant.now().plusSeconds(properties.getProofTtlSeconds());
        String verificationToken = buildProofToken(new CaptchaProofPayload(PROVIDER, normalizedEmail, expiresAt.getEpochSecond()));
        return new GeetestVerifyResponse(true, PROVIDER, verificationToken, TimePayloads.toEpochMillis(expiresAt), reason);
    }

    public void assertRegistrationProof(String email, String verificationToken) {
        if (!properties.isReady()) {
            return;
        }
        if (!StringUtils.hasText(verificationToken)) {
            throw AuthException.captchaRequired();
        }

        String[] tokenParts = verificationToken.split("\\.");
        if (tokenParts.length != 2) {
            throw AuthException.captchaInvalidOrExpired();
        }

        String payloadSegment = tokenParts[0];
        String signatureSegment = tokenParts[1];
        String expectedSignature = buildProofSignature(payloadSegment);
        if (!MessageDigest.isEqual(
                signatureSegment.getBytes(StandardCharsets.UTF_8),
                expectedSignature.getBytes(StandardCharsets.UTF_8)
        )) {
            throw AuthException.captchaInvalidOrExpired();
        }

        CaptchaProofPayload payload = readProofPayload(payloadSegment);
        if (!PROVIDER.equals(payload.provider())) {
            throw AuthException.captchaInvalidOrExpired();
        }
        if (!normalizeEmail(email).equals(payload.email())) {
            throw AuthException.captchaInvalidOrExpired();
        }
        if (Instant.now().getEpochSecond() > payload.expiresAtEpochSecond()) {
            throw AuthException.captchaInvalidOrExpired();
        }
    }

    private String verifyWithGeetest(GeetestVerifyRequest request) {
        String signToken = buildGeetestSignToken(request.lotNumber().trim());
        String verifyUrl = properties.getVerifyUrl().trim() + "?captcha_id=" + properties.getCaptchaId().trim();
        WebClient client = webClientBuilder.build();

        GeetestHttpResponse response;
        try {
            response = client.post()
                    .uri(verifyUrl)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(BodyInserters.fromFormData("lot_number", request.lotNumber().trim())
                            .with("captcha_output", request.captchaOutput().trim())
                            .with("pass_token", request.passToken().trim())
                            .with("gen_time", request.genTime().trim())
                            .with("sign_token", signToken))
                    .exchangeToMono(clientResponse ->
                            clientResponse.bodyToMono(byte[].class)
                                    .defaultIfEmpty(new byte[0])
                                    .map(bodyBytes -> new GeetestHttpResponse(
                                            clientResponse.statusCode(),
                                            clientResponse.headers().contentType().orElse(null),
                                            decodeResponseBody(bodyBytes, clientResponse.headers().contentType().orElse(null))
                                    )))
                    .timeout(Duration.ofMillis(properties.getTimeoutMs()))
                    .block();
        } catch (Exception ex) {
            log.warn(
                    "Geetest validate request failed due to transport error: type={}, message={}",
                    ex.getClass().getName(),
                    ex.getMessage(),
                    ex
            );
            throw AuthException.captchaUnavailable();
        }

        if (response == null) {
            log.warn("Geetest validate returned null response");
            throw AuthException.captchaUnavailable();
        }

        if (response.statusCode().isError()) {
            String reason = extractGeetestReason(response.body());
            log.warn(
                    "Geetest validate request failed with status={}, contentType={}, reason={}, body={}",
                    response.statusCode().value(),
                    response.contentType(),
                    reason,
                    sanitizeForLog(response.body())
            );
            if (StringUtils.hasText(reason)) {
                throw AuthException.captchaVerifyFailed(reason);
            }
            throw AuthException.captchaUnavailable();
        }

        if (!StringUtils.hasText(response.body())) {
            log.warn(
                    "Geetest validate returned empty body with status={}, contentType={}",
                    response.statusCode().value(),
                    response.contentType()
            );
            throw AuthException.captchaUnavailable();
        }

        JsonNode payload;
        try {
            payload = objectMapper.readTree(response.body());
        } catch (Exception ex) {
            log.warn(
                    "Geetest validate returned non-json body with status={}, contentType={}, body={}",
                    response.statusCode().value(),
                    response.contentType(),
                    sanitizeForLog(response.body()),
                    ex
            );
            throw AuthException.captchaUnavailable();
        }

        String requestStatus = payload.path("status").asText(null);
        String result = payload.path("result").asText(null);
        String reason = payload.path("reason").asText(null);
        String message = payload.path("msg").asText(null);
        if ("error".equalsIgnoreCase(requestStatus)) {
            String resolvedReason = firstNonBlank(reason, message);
            log.warn(
                    "Geetest validate returned error status: status={}, reason={}, msg={}, body={}",
                    requestStatus,
                    reason,
                    message,
                    sanitizeForLog(response.body())
            );
            if (StringUtils.hasText(resolvedReason)) {
                throw AuthException.captchaVerifyFailed(resolvedReason);
            }
            throw AuthException.captchaUnavailable();
        }
        if (!"success".equalsIgnoreCase(result)) {
            String resolvedReason = firstNonBlank(reason, message);
            log.warn(
                    "Geetest validate rejected request: requestStatus={}, result={}, reason={}, msg={}, body={}",
                    requestStatus,
                    result,
                    reason,
                    message,
                    sanitizeForLog(response.body())
            );
            throw AuthException.captchaVerifyFailed(resolvedReason);
        }
        if (!StringUtils.hasText(reason)) {
            log.info(
                    "Geetest validate succeeded with empty reason: status={}, contentType={}",
                    response.statusCode().value(),
                    response.contentType()
            );
        } else {
            log.info(
                    "Geetest validate succeeded: status={}, reason={}, contentType={}",
                    response.statusCode().value(),
                    reason,
                    response.contentType()
            );
        }
        return StringUtils.hasText(reason) ? reason : "geetest verify success";
    }

    private String buildProofToken(CaptchaProofPayload payload) {
        String payloadJson;
        try {
            payloadJson = objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            throw AuthException.captchaUnavailable();
        }

        String payloadSegment = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(payloadJson.getBytes(StandardCharsets.UTF_8));
        String signatureSegment = buildProofSignature(payloadSegment);
        return payloadSegment + "." + signatureSegment;
    }

    private CaptchaProofPayload readProofPayload(String payloadSegment) {
        try {
            byte[] payloadBytes = Base64.getUrlDecoder().decode(payloadSegment);
            return objectMapper.readValue(payloadBytes, CaptchaProofPayload.class);
        } catch (Exception ex) {
            throw AuthException.captchaInvalidOrExpired();
        }
    }

    private String buildGeetestSignToken(String lotNumber) {
        byte[] digest = hmacSha256(lotNumber, properties.getCaptchaKey().trim());
        return HexFormat.of().formatHex(digest);
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
            throw AuthException.captchaUnavailable();
        }
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase();
    }

    private String extractGeetestReason(String responseBody) {
        if (!StringUtils.hasText(responseBody)) {
            return null;
        }
        try {
            var payload = objectMapper.readTree(responseBody);
            String reason = payload.path("reason").asText(null);
            if (StringUtils.hasText(reason)) {
                return reason;
            }
            String message = payload.path("message").asText(null);
            if (StringUtils.hasText(message)) {
                return message;
            }
            return payload.path("msg").asText(null);
        } catch (Exception ex) {
            return null;
        }
    }

    private String decodeResponseBody(byte[] bodyBytes, MediaType contentType) {
        if (bodyBytes == null || bodyBytes.length == 0) {
            return "";
        }
        Charset charset = contentType != null && contentType.getCharset() != null
                ? contentType.getCharset()
                : StandardCharsets.UTF_8;
        return new String(bodyBytes, charset);
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private String sanitizeForLog(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.replaceAll("[\\r\\n]+", " ").trim();
    }

    private record CaptchaProofPayload(
            String provider,
            String email,
            long expiresAtEpochSecond
    ) {
    }

    private record GeetestHttpResponse(
            HttpStatusCode statusCode,
            MediaType contentType,
            String body
    ) {
    }
}
