package com.bishe.server.consult;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 学生端正式订单创建页后端承载链路集成测试。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ConsultOrderCreatePageIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void consultCreatePage_shouldPersistSnapshotsAndSupportAttachmentLifecycle() throws Exception {
        long mentorUserId = registerUser("MENTOR", "consult-create-page-mentor@example.com", "Passw0rd!", "CreatePageMentor");
        registerUser("STUDENT", "consult-create-page-student@example.com", "Passw0rd!", "CreatePageStudent");
        String mentorToken = loginAndGetAccessToken("consult-create-page-mentor@example.com", "Passw0rd!");
        String studentToken = loginAndGetAccessToken("consult-create-page-student@example.com", "Passw0rd!");

        mockMvc.perform(put("/api/v1/profiles/students/me")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                .content("""
                                {
                                  "jobStatus": "积极准备暑期实习",
                                  "schoolName": "华东理工大学",
                                  "major": "软件工程",
                                  "grade": "大三",
                                  "gpa": "3.7 / 4.0",
                                  "targetPosition": "前端开发工程师",
                                  "honors": "校级优秀学生干部",
                                  "skillTags": ["React", "TypeScript", "前端工程化"],
                                  "selfIntro": "希望通过真实咨询快速补齐简历与项目表达短板。"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.updated").value(true));

        mockMvc.perform(put("/api/v1/mentor/profile")
                        .header("Authorization", "Bearer " + mentorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "jobTitle": "高级前端工程师",
                                  "expertiseTags": ["前端工程化", "React"],
                                  "serviceScenes": ["简历诊断", "项目表达"],
                                  "bio": "擅长简历诊断与项目表达优化。",
                                  "priceFen": 19900,
                                  "available": true
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.available").value(true));

        String createOrderBody = """
                {
                  "mentorUserId": %d,
                  "sceneCode": "RESUME_DIAGNOSIS",
                  "sourcePage": "MENTOR_MARKETPLACE",
                  "questionText": "我希望重点优化简历回复率，并确认项目表达是否足够突出。",
                  "questionPayload": {
                    "primaryConcern": "投递前端实习的回复率很低，不知道问题主要出在简历还是项目表达。",
                    "background": "当前正在准备前端开发工程师方向的春招和暑期实习。",
                    "attemptedActions": "已经根据 AI 简历建议修改过一版，也补充了部分项目数据。",
                    "expectedHelp": "希望导师指出最该优先修改的点，并给出下一步行动建议。",
                    "additionalNotes": "如果需要，我还可以继续补充项目材料。"
                  },
                  "problemSummary": "希望重点优化简历回复率与项目表达。",
                  "coreQuestions": [
                    "简历里最影响回复率的部分是什么？",
                    "项目经历还缺少哪些能体现真实能力的内容？",
                    "如果只优先改 3 处，最该改哪些？"
                  ],
                  "expectedOutcomes": [
                    "获得简历修改建议",
                    "获得项目表达优化建议",
                    "获得下一步行动清单"
                  ],
                  "selectedMaterialTypes": ["RESUME", "JOB_DESCRIPTION"],
                  "prepSheetSnapshot": {
                    "scene": "简历诊断",
                    "summaryDraft": "希望重点优化简历与项目表达。",
                    "coreQuestions": [
                      "简历里最影响回复率的部分是什么？",
                      "项目经历还缺少哪些能体现真实能力的内容？"
                    ],
                    "suggestedMaterials": ["我的最新简历", "目标岗位 JD"],
                    "expectedOutcomes": ["获得简历修改建议", "获得下一步行动清单"]
                  }
                }
                """.formatted(mentorUserId);

        MvcResult createOrderResult = mockMvc.perform(post("/api/v1/consult/orders")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createOrderBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CREATED"))
                .andExpect(jsonPath("$.data.sceneCode").value("RESUME_DIAGNOSIS"))
                .andExpect(jsonPath("$.data.currentAttachmentCount").value(0))
                .andReturn();
        String orderNo = objectMapper.readTree(createOrderResult.getResponse().getContentAsString()).path("data").path("orderNo").asText();
        assertThat(orderNo).startsWith("ORD");

        Map<String, Object> orderSnapshot = jdbcTemplate.queryForMap(
                """
                SELECT scene_code,
                       source_page,
                       question_payload_json,
                       problem_summary,
                       core_questions_json,
                       expected_outcomes_json,
                       selected_material_types,
                       prep_sheet_snapshot_json
                  FROM consult_orders
                 WHERE order_no = ?
                """,
                orderNo
        );
        assertThat(orderSnapshot.get("scene_code")).isEqualTo("RESUME_DIAGNOSIS");
        assertThat(orderSnapshot.get("source_page")).isEqualTo("MENTOR_MARKETPLACE");
        assertThat(String.valueOf(orderSnapshot.get("question_payload_json"))).contains("primaryConcern");
        assertThat(orderSnapshot.get("problem_summary")).isEqualTo("希望重点优化简历回复率与项目表达。");
        assertThat(String.valueOf(orderSnapshot.get("core_questions_json"))).contains("最影响回复率");
        assertThat(String.valueOf(orderSnapshot.get("expected_outcomes_json"))).contains("获得简历修改建议");
        assertThat(orderSnapshot.get("selected_material_types")).isEqualTo("RESUME,JOB_DESCRIPTION");
        assertThat(String.valueOf(orderSnapshot.get("prep_sheet_snapshot_json"))).contains("summaryDraft");

        MockMultipartFile manifestPart = new MockMultipartFile(
                "manifestJson",
                "",
                MediaType.APPLICATION_JSON_VALUE,
                """
                        {
                          "items": [
                            {
                              "attachmentType": "RESUME",
                              "slotCode": "RESUME",
                              "description": "这是我准备投递大厂前端实习的最新简历。",
                              "replaceCurrent": true,
                              "sourceStage": "ORDER_CREATE"
                            },
                            {
                              "attachmentType": "JOB_DESCRIPTION",
                              "slotCode": "JOB_DESCRIPTION",
                              "description": "目标岗位 JD，重点看职责与技术要求。",
                              "replaceCurrent": true,
                              "sourceStage": "ORDER_CREATE"
                            }
                          ]
                        }
                        """.getBytes(StandardCharsets.UTF_8)
        );
        MockMultipartFile resumeFile = new MockMultipartFile(
                "files",
                "resume_v1.pdf",
                "application/pdf",
                "resume-v1".getBytes(StandardCharsets.UTF_8)
        );
        MockMultipartFile jdFile = new MockMultipartFile(
                "files",
                "target-jd.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "jd-v1".getBytes(StandardCharsets.UTF_8)
        );

        mockMvc.perform(multipart("/api/v1/consult/orders/{orderNo}/attachments/batch", orderNo)
                        .file(manifestPart)
                        .file(resumeFile)
                        .file(jdFile)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records.length()").value(2))
                .andExpect(jsonPath("$.data.currentAttachmentCount").value(2))
                .andExpect(jsonPath("$.data.records[0].lifecycleStatus").value("CURRENT"));

        MockMultipartFile replaceManifest = new MockMultipartFile(
                "manifestJson",
                "",
                MediaType.APPLICATION_JSON_VALUE,
                """
                        {
                          "items": [
                            {
                              "attachmentType": "RESUME",
                              "slotCode": "RESUME",
                              "description": "这是补充优化后的第二版简历。",
                              "replaceCurrent": true,
                              "sourceStage": "ORDER_CREATE"
                            }
                          ]
                        }
                        """.getBytes(StandardCharsets.UTF_8)
        );
        MockMultipartFile replaceResumeFile = new MockMultipartFile(
                "files",
                "resume_v2.pdf",
                "application/pdf",
                "resume-v2".getBytes(StandardCharsets.UTF_8)
        );

        mockMvc.perform(multipart("/api/v1/consult/orders/{orderNo}/attachments/batch", orderNo)
                        .file(replaceManifest)
                        .file(replaceResumeFile)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records.length()").value(1))
                .andExpect(jsonPath("$.data.currentAttachmentCount").value(2))
                .andExpect(jsonPath("$.data.records[0].originalFilename").value("resume_v2.pdf"));

        MvcResult currentAttachmentsResult = mockMvc.perform(get("/api/v1/consult/orders/{orderNo}/attachments", orderNo)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records.length()").value(2))
                .andExpect(jsonPath("$.data.currentAttachmentCount").value(2))
                .andReturn();

        JsonNode currentAttachments = objectMapper.readTree(currentAttachmentsResult.getResponse().getContentAsString()).path("data").path("records");
        long currentResumeAttachmentId = 0L;
        for (JsonNode record : currentAttachments) {
          if ("RESUME".equals(record.path("attachmentType").asText())) {
              currentResumeAttachmentId = record.path("attachmentId").asLong();
          }
        }
        assertThat(currentResumeAttachmentId).isPositive();

        MvcResult allAttachmentsResult = mockMvc.perform(get("/api/v1/consult/orders/{orderNo}/attachments", orderNo)
                        .header("Authorization", "Bearer " + studentToken)
                        .param("currentOnly", "false")
                        .param("includeSuperseded", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records.length()").value(3))
                .andReturn();
        JsonNode allAttachments = objectMapper.readTree(allAttachmentsResult.getResponse().getContentAsString()).path("data").path("records");
        boolean hasSupersededAttachment = false;
        for (JsonNode record : allAttachments) {
            if ("SUPERSEDED".equals(record.path("lifecycleStatus").asText())) {
                hasSupersededAttachment = true;
                break;
            }
        }
        assertThat(hasSupersededAttachment).isTrue();

        mockMvc.perform(get("/api/v1/consult/orders/{orderNo}", orderNo)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sceneCode").value("RESUME_DIAGNOSIS"))
                .andExpect(jsonPath("$.data.sourcePage").value("MENTOR_MARKETPLACE"))
                .andExpect(jsonPath("$.data.questionPayload.primaryConcern").value("投递前端实习的回复率很低，不知道问题主要出在简历还是项目表达。"))
                .andExpect(jsonPath("$.data.problemSummary").value("希望重点优化简历回复率与项目表达。"))
                .andExpect(jsonPath("$.data.coreQuestions[0]").value("简历里最影响回复率的部分是什么？"))
                .andExpect(jsonPath("$.data.expectedOutcomes[0]").value("获得简历修改建议"))
                .andExpect(jsonPath("$.data.selectedMaterialTypes[0]").value("RESUME"))
                .andExpect(jsonPath("$.data.prepSheetSnapshot.scene").value("简历诊断"))
                .andExpect(jsonPath("$.data.prepSheetSnapshot.summaryDraft").value("希望重点优化简历与项目表达。"))
                .andExpect(jsonPath("$.data.attachmentsSummary.currentAttachmentCount").value(2))
                .andExpect(jsonPath("$.data.attachmentsSummary.currentMaterialTypes.length()").value(2))
                .andExpect(jsonPath("$.data.attachmentsSummary.records.length()").value(2))
                .andExpect(jsonPath("$.data.studentProfile.jobStatus").value("积极准备暑期实习"))
                .andExpect(jsonPath("$.data.studentProfile.schoolName").value("华东理工大学"))
                .andExpect(jsonPath("$.data.studentProfile.major").value("软件工程"))
                .andExpect(jsonPath("$.data.studentProfile.grade").value("大三"))
                .andExpect(jsonPath("$.data.studentProfile.targetPosition").value("前端开发工程师"))
                .andExpect(jsonPath("$.data.studentProfile.skillTags[0]").value("React"))
                .andExpect(jsonPath("$.data.studentProfile.selfIntro").value("希望通过真实咨询快速补齐简历与项目表达短板。"));

        mockMvc.perform(get("/api/v1/consult/orders/{orderNo}/attachments/{attachmentId}/content", orderNo, currentResumeAttachmentId)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/pdf"))
                .andExpect(result -> assertThat(result.getResponse().getContentAsByteArray()).isNotEmpty());

        mockMvc.perform(delete("/api/v1/consult/orders/{orderNo}/attachments/{attachmentId}", orderNo, currentResumeAttachmentId)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.deleted").value(true))
                .andExpect(jsonPath("$.data.currentAttachmentCount").value(1));

        mockMvc.perform(get("/api/v1/consult/orders/{orderNo}/attachments", orderNo)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records.length()").value(1))
                .andExpect(jsonPath("$.data.records[0].attachmentType").value("JOB_DESCRIPTION"));

        Integer deletedCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM consult_order_attachments WHERE order_no = ? AND lifecycle_status = 'DELETED'",
                Integer.class,
                orderNo
        );
        Integer supersededCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM consult_order_attachments WHERE order_no = ? AND lifecycle_status = 'SUPERSEDED'",
                Integer.class,
                orderNo
        );
        assertThat(deletedCount).isEqualTo(1);
        assertThat(supersededCount).isEqualTo(1);
    }

    @Test
    void consultCreatePage_shouldResolveEnabledMentorPackageAndPersistPackageSnapshot() throws Exception {
        long mentorUserId = registerUser("MENTOR", "consult-package-mentor@example.com", "Passw0rd!", "PackageMentor");
        registerUser("STUDENT", "consult-package-student@example.com", "Passw0rd!", "PackageStudent");
        String mentorToken = loginAndGetAccessToken("consult-package-mentor@example.com", "Passw0rd!");
        String studentToken = loginAndGetAccessToken("consult-package-student@example.com", "Passw0rd!");

        MvcResult updateProfileResult = mockMvc.perform(put("/api/v1/mentor/profile")
                        .header("Authorization", "Bearer " + mentorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "jobTitle": "前端工程师",
                                  "serviceScenes": ["简历诊断", "项目表达", "模拟面试复盘"],
                                  "priceFen": 19900,
                                  "available": true,
                                  "packages": [
                                    {
                                      "packageName": "简历急救包",
                                      "sceneLabel": "简历诊断",
                                      "deliveryMode": "TEXT_ASYNC",
                                      "priceFen": 12900,
                                      "description": "适合先快速收口简历重点问题。",
                                      "enabled": true
                                    },
                                    {
                                      "packageName": "项目表达深挖",
                                      "sceneLabel": "项目表达",
                                      "deliveryMode": "TEXT_ASYNC",
                                      "priceFen": 16900,
                                      "description": "适合补项目亮点和故事线。",
                                      "enabled": false
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.packages.length()").value(2))
                .andExpect(jsonPath("$.data.packages[0].packageName").value("简历急救包"))
                .andExpect(jsonPath("$.data.packages[0].sceneCode").value("RESUME_DIAGNOSIS"))
                .andExpect(jsonPath("$.data.packages[1].packageName").value("项目表达深挖"))
                .andReturn();

        JsonNode mentorPackages = objectMapper.readTree(updateProfileResult.getResponse().getContentAsString())
                .path("data")
                .path("packages");
        long enabledPackageId = mentorPackages.get(0).path("id").asLong();
        long disabledPackageId = mentorPackages.get(1).path("id").asLong();
        assertThat(enabledPackageId).isPositive();
        assertThat(disabledPackageId).isPositive();

        String createOrderBody = """
                {
                  "mentorUserId": %d,
                  "mentorPackageId": %d,
                  "sceneCode": "CAREER_DIRECTION",
                  "sourcePage": "MENTOR_MARKETPLACE",
                  "questionText": "我想先快速确认简历里最影响回复率的问题。"
                }
                """.formatted(mentorUserId, enabledPackageId);

        MvcResult createOrderResult = mockMvc.perform(post("/api/v1/consult/orders")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createOrderBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.amountFen").value(12900))
                .andExpect(jsonPath("$.data.sceneCode").value("RESUME_DIAGNOSIS"))
                .andReturn();

        String orderNo = objectMapper.readTree(createOrderResult.getResponse().getContentAsString())
                .path("data")
                .path("orderNo")
                .asText();
        assertThat(orderNo).startsWith("ORD");

        Map<String, Object> orderSnapshot = jdbcTemplate.queryForMap(
                """
                SELECT amount_fen,
                       scene_code,
                       service_package_snapshot_json
                  FROM consult_orders
                 WHERE order_no = ?
                """,
                orderNo
        );
        assertThat(orderSnapshot.get("amount_fen")).isEqualTo(12900);
        assertThat(orderSnapshot.get("scene_code")).isEqualTo("RESUME_DIAGNOSIS");
        assertThat(String.valueOf(orderSnapshot.get("service_package_snapshot_json")))
                .contains("简历急救包")
                .contains("RESUME_DIAGNOSIS")
                .contains("TEXT_ASYNC");

        String disabledPackageOrderBody = """
                {
                  "mentorUserId": %d,
                  "mentorPackageId": %d,
                  "questionText": "我想确认项目经历的表达方式。"
                }
                """.formatted(mentorUserId, disabledPackageId);

        mockMvc.perform(post("/api/v1/consult/orders")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(disabledPackageOrderBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("mentor package invalid"));
    }

    private long registerUser(String role, String email, String password, String displayName) throws Exception {
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

        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andReturn();

        JsonNode jsonNode = objectMapper.readTree(result.getResponse().getContentAsString());
        long userId = jsonNode.path("data").path("userId").asLong();
        if ("MENTOR".equals(role)) {
            jdbcTemplate.update(
                    "UPDATE mentor_profiles SET approval_status = 'APPROVED', updated_at = CURRENT_TIMESTAMP WHERE user_id = ?",
                    userId
            );
        }
        return userId;
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
}
