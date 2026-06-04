package com.bishe.server.auth;

import com.bishe.server.auth.captcha.AuthCaptchaConfigCacheService;
import com.bishe.server.auth.model.AppUser;
import com.bishe.server.auth.model.UserAccountStatus;
import com.bishe.server.auth.model.UserRole;
import com.bishe.server.security.JwtService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 鉴权最小闭环测试：注册、登录、当前用户、RBAC。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AuthCaptchaConfigCacheService authCaptchaConfigCacheService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        authCaptchaConfigCacheService.evictNow();
    }

    @Test
    void captchaConfig_shouldExposePublicConfig() throws Exception {
        mockMvc.perform(get("/api/v1/auth/captcha/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.provider").value("GEETEST_V4"))
                .andExpect(jsonPath("$.data.product").value("bind"))
                .andExpect(jsonPath("$.data.enabled").isBoolean())
                .andExpect(jsonPath("$.data.proofTtlSeconds").isNumber());
    }

    @Test
    void registerLoginMe_shouldWork() throws Exception {
        String registerBody = """
                {
                  "role": "STUDENT",
                  "email": "alice@example.com",
                  "password": "Passw0rd!",
                  "displayName": "Alice"
                }
                """;

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.message").value("registered"))
                .andExpect(jsonPath("$.data.userId").isNumber());

        String accessToken = loginAndGetAccessToken("alice@example.com", "Passw0rd!");

        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.role").value("STUDENT"))
                .andExpect(jsonPath("$.data.displayName").value("Alice"));
    }

    @Test
    void mentorAccessStudentEndpoint_shouldReturn403() throws Exception {
        String registerBody = """
                {
                  "role": "MENTOR",
                  "email": "mentor@example.com",
                  "password": "Passw0rd!",
                  "displayName": "Mentor"
                }
                """;

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody))
                .andExpect(status().isOk());

        String mentorToken = loginAndGetAccessToken("mentor@example.com", "Passw0rd!");

        mockMvc.perform(post("/api/v1/ai/ping")
                        .header("Authorization", "Bearer " + mentorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH-1004"));
    }

    @Test
    void unauthenticatedMe_shouldReturn401() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH-1003"));
    }

    @Test
    void refresh_shouldIssueNewAccessToken() throws Exception {
        String registerBody = """
                {
                  "role": "STUDENT",
                  "email": "refresh@example.com",
                  "password": "Passw0rd!",
                  "displayName": "RefreshUser"
                }
                """;

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody))
                .andExpect(status().isOk());

        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"refresh@example.com\",\"password\":\"Passw0rd!\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andReturn();

        JsonNode loginPayload = objectMapper.readTree(loginResult.getResponse().getContentAsString());
        String refreshToken = loginPayload.path("data").path("refreshToken").asText();
        assertThat(refreshToken).isNotBlank();

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refreshToken + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.message").value("refreshed"))
                .andExpect(jsonPath("$.data.accessToken").isString())
                .andExpect(jsonPath("$.data.refreshToken").isString())
                .andExpect(jsonPath("$.data.role").value("STUDENT"));
    }

    @Test
    void passwordResetEndpoints_shouldAllowAnonymousForgotPasswordFlow() throws Exception {
        String email = "forgot-password@example.com";
        String originalPassword = "Passw0rd!";
        String newPassword = "NewPassw0rd1";

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "role": "STUDENT",
                                  "email": "%s",
                                  "password": "%s",
                                  "displayName": "ForgotPasswordUser"
                                }
                                """.formatted(email, originalPassword)))
                .andExpect(status().isOk());

        MvcResult sendCodeResult = mockMvc.perform(post("/api/v1/auth/password/reset/send-code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s"
                                }
                                """.formatted(email)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.sent").value(true))
                .andExpect(jsonPath("$.data.targetEmail").value(email))
                .andExpect(jsonPath("$.data.debugCode").isString())
                .andReturn();

        JsonNode sendCodeJson = objectMapper.readTree(sendCodeResult.getResponse().getContentAsString());
        String debugCode = sendCodeJson.path("data").path("debugCode").asText();
        assertThat(debugCode).matches("^\\d{6}$");

        MvcResult verifyCodeResult = mockMvc.perform(post("/api/v1/auth/password/reset/verify-code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "code": "%s"
                                }
                                """.formatted(email, debugCode)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.verified").value(true))
                .andExpect(jsonPath("$.data.verificationToken").isString())
                .andReturn();

        JsonNode verifyCodeJson = objectMapper.readTree(verifyCodeResult.getResponse().getContentAsString());
        String passwordResetToken = verifyCodeJson.path("data").path("verificationToken").asText();
        assertThat(passwordResetToken).isNotBlank();

        mockMvc.perform(post("/api/v1/auth/password/reset/change")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "passwordResetToken": "%s",
                                  "newPassword": "%s"
                                }
                                """.formatted(email, passwordResetToken, newPassword)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.updated").value(true));

        loginAndGetAccessToken(email, newPassword);
    }

    @Test
    void proxiedSameOriginLogin_shouldNotBeRejectedAsCors() throws Exception {
        String registerBody = """
                {
                  "role": "STUDENT",
                  "email": "proxy-login@example.com",
                  "password": "Passw0rd!",
                  "displayName": "ProxyLogin"
                }
                """;

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/login")
                        .header("Origin", "https://bishe.yanachan.de")
                        .header("X-Forwarded-Proto", "https")
                        .header("X-Forwarded-Host", "bishe.yanachan.de")
                        .header("X-Forwarded-Port", "443")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"proxy-login@example.com\",\"password\":\"Passw0rd!\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.accessToken").isString())
                .andExpect(jsonPath("$.data.refreshToken").isString());
    }

    @Test
    void register_shouldRejectReservedSystemEmailDomain() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "role": "MENTOR",
                                  "email": "reserved-bot@system.local",
                                  "password": "Passw0rd!",
                                  "displayName": "ReservedBot"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("AUTH-1017"));
    }

    @Test
    void systemAccount_shouldBeBlockedFromLoginAndSessionEndpoints() throws Exception {
        String email = "auth-system-bot@system.local";
        long userId = insertSystemUser(email, "AuthSystemBot", "Passw0rd!");
        String passwordHash = jdbcTemplate.queryForObject("SELECT password_hash FROM users WHERE id = ?", String.class, userId);
        assertThat(passwordHash).isNotBlank();
        AppUser user = new AppUser(
                userId,
                email,
                passwordHash,
                UserRole.MENTOR,
                "FREE",
                UserAccountStatus.ACTIVE,
                "AuthSystemBot",
                Instant.now()
        );

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "password": "Passw0rd!"
                                }
                                """.formatted(email)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH-1002"));

        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + jwtService.generateAccessToken(user)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH-1003"));

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "refreshToken": "%s"
                                }
                                """.formatted(jwtService.generateRefreshToken(user))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH-1003"));
    }

    private long insertSystemUser(String email, String displayName, String password) {
        jdbcTemplate.update(
                """
                INSERT INTO users(
                    email, password_hash, role, tier, status, display_name, real_name,
                    last_login_at, is_deleted, created_at, updated_at
                ) VALUES (?, ?, 'MENTOR', 'FREE', 'ACTIVE', ?, ?, NULL, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """,
                email,
                passwordEncoder.encode(password),
                displayName,
                displayName
        );
        Long userId = jdbcTemplate.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
        assertThat(userId).isNotNull();
        return userId;
    }

    private String loginAndGetAccessToken(String email, String password) throws Exception {
        String loginBody = String.format(
                "{\"email\":\"%s\",\"password\":\"%s\"}",
                email,
                password
        );

        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andReturn();

        JsonNode jsonNode = objectMapper.readTree(result.getResponse().getContentAsString());
        String accessToken = jsonNode.path("data").path("accessToken").asText();
        assertThat(accessToken).isNotBlank();
        return accessToken;
    }
}
