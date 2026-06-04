package com.bishe.server.profile;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 导师资料中心闭环测试。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MentorProfileCenterIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void mentorProfileCenter_shouldSupportExtendedProfileUpdate() throws Exception {
        registerUser("MENTOR", "mentor-profile-center@example.com", "Passw0rd!", "林导师");
        String mentorToken = loginAndGetAccessToken("mentor-profile-center@example.com", "Passw0rd!");

        mockMvc.perform(get("/api/v1/mentor/profile")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + mentorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.displayName").value("林导师"))
                .andExpect(jsonPath("$.data.realName").value("林导师"))
                .andExpect(jsonPath("$.data.avatarConfigured").value(false))
                .andExpect(jsonPath("$.data.serviceScenes").isArray());

        String updateBody = """
                {
                  "jobTitle": "高级前端架构师",
                  "expertiseTags": ["React性能优化", "前端工程化", "大厂面试辅导"],
                  "serviceScenes": ["简历诊断", "项目表达", "模拟面试复盘"],
                  "bio": "5 年大厂前端开发经验，擅长从 0 到 1 搭建前端工程化体系。",
                  "suitableFor": "适合希望冲刺中大厂面试、梳理项目表达的前端同学。",
                  "notSuitableFor": "不适合零基础从语法开始的系统教学诉求。",
                  "prepMaterials": "请提前准备简历、目标岗位 JD 和当前最卡的问题。",
                  "replyRhythm": "工作日通常在晚上集中回复，周末可安排语音咨询。",
                  "priceFen": 19900,
                  "available": true
                }
                """;

        mockMvc.perform(put("/api/v1/mentor/profile")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + mentorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.jobTitle").value("高级前端架构师"))
                .andExpect(jsonPath("$.data.expertiseTags[0]").value("React性能优化"))
                .andExpect(jsonPath("$.data.serviceScenes[1]").value("项目表达"))
                .andExpect(jsonPath("$.data.suitableFor").value("适合希望冲刺中大厂面试、梳理项目表达的前端同学。"))
                .andExpect(jsonPath("$.data.prepMaterials").value("请提前准备简历、目标岗位 JD 和当前最卡的问题。"))
                .andExpect(jsonPath("$.data.replyRhythm").value("工作日通常在晚上集中回复，周末可安排语音咨询。"))
                .andExpect(jsonPath("$.data.priceFen").value(19900))
                .andExpect(jsonPath("$.data.available").value(true));

        mockMvc.perform(get("/api/v1/mentor/profile")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + mentorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.realName").value("林导师"))
                .andExpect(jsonPath("$.data.suitableFor").value(containsString("中大厂面试")))
                .andExpect(jsonPath("$.data.notSuitableFor").value(containsString("零基础")))
                .andExpect(jsonPath("$.data.prepMaterials").value(containsString("目标岗位 JD")))
                .andExpect(jsonPath("$.data.replyRhythm").value(containsString("工作日")))
                .andExpect(jsonPath("$.data.avatarConfigured").value(false));
    }

    @Test
    void mentorProfileCenter_shouldSupportAvatarUploadAndPublicRead() throws Exception {
        registerUser("MENTOR", "mentor-avatar-center@example.com", "Passw0rd!", "导师头像");
        String mentorToken = loginAndGetAccessToken("mentor-avatar-center@example.com", "Passw0rd!");
        long mentorUserId = findUserIdByEmail("mentor-avatar-center@example.com");

        MockMultipartFile avatar = new MockMultipartFile(
                "file",
                "mentor-avatar.png",
                "image/png",
                createAvatarPngBytes(new Color(16, 185, 129), "导")
        );

        mockMvc.perform(multipart("/api/v1/mentor/profile/avatar")
                        .file(avatar)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + mentorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("avatar uploaded"))
                .andExpect(jsonPath("$.data.uploaded").value(true))
                .andExpect(jsonPath("$.data.contentType").value("image/jpeg"))
                .andExpect(jsonPath("$.data.avatarUrl").value(containsString("/api/v1/mentors/" + mentorUserId + "/avatar")))
                .andExpect(jsonPath("$.data.updatedAt").isNumber());

        mockMvc.perform(get("/api/v1/mentor/profile")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + mentorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.avatarConfigured").value(true))
                .andExpect(jsonPath("$.data.avatarContentType").value("image/jpeg"))
                .andExpect(jsonPath("$.data.avatarUpdatedAt").isNumber())
                .andExpect(jsonPath("$.data.avatarUrl").value(containsString("/api/v1/mentors/" + mentorUserId + "/avatar")));

        mockMvc.perform(get("/api/v1/mentors/{mentorUserId}/avatar", mentorUserId))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/jpeg"))
                .andExpect(result -> assertThat(result.getResponse().getContentAsByteArray()).isNotEmpty());
    }

    @Test
    void publicMentorEndpoints_shouldHideSystemAccounts() throws Exception {
        registerUser("STUDENT", "mentor-list-student@example.com", "Passw0rd!", "MentorListStudent");
        String studentToken = loginAndGetAccessToken("mentor-list-student@example.com", "Passw0rd!");
        long systemMentorUserId = insertApprovedSystemMentor("mentor-bot@system.local", "AI 助手");

        mockMvc.perform(get("/api/v1/mentors")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + studentToken)
                        .param("keyword", "AI 助手"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0));

        mockMvc.perform(get("/api/v1/mentors/{mentorUserId}", systemMentorUserId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + studentToken))
                .andExpect(status().isNotFound());
    }

    private void registerUser(String role, String email, String password, String displayName) throws Exception {
        String body = String.format(
                "{" +
                        "\"role\":\"%s\"," +
                        "\"email\":\"%s\"," +
                        "\"password\":\"%s\"," +
                        "\"displayName\":\"%s\"" +
                        "}",
                role,
                email,
                password,
                displayName
        );

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"));
    }

    private String loginAndGetAccessToken(String email, String password) throws Exception {
        String loginBody = String.format(
                "{" +
                        "\"email\":\"%s\"," +
                        "\"password\":\"%s\"" +
                        "}",
                email,
                password
        );

        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode jsonNode = objectMapper.readTree(result.getResponse().getContentAsString());
        String accessToken = jsonNode.path("data").path("accessToken").asText();
        assertThat(accessToken).isNotBlank();
        return accessToken;
    }

    private long findUserIdByEmail(String email) {
        Long userId = jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE email = ?",
                Long.class,
                email
        );
        assertThat(userId).isNotNull();
        return userId;
    }

    private long insertApprovedSystemMentor(String email, String displayName) {
        jdbcTemplate.update(
                """
                INSERT INTO users(
                    email, password_hash, role, tier, status, display_name, real_name,
                    last_login_at, is_deleted, created_at, updated_at
                ) VALUES (?, ?, 'MENTOR', 'FREE', 'ACTIVE', ?, ?, NULL, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """,
                email,
                "!system-account-disabled!",
                displayName,
                displayName
        );
        Long userId = jdbcTemplate.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
        assertThat(userId).isNotNull();
        jdbcTemplate.update(
                """
                INSERT INTO mentor_profiles(
                    user_id, company_name, job_title, avatar_url, expertise_tags, service_scenes,
                    bio, price_fen, is_available, approval_status, total_orders, avg_rating, created_at, updated_at
                ) VALUES (?, ?, ?, NULL, ?, ?, ?, ?, 1, 'APPROVED', 0, 0.00, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """,
                userId,
                "系统账号",
                "AI 评论账号",
                "自动首评",
                "社区自动回复",
                "仅用于验证系统账号不会出现在导师公开列表",
                0
        );
        return userId;
    }

    private byte[] createAvatarPngBytes(Color backgroundColor, String label) throws Exception {
        BufferedImage image = new BufferedImage(320, 320, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(backgroundColor);
            graphics.fillRect(0, 0, 320, 320);
            graphics.setColor(Color.WHITE);
            graphics.setFont(new Font("SansSerif", Font.BOLD, 168));
            FontMetrics metrics = graphics.getFontMetrics();
            int textWidth = metrics.stringWidth(label);
            int textHeight = metrics.getAscent() - metrics.getDescent();
            graphics.drawString(label, (320 - textWidth) / 2, (320 + textHeight) / 2);
        } finally {
            graphics.dispose();
        }

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ImageIO.write(image, "png", outputStream);
        return outputStream.toByteArray();
    }
}
