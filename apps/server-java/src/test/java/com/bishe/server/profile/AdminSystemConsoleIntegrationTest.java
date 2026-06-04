package com.bishe.server.profile;

import com.bishe.server.adminconsole.AdminConsoleSnapshotCacheService;
import com.bishe.server.featureflag.FeatureFlagCacheService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 管理控制台聚合快照缓存回归测试。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AdminSystemConsoleIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AdminConsoleSnapshotCacheService adminConsoleSnapshotCacheService;

    @Autowired
    private FeatureFlagCacheService featureFlagCacheService;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM feature_flags");
        featureFlagCacheService.evictNow();
        adminConsoleSnapshotCacheService.evictNow();
    }

    @Test
    void consoleSnapshot_shouldUseRedisCacheAndRefreshAfterFeatureFlagUpdate() throws Exception {
        String adminToken = loginAndGetAccessToken("admin@test.local", "Admin123456!");

        mockMvc.perform(get("/api/v1/admin/system/console-snapshot")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.featureFlags[?(@.key=='payment.mode')].currentValue").value(org.hamcrest.Matchers.contains("MOCK")));

        upsertFeatureFlag("payment.mode", "SANDBOX", 1L);

        mockMvc.perform(get("/api/v1/admin/system/console-snapshot")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.featureFlags[?(@.key=='payment.mode')].currentValue").value(org.hamcrest.Matchers.contains("MOCK")));

        mockMvc.perform(post("/api/v1/admin/feature-flags")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "key": "payment.mode",
                                  "value": "SANDBOX"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("feature flag updated"))
                .andExpect(jsonPath("$.data.currentValue").value("SANDBOX"));

        mockMvc.perform(get("/api/v1/admin/system/console-snapshot")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.featureFlags[?(@.key=='payment.mode')].currentValue").value(org.hamcrest.Matchers.contains("SANDBOX")));
    }

    private void upsertFeatureFlag(String key, String value, long updatedBy) {
        int updated = jdbcTemplate.update(
                """
                UPDATE feature_flags
                   SET flag_value = ?,
                       description = ?,
                       updated_by = ?,
                       updated_at = CURRENT_TIMESTAMP
                 WHERE flag_key = ?
                """,
                value,
                "控制台缓存直写测试",
                updatedBy,
                key
        );
        if (updated == 0) {
            jdbcTemplate.update(
                    """
                    INSERT INTO feature_flags(flag_key, flag_value, description, updated_by, created_at, updated_at)
                    VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """,
                    key,
                    value,
                    "控制台缓存直写测试",
                    updatedBy
            );
        }
    }

    private String loginAndGetAccessToken(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{" +
                                "\"email\":\"" + email + "\"," +
                                "\"password\":\"" + password + "\"" +
                                "}"))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data").path("accessToken").asText();
    }
}
