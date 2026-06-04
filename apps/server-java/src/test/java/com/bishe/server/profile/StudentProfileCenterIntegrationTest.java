package com.bishe.server.profile;

import com.bishe.server.profile.service.StudentPortraitSnapshotCacheService;
import com.bishe.server.profile.service.StudentPublicProfileCacheService;
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
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 学生资料中心闭环测试。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class StudentProfileCenterIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private StudentPublicProfileCacheService studentPublicProfileCacheService;

    @Autowired
    private StudentPortraitSnapshotCacheService studentPortraitSnapshotCacheService;

    @Test
    void profileCenter_shouldSupportExtendedProfileAndPrivacyUpdate() throws Exception {
        registerUser("STUDENT", "profile-center@example.com", "Passw0rd!", "ProfileCenter");
        String studentToken = loginAndGetAccessToken("profile-center@example.com", "Passw0rd!");

        mockMvc.perform(get("/api/v1/profiles/students/me")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value("profile-center@example.com"))
                .andExpect(jsonPath("$.data.tier").value("FREE"))
                .andExpect(jsonPath("$.data.completionRate").isNumber())
                .andExpect(jsonPath("$.data.avatar.uploaded").value(false))
                .andExpect(jsonPath("$.data.socialLinks").isArray())
                .andExpect(jsonPath("$.data.privacy.realName.guest").value(false))
                .andExpect(jsonPath("$.data.privacy.realName.platformStudent").value(true))
                .andExpect(jsonPath("$.data.privacy.email.mentor").value(true));

        String updateBody = """
                {
                  "displayName": "前端小林",
                  "realName": "林某某",
                  "jobStatus": "🟢 积极找工作",
                  "schoolName": "华东理工大学",
                  "major": "软件工程",
                  "grade": "2025届",
                  "gpa": "3.8 / 4.0",
                  "targetPosition": "前端开发工程师",
                  "honors": "2023年 国家励志奖学金\\n2022年 蓝桥杯省赛一等奖",
                  "socialLinks": [
                    { "platform": "GITHUB", "value": "lin-frontend" },
                    { "platform": "PORTFOLIO", "value": "lin-blog.tech" },
                    { "platform": "JUEJIN", "value": "lin_frontend" },
                    { "platform": "CSDN", "value": "lin_backend_notes" }
                  ],
                  "phone": "13800138000",
                  "wechat": "lin_frontend",
                  "skillTags": ["React", "TypeScript", "Node.js", "工程化"],
                  "selfIntro": "热爱前端技术，正在系统准备求职。"
                }
                """;

        mockMvc.perform(put("/api/v1/profiles/students/me")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("profile updated"))
                .andExpect(jsonPath("$.data.updated").value(true));

        String privacyBody = """
                {
                  "privacy": {
                    "realName": { "guest": false, "student": true, "platformStudent": false, "mentor": true, "enterprise": true },
                    "jobStatus": { "guest": true, "student": true, "platformStudent": true, "mentor": true, "enterprise": true },
                    "eduInfo": { "guest": true, "student": true, "platformStudent": true, "mentor": true, "enterprise": true },
                    "targetPos": { "guest": false, "student": true, "platformStudent": true, "mentor": true, "enterprise": true },
                    "academic": { "guest": false, "student": true, "platformStudent": false, "mentor": true, "enterprise": false },
                    "skills": { "guest": true, "student": true, "platformStudent": true, "mentor": true, "enterprise": true },
                    "intro": { "guest": true, "student": true, "platformStudent": true, "mentor": true, "enterprise": true },
                    "social": { "guest": false, "student": true, "platformStudent": false, "mentor": true, "enterprise": true },
                    "email": { "guest": false, "student": false, "platformStudent": false, "mentor": true, "enterprise": false },
                    "phone": { "guest": false, "student": false, "platformStudent": false, "mentor": true, "enterprise": false },
                    "wechat": { "guest": false, "student": false, "platformStudent": false, "mentor": true, "enterprise": false },
                    "portrait": { "guest": false, "student": true, "platformStudent": true, "mentor": true, "enterprise": true }
                  }
                }
                """;

        mockMvc.perform(put("/api/v1/profiles/students/me/privacy")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(privacyBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("privacy updated"))
                .andExpect(jsonPath("$.data.updated").value(true));

        mockMvc.perform(get("/api/v1/profiles/students/me")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.displayName").value("前端小林"))
                .andExpect(jsonPath("$.data.realName").value("林某某"))
                .andExpect(jsonPath("$.data.jobStatus").value("🟢 积极找工作"))
                .andExpect(jsonPath("$.data.schoolName").value("华东理工大学"))
                .andExpect(jsonPath("$.data.gpa").value("3.8 / 4.0"))
                .andExpect(jsonPath("$.data.github").value("lin-frontend"))
                .andExpect(jsonPath("$.data.portfolio").value("lin-blog.tech"))
                .andExpect(jsonPath("$.data.socialLinks[0].platform").value("GITHUB"))
                .andExpect(jsonPath("$.data.socialLinks[0].value").value("lin-frontend"))
                .andExpect(jsonPath("$.data.socialLinks[2].platform").value("JUEJIN"))
                .andExpect(jsonPath("$.data.socialLinks[2].value").value("lin_frontend"))
                .andExpect(jsonPath("$.data.phone").value("13800138000"))
                .andExpect(jsonPath("$.data.wechat").value("lin_frontend"))
                .andExpect(jsonPath("$.data.skillTags[0]").value("React"))
                .andExpect(jsonPath("$.data.completionRate").value(100))
                .andExpect(jsonPath("$.data.privacy.targetPos.guest").value(false))
                .andExpect(jsonPath("$.data.privacy.realName.platformStudent").value(false))
                .andExpect(jsonPath("$.data.privacy.targetPos.platformStudent").value(true))
                .andExpect(jsonPath("$.data.privacy.academic.enterprise").value(false))
                .andExpect(jsonPath("$.data.privacy.email.enterprise").value(false))
                .andExpect(jsonPath("$.data.portrait.signalLevel").value("WEAK"))
                .andExpect(jsonPath("$.data.portrait.freshnessLevel").value("RECENT"))
                .andExpect(jsonPath("$.data.portrait.strengthTags[0]").value("目标方向已明确"))
                .andExpect(jsonPath("$.data.portrait.riskTags.length()").value(0))
                .andExpect(jsonPath("$.data.portrait.headline").value(org.hamcrest.Matchers.containsString("前端开发工程师")))
                .andExpect(jsonPath("$.data.portrait.summary").value(org.hamcrest.Matchers.containsString("当前画像聚焦在「前端开发工程师」方向。")))
                .andExpect(jsonPath("$.data.portrait.nextActions.length()").value(org.hamcrest.Matchers.greaterThan(0)))
                .andExpect(jsonPath("$.data.portrait.summaryVersion").value("TEMPLATE_V1"));

        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.displayName").value("前端小林"));

        String schoolNameKey = jdbcTemplate.queryForObject(
                "SELECT school_name_key FROM student_profiles sp JOIN users u ON u.id = sp.user_id WHERE u.email = ?",
                String.class,
                "profile-center@example.com"
        );
        assertThat(schoolNameKey).isEqualTo("华东理工大学");

        String clearRealNameBody = """
                {
                  "displayName": "前端小林",
                  "realName": "",
                  "jobStatus": "🟢 积极找工作",
                  "schoolName": "华东理工大学",
                  "major": "软件工程",
                  "grade": "2025届",
                  "gpa": "3.8 / 4.0",
                  "targetPosition": "前端开发工程师",
                  "honors": "2023年 国家励志奖学金\\n2022年 蓝桥杯省赛一等奖",
                  "socialLinks": [
                    { "platform": "GITHUB", "value": "lin-frontend" },
                    { "platform": "PORTFOLIO", "value": "lin-blog.tech" },
                    { "platform": "JUEJIN", "value": "lin_frontend" },
                    { "platform": "CSDN", "value": "lin_backend_notes" }
                  ],
                  "phone": "13800138000",
                  "wechat": "lin_frontend",
                  "skillTags": ["React", "TypeScript", "Node.js", "工程化"],
                  "selfIntro": "热爱前端技术，正在系统准备求职。"
                }
                """;

        mockMvc.perform(put("/api/v1/profiles/students/me")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(clearRealNameBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.updated").value(true));

        mockMvc.perform(get("/api/v1/profiles/students/me")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.displayName").value("前端小林"))
                .andExpect(jsonPath("$.data.realName").value(nullValue()))
                .andExpect(jsonPath("$.data.schoolName").value("华东理工大学"))
                .andExpect(jsonPath("$.data.completionRate").value(93));
    }

    @Test
    void profileCenter_shouldSupportAvatarUploadReplacementAndPremiumTierBadgeData() throws Exception {
        registerUser("STUDENT", "profile-avatar@example.com", "Passw0rd!", "ProfileAvatar");
        jdbcTemplate.update("UPDATE users SET tier = 'PREMIUM' WHERE email = ?", "profile-avatar@example.com");
        String studentToken = loginAndGetAccessToken("profile-avatar@example.com", "Passw0rd!");

        MockMultipartFile firstAvatar = new MockMultipartFile(
                "file",
                "avatar-a.png",
                "image/png",
                createAvatarPngBytes(new Color(79, 70, 229), "A")
        );
        mockMvc.perform(multipart("/api/v1/profiles/students/me/avatar")
                        .file(firstAvatar)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("avatar uploaded"))
                .andExpect(jsonPath("$.data.uploaded").value(true))
                .andExpect(jsonPath("$.data.contentType").value("image/jpeg"))
                .andExpect(jsonPath("$.data.sizeBytes").isNumber())
                .andExpect(jsonPath("$.data.updatedAt").isNumber());

        MockMultipartFile secondAvatar = new MockMultipartFile(
                "file",
                "avatar-b.png",
                "image/png",
                createAvatarPngBytes(new Color(20, 184, 166), "B")
        );
        mockMvc.perform(multipart("/api/v1/profiles/students/me/avatar")
                        .file(secondAvatar)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.uploaded").value(true));

        mockMvc.perform(get("/api/v1/profiles/students/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tier").value("PREMIUM"))
                .andExpect(jsonPath("$.data.avatar.uploaded").value(true))
                .andExpect(jsonPath("$.data.avatar.contentType").value("image/jpeg"))
                .andExpect(jsonPath("$.data.avatar.updatedAt").isNumber());

        mockMvc.perform(get("/api/v1/profiles/students/me/avatar")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/jpeg"))
                .andExpect(result -> assertThat(result.getResponse().getContentAsByteArray()).isNotEmpty());
    }

    @Test
    void profileCenter_shouldSupportEmailChangeFlowWithMockCode() throws Exception {
        registerUser("STUDENT", "profile-email@example.com", "Passw0rd!", "ProfileEmail");
        String studentToken = loginAndGetAccessToken("profile-email@example.com", "Passw0rd!");

        MvcResult sendCurrentCodeResult = mockMvc.perform(post("/api/v1/profiles/students/me/security/email/send-code")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "stage": "CURRENT"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.deliveryChannel").value("MOCK"))
                .andReturn();
        String currentCode = readDataField(sendCurrentCodeResult, "debugCode");
        assertThat(currentCode).hasSize(6);
        assertSecurityCodeCount("PROFILE_EMAIL_CURRENT_CODE", "profile-email@example.com", 1);

        MvcResult verifyCurrentCodeResult = mockMvc.perform(post("/api/v1/profiles/students/me/security/email/verify-current-code")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code": "%s"
                                }
                                """.formatted(currentCode)))
                .andExpect(status().isOk())
                .andReturn();
        String currentEmailVerificationToken = readDataField(verifyCurrentCodeResult, "verificationToken");
        assertThat(currentEmailVerificationToken).isNotBlank();
        assertSecurityCodeCount("PROFILE_EMAIL_CURRENT_CODE", "profile-email@example.com", 0);

        MvcResult sendNewCodeResult = mockMvc.perform(post("/api/v1/profiles/students/me/security/email/send-code")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "stage": "NEW",
                                  "newEmail": "profile-email-updated@example.com"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.targetEmail").value("profile-email-updated@example.com"))
                .andReturn();
        String newEmailCode = readDataField(sendNewCodeResult, "debugCode");
        assertThat(newEmailCode).hasSize(6);
        assertSecurityCodeCount("PROFILE_EMAIL_NEW_CODE", "profile-email-updated@example.com", 1);

        mockMvc.perform(post("/api/v1/profiles/students/me/security/email/change")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "currentEmailVerificationToken": "%s",
                                  "newEmail": "profile-email-updated@example.com",
                                  "newEmailCode": "%s"
                                }
                                """.formatted(currentEmailVerificationToken, newEmailCode)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("email changed"))
                .andExpect(jsonPath("$.data.updated").value(true))
                .andExpect(jsonPath("$.data.email").value("profile-email-updated@example.com"));
        assertSecurityCodeCount("PROFILE_EMAIL_NEW_CODE", "profile-email-updated@example.com", 0);

        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value("profile-email-updated@example.com"));

        String updatedToken = loginAndGetAccessToken("profile-email-updated@example.com", "Passw0rd!");
        assertThat(updatedToken).isNotBlank();
    }

    @Test
    void profileCenter_shouldSupportPasswordResetFlowWithMockCode() throws Exception {
        registerUser("STUDENT", "profile-password@example.com", "Passw0rd!", "ProfilePassword");
        String studentToken = loginAndGetAccessToken("profile-password@example.com", "Passw0rd!");

        MvcResult sendCodeResult = mockMvc.perform(post("/api/v1/profiles/students/me/security/password/send-code")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.deliveryChannel").value("MOCK"))
                .andReturn();
        String passwordCode = readDataField(sendCodeResult, "debugCode");
        assertThat(passwordCode).hasSize(6);
        assertSecurityCodeCount("PROFILE_PASSWORD_RESET_CODE", "profile-password@example.com", 1);

        MvcResult verifyCodeResult = mockMvc.perform(post("/api/v1/profiles/students/me/security/password/verify-code")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code": "%s"
                                }
                                """.formatted(passwordCode)))
                .andExpect(status().isOk())
                .andReturn();
        String passwordResetToken = readDataField(verifyCodeResult, "verificationToken");
        assertThat(passwordResetToken).isNotBlank();
        assertSecurityCodeCount("PROFILE_PASSWORD_RESET_CODE", "profile-password@example.com", 0);

        mockMvc.perform(post("/api/v1/profiles/students/me/security/password/change")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "passwordResetToken": "%s",
                                  "newPassword": "N3wPassw0rd!"
                                }
                                """.formatted(passwordResetToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("password changed"))
                .andExpect(jsonPath("$.data.updated").value(true));

        String newToken = loginAndGetAccessToken("profile-password@example.com", "N3wPassw0rd!");
        assertThat(newToken).isNotBlank();
    }

    @Test
    void publicProfile_shouldRespectViewerRoleAndSameSchoolVisibility() throws Exception {
        registerUser("STUDENT", "public-target@example.com", "Passw0rd!", "PublicTarget");
        registerUser("STUDENT", "public-same-school@example.com", "Passw0rd!", "PublicSameSchool");
        registerUser("STUDENT", "public-other-school@example.com", "Passw0rd!", "PublicOtherSchool");
        registerUser("MENTOR", "public-mentor@example.com", "Passw0rd!", "PublicMentor");
        registerUser("ENTERPRISE", "public-enterprise@example.com", "Passw0rd!", "PublicEnterprise");

        String targetToken = loginAndGetAccessToken("public-target@example.com", "Passw0rd!");
        String sameSchoolToken = loginAndGetAccessToken("public-same-school@example.com", "Passw0rd!");
        String otherSchoolToken = loginAndGetAccessToken("public-other-school@example.com", "Passw0rd!");
        String mentorToken = loginAndGetAccessToken("public-mentor@example.com", "Passw0rd!");
        String enterpriseToken = loginAndGetAccessToken("public-enterprise@example.com", "Passw0rd!");
        long targetUserId = findUserIdByEmail("public-target@example.com");
        studentPublicProfileCacheService.evictNow(targetUserId);

        mockMvc.perform(put("/api/v1/profiles/students/me")
                        .header("Authorization", "Bearer " + targetToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "displayName": "前端小林",
                                  "realName": "林同学",
                                  "jobStatus": "🟢 积极找工作",
                                  "schoolName": "华东理工大学",
                                  "major": "软件工程",
                                  "grade": "2025届",
                                  "gpa": "3.8 / 4.0",
                                  "targetPosition": "前端开发工程师",
                                  "honors": "国家励志奖学金",
                                  "socialLinks": [
                                    { "platform": "GITHUB", "value": "public-target" },
                                    { "platform": "PORTFOLIO", "value": "public-target.dev" }
                                  ],
                                  "skillTags": ["React", "TypeScript"],
                                  "selfIntro": "持续准备前端求职，也会在社区分享经验。"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.updated").value(true));

        mockMvc.perform(put("/api/v1/profiles/students/me")
                        .header("Authorization", "Bearer " + sameSchoolToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "schoolName": "华东理工大学"
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/v1/profiles/students/me")
                        .header("Authorization", "Bearer " + otherSchoolToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "schoolName": "复旦大学"
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/v1/profiles/students/me/privacy")
                        .header("Authorization", "Bearer " + targetToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "privacy": {
                                    "realName": { "guest": false, "student": true, "platformStudent": false, "mentor": true, "enterprise": true },
                                    "jobStatus": { "guest": false, "student": true, "platformStudent": true, "mentor": true, "enterprise": true },
                                    "eduInfo": { "guest": false, "student": true, "platformStudent": false, "mentor": true, "enterprise": false },
                                    "targetPos": { "guest": true, "student": true, "platformStudent": true, "mentor": true, "enterprise": true },
                                    "academic": { "guest": false, "student": true, "platformStudent": false, "mentor": true, "enterprise": false },
                                    "skills": { "guest": false, "student": true, "platformStudent": true, "mentor": true, "enterprise": true },
                                    "intro": { "guest": true, "student": true, "platformStudent": true, "mentor": true, "enterprise": true },
                                    "social": { "guest": false, "student": true, "platformStudent": false, "mentor": true, "enterprise": true },
                                    "email": { "guest": false, "student": false, "platformStudent": false, "mentor": false, "enterprise": false },
                                    "phone": { "guest": false, "student": false, "platformStudent": false, "mentor": false, "enterprise": false },
                                    "wechat": { "guest": false, "student": false, "platformStudent": false, "mentor": false, "enterprise": false },
                                    "portrait": { "guest": false, "student": false, "platformStudent": true, "mentor": true, "enterprise": false }
                                  }
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.updated").value(true));

        mockMvc.perform(get("/api/v1/profiles/students/{studentUserId}/public", targetUserId)
                        .header("Authorization", "Bearer " + sameSchoolToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.displayName").value("前端小林"))
                .andExpect(jsonPath("$.data.realName").value("林同学"))
                .andExpect(jsonPath("$.data.schoolName").value("华东理工大学"))
                .andExpect(jsonPath("$.data.gpa").value("3.8 / 4.0"))
                .andExpect(jsonPath("$.data.socialLinks[0].platform").value("GITHUB"))
                .andExpect(jsonPath("$.data.portrait.evidence.posts7d").value(0));

        mockMvc.perform(get("/api/v1/profiles/students/{studentUserId}/public", targetUserId)
                        .header("Authorization", "Bearer " + otherSchoolToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.realName").value(nullValue()))
                .andExpect(jsonPath("$.data.schoolName").value(nullValue()))
                .andExpect(jsonPath("$.data.gpa").value(nullValue()))
                .andExpect(jsonPath("$.data.targetPosition").value("前端开发工程师"))
                .andExpect(jsonPath("$.data.socialLinks").isArray())
                .andExpect(jsonPath("$.data.socialLinks.length()").value(0))
                .andExpect(jsonPath("$.data.portrait.evidence.posts7d").value(0));

        mockMvc.perform(get("/api/v1/profiles/students/{studentUserId}/public", targetUserId)
                        .header("Authorization", "Bearer " + mentorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.realName").value("林同学"))
                .andExpect(jsonPath("$.data.schoolName").value("华东理工大学"))
                .andExpect(jsonPath("$.data.socialLinks[0].platform").value("GITHUB"))
                .andExpect(jsonPath("$.data.portrait.evidence.comments7d").value(0));

        mockMvc.perform(get("/api/v1/profiles/students/{studentUserId}/public", targetUserId)
                        .header("Authorization", "Bearer " + enterpriseToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.realName").value("林同学"))
                .andExpect(jsonPath("$.data.schoolName").value(nullValue()))
                .andExpect(jsonPath("$.data.gpa").value(nullValue()))
                .andExpect(jsonPath("$.data.socialLinks[0].platform").value("GITHUB"))
                .andExpect(jsonPath("$.data.portrait").value(nullValue()));

        mockMvc.perform(get("/api/v1/profiles/students/{studentUserId}/public", targetUserId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.realName").value(nullValue()))
                .andExpect(jsonPath("$.data.schoolName").value(nullValue()))
                .andExpect(jsonPath("$.data.targetPosition").value("前端开发工程师"))
                .andExpect(jsonPath("$.data.selfIntro").value("持续准备前端求职，也会在社区分享经验。"))
                .andExpect(jsonPath("$.data.socialLinks.length()").value(0))
                .andExpect(jsonPath("$.data.portrait").value(nullValue()));
    }

    @Test
    void publicAvatar_shouldSupportAnonymousRead() throws Exception {
        registerUser("STUDENT", "public-avatar@example.com", "Passw0rd!", "PublicAvatar");
        String studentToken = loginAndGetAccessToken("public-avatar@example.com", "Passw0rd!");
        long studentUserId = findUserIdByEmail("public-avatar@example.com");
        studentPublicProfileCacheService.evictNow(studentUserId);

        MockMultipartFile avatarFile = new MockMultipartFile(
                "file",
                "public-avatar.png",
                "image/png",
                createAvatarPngBytes(new Color(37, 99, 235), "P")
        );

        mockMvc.perform(multipart("/api/v1/profiles/students/me/avatar")
                        .file(avatarFile)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.uploaded").value(true));

        mockMvc.perform(get("/api/v1/profiles/students/{studentUserId}/public", studentUserId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.avatar.uploaded").value(true));

        mockMvc.perform(get("/api/v1/profiles/students/{studentUserId}/avatar", studentUserId))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/jpeg"))
                .andExpect(result -> assertThat(result.getResponse().getContentAsByteArray()).isNotEmpty());
    }

    @Test
    void publicProfile_shouldUseRedisCacheAndRefreshAfterProfileUpdate() throws Exception {
        registerUser("STUDENT", "public-profile-cache@example.com", "Passw0rd!", "缓存初始名");
        String studentToken = loginAndGetAccessToken("public-profile-cache@example.com", "Passw0rd!");
        long studentUserId = findUserIdByEmail("public-profile-cache@example.com");
        studentPublicProfileCacheService.evictNow(studentUserId);

        mockMvc.perform(get("/api/v1/profiles/students/{studentUserId}/public", studentUserId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.displayName").value("缓存初始名"));

        jdbcTemplate.update("UPDATE users SET display_name = ? WHERE id = ?", "数据库直改名", studentUserId);

        mockMvc.perform(get("/api/v1/profiles/students/{studentUserId}/public", studentUserId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.displayName").value("缓存初始名"));

        mockMvc.perform(put("/api/v1/profiles/students/me")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "displayName": "接口更新名"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.updated").value(true));

        mockMvc.perform(get("/api/v1/profiles/students/{studentUserId}/public", studentUserId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.displayName").value("接口更新名"));
    }

    @Test
    void myProfilePortrait_shouldUseRedisCacheAndRefreshAfterPortraitSignalsChange() throws Exception {
        registerUser("STUDENT", "portrait-cache@example.com", "Passw0rd!", "画像缓存");
        String studentToken = loginAndGetAccessToken("portrait-cache@example.com", "Passw0rd!");
        long studentUserId = findUserIdByEmail("portrait-cache@example.com");
        studentPortraitSnapshotCacheService.evictNow(studentUserId);

        mockMvc.perform(get("/api/v1/profiles/students/me")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.portrait.evidence.masteredSkills").value(0))
                .andExpect(jsonPath("$.data.portrait.tags.length()").value(0));

        jdbcTemplate.update(
                "UPDATE student_portrait_snapshots SET portrait_tags = ?, evidence = ? WHERE student_user_id = ?",
                "[{\"code\":\"FAKE_SIGNAL\",\"label\":\"数据库直改标签\",\"source\":\"MANUAL\",\"confidence\":0.99}]",
                "{\"masteredSkills\":9,\"learningSkills\":0,\"interviewMessages7d\":0,\"posts7d\":0,\"comments7d\":0,\"likesReceived7d\":0}",
                studentUserId
        );

        mockMvc.perform(get("/api/v1/profiles/students/me")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.portrait.evidence.masteredSkills").value(0))
                .andExpect(jsonPath("$.data.portrait.tags.length()").value(0));

        mockMvc.perform(post("/api/v1/skills/progress")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "nodeId": "programming_language_foundations",
                                  "targetStatus": "MASTERED"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.portraitRefreshTriggered").value(true));

        mockMvc.perform(get("/api/v1/profiles/students/me")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.portrait.evidence.masteredSkills").value(1))
                .andExpect(jsonPath("$.data.portrait.tags[0].code").value("SKILL_PROGRESS_ACTIVE"));
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
        Long userId = jdbcTemplate.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
        assertThat(userId).isNotNull();
        return userId;
    }

    private String readDataField(MvcResult result, String fieldName) throws Exception {
        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        return root.path("data").path(fieldName).asText();
    }

    private void assertSecurityCodeCount(String purpose, String targetEmail, int expectedCount) {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                  FROM student_profile_security_codes
                 WHERE purpose = ?
                   AND target_email = ?
                """,
                Integer.class,
                purpose,
                targetEmail
        );
        assertThat(count).isEqualTo(expectedCount);
    }

    private byte[] createAvatarPngBytes(Color backgroundColor, String label) throws Exception {
        BufferedImage image = new BufferedImage(320, 320, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(backgroundColor);
            graphics.fillRect(0, 0, 320, 320);
            graphics.setColor(Color.WHITE);
            graphics.setFont(new Font("SansSerif", Font.BOLD, 136));
            FontMetrics metrics = graphics.getFontMetrics();
            int x = (320 - metrics.stringWidth(label)) / 2;
            int y = ((320 - metrics.getHeight()) / 2) + metrics.getAscent();
            graphics.drawString(label, x, y);
        } finally {
            graphics.dispose();
        }

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ImageIO.write(image, "png", outputStream);
        return outputStream.toByteArray();
    }
}
