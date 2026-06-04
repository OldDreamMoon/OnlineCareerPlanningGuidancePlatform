package com.bishe.server.profile;

import com.bishe.server.certification.CertificationStorageService;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 企业资料与认证中心闭环测试。
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:enterpriseprofilecenter;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EnterpriseProfileCenterIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private CertificationStorageService certificationStorageService;

    @Test
    void enterpriseProfileCenter_shouldSupportExtendedProfileUpdate() throws Exception {
        registerUser("ENTERPRISE", "enterprise-profile-center@example.com", "Passw0rd!", "企业资料中心");
        String enterpriseToken = loginAndGetAccessToken("enterprise-profile-center@example.com", "Passw0rd!");

        mockMvc.perform(get("/api/v1/profiles/enterprises/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + enterpriseToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.displayName").value("企业资料中心"))
                .andExpect(jsonPath("$.data.industry").value("待补充"))
                .andExpect(jsonPath("$.data.companySize").value("待补充"))
                .andExpect(jsonPath("$.data.hiringTags[0]").value("校招"));

        String updateBody = """
                {
                  "realName": "王小明",
                  "companyName": "星河智能",
                  "jobTitle": "校园招聘负责人",
                  "industry": "人工智能 / 企业服务",
                  "companySize": "200-500 人",
                  "hiringTags": ["算法工程", "前端产品化", "数据策略"],
                  "bio": "我们专注企业智能化产品与校招合作项目，希望学生能在真实任务中形成作品集。",
                  "externalLinks": "官网：https://nebula.example.com\\n邮箱：campus@nebula.example.com",
                  "preferences": "偏好有课程项目或竞赛经历的同学，提交时建议附上代码仓库。"
                }
                """;

        mockMvc.perform(put("/api/v1/profiles/enterprises/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + enterpriseToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.realName").value("王小明"))
                .andExpect(jsonPath("$.data.companyName").value("星河智能"))
                .andExpect(jsonPath("$.data.jobTitle").value("校园招聘负责人"))
                .andExpect(jsonPath("$.data.industry").value("人工智能 / 企业服务"))
                .andExpect(jsonPath("$.data.companySize").value("200-500 人"))
                .andExpect(jsonPath("$.data.hiringTags[0]").value("算法工程"))
                .andExpect(jsonPath("$.data.hiringTags[2]").value("数据策略"))
                .andExpect(jsonPath("$.data.bio").value("我们专注企业智能化产品与校招合作项目，希望学生能在真实任务中形成作品集。"))
                .andExpect(jsonPath("$.data.externalLinks").value("官网：https://nebula.example.com\n邮箱：campus@nebula.example.com"))
                .andExpect(jsonPath("$.data.preferences").value("偏好有课程项目或竞赛经历的同学，提交时建议附上代码仓库。"));

        mockMvc.perform(get("/api/v1/profiles/enterprises/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + enterpriseToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.realName").value("王小明"))
                .andExpect(jsonPath("$.data.companyName").value("星河智能"))
                .andExpect(jsonPath("$.data.jobTitle").value("校园招聘负责人"))
                .andExpect(jsonPath("$.data.industry").value("人工智能 / 企业服务"))
                .andExpect(jsonPath("$.data.companySize").value("200-500 人"))
                .andExpect(jsonPath("$.data.hiringTags.length()").value(3))
                .andExpect(jsonPath("$.data.bio").value("我们专注企业智能化产品与校招合作项目，希望学生能在真实任务中形成作品集。"))
                .andExpect(jsonPath("$.data.externalLinks").value("官网：https://nebula.example.com\n邮箱：campus@nebula.example.com"))
                .andExpect(jsonPath("$.data.preferences").value("偏好有课程项目或竞赛经历的同学，提交时建议附上代码仓库。"));
    }

    @Test
    void enterpriseProfileCenter_shouldSupportPasswordResetFlowWithMockCode() throws Exception {
        registerUser("ENTERPRISE", "enterprise-password@example.com", "Passw0rd!", "企业改密测试");
        String enterpriseToken = loginAndGetAccessToken("enterprise-password@example.com", "Passw0rd!");

        MvcResult sendCodeResult = mockMvc.perform(post("/api/v1/profiles/enterprises/me/security/password/send-code")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + enterpriseToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.deliveryChannel").value("MOCK"))
                .andReturn();
        String passwordCode = readDataField(sendCodeResult, "debugCode");
        assertThat(passwordCode).hasSize(6);
        assertSecurityCodeCount("PROFILE_PASSWORD_RESET_CODE", "enterprise-password@example.com", 1);

        MvcResult verifyCodeResult = mockMvc.perform(post("/api/v1/profiles/enterprises/me/security/password/verify-code")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + enterpriseToken)
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
        assertSecurityCodeCount("PROFILE_PASSWORD_RESET_CODE", "enterprise-password@example.com", 0);

        mockMvc.perform(post("/api/v1/profiles/enterprises/me/security/password/change")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + enterpriseToken)
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

        String newToken = loginAndGetAccessToken("enterprise-password@example.com", "N3wPassw0rd!");
        assertThat(newToken).isNotBlank();
    }

    @Test
    void enterpriseCertificationHistory_shouldKeepReplacedAssetsReadable() throws Exception {
        Map<String, byte[]> storedObjects = new LinkedHashMap<>();
        AtomicInteger objectCounter = new AtomicInteger();
        given(certificationStorageService.upload(anyString(), anyString(), anyString(), any(MultipartFile.class)))
                .willAnswer(invocation -> {
                    MultipartFile file = invocation.getArgument(3, MultipartFile.class);
                    String objectKey = "test-certification-object-" + objectCounter.incrementAndGet();
                    storedObjects.put(objectKey, file.getBytes());
                    return new CertificationStorageService.StoredObject(
                            "test-certification-bucket",
                            objectKey,
                            file.getOriginalFilename(),
                            file.getContentType(),
                            file.getSize(),
                            Instant.now().toString()
                    );
                });
        given(certificationStorageService.read(anyString(), anyString()))
                .willAnswer(invocation -> {
                    String objectKey = invocation.getArgument(1, String.class);
                    byte[] bytes = storedObjects.get(objectKey);
                    return new CertificationStorageService.StoredContent(bytes == null ? new byte[0] : bytes);
                });

        registerUser("ENTERPRISE", "enterprise-cert-history@example.com", "Passw0rd!", "企业历史材料");
        String enterpriseToken = loginAndGetAccessToken("enterprise-cert-history@example.com", "Passw0rd!");

        submitCertificationAsset(
                enterpriseToken,
                "王小明",
                "星河智能",
                "招聘负责人",
                "first-proof.pdf",
                "第一版认证材料"
        );

        submitCertificationAsset(
                enterpriseToken,
                "王小明",
                "星河智能",
                "招聘负责人",
                "second-proof.pdf",
                "第二版认证材料"
        );

        MvcResult detailResult = mockMvc.perform(get("/api/v1/certification/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + enterpriseToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.submissions.length()").value(2))
                .andExpect(jsonPath("$.data.submissions[0].assets[0].lifecycleStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.data.submissions[1].assets[0].lifecycleStatus").value("REPLACED"))
                .andReturn();

        JsonNode data = objectMapper.readTree(detailResult.getResponse().getContentAsString()).path("data");
        long historicalAssetId = data.path("submissions").get(1).path("assets").get(0).path("assetId").asLong();
        assertThat(historicalAssetId).isPositive();

        mockMvc.perform(get("/api/v1/certification/assets/{assetId}/content", historicalAssetId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + enterpriseToken))
                .andExpect(status().isOk())
                .andExpect(content().bytes("第一版认证材料".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void certificationOwnView_shouldUseCacheAndRefreshAfterReviewAndResubmit() throws Exception {
        Map<String, byte[]> storedObjects = new LinkedHashMap<>();
        AtomicInteger objectCounter = new AtomicInteger();
        given(certificationStorageService.upload(anyString(), anyString(), anyString(), any(MultipartFile.class)))
                .willAnswer(invocation -> {
                    MultipartFile file = invocation.getArgument(3, MultipartFile.class);
                    String objectKey = "test-certification-cache-object-" + objectCounter.incrementAndGet();
                    storedObjects.put(objectKey, file.getBytes());
                    return new CertificationStorageService.StoredObject(
                            "test-certification-bucket",
                            objectKey,
                            file.getOriginalFilename(),
                            file.getContentType(),
                            file.getSize(),
                            Instant.now().toString()
                    );
                });
        given(certificationStorageService.read(anyString(), anyString()))
                .willAnswer(invocation -> {
                    String objectKey = invocation.getArgument(1, String.class);
                    byte[] bytes = storedObjects.get(objectKey);
                    return new CertificationStorageService.StoredContent(bytes == null ? new byte[0] : bytes);
                });

        registerUser("ENTERPRISE", "enterprise-cert-cache@example.com", "Passw0rd!", "认证缓存企业");
        String enterpriseToken = loginAndGetAccessToken("enterprise-cert-cache@example.com", "Passw0rd!");
        long enterpriseUserId = findUserIdByEmail("enterprise-cert-cache@example.com");

        submitCertificationAsset(
                enterpriseToken,
                "王小明",
                "星河智能",
                "招聘负责人",
                "cache-first-proof.pdf",
                "第一版认证材料"
        );

        mockMvc.perform(get("/api/v1/certification/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + enterpriseToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.approvalStatus").value("PENDING"))
                .andExpect(jsonPath("$.data.currentSubmission.companyName").value("星河智能"))
                .andExpect(jsonPath("$.data.submissions.length()").value(1));

        jdbcTemplate.update(
                """
                UPDATE certification_submissions
                   SET company_name = ?,
                       updated_at = CURRENT_TIMESTAMP
                 WHERE user_id = ?
                   AND is_current = 1
                """,
                "数据库直改公司",
                enterpriseUserId
        );

        mockMvc.perform(get("/api/v1/certification/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + enterpriseToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currentSubmission.companyName").value("星河智能"));

        String adminToken = loginAndGetAccessToken("admin@test.local", "Admin123456!");
        mockMvc.perform(post("/api/v1/admin/users/{userId}/certification-review", enterpriseUserId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "approvalStatus": "REJECTED",
                                  "reviewNote": "请补充新的认证资料后再次提交。"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currentSubmission.status").value("REJECTED"));

        mockMvc.perform(get("/api/v1/certification/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + enterpriseToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.approvalStatus").value("REJECTED"))
                .andExpect(jsonPath("$.data.currentSubmission.status").value("REJECTED"))
                .andExpect(jsonPath("$.data.currentSubmission.reviewNote").value("请补充新的认证资料后再次提交。"))
                .andExpect(jsonPath("$.data.currentSubmission.companyName").value("数据库直改公司"));

        submitCertificationAsset(
                enterpriseToken,
                "王小明",
                "重新提交公司",
                "招聘负责人",
                "cache-second-proof.pdf",
                "第二版认证材料"
        );

        mockMvc.perform(get("/api/v1/certification/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + enterpriseToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.approvalStatus").value("PENDING"))
                .andExpect(jsonPath("$.data.currentSubmission.status").value("PENDING"))
                .andExpect(jsonPath("$.data.currentSubmission.companyName").value("重新提交公司"))
                .andExpect(jsonPath("$.data.submissions.length()").value(2))
                .andExpect(jsonPath("$.data.submissions[0].assets[0].lifecycleStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.data.submissions[1].assets[0].lifecycleStatus").value("REPLACED"));
    }

    @Test
    void certificationReviewList_shouldUseCacheAndRefreshAfterReviewAndResubmit() throws Exception {
        Map<String, byte[]> storedObjects = new LinkedHashMap<>();
        AtomicInteger objectCounter = new AtomicInteger();
        given(certificationStorageService.upload(anyString(), anyString(), anyString(), any(MultipartFile.class)))
                .willAnswer(invocation -> {
                    MultipartFile file = invocation.getArgument(3, MultipartFile.class);
                    String objectKey = "test-certification-review-list-object-" + objectCounter.incrementAndGet();
                    storedObjects.put(objectKey, file.getBytes());
                    return new CertificationStorageService.StoredObject(
                            "test-certification-bucket",
                            objectKey,
                            file.getOriginalFilename(),
                            file.getContentType(),
                            file.getSize(),
                            Instant.now().toString()
                    );
                });

        registerUser("ENTERPRISE", "enterprise-review-list@example.com", "Passw0rd!", "审核列表企业");
        String enterpriseToken = loginAndGetAccessToken("enterprise-review-list@example.com", "Passw0rd!");
        long enterpriseUserId = findUserIdByEmail("enterprise-review-list@example.com");
        String adminToken = loginAndGetAccessToken("admin@test.local", "Admin123456!");

        submitCertificationAsset(
                enterpriseToken,
                "王小明",
                "审核列表缓存企业",
                "招聘负责人",
                "review-list-first.pdf",
                "第一版审核列表材料"
        );

        mockMvc.perform(get("/api/v1/admin/users/certification-reviews")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .param("page", "1")
                        .param("size", "10")
                        .param("keyword", "审核列表缓存企业"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].companyName").value("审核列表缓存企业"))
                .andExpect(jsonPath("$.data.records[0].submissionStatus").value("PENDING"));

        jdbcTemplate.update(
                """
                UPDATE certification_submissions
                   SET company_name = ?,
                       updated_at = CURRENT_TIMESTAMP
                 WHERE user_id = ?
                   AND is_current = 1
                """,
                "审核列表数据库直改公司",
                enterpriseUserId
        );

        mockMvc.perform(get("/api/v1/admin/users/certification-reviews")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .param("page", "1")
                        .param("size", "10")
                        .param("keyword", "审核列表缓存企业"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].companyName").value("审核列表缓存企业"));

        mockMvc.perform(post("/api/v1/admin/users/{userId}/certification-review", enterpriseUserId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "approvalStatus": "REJECTED",
                                  "reviewNote": "请补充新的审核列表材料。"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currentSubmission.status").value("REJECTED"));

        mockMvc.perform(get("/api/v1/admin/users/certification-reviews")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .param("page", "1")
                        .param("size", "10")
                        .param("keyword", "审核列表缓存企业"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0));

        submitCertificationAsset(
                enterpriseToken,
                "王小明",
                "审核列表重新提交公司",
                "招聘负责人",
                "review-list-second.pdf",
                "第二版审核列表材料"
        );

        mockMvc.perform(get("/api/v1/admin/users/certification-reviews")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .param("page", "1")
                        .param("size", "10")
                        .param("keyword", "审核列表重新提交公司"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].companyName").value("审核列表重新提交公司"))
                .andExpect(jsonPath("$.data.records[0].submissionStatus").value("PENDING"))
                .andExpect(jsonPath("$.data.records[0].activeAssetCount").value(1));
    }

    @Test
    void certificationReviewDetail_shouldUseCacheAndRefreshAfterReviewAndResubmit() throws Exception {
        Map<String, byte[]> storedObjects = new LinkedHashMap<>();
        AtomicInteger objectCounter = new AtomicInteger();
        given(certificationStorageService.upload(anyString(), anyString(), anyString(), any(MultipartFile.class)))
                .willAnswer(invocation -> {
                    MultipartFile file = invocation.getArgument(3, MultipartFile.class);
                    String objectKey = "test-certification-review-detail-object-" + objectCounter.incrementAndGet();
                    storedObjects.put(objectKey, file.getBytes());
                    return new CertificationStorageService.StoredObject(
                            "test-certification-bucket",
                            objectKey,
                            file.getOriginalFilename(),
                            file.getContentType(),
                            file.getSize(),
                            Instant.now().toString()
                    );
                });

        registerUser("ENTERPRISE", "enterprise-review-detail@example.com", "Passw0rd!", "审核详情企业");
        String enterpriseToken = loginAndGetAccessToken("enterprise-review-detail@example.com", "Passw0rd!");
        long enterpriseUserId = findUserIdByEmail("enterprise-review-detail@example.com");
        String adminToken = loginAndGetAccessToken("admin@test.local", "Admin123456!");

        submitCertificationAsset(
                enterpriseToken,
                "王小明",
                "审核详情缓存企业",
                "招聘负责人",
                "review-detail-first.pdf",
                "第一版审核详情材料"
        );

        mockMvc.perform(get("/api/v1/admin/users/{userId}/certification-review", enterpriseUserId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currentSubmission.companyName").value("审核详情缓存企业"))
                .andExpect(jsonPath("$.data.currentSubmission.status").value("PENDING"))
                .andExpect(jsonPath("$.data.submissions.length()").value(1));

        jdbcTemplate.update(
                """
                UPDATE certification_submissions
                   SET company_name = ?,
                       updated_at = CURRENT_TIMESTAMP
                 WHERE user_id = ?
                   AND is_current = 1
                """,
                "审核详情数据库直改公司",
                enterpriseUserId
        );

        mockMvc.perform(get("/api/v1/admin/users/{userId}/certification-review", enterpriseUserId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currentSubmission.companyName").value("审核详情缓存企业"));

        mockMvc.perform(post("/api/v1/admin/users/{userId}/certification-review", enterpriseUserId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "approvalStatus": "REJECTED",
                                  "reviewNote": "请补充新的审核详情材料。"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currentSubmission.status").value("REJECTED"));

        mockMvc.perform(get("/api/v1/admin/users/{userId}/certification-review", enterpriseUserId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currentSubmission.companyName").value("审核详情数据库直改公司"))
                .andExpect(jsonPath("$.data.currentSubmission.status").value("REJECTED"))
                .andExpect(jsonPath("$.data.currentSubmission.reviewNote").value("请补充新的审核详情材料。"));

        submitCertificationAsset(
                enterpriseToken,
                "王小明",
                "审核详情重新提交公司",
                "招聘负责人",
                "review-detail-second.pdf",
                "第二版审核详情材料"
        );

        mockMvc.perform(get("/api/v1/admin/users/{userId}/certification-review", enterpriseUserId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currentSubmission.companyName").value("审核详情重新提交公司"))
                .andExpect(jsonPath("$.data.currentSubmission.status").value("PENDING"))
                .andExpect(jsonPath("$.data.submissions.length()").value(2))
                .andExpect(jsonPath("$.data.submissions[0].assets[0].lifecycleStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.data.submissions[1].assets[0].lifecycleStatus").value("REPLACED"));
    }

    private void submitCertificationAsset(
            String enterpriseToken,
            String realName,
            String companyName,
            String jobTitle,
            String filename,
            String contentText
    ) throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                filename,
                MediaType.APPLICATION_PDF_VALUE,
                contentText.getBytes(StandardCharsets.UTF_8)
        );

        mockMvc.perform(multipart("/api/v1/certification/me")
                        .file(file)
                        .param("realName", realName)
                        .param("companyName", companyName)
                        .param("jobTitle", jobTitle)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + enterpriseToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"));
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
                "SELECT COUNT(*) FROM student_profile_security_codes WHERE purpose = ? AND target_email = ?",
                Integer.class,
                purpose,
                targetEmail
        );
        assertThat(count).isEqualTo(expectedCount);
    }
}
