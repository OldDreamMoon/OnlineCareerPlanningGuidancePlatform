package com.bishe.server.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Geetest + 邮箱验证码注册闭环测试。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthCaptchaIntegrationTest {

    private static final String CAPTCHA_ID = "test-geetest-captcha-id";
    private static final String CAPTCHA_KEY = "test-geetest-captcha-key";
    private static final Pattern EMAIL_CODE_PATTERN = Pattern.compile("\\b(\\d{6})\\b");
    private static final AtomicReference<Map<String, String>> LAST_VALIDATE_REQUEST = new AtomicReference<>(Map.of());
    private static final AtomicReference<String> LAST_RESEND_REQUEST_BODY = new AtomicReference<>("");

    private static HttpServer mockServer;
    private static int mockPort;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @DynamicPropertySource
    static void registerCaptchaProperties(DynamicPropertyRegistry registry) throws IOException {
        ensureMockStarted();
        registry.add("spring.datasource.url", () -> "jdbc:h2:mem:bishe_captcha;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE");
        registry.add("auth.captcha.geetest.enabled", () -> true);
        registry.add("auth.captcha.geetest.captcha-id", () -> CAPTCHA_ID);
        registry.add("auth.captcha.geetest.captcha-key", () -> CAPTCHA_KEY);
        registry.add("auth.captcha.geetest.verify-url", () -> "http://127.0.0.1:" + mockPort + "/validate");
        registry.add("auth.captcha.geetest.timeout-ms", () -> 2000);
        registry.add("auth.captcha.geetest.proof-ttl-seconds", () -> 600);
        registry.add("auth.email.resend-api-key", () -> "test-resend-api-key");
        registry.add("auth.email.resend-from-email", () -> "noreply@example.com");
        registry.add("auth.email.resend-api-url", () -> "http://127.0.0.1:" + mockPort + "/emails");
        registry.add("auth.email.code-ttl-seconds", () -> 600);
        registry.add("auth.email.resend-cooldown-seconds", () -> 1);
        registry.add("auth.email.proof-ttl-seconds", () -> 900);
    }

    @AfterAll
    static void stopMockServer() {
        if (mockServer != null) {
            mockServer.stop(0);
            mockServer = null;
        }
    }

    @Test
    void captchaConfig_shouldExposePublicConfig() throws Exception {
        mockMvc.perform(get("/api/v1/auth/captcha/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.enabled").value(true))
                .andExpect(jsonPath("$.data.provider").value("GEETEST_V4"))
                .andExpect(jsonPath("$.data.captchaId").isString())
                .andExpect(jsonPath("$.data.product").value("bind"));
    }

    @Test
    void sendEmailCode_shouldRejectInvalidCaptchaProofWhenEnabled() throws Exception {
        String sendCodeBody = """
                {
                  "email": "captcha-missing@example.com",
                  "captchaVerificationToken": "invalid-token"
                }
                """;

        mockMvc.perform(post("/api/v1/auth/email/send-code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(sendCodeBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("AUTH-1008"));
    }

    @Test
    void sendPasswordResetCode_shouldRejectInvalidCaptchaProofWhenEnabled() throws Exception {
        insertActiveUser("captcha-reset@example.com", "CaptchaResetUser", "Passw0rd!");

        String sendCodeBody = """
                {
                  "email": "captcha-reset@example.com",
                  "captchaVerificationToken": "invalid-token"
                }
                """;

        mockMvc.perform(post("/api/v1/auth/password/reset/send-code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(sendCodeBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("AUTH-1008"));
    }

    @Test
    void verifyCaptcha_thenSendCode_thenVerifyEmail_thenRegister_shouldSucceed() throws Exception {
        String verifyBody = """
                {
                  "email": "captcha-success@example.com",
                  "lotNumber": "lot-20260317-001",
                  "captchaOutput": "captcha-output-demo",
                  "passToken": "pass-token-demo",
                  "genTime": "1710662400"
                }
                """;

        MvcResult verifyResult = mockMvc.perform(post("/api/v1/auth/captcha/geetest/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(verifyBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.valid").value(true))
                .andExpect(jsonPath("$.data.verificationToken").isString())
                .andReturn();

        JsonNode verifyJson = objectMapper.readTree(verifyResult.getResponse().getContentAsString());
        String captchaVerificationToken = verifyJson.path("data").path("verificationToken").asText();
        assertThat(captchaVerificationToken).isNotBlank();

        Map<String, String> geetestRequest = LAST_VALIDATE_REQUEST.get();
        assertThat(geetestRequest.get("captcha_id")).isEqualTo(CAPTCHA_ID);
        assertThat(geetestRequest.get("lot_number")).isEqualTo("lot-20260317-001");
        assertThat(geetestRequest.get("captcha_output")).isEqualTo("captcha-output-demo");
        assertThat(geetestRequest.get("pass_token")).isEqualTo("pass-token-demo");
        assertThat(geetestRequest.get("gen_time")).isEqualTo("1710662400");
        assertThat(geetestRequest.get("sign_token")).matches("^[0-9a-f]{64}$");
        assertThat(geetestRequest.get("sign_token")).isEqualTo(signLotNumber("lot-20260317-001"));

        String sendCodeBody = """
                {
                  "email": "captcha-success@example.com",
                  "captchaVerificationToken": "%s"
                }
                """.formatted(captchaVerificationToken);

        mockMvc.perform(post("/api/v1/auth/email/send-code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(sendCodeBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.sent").value(true))
                .andExpect(jsonPath("$.data.email").value("captcha-success@example.com"));

        String emailCode = extractEmailCode(LAST_RESEND_REQUEST_BODY.get());
        assertThat(emailCode).matches("^\\d{6}$");

        String verifyEmailCodeBody = """
                {
                  "email": "captcha-success@example.com",
                  "code": "%s"
                }
                """.formatted(emailCode);

        MvcResult verifyEmailCodeResult = mockMvc.perform(post("/api/v1/auth/email/verify-code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(verifyEmailCodeBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.verified").value(true))
                .andReturn();

        JsonNode verifyEmailJson = objectMapper.readTree(verifyEmailCodeResult.getResponse().getContentAsString());
        String emailVerificationToken = verifyEmailJson.path("data").path("verificationToken").asText();
        assertThat(emailVerificationToken).isNotBlank();

        String registerBody = """
                {
                  "role": "STUDENT",
                  "email": "captcha-success@example.com",
                  "password": "Passw0rd!",
                  "displayName": "CaptchaSuccess",
                  "emailVerificationToken": "%s"
                }
                """.formatted(emailVerificationToken);

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.userId").isNumber());
    }

    @Test
    void verifyCaptcha_thenSendPasswordResetCode_shouldSucceed() throws Exception {
        insertActiveUser("captcha-reset-success@example.com", "CaptchaResetSuccess", "Passw0rd!");

        String verifyBody = """
                {
                  "email": "captcha-reset-success@example.com",
                  "lotNumber": "lot-20260317-002",
                  "captchaOutput": "captcha-output-reset",
                  "passToken": "pass-token-reset",
                  "genTime": "1710662401"
                }
                """;

        MvcResult verifyResult = mockMvc.perform(post("/api/v1/auth/captcha/geetest/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(verifyBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andReturn();

        JsonNode verifyJson = objectMapper.readTree(verifyResult.getResponse().getContentAsString());
        String captchaVerificationToken = verifyJson.path("data").path("verificationToken").asText();
        assertThat(captchaVerificationToken).isNotBlank();

        mockMvc.perform(post("/api/v1/auth/password/reset/send-code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "captcha-reset-success@example.com",
                                  "captchaVerificationToken": "%s"
                                }
                                """.formatted(captchaVerificationToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.sent").value(true))
                .andExpect(jsonPath("$.data.targetEmail").value("captcha-reset-success@example.com"));
    }

    private static void ensureMockStarted() throws IOException {
        if (mockServer != null) {
            return;
        }

        mockServer = HttpServer.create(new InetSocketAddress(0), 0);
        mockPort = mockServer.getAddress().getPort();
        mockServer.createContext("/validate", AuthCaptchaIntegrationTest::handleValidateRequest);
        mockServer.createContext("/emails", AuthCaptchaIntegrationTest::handleResendRequest);
        mockServer.start();
    }

    private static void handleValidateRequest(HttpExchange exchange) throws IOException {
        Map<String, String> params = new LinkedHashMap<>();
        params.putAll(parseQuery(exchange.getRequestURI().getRawQuery()));
        params.putAll(parseQuery(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)));
        LAST_VALIDATE_REQUEST.set(params);

        String expectedSignToken = signLotNumber(params.getOrDefault("lot_number", ""));
        boolean signValid = expectedSignToken.equals(params.get("sign_token"));
        int status = signValid ? 200 : 400;
        byte[] responseBody = (signValid
                ? """
                {"result":"success","reason":"mock geetest accepted"}
                """
                : """
                {"result":"fail","reason":"illegal sign_token"}
                """).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, responseBody.length);
        exchange.getResponseBody().write(responseBody);
        exchange.close();
    }

    private static void handleResendRequest(HttpExchange exchange) throws IOException {
        LAST_RESEND_REQUEST_BODY.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
        byte[] responseBody = """
                {"id":"email_test_001"}
                """.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, responseBody.length);
        exchange.getResponseBody().write(responseBody);
        exchange.close();
    }

    private static Map<String, String> parseQuery(String query) {
        Map<String, String> values = new LinkedHashMap<>();
        if (query == null || query.isBlank()) {
            return values;
        }

        String[] pairs = query.split("&");
        for (String pair : pairs) {
            if (pair.isBlank()) {
                continue;
            }

            String[] keyValue = pair.split("=", 2);
            String key = URLDecoder.decode(keyValue[0], StandardCharsets.UTF_8);
            String value = keyValue.length > 1
                    ? URLDecoder.decode(keyValue[1], StandardCharsets.UTF_8)
                    : "";
            values.put(key, value);
        }
        return values;
    }

    private void insertActiveUser(String email, String displayName, String password) {
        jdbcTemplate.update(
                """
                INSERT INTO users(
                    email, password_hash, role, tier, status, display_name, real_name,
                    last_login_at, is_deleted, created_at, updated_at
                ) VALUES (?, ?, 'STUDENT', 'FREE', 'ACTIVE', ?, ?, NULL, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """,
                email,
                passwordEncoder.encode(password),
                displayName,
                displayName
        );
    }

    private static String signLotNumber(String lotNumber) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(CAPTCHA_KEY.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal(lotNumber.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception ex) {
            throw new IllegalStateException("failed to sign lot number for test", ex);
        }
    }

    private static String extractEmailCode(String requestBody) {
        Matcher matcher = EMAIL_CODE_PATTERN.matcher(requestBody);
        if (!matcher.find()) {
            throw new IllegalStateException("failed to extract email verification code from resend request body: " + requestBody);
        }
        return matcher.group(1);
    }
}
