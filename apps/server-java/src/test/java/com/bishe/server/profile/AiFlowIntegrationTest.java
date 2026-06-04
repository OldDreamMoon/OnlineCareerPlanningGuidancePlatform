package com.bishe.server.profile;

import com.bishe.server.ai.gateway.task.AiAsyncTaskWorker;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
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

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AI 简历优化与文本面试闭环测试。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AiFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AiAsyncTaskWorker aiAsyncTaskWorker;

    @Test
    void resumeOptimize_shouldReturnStructuredSuggestionsAndConsumeResumeQuota() throws Exception {
        registerUser("STUDENT", "resume-flow@example.com", "Passw0rd!", "ResumeFlow");
        String studentToken = loginAndGetAccessToken("resume-flow@example.com", "Passw0rd!");

        String body = """
                {
                  "targetRole": "Backend Engineer",
                  "targetContext": "校招正式批",
                  "jobDescription": "负责核心后端服务研发，要求熟悉 Java、MySQL、Redis 与性能优化。",
                  "resumeText": "负责用户增长平台后端接口开发，重构缓存链路后接口延迟降低 30%，支撑日活 10 万。"
                }
                """;

        mockMvc.perform(post("/api/v1/ai/resume/optimize")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.recordId").isNumber())
                .andExpect(jsonPath("$.data.summary").isNotEmpty())
                .andExpect(jsonPath("$.data.scoreLabel").isNotEmpty())
                .andExpect(jsonPath("$.data.strengths[0]").isNotEmpty())
                .andExpect(jsonPath("$.data.structureItems[0].label").isNotEmpty())
                .andExpect(jsonPath("$.data.rewriteItems[0].afterText").isNotEmpty())
                .andExpect(jsonPath("$.data.aiMeta.taskType").value("RESUME"))
                .andExpect(jsonPath("$.data.moderation.action").value("PASS"));

        mockMvc.perform(get("/api/v1/ai/quota/remaining")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.quotas[?(@.taskType=='RESUME')].usedToday").value(org.hamcrest.Matchers.contains(1)));
    }

    @Test
    void resumeOptimizeStream_shouldSendSegmentedSseEventsAndConsumeResumeQuota() throws Exception {
        registerUser("STUDENT", "resume-stream@example.com", "Passw0rd!", "ResumeStream");
        String studentToken = loginAndGetAccessToken("resume-stream@example.com", "Passw0rd!");

        String body = """
                {
                  "targetRole": "Backend Engineer",
                  "targetContext": "校招正式批",
                  "jobDescription": "负责核心后端服务研发，要求熟悉 Java、MySQL、Redis 与性能优化。",
                  "resumeText": "负责用户增长平台后端接口开发，重构缓存链路后接口延迟降低 30%，支撑日活 10 万。"
                }
                """;

        MvcResult streamResult = mockMvc.perform(post("/api/v1/ai/resume/optimize/stream")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(request().asyncStarted())
                .andReturn();

        streamResult.getAsyncResult(5000);

        assertThat(streamResult.getResponse().getContentType()).contains(MediaType.TEXT_EVENT_STREAM_VALUE);
        String streamBody = streamResult.getResponse().getContentAsString();
        assertThat(streamBody).containsPattern("event:\\s*start");
        assertThat(streamBody).containsPattern("event:\\s*summary");
        assertThat(streamBody).containsPattern("event:\\s*strengths");
        assertThat(streamBody).containsPattern("event:\\s*risks");
        assertThat(streamBody).containsPattern("event:\\s*suggestions");
        assertThat(streamBody).containsPattern("event:\\s*done");
        assertThat(streamBody).contains("\"summary\"");
        assertThat(streamBody).contains("\"recordId\"");
        assertThat(streamBody).contains("\"result\"");

        mockMvc.perform(get("/api/v1/ai/quota/remaining")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.quotas[?(@.taskType=='RESUME')].usedToday").value(org.hamcrest.Matchers.contains(1)));
    }

    @Test
    void resumeOptimizePdf_shouldReturnStructuredSuggestions() throws Exception {
        registerUser("STUDENT", "resume-pdf@example.com", "Passw0rd!", "ResumePdf");
        String studentToken = loginAndGetAccessToken("resume-pdf@example.com", "Passw0rd!");
        String pdfResumeText = "Built backend services for order center, reduced latency by 30 percent, and verified improvements with benchmark and monitoring.";

        MockMultipartFile resumeFile = new MockMultipartFile(
                "resumeFile",
                "resume.pdf",
                MediaType.APPLICATION_PDF_VALUE,
                buildResumePdfBytes(pdfResumeText)
        );
        MockMultipartFile targetRole = new MockMultipartFile("targetRole", "", MediaType.TEXT_PLAIN_VALUE, "Backend Engineer".getBytes());
        MockMultipartFile targetContext = new MockMultipartFile("targetContext", "", MediaType.TEXT_PLAIN_VALUE, "校招正式批".getBytes(StandardCharsets.UTF_8));
        MockMultipartFile jobDescription = new MockMultipartFile("jobDescription", "", MediaType.TEXT_PLAIN_VALUE, "负责核心后端服务研发，要求熟悉 Java、MySQL、Redis 与性能优化。".getBytes(StandardCharsets.UTF_8));

        MvcResult optimizeResult = mockMvc.perform(multipart("/api/v1/ai/resume/optimize/pdf")
                        .file(resumeFile)
                        .file(targetRole)
                        .file(targetContext)
                        .file(jobDescription)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.recordId").isNumber())
                .andExpect(jsonPath("$.data.summary").isNotEmpty())
                .andExpect(jsonPath("$.data.structureItems[0].label").isNotEmpty())
                .andExpect(jsonPath("$.data.rewriteItems[0].afterText").isNotEmpty())
                .andExpect(jsonPath("$.data.aiMeta.taskType").value("RESUME"))
                .andReturn();

        long recordId = objectMapper.readTree(optimizeResult.getResponse().getContentAsString()).path("data").path("recordId").asLong();
        assertThat(recordId).isPositive();

        mockMvc.perform(get("/api/v1/ai/history/resume/{recordId}/preview", recordId)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.inputMode").value("pdf"))
                .andExpect(jsonPath("$.data.pdfFileName").value("resume.pdf"))
                .andExpect(jsonPath("$.data.resumeText").value(org.hamcrest.Matchers.containsString("Built backend services for order center")));
    }

    @Test
    void interviewSessionCreate_shouldPersistSelectedResumeContextFromPdfHistory() throws Exception {
        registerUser("STUDENT", "interview-resume-context@example.com", "Passw0rd!", "InterviewResumeContext");
        String studentToken = loginAndGetAccessToken("interview-resume-context@example.com", "Passw0rd!");
        String pdfResumeText = "Built backend services for order center, reduced latency by 30 percent, and verified improvements with benchmark and monitoring.";

        MockMultipartFile resumeFile = new MockMultipartFile(
                "resumeFile",
                "resume.pdf",
                MediaType.APPLICATION_PDF_VALUE,
                buildResumePdfBytes(pdfResumeText)
        );
        MockMultipartFile targetRole = new MockMultipartFile("targetRole", "", MediaType.TEXT_PLAIN_VALUE, "Backend Engineer".getBytes());
        MockMultipartFile targetContext = new MockMultipartFile("targetContext", "", MediaType.TEXT_PLAIN_VALUE, "校招正式批".getBytes(StandardCharsets.UTF_8));
        MockMultipartFile jobDescription = new MockMultipartFile("jobDescription", "", MediaType.TEXT_PLAIN_VALUE, "负责核心后端服务研发，要求熟悉 Java、MySQL、Redis 与性能优化。".getBytes(StandardCharsets.UTF_8));

        MvcResult optimizeResult = mockMvc.perform(multipart("/api/v1/ai/resume/optimize/pdf")
                        .file(resumeFile)
                        .file(targetRole)
                        .file(targetContext)
                        .file(jobDescription)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.recordId").isNumber())
                .andReturn();

        long recordId = objectMapper.readTree(optimizeResult.getResponse().getContentAsString()).path("data").path("recordId").asLong();
        assertThat(recordId).isPositive();

        MvcResult createResult = mockMvc.perform(post("/api/v1/ai/interview/sessions")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(("""
                                {
                                  "targetRole": "Backend Engineer",
                                  "mode": "INTERVIEW_TEXT",
                                  "resumeRecordId": %d,
                                  "sessionContext": {
                                    "interviewType": "PROJECT_DEEP_DIVE",
                                    "interviewerStyle": "COACHING",
                                    "difficulty": "HARD",
                                    "answerMode": "VOICE",
                                    "targetCompany": "字节跳动",
                                    "targetJobDescription": "负责核心后端服务研发，要求熟悉 Java、MySQL、Redis 与性能优化。",
                                    "prepMaterialKeys": ["LATEST_RESUME", "TARGET_COMPANY", "SKILL_TAGS"],
                                    "answerHelperEnabled": true,
                                    "answerHelperCueKeys": ["METRICS", "COMPLETENESS"],
                                    "promptContext": "技能关键词：Java / Redis / MySQL\\n最近一份简历：已授权带入最近一份简历。"
                                  }
                                }
                                """).formatted(recordId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.firstQuestion").isNotEmpty())
                .andReturn();

        String sessionId = objectMapper.readTree(createResult.getResponse().getContentAsString()).path("data").path("sessionId").asText();
        assertThat(sessionId).startsWith("is_");

        String resumeContextJson = jdbcTemplate.queryForObject(
                "SELECT resume_context_json FROM interview_sessions WHERE session_id = ?",
                String.class,
                sessionId
        );
        assertThat(resumeContextJson).isNotBlank();

        JsonNode resumeContextNode = objectMapper.readTree(resumeContextJson);
        assertThat(resumeContextNode.path("recordId").asLong()).isEqualTo(recordId);
        assertThat(resumeContextNode.path("inputMode").asText()).isEqualTo("pdf");
        assertThat(resumeContextNode.path("pdfFileName").asText()).isEqualTo("resume.pdf");
        assertThat(resumeContextNode.path("resumeTextExcerpt").asText()).contains("Built backend services for order center");

        String sessionContextJson = jdbcTemplate.queryForObject(
                "SELECT session_context_json FROM interview_sessions WHERE session_id = ?",
                String.class,
                sessionId
        );
        assertThat(sessionContextJson).isNotBlank();

        JsonNode sessionContextNode = objectMapper.readTree(sessionContextJson);
        assertThat(sessionContextNode.path("interviewType").asText()).isEqualTo("PROJECT_DEEP_DIVE");
        assertThat(sessionContextNode.path("interviewerStyle").asText()).isEqualTo("COACHING");
        assertThat(sessionContextNode.path("difficulty").asText()).isEqualTo("HARD");
        assertThat(sessionContextNode.path("answerMode").asText()).isEqualTo("VOICE");
        assertThat(sessionContextNode.path("targetCompany").asText()).isEqualTo("字节跳动");
        assertThat(sessionContextNode.path("targetJobDescription").asText()).contains("负责核心后端服务研发");
        assertThat(objectMapper.convertValue(sessionContextNode.path("prepMaterialKeys"), new com.fasterxml.jackson.core.type.TypeReference<java.util.List<String>>() {
        })).containsExactly("LATEST_RESUME", "TARGET_COMPANY", "SKILL_TAGS");
        assertThat(sessionContextNode.path("answerHelperEnabled").asBoolean()).isTrue();
        assertThat(objectMapper.convertValue(sessionContextNode.path("answerHelperCueKeys"), new com.fasterxml.jackson.core.type.TypeReference<java.util.List<String>>() {
        })).containsExactly("METRICS", "COMPLETENESS");
        assertThat(sessionContextNode.path("promptContext").asText()).contains("技能关键词");

        mockMvc.perform(get("/api/v1/ai/interview/sessions/{sessionId}", sessionId)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sessionContext.interviewType").value("PROJECT_DEEP_DIVE"))
                .andExpect(jsonPath("$.data.sessionContext.interviewerStyle").value("COACHING"))
                .andExpect(jsonPath("$.data.sessionContext.difficulty").value("HARD"))
                .andExpect(jsonPath("$.data.sessionContext.answerMode").value("VOICE"))
                .andExpect(jsonPath("$.data.sessionContext.targetCompany").value("字节跳动"))
                .andExpect(jsonPath("$.data.sessionContext.targetJobDescription").value(org.hamcrest.Matchers.containsString("负责核心后端服务研发")))
                .andExpect(jsonPath("$.data.sessionContext.prepMaterialKeys.length()").value(3))
                .andExpect(jsonPath("$.data.sessionContext.prepMaterialKeys[0]").value("LATEST_RESUME"))
                .andExpect(jsonPath("$.data.sessionContext.prepMaterialKeys[1]").value("TARGET_COMPANY"))
                .andExpect(jsonPath("$.data.sessionContext.prepMaterialKeys[2]").value("SKILL_TAGS"))
                .andExpect(jsonPath("$.data.sessionContext.answerHelperEnabled").value(true))
                .andExpect(jsonPath("$.data.sessionContext.answerHelperCueKeys.length()").value(2))
                .andExpect(jsonPath("$.data.sessionContext.answerHelperCueKeys[0]").value("METRICS"))
                .andExpect(jsonPath("$.data.sessionContext.answerHelperCueKeys[1]").value("COMPLETENESS"))
                .andExpect(jsonPath("$.data.sessionContext.promptContext").value(org.hamcrest.Matchers.containsString("技能关键词")))
                .andExpect(jsonPath("$.data.resumeContext.recordId").value(recordId))
                .andExpect(jsonPath("$.data.resumeContext.inputMode").value("pdf"))
                .andExpect(jsonPath("$.data.resumeContext.pdfFileName").value("resume.pdf"))
                .andExpect(jsonPath("$.data.resumeContext.resumeTextExcerpt").value(org.hamcrest.Matchers.containsString("Built backend services for order center")));

        postInterviewReply(
                studentToken,
                sessionId,
                "我在这段经历里负责订单中心后端服务改造，主要做了缓存链路治理、接口压测和监控校验，最终把延迟降低了 30%。"
        )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.followUpQuestion").isNotEmpty())
                .andExpect(jsonPath("$.data.sessionStatus").value("ACTIVE"));
    }

    @Test
    void interviewSessionCreate_shouldReuseCachedResultForDuplicateRequest() throws Exception {
        registerUser("STUDENT", "interview-create-dedupe@example.com", "Passw0rd!", "InterviewCreateDedupe");
        String studentToken = loginAndGetAccessToken("interview-create-dedupe@example.com", "Passw0rd!");
        long userId = findUserIdByEmail("interview-create-dedupe@example.com");

        String body = """
                {
                  "targetRole": "Backend Engineer",
                  "mode": "INTERVIEW_TEXT",
                  "sessionContext": {
                    "interviewType": "PROJECT_DEEP_DIVE",
                    "interviewerStyle": "STANDARD",
                    "difficulty": "MEDIUM",
                    "answerMode": "TEXT",
                    "targetCompany": "字节跳动",
                    "targetJobDescription": "负责核心后端服务研发，要求熟悉 Java、MySQL、Redis 与性能优化。",
                    "prepMaterialKeys": ["TARGET_COMPANY", "TARGET_JD"],
                    "promptContext": "技能关键词：Java / Redis / MySQL"
                  }
                }
                """;

        MvcResult firstCreateResult = mockMvc.perform(post("/api/v1/ai/interview/sessions")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("session created"))
                .andExpect(jsonPath("$.data.firstQuestion").isNotEmpty())
                .andReturn();

        JsonNode firstCreateJson = objectMapper.readTree(firstCreateResult.getResponse().getContentAsString()).path("data");
        String firstSessionId = firstCreateJson.path("sessionId").asText();
        String firstQuestion = firstCreateJson.path("firstQuestion").asText();
        assertThat(firstSessionId).startsWith("is_");
        assertThat(firstQuestion).isNotBlank();

        mockMvc.perform(post("/api/v1/ai/interview/sessions")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("session created"))
                .andExpect(jsonPath("$.data.sessionId").value(firstSessionId))
                .andExpect(jsonPath("$.data.firstQuestion").value(firstQuestion))
                .andExpect(jsonPath("$.data.quotaUnitsReserved").value(5));

        Integer sessionCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM interview_sessions WHERE student_user_id = ? AND user_deleted_at IS NULL",
                Integer.class,
                userId
        );
        assertThat(sessionCount).isEqualTo(1);

        mockMvc.perform(get("/api/v1/ai/quota/remaining")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.quotas[?(@.taskType=='INTERVIEW_TEXT')].usedToday").value(org.hamcrest.Matchers.contains(5)));
    }

    @Test
    void interviewSessionCreate_shouldAllowMissingResumeContextWhenNoResumeSelected() throws Exception {
        registerUser("STUDENT", "interview-no-resume@example.com", "Passw0rd!", "InterviewNoResume");
        String studentToken = loginAndGetAccessToken("interview-no-resume@example.com", "Passw0rd!");

        MvcResult createResult = mockMvc.perform(post("/api/v1/ai/interview/sessions")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "targetRole": "Frontend Engineer",
                                  "mode": "INTERVIEW_TEXT",
                                  "sessionContext": {
                                    "interviewType": "PROJECT_DEEP_DIVE",
                                    "interviewerStyle": "STANDARD",
                                    "difficulty": "MEDIUM",
                                    "answerMode": "TEXT",
                                    "targetCompany": "美团",
                                    "targetJobDescription": "负责核心前端页面开发，要求熟悉 React、TypeScript 与工程化。",
                                    "prepMaterialKeys": ["TARGET_COMPANY", "TARGET_JD"],
                                    "answerHelperEnabled": true,
                                    "answerHelperCueKeys": ["STAR", "COMPLETENESS"],
                                    "promptContext": "目标企业：美团\\n岗位 JD：负责核心前端页面开发，要求熟悉 React、TypeScript 与工程化。"
                                  }
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sessionId").isNotEmpty())
                .andExpect(jsonPath("$.data.firstQuestion").isNotEmpty())
                .andReturn();

        String sessionId = objectMapper.readTree(createResult.getResponse().getContentAsString()).path("data").path("sessionId").asText();
        assertThat(sessionId).startsWith("is_");

        String resumeContextJson = jdbcTemplate.queryForObject(
                "SELECT resume_context_json FROM interview_sessions WHERE session_id = ?",
                String.class,
                sessionId
        );
        assertThat(resumeContextJson).isNull();

        mockMvc.perform(get("/api/v1/ai/interview/sessions/{sessionId}", sessionId)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.resumeContext").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.data.sessionContext.targetCompany").value("美团"))
                .andExpect(jsonPath("$.data.sessionContext.answerHelperEnabled").value(true))
                .andExpect(jsonPath("$.data.sessionContext.answerHelperCueKeys.length()").value(2));
    }

    @Test
    void resumeOptimizeAsyncTask_shouldAcceptAndExposeSucceededResult() throws Exception {
        registerUser("STUDENT", "resume-async@example.com", "Passw0rd!", "ResumeAsync");
        String studentToken = loginAndGetAccessToken("resume-async@example.com", "Passw0rd!");

        MvcResult submitResult = mockMvc.perform(post("/api/v1/ai/resume/tasks")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "targetRole": "Backend Engineer",
                                  "targetContext": "校招正式批",
                                  "jobDescription": "负责核心后端服务研发，要求熟悉 Java、MySQL、Redis 与性能优化。",
                                  "resumeText": "负责用户增长平台后端接口开发，重构缓存链路后接口延迟降低 30%，支撑日活 10 万。"
                                }
                                """))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.message").value("accepted"))
                .andExpect(jsonPath("$.data.taskType").value("RESUME"))
                .andExpect(jsonPath("$.data.sceneCode").value("RESUME_OPTIMIZE"))
                .andExpect(jsonPath("$.data.executionMode").value("ASYNC_JOB"))
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andReturn();

        String taskId = objectMapper.readTree(submitResult.getResponse().getContentAsString()).path("data").path("taskId").asText();
        assertThat(taskId).startsWith("aitk_");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ai_async_task_jobs WHERE task_id = ? AND task_type = 'RESUME'",
                Integer.class,
                taskId
        )).isEqualTo(1);

        aiAsyncTaskWorker.poll();

        mockMvc.perform(get("/api/v1/ai/tasks/{taskId}", taskId)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.taskId").value(taskId))
                .andExpect(jsonPath("$.data.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.data.terminal").value(true))
                .andExpect(jsonPath("$.data.resultPayload.summary").isNotEmpty())
                .andExpect(jsonPath("$.data.resultPayload.recordId").isNumber())
                .andExpect(jsonPath("$.data.linkedRecordId").isNumber());

        mockMvc.perform(get("/api/v1/ai/quota/remaining")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.quotas[?(@.taskType=='RESUME')].usedToday").value(org.hamcrest.Matchers.contains(1)));
    }

    @Test
    void resumeAsyncTask_shouldRejectCrossUserAccess() throws Exception {
        registerUser("STUDENT", "resume-async-owner@example.com", "Passw0rd!", "ResumeAsyncOwner");
        registerUser("STUDENT", "resume-async-visitor@example.com", "Passw0rd!", "ResumeAsyncVisitor");
        String ownerToken = loginAndGetAccessToken("resume-async-owner@example.com", "Passw0rd!");
        String visitorToken = loginAndGetAccessToken("resume-async-visitor@example.com", "Passw0rd!");

        MvcResult submitResult = mockMvc.perform(post("/api/v1/ai/resume/tasks")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "targetRole": "Backend Engineer",
                                  "targetContext": "校招正式批",
                                  "jobDescription": "负责核心后端服务研发，要求熟悉 Java、MySQL、Redis 与性能优化。",
                                  "resumeText": "负责用户增长平台后端接口开发，重构缓存链路后接口延迟降低 30%，支撑日活 10 万。"
                                }
                                """))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.message").value("accepted"))
                .andReturn();

        String taskId = objectMapper.readTree(submitResult.getResponse().getContentAsString()).path("data").path("taskId").asText();
        assertThat(taskId).startsWith("aitk_");

        mockMvc.perform(get("/api/v1/ai/tasks/{taskId}", taskId)
                        .header("Authorization", "Bearer " + visitorToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("async task not found"));
    }

    @Test
    void resumeExportPdf_shouldReturnPdfBytes() throws Exception {
        registerUser("STUDENT", "resume-export@example.com", "Passw0rd!", "ResumeExport");
        String studentToken = loginAndGetAccessToken("resume-export@example.com", "Passw0rd!");
        long userId = findUserIdByEmail("resume-export@example.com");

        MvcResult optimizeResult = mockMvc.perform(post("/api/v1/ai/resume/optimize")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "targetRole": "Backend Engineer",
                                  "targetContext": "校招提前批",
                                  "jobDescription": "负责核心后端服务研发，要求熟悉 Java、MySQL、Redis 与性能优化。",
                                  "resumeText": "负责订单中心后端服务与缓存优化，接口延迟下降 30%，并通过压测和监控验证结果。"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.recordId").isNumber())
                .andReturn();

        JsonNode optimizePayload = objectMapper.readTree(optimizeResult.getResponse().getContentAsString()).path("data");
        long recordId = optimizePayload.path("recordId").asLong();
        assertThat(recordId).isPositive();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ai_call_logs WHERE id = ? AND user_id = ? AND task_type = 'RESUME' AND status = 'SUCCESS'",
                Integer.class,
                recordId,
                userId
        )).isEqualTo(1);

        MvcResult result = mockMvc.perform(get("/api/v1/ai/resume/export/{recordId}", recordId)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(result.getResponse().getContentType()).contains(MediaType.APPLICATION_PDF_VALUE);
        byte[] pdfBytes = result.getResponse().getContentAsByteArray();
        assertThat(pdfBytes.length).isGreaterThan(256);
        assertThat(new String(pdfBytes, 0, 5, StandardCharsets.ISO_8859_1)).startsWith("%PDF-");
    }

    @Test
    void communityPreAnswerAndIcebreak_shouldGenerateDrafts() throws Exception {
        registerUser("MENTOR", "mentor-draft@example.com", "Passw0rd!", "MentorDraft");
        registerUser("STUDENT", "student-draft@example.com", "Passw0rd!", "StudentDraft");
        String mentorToken = loginAndGetAccessToken("mentor-draft@example.com", "Passw0rd!");
        String studentToken = loginAndGetAccessToken("student-draft@example.com", "Passw0rd!");
        long mentorUserId = findUserIdByEmail("mentor-draft@example.com");
        long studentUserId = findUserIdByEmail("student-draft@example.com");

        MvcResult createPostResult = mockMvc.perform(post("/api/v1/community/posts")
                        .header("Authorization", "Bearer " + mentorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "如何准备 Java 后端实习面试？",
                                  "content": "我目前在准备简历、项目表达和八股复习，想知道应该先补哪一块。",
                                  "tags": ["求职提问", "后端"]
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();

        long postId = objectMapper.readTree(createPostResult.getResponse().getContentAsString()).path("data").path("postId").asLong();
        assertThat(postId).isPositive();

        mockMvc.perform(post("/api/v1/ai/community/pre-answer")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(("""
                                {
                                  "postId": %d
                                }
                                """).formatted(postId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("generated"))
                .andExpect(jsonPath("$.data.tag").value("AI_GENERATED"))
                .andExpect(jsonPath("$.data.draftComment").isNotEmpty())
                .andExpect(jsonPath("$.data.moderation.action").value("PASS"));

        mockMvc.perform(post("/api/v1/ai/community/pre-answer")
                        .header("Authorization", "Bearer " + mentorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(("""
                                {
                                  "postId": %d
                                }
                                """).formatted(postId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.draftComment").isNotEmpty());

        mockMvc.perform(post("/api/v1/ai/icebreak-message")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(("""
                                {
                                  "mentorId": %d,
                                  "studentGoal": "希望获得后端实习准备建议"
                                }
                                """).formatted(mentorUserId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("generated"))
                .andExpect(jsonPath("$.data.messageDraft").isNotEmpty())
                .andExpect(jsonPath("$.data.moderation.action").value("PASS"));

        Integer communityReplyCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ai_call_logs WHERE task_type = 'COMMUNITY_REPLY' AND status = 'SUCCESS' AND user_id IN (?, ?)",
                Integer.class,
                studentUserId,
                mentorUserId
        );
        Integer icebreakCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ai_call_logs WHERE task_type = 'ICEBREAK' AND status = 'SUCCESS' AND user_id = ?",
                Integer.class,
                studentUserId
        );
        assertThat(communityReplyCount).isEqualTo(2);
        assertThat(icebreakCount).isEqualTo(1);
    }

    @Test
    void interviewReplyStream_shouldSendReplyDeltaEventsAndPersistConversation() throws Exception {
        registerUser("STUDENT", "interview-stream@example.com", "Passw0rd!", "InterviewStream");
        String studentToken = loginAndGetAccessToken("interview-stream@example.com", "Passw0rd!");

        MvcResult createResult = mockMvc.perform(post("/api/v1/ai/interview/sessions")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "targetRole": "Backend Engineer",
                                  "mode": "INTERVIEW_TEXT"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();

        String sessionId = objectMapper.readTree(createResult.getResponse().getContentAsString()).path("data").path("sessionId").asText();
        assertThat(sessionId).startsWith("is_");

        String body = objectMapper.writeValueAsString(java.util.Map.of(
                "answerText",
                "我主导了订单中心容量治理和缓存改造，核心接口延迟下降了 30%，并通过压测验证了结果。"
        ));
        MvcResult streamResult = mockMvc.perform(post("/api/v1/ai/interview/sessions/{sessionId}/reply/stream", sessionId)
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(request().asyncStarted())
                .andReturn();

        streamResult.getAsyncResult(5000);

        assertThat(streamResult.getResponse().getContentType()).contains(MediaType.TEXT_EVENT_STREAM_VALUE);
        String streamBody = streamResult.getResponse().getContentAsString();
        assertThat(streamBody).containsPattern("event:\\s*start");
        assertThat(streamBody).containsPattern("event:\\s*reply_delta");
        assertThat(streamBody).containsPattern("event:\\s*done");
        assertThat(streamBody).contains("\"delta\"");
        assertThat(streamBody).contains("\"result\"");

        mockMvc.perform(get("/api/v1/ai/interview/sessions/{sessionId}", sessionId)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.messages.length()").value(3))
                .andExpect(jsonPath("$.data.messages[1].role").value("USER"))
                .andExpect(jsonPath("$.data.messages[2].role").value("ASSISTANT"));
    }

    @Test
    void interviewVoiceRoundtripStream_shouldSendTranscriptAndReplyDeltaEvents() throws Exception {
        registerUser("STUDENT", "interview-voice-stream@example.com", "Passw0rd!", "InterviewVoiceStream");
        String studentToken = loginAndGetAccessToken("interview-voice-stream@example.com", "Passw0rd!");

        MvcResult createResult = mockMvc.perform(post("/api/v1/ai/interview/sessions")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "targetRole": "Backend Engineer",
                                  "mode": "INTERVIEW_VOICE"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();

        String sessionId = objectMapper.readTree(createResult.getResponse().getContentAsString()).path("data").path("sessionId").asText();
        assertThat(sessionId).startsWith("is_");

        MockMultipartFile audioFile = new MockMultipartFile(
                "audioFile",
                "answer.webm",
                "audio/webm",
                "fake-voice-binary".getBytes()
        );
        MvcResult streamResult = mockMvc.perform(multipart("/api/v1/ai/interview/sessions/{sessionId}/voice-roundtrip/stream", sessionId)
                        .file(audioFile)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(request().asyncStarted())
                .andReturn();

        streamResult.getAsyncResult(5000);

        assertThat(streamResult.getResponse().getContentType()).contains(MediaType.TEXT_EVENT_STREAM_VALUE);
        String streamBody = streamResult.getResponse().getContentAsString();
        assertThat(streamBody).containsPattern("event:\\s*start");
        assertThat(streamBody).containsPattern("event:\\s*transcript");
        assertThat(streamBody).containsPattern("event:\\s*reply_delta");
        assertThat(streamBody).containsPattern("event:\\s*done");
        assertThat(streamBody).contains("\"transcript\"");
        assertThat(streamBody).contains("\"audioObjectKey\"");

        Integer audioMessageCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM interview_messages WHERE audio_object_key IS NOT NULL AND session_pk = (SELECT id FROM interview_sessions WHERE session_id = ?)",
                Integer.class,
                sessionId
        );
        assertThat(audioMessageCount).isEqualTo(1);
    }

    @Test
    void interviewVoiceRoundtrip_shouldTranscribeReplyAndPersistAudioReference() throws Exception {
        registerUser("STUDENT", "interview-voice@example.com", "Passw0rd!", "InterviewVoice");
        String studentToken = loginAndGetAccessToken("interview-voice@example.com", "Passw0rd!");

        MvcResult createResult = mockMvc.perform(post("/api/v1/ai/interview/sessions")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "targetRole": "Backend Engineer",
                                  "mode": "INTERVIEW_VOICE"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mode").value("INTERVIEW_VOICE"))
                .andReturn();

        String sessionId = objectMapper.readTree(createResult.getResponse().getContentAsString()).path("data").path("sessionId").asText();
        assertThat(sessionId).startsWith("is_");

        MockMultipartFile audioFile = new MockMultipartFile(
                "audioFile",
                "answer.webm",
                "audio/webm",
                "fake-voice-binary".getBytes()
        );

        mockMvc.perform(multipart("/api/v1/ai/interview/sessions/{sessionId}/voice-roundtrip", sessionId)
                        .file(audioFile)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.transcript").isNotEmpty())
                .andExpect(jsonPath("$.data.audioObjectKey").value(org.hamcrest.Matchers.containsString("upload://interview/")))
                .andExpect(jsonPath("$.data.transcriptMeta.taskType").value("STT"))
                .andExpect(jsonPath("$.data.aiMeta.taskType").value("INTERVIEW_TEXT"))
                .andExpect(jsonPath("$.data.coachFeedback").isNotEmpty())
                .andExpect(jsonPath("$.data.sessionStatus").value("ACTIVE"));

        long voiceUserId = findUserIdByEmail("interview-voice@example.com");
        Integer sttLogCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ai_call_logs WHERE user_id = ? AND task_type = 'STT' AND scene_code = 'INTERVIEW_VOICE_TRANSCRIBE'",
                Integer.class,
                voiceUserId
        );
        assertThat(sttLogCount).isEqualTo(1);

        Integer audioMessageCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM interview_messages WHERE audio_object_key IS NOT NULL AND session_pk = (SELECT id FROM interview_sessions WHERE session_id = ?)",
                Integer.class,
                sessionId
        );
        assertThat(audioMessageCount).isEqualTo(1);

        mockMvc.perform(get("/api/v1/ai/interview/sessions/{sessionId}", sessionId)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mode").value("INTERVIEW_VOICE"))
                .andExpect(jsonPath("$.data.messages[1].audioObjectKey").isNotEmpty())
                .andExpect(jsonPath("$.data.messages[2].coachFeedback").isNotEmpty());
    }

    @Test
    void interviewLiveSession_shouldImportTranscriptAndGenerateSummary() throws Exception {
        registerUser("STUDENT", "interview-live@example.com", "Passw0rd!", "InterviewLive");
        String studentToken = loginAndGetAccessToken("interview-live@example.com", "Passw0rd!");
        long userId = findUserIdByEmail("interview-live@example.com");

        MvcResult createResult = mockMvc.perform(post("/api/v1/ai/interview/sessions")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "targetRole": "Backend Engineer",
                                  "mode": "INTERVIEW_TEXT",
                                  "sessionContext": {
                                    "interviewType": "PROJECT_DEEP_DIVE",
                                    "interviewerStyle": "STANDARD",
                                    "difficulty": "MEDIUM",
                                    "answerMode": "LIVE",
                                    "targetCompany": "字节跳动",
                                    "promptContext": "技能关键词：Java / Redis / MySQL"
                                  }
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mode").value("INTERVIEW_TEXT"))
                .andExpect(jsonPath("$.data.firstQuestion").value(""))
                .andReturn();

        String sessionId = objectMapper.readTree(createResult.getResponse().getContentAsString()).path("data").path("sessionId").asText();
        assertThat(sessionId).startsWith("is_");

        Integer liveInitLogCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ai_call_logs WHERE user_id = ? AND task_type = 'INTERVIEW_TEXT' AND scene_code = 'INTERVIEW_LIVE_TEST'",
                Integer.class,
                userId
        );
        assertThat(liveInitLogCount).isEqualTo(1);

        mockMvc.perform(post("/api/v1/ai/interview/sessions/{sessionId}/live-transcript", sessionId)
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "messages": [
                                    { "role": "ASSISTANT", "text": "先请你用一分钟介绍一下你做过的订单中心项目。" },
                                    { "role": "USER", "text": "我主要负责订单中心后端服务改造，重点做了缓存治理、索引优化和压测回归。" },
                                    { "role": "ASSISTANT", "text": "这里面的核心技术取舍是什么？为什么不是直接继续堆机器？" },
                                    { "role": "USER", "text": "因为当时瓶颈主要是热点查询和慢 SQL，先做缓存与索引优化能更快收敛问题，最终接口延迟降低了 30%。" },
                                    { "role": "ASSISTANT", "text": "好的，这一轮实时面试先到这里。整体表达比较完整，但量化细节还可以再更具体一些。" }
                                  ]
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/ai/interview/sessions/{sessionId}", sessionId)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sessionContext.answerMode").value("LIVE"))
                .andExpect(jsonPath("$.data.replyRoundUsed").value(2))
                .andExpect(jsonPath("$.data.messages.length()").value(5))
                .andExpect(jsonPath("$.data.messages[0].text").value(org.hamcrest.Matchers.containsString("订单中心项目")))
                .andExpect(jsonPath("$.data.messages[4].text").value(org.hamcrest.Matchers.containsString("整体表达比较完整")));

        mockMvc.perform(post("/api/v1/ai/interview/sessions/{sessionId}/summary", sessionId)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.overallScore").value(org.hamcrest.Matchers.greaterThanOrEqualTo(70)))
                .andExpect(jsonPath("$.data.suggestions[0]").isNotEmpty());

        mockMvc.perform(get("/api/v1/ai/interview/sessions/{sessionId}", sessionId)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.summary.overallScore").value(org.hamcrest.Matchers.greaterThanOrEqualTo(70)));
    }

    @Test
    void ttsSynthesize_shouldReturnBase64PcmAudioAndConsumeTtsQuota() throws Exception {
        registerUser("STUDENT", "tts-flow@example.com", "Passw0rd!", "TtsFlow");
        String studentToken = loginAndGetAccessToken("tts-flow@example.com", "Passw0rd!");

        mockMvc.perform(post("/api/v1/ai/tts/synthesize")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "text": "你好，这是一个用于测试 TTS 的文本。",
                                  "stylePrompt": "Read aloud in a warm and friendly tone:",
                                  "voiceName": "Zephyr"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.audioBase64").isNotEmpty())
                .andExpect(jsonPath("$.data.mimeType").value(org.hamcrest.Matchers.containsString("audio/L16")))
                .andExpect(jsonPath("$.data.sampleRate").value(24000))
                .andExpect(jsonPath("$.data.aiMeta.taskType").value("TTS"));

        mockMvc.perform(get("/api/v1/ai/quota/remaining")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.quotas[?(@.taskType=='TTS')].usedToday").value(org.hamcrest.Matchers.contains(1)));
    }

    @Test
    void interviewSessionTtsCache_shouldReuseSamePayloadAndClearBySession() throws Exception {
        registerUser("STUDENT", "tts-cache@example.com", "Passw0rd!", "TtsCache");
        String studentToken = loginAndGetAccessToken("tts-cache@example.com", "Passw0rd!");

        MvcResult createResult = mockMvc.perform(post("/api/v1/ai/interview/sessions")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "targetRole": "Backend Engineer",
                                  "mode": "INTERVIEW_TEXT"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();

        String sessionId = objectMapper.readTree(createResult.getResponse().getContentAsString()).path("data").path("sessionId").asText();
        assertThat(sessionId).startsWith("is_");

        String synthesizeBody = """
                {
                  "text": "你好，这是一个用于测试面试会话缓存的播报文本。",
                  "stylePrompt": "Read aloud in a warm and friendly tone:",
                  "voiceName": "Zephyr",
                  "sessionId": "%s"
                }
                """.formatted(sessionId);

        mockMvc.perform(post("/api/v1/ai/tts/synthesize")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(synthesizeBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.audioBase64").isNotEmpty());

        mockMvc.perform(post("/api/v1/ai/tts/synthesize")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(synthesizeBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.audioBase64").isNotEmpty());

        mockMvc.perform(get("/api/v1/ai/quota/remaining")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.quotas[?(@.taskType=='TTS')].usedToday").value(org.hamcrest.Matchers.contains(1)));

        mockMvc.perform(delete("/api/v1/ai/interview/sessions/{sessionId}/tts-cache", sessionId)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("tts cache cleared"));

        mockMvc.perform(post("/api/v1/ai/tts/synthesize")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(synthesizeBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.audioBase64").isNotEmpty());

        mockMvc.perform(get("/api/v1/ai/quota/remaining")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.quotas[?(@.taskType=='TTS')].usedToday").value(org.hamcrest.Matchers.contains(2)));
    }

    @Test
    void interviewFlow_shouldNotForceFinishAtThirdReply_andShouldAllowManualSummary_withSessionPackageQuota() throws Exception {
        registerUser("STUDENT", "interview-flow@example.com", "Passw0rd!", "InterviewFlow");
        String studentToken = loginAndGetAccessToken("interview-flow@example.com", "Passw0rd!");

        MvcResult createResult = mockMvc.perform(post("/api/v1/ai/interview/sessions")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "targetRole": "Backend Engineer",
                                  "mode": "INTERVIEW_TEXT"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("session created"))
                .andExpect(jsonPath("$.data.firstQuestion").isNotEmpty())
                .andExpect(jsonPath("$.data.chargedPoints").value(0))
                .andExpect(jsonPath("$.data.quotaUnitsReserved").value(5))
                .andExpect(jsonPath("$.data.replyRoundLimit").value(30))
                .andReturn();

        JsonNode createJson = objectMapper.readTree(createResult.getResponse().getContentAsString());
        String sessionId = createJson.path("data").path("sessionId").asText();
        assertThat(sessionId).startsWith("is_");

        postInterviewReply(studentToken, sessionId, "我负责过订单中心重构，围绕缓存与数据库读写路径做了性能优化，接口 P95 延迟降低 30%，并通过压测和线上监控验证了结果。")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.shouldFinish").value(false))
                .andExpect(jsonPath("$.data.scoreHint").value(org.hamcrest.Matchers.greaterThanOrEqualTo(78)));

        postInterviewReply(studentToken, sessionId, "在高并发场景下，我补充了线程池隔离、限流和重试保护，还针对热点 Key 做了缓存治理，最终把峰值吞吐提升到原来的 1.8 倍。")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.shouldFinish").value(false));

        postInterviewReply(studentToken, sessionId, "我还整理了复盘文档，说明为什么选择数据库索引优化、缓存预热和监控告警组合方案，并给出了结果、基线与技术权衡，团队后续可以直接复用。")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.shouldFinish").value(false))
                .andExpect(jsonPath("$.data.sessionStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.data.summary").doesNotExist());

        mockMvc.perform(get("/api/v1/ai/interview/sessions/{sessionId}", sessionId)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sessionId").value(sessionId))
                .andExpect(jsonPath("$.data.messages.length()").value(7))
                .andExpect(jsonPath("$.data.messages[2].coachFeedback").isNotEmpty())
                .andExpect(jsonPath("$.data.summary").doesNotExist());

        mockMvc.perform(post("/api/v1/ai/interview/sessions/{sessionId}/summary", sessionId)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.overallScore").value(org.hamcrest.Matchers.greaterThanOrEqualTo(70)))
                .andExpect(jsonPath("$.data.aiMeta.taskType").value("INTERVIEW_SUMMARY"));

        long summaryUserId = findUserIdByEmail("interview-flow@example.com");
        Integer interviewSummaryLogCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ai_call_logs WHERE user_id = ? AND task_type = 'INTERVIEW_SUMMARY' AND scene_code = 'INTERVIEW_SUMMARY'",
                Integer.class,
                summaryUserId
        );
        assertThat(interviewSummaryLogCount).isEqualTo(1);

        mockMvc.perform(get("/api/v1/ai/interview/sessions/{sessionId}", sessionId)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sessionId").value(sessionId))
                .andExpect(jsonPath("$.data.summary.overallScore").value(org.hamcrest.Matchers.greaterThanOrEqualTo(70)));

        mockMvc.perform(get("/api/v1/ai/quota/remaining")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.quotas[?(@.taskType=='INTERVIEW_TEXT')].usedToday").value(org.hamcrest.Matchers.contains(5)));
    }

    @Test
    void interviewAnswerHelper_shouldReturnStructuredAnalysisWithoutConsumingExtraQuota() throws Exception {
        registerUser("STUDENT", "interview-answer-helper@example.com", "Passw0rd!", "InterviewAnswerHelper");
        String studentToken = loginAndGetAccessToken("interview-answer-helper@example.com", "Passw0rd!");

        MvcResult createResult = mockMvc.perform(post("/api/v1/ai/interview/sessions")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "targetRole": "Backend Engineer",
                                  "mode": "INTERVIEW_TEXT",
                                  "sessionContext": {
                                    "answerHelperEnabled": true,
                                    "answerHelperCueKeys": ["METRICS", "COMPLETENESS"]
                                  }
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sessionId").isNotEmpty())
                .andExpect(jsonPath("$.data.replyRoundLimit").value(30))
                .andReturn();

        String sessionId = objectMapper.readTree(createResult.getResponse().getContentAsString()).path("data").path("sessionId").asText();
        assertThat(sessionId).startsWith("is_");

        postInterviewReply(
                studentToken,
                sessionId,
                "我负责订单中心重构，做了缓存预热、数据库索引优化和压测校验，把接口 P95 延迟降低了 30%，但还可以继续补充更完整的验证过程。"
        )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sessionStatus").value("ACTIVE"));

        MvcResult helperResult = mockMvc.perform(post("/api/v1/ai/interview/sessions/{sessionId}/answer-helper", sessionId)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.overallSummary").isNotEmpty())
                .andExpect(jsonPath("$.data.items.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.data.aiMeta.taskType").value("INTERVIEW_ANSWER_HELPER"))
                .andReturn();

        JsonNode helperData = objectMapper.readTree(helperResult.getResponse().getContentAsString()).path("data");
        assertThat(helperData.path("details").isArray()).isTrue();
        assertThat(helperData.path("details").size()).isGreaterThanOrEqualTo(1);
        assertThat(helperData.path("items").get(0).path("key").asText()).isIn("STAR", "METRICS", "COMPLETENESS");

        mockMvc.perform(get("/api/v1/ai/quota/remaining")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.quotas[?(@.taskType=='INTERVIEW_TEXT')].usedToday").value(org.hamcrest.Matchers.contains(5)));
    }

    @Test
    void interviewSessionReservation_shouldChargeOnceWhenFreeQuotaConsumed() throws Exception {
        registerUser("STUDENT", "interview-charge@example.com", "Passw0rd!", "InterviewCharge");
        String studentToken = loginAndGetAccessToken("interview-charge@example.com", "Passw0rd!");
        String adminToken = loginAndGetAccessToken("admin@test.local", "Admin123456!");
        long userId = findUserIdByEmail("interview-charge@example.com");

        consumeInterviewFreeQuota(studentToken);
        grantPoints(adminToken, userId, 25, "TEST_TOPUP");

        MvcResult createResult = mockMvc.perform(post("/api/v1/ai/interview/sessions")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "targetRole": "Backend Engineer",
                                  "mode": "INTERVIEW_TEXT"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.chargedPoints").value(25))
                .andExpect(jsonPath("$.data.pointsBalanceAfterReserve").value(0))
                .andExpect(jsonPath("$.data.quotaUnitsReserved").value(5))
                .andReturn();

        String sessionId = objectMapper.readTree(createResult.getResponse().getContentAsString()).path("data").path("sessionId").asText();
        assertThat(sessionId).isNotBlank();

        postInterviewReply(studentToken, sessionId, "我负责支付链路重构，通过缓存、索引和压测把延迟降低 30%，也补充了监控告警。")
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/ai/interview/sessions/{sessionId}", sessionId)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sessionId").value(sessionId))
                .andExpect(jsonPath("$.data.pointsBalance").value(0))
                .andExpect(jsonPath("$.data.prepaidPoints").value(25));

        Integer ledgerCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM points_ledger WHERE student_user_id = ? AND reason_code = 'AI_INTERVIEW_SESSION'",
                Integer.class,
                userId
        );
        Integer chargedPoints = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(delta_points), 0) FROM points_ledger WHERE student_user_id = ? AND reason_code = 'AI_INTERVIEW_SESSION'",
                Integer.class,
                userId
        );
        assertThat(ledgerCount).isEqualTo(1);
        assertThat(chargedPoints).isEqualTo(-25);

        mockMvc.perform(get("/api/v1/ai/quota/remaining")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.quotas[?(@.taskType=='INTERVIEW_TEXT')].usedToday").value(org.hamcrest.Matchers.contains(10)));
    }

    private org.springframework.test.web.servlet.ResultActions postInterviewReply(String studentToken, String sessionId, String answerText) throws Exception {
        String body = objectMapper.writeValueAsString(java.util.Map.of("answerText", answerText));
        return mockMvc.perform(post("/api/v1/ai/interview/sessions/{sessionId}/reply", sessionId)
                .header("Authorization", "Bearer " + studentToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private void consumeInterviewFreeQuota(String studentToken) throws Exception {
        String body = """
                {
                  "taskType": "INTERVIEW_TEXT",
                  "scene": "quota-setup"
                }
                """;
        for (int index = 0; index < 5; index++) {
            mockMvc.perform(post("/api/v1/ai/ping")
                            .header("Authorization", "Bearer " + studentToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk());
        }
    }

    private byte[] buildResumePdfBytes(String text) throws Exception {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                stream.beginText();
                stream.setLeading(16);
                stream.setFont(new org.apache.pdfbox.pdmodel.font.PDType1Font(org.apache.pdfbox.pdmodel.font.Standard14Fonts.FontName.HELVETICA), 12);
                stream.newLineAtOffset(50, 700);
                for (String line : text.split("\\n")) {
                    stream.showText(line);
                    stream.newLine();
                }
                stream.endText();
            }
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            document.save(outputStream);
            return outputStream.toByteArray();
        }
    }

    private void grantPoints(String adminToken, long userId, int points, String reasonCode) throws Exception {
        String body = String.format(
                "{" +
                        "\"userId\":%d," +
                        "\"points\":%d," +
                        "\"reasonCode\":\"%s\"" +
                        "}",
                userId,
                points,
                reasonCode
        );

        mockMvc.perform(post("/api/v1/admin/growth/points/grant")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("points granted"));
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

        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andReturn();

        long userId = objectMapper.readTree(result.getResponse().getContentAsString()).path("data").path("userId").asLong();
        if ("MENTOR".equals(role)) {
            jdbcTemplate.update(
                    "UPDATE mentor_profiles SET approval_status = 'APPROVED', updated_at = CURRENT_TIMESTAMP WHERE user_id = ?",
                    userId
            );
        }
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
}
