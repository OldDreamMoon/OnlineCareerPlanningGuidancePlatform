package com.bishe.server.ai.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:bishe_ai_gateway_bootstrap;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "ai.gateway.mode=ROUTED",
        "ai.gateway.allow-legacy-fallback=false",
        "ai.gateway.openai-compatible.base-url=https://newapi-proxy.example.com/v1",
        "ai.gateway.openai-compatible.api-key=test-bootstrap-key",
        "ai.gateway.openai-compatible.default-model=gemini-2.5-flash",
        "ai.gateway.openai-compatible.timeout-ms=15000",
        "ai.gateway.openai-compatible.max-retries=1",
        "ai.gateway.openai-compatible.temperature=0.2"
})
@ActiveProfiles("test")
@Transactional
class AiGatewaySystemBootstrapServiceTest {

    @Autowired
    private AiModelRouteRepository aiModelRouteRepository;

    @Autowired
    private PromptTemplateRepository promptTemplateRepository;

    @Autowired
    private AiProviderConfigRepository aiProviderConfigRepository;

    @Autowired
    private AiProviderModelRepository aiProviderModelRepository;

    @Autowired
    private AiRouteResolver routeResolver;

    @Autowired
    private AiGatewaySystemBootstrapService bootstrapService;

    @Test
    void routedMode_shouldBootstrapResumeProviderRouteAndPromptTemplate() {
        AiProviderConfigRepository.ProviderConfigRow provider = aiProviderConfigRepository.findProviderByCode(AiGatewaySystemBootstrapService.RESUME_PROVIDER_CODE)
                .orElseThrow();
        assertThat(provider.providerType()).isEqualTo(AiProviderType.GEMINI_NATIVE.name());
        assertThat(provider.baseUrl()).isEqualTo("https://newapi-proxy.example.com/v1");
        assertThat(provider.apiKeyCiphertext()).isNotBlank();
        assertThat(provider.apiKeyMasked()).contains("***");
        assertThat(provider.timeoutMs()).isEqualTo(AiGatewaySystemBootstrapService.MIN_RESUME_PROVIDER_TIMEOUT_MS);
        assertThat(provider.extraConfigJson()).contains("\"authMode\":\"BEARER_TOKEN\"");
        List<AiProviderModelRepository.ProviderModelRow> providerModels = aiProviderModelRepository.findProviderModelsByProviderId(provider.id());
        assertThat(providerModels).hasSize(1);
        assertThat(providerModels.getFirst().modelCode()).isEqualTo("gemini-2.5-flash");
        assertThat(providerModels.getFirst().inputCostPer1k()).isEqualByComparingTo("0.002048");
        assertThat(providerModels.getFirst().outputCostPer1k()).isEqualByComparingTo("0.017070");
        assertThat(providerModels.getFirst().supportedTaskTypesJson()).contains("RESUME");

        PromptTemplateRepository.PromptTemplateRow template = promptTemplateRepository.findActivePromptTemplate(
                        AiGatewaySystemBootstrapService.RESUME_TASK_TYPE,
                        AiGatewaySystemBootstrapService.RESUME_TEMPLATE_NAME
                )
                .orElseThrow();
        assertThat(template.versionNo()).isEqualTo(AiGatewaySystemBootstrapService.RESUME_TEMPLATE_VERSION);
        assertThat(template.content()).contains("scoreLabel");
        assertThat(template.variablesJson()).contains("targetRole");
        assertThat(template.variablesJson()).doesNotContain("language");

        AiModelRouteRepository.ModelRouteRow route = aiModelRouteRepository.findRouteByCode(AiGatewaySystemBootstrapService.RESUME_ROUTE_CODE)
                .orElseThrow();
        assertThat(route.taskType()).isEqualTo(AiGatewaySystemBootstrapService.RESUME_TASK_TYPE);
        assertThat(route.sceneCode()).isEqualTo(AiGatewaySystemBootstrapService.RESUME_SCENE_CODE);
        assertThat(route.priorityNo()).isEqualTo(AiGatewaySystemBootstrapService.RESUME_ROUTE_PRIORITY);
        assertThat(route.executionMode()).isEqualTo(AiExecutionMode.SYNC_BLOCKING.name());
        assertThat(route.promptTemplateName()).isEqualTo(AiGatewaySystemBootstrapService.RESUME_TEMPLATE_NAME);
        assertThat(route.providerConfigId()).isEqualTo(provider.id());

        AiProviderInvocation invocation = routeResolver.resolve(
                AiGatewaySystemBootstrapService.RESUME_TASK_TYPE,
                AiGatewaySystemBootstrapService.RESUME_SCENE_CODE,
                null,
                Map.of(
                        "targetRole", "Backend Engineer",
                        "targetContext", "校招正式批",
                        "inputMode", "text",
                        "jobDescription", "负责核心后端服务研发",
                        "resumeFileName", "resume.pdf"
                )
        );

        assertThat(invocation.routeCode()).isEqualTo(AiGatewaySystemBootstrapService.RESUME_ROUTE_CODE);
        assertThat(invocation.providerType()).isEqualTo(AiProviderType.GEMINI_NATIVE);
        assertThat(invocation.executionMode()).isEqualTo(AiExecutionMode.SYNC_BLOCKING);
        assertThat(invocation.promptTemplateName()).isEqualTo(AiGatewaySystemBootstrapService.RESUME_TEMPLATE_NAME);
        assertThat(invocation.promptTemplateVersionNo()).isEqualTo(AiGatewaySystemBootstrapService.RESUME_TEMPLATE_VERSION);
        assertThat(invocation.timeout().toMillis()).isEqualTo(AiGatewaySystemBootstrapService.MIN_RESUME_PROVIDER_TIMEOUT_MS);
        assertThat(invocation.systemPrompt()).contains("Backend Engineer");
        assertThat(invocation.systemPrompt()).contains("校招正式批");
        assertThat(invocation.costPer1kInput()).isEqualByComparingTo("0.002048");
        assertThat(invocation.costPer1kOutput()).isEqualByComparingTo("0.017070");
    }

    @Test
    void routedMode_shouldBootstrapInterviewRoutesAndPromptTemplates() {
        AiProviderConfigRepository.ProviderConfigRow provider = aiProviderConfigRepository.findProviderByCode(
                        AiGatewaySystemBootstrapService.INTERVIEW_PROVIDER_CODE
                )
                .orElseThrow();
        assertThat(provider.providerType()).isEqualTo(AiProviderType.GEMINI_NATIVE.name());
        assertThat(provider.timeoutMs()).isEqualTo(AiGatewaySystemBootstrapService.MIN_INTERVIEW_PROVIDER_TIMEOUT_MS);
        assertThat(provider.extraConfigJson()).contains("\"authMode\":\"BEARER_TOKEN\"");
        List<AiProviderModelRepository.ProviderModelRow> providerModels = aiProviderModelRepository.findProviderModelsByProviderId(provider.id());
        assertThat(providerModels)
                .extracting(AiProviderModelRepository.ProviderModelRow::modelCode)
                .containsExactlyInAnyOrder("gemini-2.5-flash", AiGatewaySystemBootstrapService.DEFAULT_GEMINI_TTS_MODEL);
        AiProviderModelRepository.ProviderModelRow textModel = providerModels.stream()
                .filter(model -> "gemini-2.5-flash".equals(model.modelCode()))
                .findFirst()
                .orElseThrow();
        AiProviderModelRepository.ProviderModelRow ttsModel = providerModels.stream()
                .filter(model -> AiGatewaySystemBootstrapService.DEFAULT_GEMINI_TTS_MODEL.equals(model.modelCode()))
                .findFirst()
                .orElseThrow();
        assertThat(textModel.inputCostPer1k()).isEqualByComparingTo("0.002048");
        assertThat(textModel.outputCostPer1k()).isEqualByComparingTo("0.017070");
        assertThat(textModel.supportedTaskTypesJson()).contains("INTERVIEW_TEXT");
        assertThat(textModel.supportedTaskTypesJson()).contains("INTERVIEW_SUMMARY");
        assertThat(textModel.supportedTaskTypesJson()).contains("STT");
        assertThat(ttsModel.inputCostPer1k()).isEqualByComparingTo("0.003414");
        assertThat(ttsModel.outputCostPer1k()).isEqualByComparingTo("0.068280");
        assertThat(ttsModel.supportedTaskTypesJson()).contains("TTS");

        PromptTemplateRepository.PromptTemplateRow openingTemplate = promptTemplateRepository.findActivePromptTemplate(
                        AiGatewaySystemBootstrapService.INTERVIEW_TEXT_TASK_TYPE,
                        AiGatewaySystemBootstrapService.INTERVIEW_OPENING_TEMPLATE_NAME
                )
                .orElseThrow();
        PromptTemplateRepository.PromptTemplateRow replyTemplate = promptTemplateRepository.findActivePromptTemplate(
                        AiGatewaySystemBootstrapService.INTERVIEW_TEXT_TASK_TYPE,
                        AiGatewaySystemBootstrapService.INTERVIEW_REPLY_TEMPLATE_NAME
                )
                .orElseThrow();
        PromptTemplateRepository.PromptTemplateRow summaryTemplate = promptTemplateRepository.findActivePromptTemplate(
                        AiGatewaySystemBootstrapService.INTERVIEW_SUMMARY_TASK_TYPE,
                        AiGatewaySystemBootstrapService.INTERVIEW_SUMMARY_TEMPLATE_NAME
                )
                .orElseThrow();
        assertThat(openingTemplate.versionNo()).isEqualTo(AiGatewaySystemBootstrapService.INTERVIEW_TEMPLATE_VERSION);
        assertThat(replyTemplate.content()).contains("followUpQuestion");
        assertThat(summaryTemplate.content()).contains("overallScore");

        AiModelRouteRepository.ModelRouteRow openingRoute = aiModelRouteRepository.findRouteByCode(
                AiGatewaySystemBootstrapService.INTERVIEW_OPENING_ROUTE_CODE
        ).orElseThrow();
        AiModelRouteRepository.ModelRouteRow replyRoute = aiModelRouteRepository.findRouteByCode(
                AiGatewaySystemBootstrapService.INTERVIEW_REPLY_ROUTE_CODE
        ).orElseThrow();
        AiModelRouteRepository.ModelRouteRow summaryRoute = aiModelRouteRepository.findRouteByCode(
                AiGatewaySystemBootstrapService.INTERVIEW_SUMMARY_ROUTE_CODE
        ).orElseThrow();
        AiModelRouteRepository.ModelRouteRow sttRoute = aiModelRouteRepository.findRouteByCode(
                AiGatewaySystemBootstrapService.INTERVIEW_STT_ROUTE_CODE
        ).orElseThrow();
        AiModelRouteRepository.ModelRouteRow ttsRoute = aiModelRouteRepository.findRouteByCode(
                AiGatewaySystemBootstrapService.INTERVIEW_TTS_ROUTE_CODE
        ).orElseThrow();

        assertThat(openingRoute.providerConfigId()).isEqualTo(provider.id());
        assertThat(replyRoute.promptTemplateName()).isEqualTo(AiGatewaySystemBootstrapService.INTERVIEW_REPLY_TEMPLATE_NAME);
        assertThat(replyRoute.executionMode()).isEqualTo(AiExecutionMode.STREAM_SSE.name());
        assertThat(summaryRoute.sceneCode()).isEqualTo(AiGatewaySystemBootstrapService.INTERVIEW_SUMMARY_SCENE_CODE);
        assertThat(sttRoute.sceneCode()).isEqualTo(AiGatewaySystemBootstrapService.INTERVIEW_STT_SCENE_CODE);
        assertThat(ttsRoute.modelName()).isEqualTo(AiGatewaySystemBootstrapService.DEFAULT_GEMINI_TTS_MODEL);

        AiProviderInvocation openingInvocation = routeResolver.resolve(
                AiGatewaySystemBootstrapService.INTERVIEW_TEXT_TASK_TYPE,
                AiGatewaySystemBootstrapService.INTERVIEW_OPENING_SCENE_CODE,
                null,
                Map.of(
                        "targetRole", "Backend Engineer",
                        "sessionContext", "interviewType=PROJECT_DEEP_DIVE\ninterviewerStyle=COACHING\ndifficulty=MEDIUM",
                        "resumeContext", "候选人已授权带入最近一份简历\n简历摘要=订单中心重构经验"
                )
        );
        AiProviderInvocation replyInvocation = routeResolver.resolve(
                AiGatewaySystemBootstrapService.INTERVIEW_TEXT_TASK_TYPE,
                AiGatewaySystemBootstrapService.INTERVIEW_REPLY_SCENE_CODE,
                null,
                Map.of(
                        "targetRole", "Backend Engineer",
                        "sessionContext", "interviewType=PROJECT_DEEP_DIVE\ninterviewerStyle=COACHING\ndifficulty=MEDIUM",
                        "resumeContext", "候选人已授权带入最近一份简历\n简历摘要=订单中心重构经验",
                        "historyLines", "ASSISTANT: 请介绍一个项目",
                        "currentAnswer", "我主导了订单中心重构",
                        "userAnswerCount", 1
                )
        );
        AiProviderInvocation summaryInvocation = routeResolver.resolve(
                AiGatewaySystemBootstrapService.INTERVIEW_SUMMARY_TASK_TYPE,
                AiGatewaySystemBootstrapService.INTERVIEW_SUMMARY_SCENE_CODE,
                null,
                Map.of(
                        "targetRole", "Backend Engineer",
                        "sessionContext", "interviewType=PROJECT_DEEP_DIVE\ninterviewerStyle=STANDARD\ndifficulty=HARD",
                        "resumeContext", "候选人已授权带入最近一份简历\n简历摘要=订单中心重构经验",
                        "historyLines", "ASSISTANT: 请介绍一个项目\nUSER: 我主导了订单中心重构"
                )
        );
        AiProviderInvocation sttInvocation = routeResolver.resolve(
                AiGatewaySystemBootstrapService.STT_TASK_TYPE,
                AiGatewaySystemBootstrapService.INTERVIEW_STT_SCENE_CODE,
                null
        );
        AiProviderInvocation ttsInvocation = routeResolver.resolve(
                AiGatewaySystemBootstrapService.TTS_TASK_TYPE,
                AiGatewaySystemBootstrapService.INTERVIEW_TTS_SCENE_CODE,
                null
        );

        assertThat(openingInvocation.routeCode()).isEqualTo(AiGatewaySystemBootstrapService.INTERVIEW_OPENING_ROUTE_CODE);
        assertThat(openingInvocation.systemPrompt()).contains("Backend Engineer");
        assertThat(openingInvocation.systemPrompt()).contains("sessionContext");
        assertThat(openingInvocation.systemPrompt()).contains("resumeContext");
        assertThat(replyInvocation.routeCode()).isEqualTo(AiGatewaySystemBootstrapService.INTERVIEW_REPLY_ROUTE_CODE);
        assertThat(replyInvocation.executionMode()).isEqualTo(AiExecutionMode.STREAM_SSE);
        assertThat(replyInvocation.promptTemplateName()).isEqualTo(AiGatewaySystemBootstrapService.INTERVIEW_REPLY_TEMPLATE_NAME);
        assertThat(replyInvocation.systemPrompt()).contains("COACHING");
        assertThat(summaryInvocation.routeCode()).isEqualTo(AiGatewaySystemBootstrapService.INTERVIEW_SUMMARY_ROUTE_CODE);
        assertThat(summaryInvocation.systemPrompt()).contains("resumeContext");
        assertThat(sttInvocation.routeCode()).isEqualTo(AiGatewaySystemBootstrapService.INTERVIEW_STT_ROUTE_CODE);
        assertThat(sttInvocation.costPer1kInput()).isEqualByComparingTo("0.006828");
        assertThat(sttInvocation.costPer1kOutput()).isEqualByComparingTo("0.017070");
        assertThat(ttsInvocation.routeCode()).isEqualTo(AiGatewaySystemBootstrapService.INTERVIEW_TTS_ROUTE_CODE);
        assertThat(ttsInvocation.model()).isEqualTo(AiGatewaySystemBootstrapService.DEFAULT_GEMINI_TTS_MODEL);
        assertThat(ttsInvocation.costPer1kInput()).isEqualByComparingTo("0.003414");
        assertThat(ttsInvocation.costPer1kOutput()).isEqualByComparingTo("0.068280");
    }

    @Test
    void routedMode_shouldBootstrapMentorPrepRouteAndPromptTemplate() {
        AiProviderConfigRepository.ProviderConfigRow provider = aiProviderConfigRepository.findProviderByCode(
                        AiGatewaySystemBootstrapService.MENTOR_PREP_PROVIDER_CODE
                )
                .orElseThrow();
        assertThat(provider.providerType()).isEqualTo(AiProviderType.GEMINI_NATIVE.name());
        assertThat(provider.timeoutMs()).isEqualTo(AiGatewaySystemBootstrapService.MIN_MENTOR_PREP_PROVIDER_TIMEOUT_MS);

        PromptTemplateRepository.PromptTemplateRow template = promptTemplateRepository.findActivePromptTemplate(
                        AiGatewaySystemBootstrapService.MENTOR_PREP_TASK_TYPE,
                        AiGatewaySystemBootstrapService.MENTOR_PREP_TEMPLATE_NAME
                )
                .orElseThrow();
        assertThat(template.versionNo()).isEqualTo(AiGatewaySystemBootstrapService.MENTOR_PREP_TEMPLATE_VERSION);
        assertThat(template.content()).contains("summaryDraft");
        assertThat(template.variablesJson()).contains("latestResumeContext");
        assertThat(template.variablesJson()).contains("latestInterviewSummaryContext");

        AiModelRouteRepository.ModelRouteRow route = aiModelRouteRepository.findRouteByCode(
                AiGatewaySystemBootstrapService.MENTOR_PREP_ROUTE_CODE
        ).orElseThrow();
        assertThat(route.taskType()).isEqualTo(AiGatewaySystemBootstrapService.MENTOR_PREP_TASK_TYPE);
        assertThat(route.sceneCode()).isEqualTo(AiGatewaySystemBootstrapService.MENTOR_PREP_SCENE_CODE);
        assertThat(route.promptTemplateName()).isEqualTo(AiGatewaySystemBootstrapService.MENTOR_PREP_TEMPLATE_NAME);
        assertThat(route.providerConfigId()).isEqualTo(provider.id());

        java.util.LinkedHashMap<String, Object> promptVariables = new java.util.LinkedHashMap<>();
        promptVariables.put("scene", "简历诊断");
        promptVariables.put("targetPosition", "前端开发工程师");
        promptVariables.put("signalTags", "问题场景：简历诊断 | 目标岗位：前端开发工程师");
        promptVariables.put("mentorContext", "导师：王老师\n公司：字节跳动\n职位：高级前端工程师");
        promptVariables.put("studentContext", "目标岗位：前端开发工程师\n技能标签：React、TypeScript");
        promptVariables.put("latestResumeContext", "最近简历总结：量化结果不足");
        promptVariables.put("latestInterviewSummaryContext", "最近面试短板：项目表达偏空");
        promptVariables.put("fallbackSummaryDraft", "我目前正在准备前端开发工程师方向的求职。");
        promptVariables.put("fallbackCoreQuestions", "1. 简历里哪些内容最需要补强？");
        promptVariables.put("fallbackSuggestedMaterials", "我的最新简历 | 目标岗位 JD | 项目介绍");
        promptVariables.put("fallbackExpectedOutcomes", "获得简历修改建议 | 获得综合咨询建议");

        AiProviderInvocation invocation = routeResolver.resolve(
                AiGatewaySystemBootstrapService.MENTOR_PREP_TASK_TYPE,
                AiGatewaySystemBootstrapService.MENTOR_PREP_SCENE_CODE,
                null,
                promptVariables
        );

        assertThat(invocation.routeCode()).isEqualTo(AiGatewaySystemBootstrapService.MENTOR_PREP_ROUTE_CODE);
        assertThat(invocation.promptTemplateName()).isEqualTo(AiGatewaySystemBootstrapService.MENTOR_PREP_TEMPLATE_NAME);
        assertThat(invocation.systemPrompt()).contains("latestResumeContext");
        assertThat(invocation.systemPrompt()).contains("前端开发工程师");
        assertThat(invocation.systemPrompt()).contains("最近面试短板");
        assertThat(invocation.costPer1kInput()).isEqualByComparingTo("0.002048");
        assertThat(invocation.costPer1kOutput()).isEqualByComparingTo("0.017070");
    }

    @Test
    void routedMode_shouldBootstrapPortraitSummaryRouteAndPromptTemplate() {
        AiProviderConfigRepository.ProviderConfigRow provider = aiProviderConfigRepository.findProviderByCode(
                        AiGatewaySystemBootstrapService.MENTOR_PREP_PROVIDER_CODE
                )
                .orElseThrow();
        List<AiProviderModelRepository.ProviderModelRow> providerModels = aiProviderModelRepository.findProviderModelsByProviderId(provider.id());
        assertThat(providerModels).isNotEmpty();
        assertThat(providerModels.stream().anyMatch(model -> model.supportedTaskTypesJson().contains("PORTRAIT_SUMMARY"))).isTrue();

        PromptTemplateRepository.PromptTemplateRow template = promptTemplateRepository.findActivePromptTemplate(
                        AiGatewaySystemBootstrapService.PORTRAIT_SUMMARY_TASK_TYPE,
                        AiGatewaySystemBootstrapService.PORTRAIT_SUMMARY_TEMPLATE_NAME
                )
                .orElseThrow();
        assertThat(template.versionNo()).isEqualTo(AiGatewaySystemBootstrapService.PORTRAIT_SUMMARY_TEMPLATE_VERSION);
        assertThat(template.content()).contains("fallbackHeadline");
        assertThat(template.variablesJson()).contains("strengthTags");
        assertThat(template.variablesJson()).contains("riskTags");

        AiModelRouteRepository.ModelRouteRow route = aiModelRouteRepository.findRouteByCode(
                AiGatewaySystemBootstrapService.PORTRAIT_SUMMARY_ROUTE_CODE
        ).orElseThrow();
        assertThat(route.taskType()).isEqualTo(AiGatewaySystemBootstrapService.PORTRAIT_SUMMARY_TASK_TYPE);
        assertThat(route.sceneCode()).isEqualTo(AiGatewaySystemBootstrapService.PORTRAIT_SUMMARY_SCENE_CODE);
        assertThat(route.promptTemplateName()).isEqualTo(AiGatewaySystemBootstrapService.PORTRAIT_SUMMARY_TEMPLATE_NAME);
        assertThat(route.providerConfigId()).isEqualTo(provider.id());

        AiProviderInvocation invocation = routeResolver.resolve(
                AiGatewaySystemBootstrapService.PORTRAIT_SUMMARY_TASK_TYPE,
                AiGatewaySystemBootstrapService.PORTRAIT_SUMMARY_SCENE_CODE,
                null,
                Map.of(
                        "targetPosition", "Java 后端开发实习生",
                        "strengthTags", "技能成长清晰 | 社区互动积极",
                        "riskTags", "系统设计表达待补强",
                        "signalLevel", "NORMAL",
                        "freshnessLevel", "FRESH",
                        "evidenceContext", "目标岗位：Java 后端开发实习生\n已掌握技能：2 项\n近 7 天面试消息：5 条",
                        "fallbackHeadline", "你在后端方向已经形成初步优势，当前最需要补的是系统设计表达。",
                        "fallbackSummary", "当前画像聚焦在后端方向，已有技能成长与面试练习信号。",
                        "fallbackNextActions", "补 1 个系统设计案例 | 复盘最近一轮面试"
                )
        );

        assertThat(invocation.routeCode()).isEqualTo(AiGatewaySystemBootstrapService.PORTRAIT_SUMMARY_ROUTE_CODE);
        assertThat(invocation.promptTemplateName()).isEqualTo(AiGatewaySystemBootstrapService.PORTRAIT_SUMMARY_TEMPLATE_NAME);
        assertThat(invocation.systemPrompt()).contains("strengthTags");
        assertThat(invocation.systemPrompt()).contains("Java 后端开发实习生");
        assertThat(invocation.systemPrompt()).contains("fallbackHeadline");
    }

    @Test
    void routedMode_shouldBootstrapCommunityPreAnswerAndIcebreakRoutes() {
        AiProviderConfigRepository.ProviderConfigRow provider = aiProviderConfigRepository.findProviderByCode(
                        AiGatewaySystemBootstrapService.MENTOR_PREP_PROVIDER_CODE
                )
                .orElseThrow();

        PromptTemplateRepository.PromptTemplateRow communityTemplate = promptTemplateRepository.findActivePromptTemplate(
                        AiGatewaySystemBootstrapService.MENTOR_PREP_TASK_TYPE,
                        AiGatewaySystemBootstrapService.COMMUNITY_PRE_ANSWER_TEMPLATE_NAME
                )
                .orElseThrow();
        PromptTemplateRepository.PromptTemplateRow icebreakTemplate = promptTemplateRepository.findActivePromptTemplate(
                        AiGatewaySystemBootstrapService.ICEBREAK_TASK_TYPE,
                        AiGatewaySystemBootstrapService.ICEBREAK_TEMPLATE_NAME
                )
                .orElseThrow();

        assertThat(communityTemplate.versionNo()).isEqualTo(AiGatewaySystemBootstrapService.COMMUNITY_PRE_ANSWER_TEMPLATE_VERSION);
        assertThat(communityTemplate.content()).contains("draftComment");
        assertThat(communityTemplate.variablesJson()).contains("generationMode");
        assertThat(communityTemplate.variablesJson()).contains("currentDraft");
        assertThat(icebreakTemplate.versionNo()).isEqualTo(AiGatewaySystemBootstrapService.ICEBREAK_TEMPLATE_VERSION);
        assertThat(icebreakTemplate.content()).contains("messageDraft");
        assertThat(icebreakTemplate.variablesJson()).contains("mentorDisplayName");
        assertThat(icebreakTemplate.variablesJson()).contains("studentGoal");

        AiModelRouteRepository.ModelRouteRow communityRoute = aiModelRouteRepository.findRouteByCode(
                AiGatewaySystemBootstrapService.COMMUNITY_PRE_ANSWER_ROUTE_CODE
        ).orElseThrow();
        AiModelRouteRepository.ModelRouteRow icebreakRoute = aiModelRouteRepository.findRouteByCode(
                AiGatewaySystemBootstrapService.ICEBREAK_ROUTE_CODE
        ).orElseThrow();

        assertThat(communityRoute.providerConfigId()).isEqualTo(provider.id());
        assertThat(communityRoute.sceneCode()).isEqualTo(AiGatewaySystemBootstrapService.COMMUNITY_PRE_ANSWER_SCENE_CODE);
        assertThat(communityRoute.promptTemplateName()).isEqualTo(AiGatewaySystemBootstrapService.COMMUNITY_PRE_ANSWER_TEMPLATE_NAME);
        assertThat(icebreakRoute.providerConfigId()).isEqualTo(provider.id());
        assertThat(icebreakRoute.sceneCode()).isEqualTo(AiGatewaySystemBootstrapService.ICEBREAK_SCENE_CODE);
        assertThat(icebreakRoute.promptTemplateName()).isEqualTo(AiGatewaySystemBootstrapService.ICEBREAK_TEMPLATE_NAME);

        AiProviderInvocation communityInvocation = routeResolver.resolve(
                AiGatewaySystemBootstrapService.MENTOR_PREP_TASK_TYPE,
                AiGatewaySystemBootstrapService.COMMUNITY_PRE_ANSWER_SCENE_CODE,
                null,
                Map.of(
                        "generationMode", "POLISH_EXISTING",
                        "title", "导师咨询问题",
                        "context", "学生希望老师先判断简历项目表达",
                        "currentDraft", "老师您好，我先整理一下情况。",
                        "instruction", "语气再温和一点"
                )
        );
        AiProviderInvocation icebreakInvocation = routeResolver.resolve(
                AiGatewaySystemBootstrapService.ICEBREAK_TASK_TYPE,
                AiGatewaySystemBootstrapService.ICEBREAK_SCENE_CODE,
                null,
                Map.of(
                        "mentorDisplayName", "王老师",
                        "expertiseTags", "前端, React, 校招",
                        "mentorBio", "多年大厂前端经验",
                        "studentGoal", "想请教前端项目表达"
                )
        );

        assertThat(communityInvocation.routeCode()).isEqualTo(AiGatewaySystemBootstrapService.COMMUNITY_PRE_ANSWER_ROUTE_CODE);
        assertThat(communityInvocation.promptTemplateName()).isEqualTo(AiGatewaySystemBootstrapService.COMMUNITY_PRE_ANSWER_TEMPLATE_NAME);
        assertThat(communityInvocation.systemPrompt()).contains("generationMode");
        assertThat(communityInvocation.systemPrompt()).contains("currentDraft");
        assertThat(icebreakInvocation.routeCode()).isEqualTo(AiGatewaySystemBootstrapService.ICEBREAK_ROUTE_CODE);
        assertThat(icebreakInvocation.promptTemplateName()).isEqualTo(AiGatewaySystemBootstrapService.ICEBREAK_TEMPLATE_NAME);
        assertThat(icebreakInvocation.systemPrompt()).contains("mentorDisplayName");
        assertThat(icebreakInvocation.systemPrompt()).contains("studentGoal");
    }

    @Test
    void routedMode_shouldDeactivateLegacyResumeRouteAndTemplateAfterBootstrap() throws Exception {
        long legacyProviderId = aiProviderConfigRepository.insertProvider(
                "legacy_resume_openai",
                AiProviderType.OPENAI_COMPATIBLE.name(),
                "Legacy Resume OpenAI",
                "https://legacy.example.com/v1",
                "ciphertext",
                "***legacy(len=10)",
                true,
                15000,
                1,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                null
        );
        aiModelRouteRepository.insertRoute(
                AiGatewaySystemBootstrapService.LEGACY_RESUME_PRIMARY_ROUTE_CODE,
                AiGatewaySystemBootstrapService.RESUME_TASK_TYPE,
                AiGatewaySystemBootstrapService.RESUME_SCENE_CODE,
                null,
                legacyProviderId,
                "gemini-2.5-flash",
                10,
                100,
                AiExecutionMode.SYNC_BLOCKING.name(),
                true,
                BigDecimal.valueOf(0.2d),
                null,
                null,
                "{\"purpose\":\"legacy\"}"
        );
        aiModelRouteRepository.insertRoute(
                AiGatewaySystemBootstrapService.LEGACY_RESUME_FALLBACK_ROUTE_CODE,
                AiGatewaySystemBootstrapService.RESUME_TASK_TYPE,
                null,
                null,
                legacyProviderId,
                "gemini-2.5-flash",
                100,
                100,
                AiExecutionMode.SYNC_BLOCKING.name(),
                true,
                BigDecimal.valueOf(0.2d),
                null,
                null,
                "{\"scope\":\"fallback\"}"
        );
        promptTemplateRepository.insertPromptTemplate(
                AiGatewaySystemBootstrapService.RESUME_TASK_TYPE,
                AiGatewaySystemBootstrapService.LEGACY_RESUME_TEMPLATE_NAME,
                1,
                "ACTIVE",
                AiPromptTemplateFormat.TEXT.name(),
                "{\"summary\":\"legacy\"}",
                "legacy resume template",
                "{\"targetPosition\":\"legacy\"}",
                null
        );

        bootstrapService.run(new DefaultApplicationArguments(new String[0]));

        AiModelRouteRepository.ModelRouteRow legacyPrimaryRoute = aiModelRouteRepository.findRouteByCode(
                AiGatewaySystemBootstrapService.LEGACY_RESUME_PRIMARY_ROUTE_CODE
        ).orElseThrow();
        AiModelRouteRepository.ModelRouteRow legacyFallbackRoute = aiModelRouteRepository.findRouteByCode(
                AiGatewaySystemBootstrapService.LEGACY_RESUME_FALLBACK_ROUTE_CODE
        ).orElseThrow();
        assertThat(legacyPrimaryRoute.enabled()).isFalse();
        assertThat(legacyFallbackRoute.enabled()).isFalse();
        assertThat(promptTemplateRepository.findActivePromptTemplate(
                AiGatewaySystemBootstrapService.RESUME_TASK_TYPE,
                AiGatewaySystemBootstrapService.LEGACY_RESUME_TEMPLATE_NAME
        )).isEmpty();

        PromptTemplateRepository.PromptTemplateRow legacyTemplate = promptTemplateRepository.findAllPromptTemplates().stream()
                .filter(row -> AiGatewaySystemBootstrapService.RESUME_TASK_TYPE.equals(row.taskType()))
                .filter(row -> AiGatewaySystemBootstrapService.LEGACY_RESUME_TEMPLATE_NAME.equals(row.templateName()))
                .findFirst()
                .orElseThrow();
        assertThat(legacyTemplate.status()).isEqualTo("INACTIVE");
    }

    @Test
    void routedMode_shouldDeactivateLegacyInterviewRoutesAndTemplatesAfterBootstrap() throws Exception {
        long legacyProviderId = aiProviderConfigRepository.insertProvider(
                "legacy_interview_openai",
                AiProviderType.OPENAI_COMPATIBLE.name(),
                "Legacy Interview OpenAI",
                "https://legacy.example.com/v1",
                "ciphertext",
                "***legacy(len=10)",
                true,
                15000,
                1,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                null
        );
        aiModelRouteRepository.insertRoute(
                AiGatewaySystemBootstrapService.LEGACY_INTERVIEW_OPENING_ROUTE_CODE,
                AiGatewaySystemBootstrapService.INTERVIEW_TEXT_TASK_TYPE,
                AiGatewaySystemBootstrapService.INTERVIEW_OPENING_SCENE_CODE,
                null,
                legacyProviderId,
                "gemini-2.5-flash",
                10,
                100,
                AiExecutionMode.SYNC_BLOCKING.name(),
                true,
                BigDecimal.valueOf(0.2d),
                null,
                null,
                "{\"style\":\"legacy-opening\"}"
        );
        aiModelRouteRepository.insertRoute(
                AiGatewaySystemBootstrapService.LEGACY_INTERVIEW_REPLY_ROUTE_CODE,
                AiGatewaySystemBootstrapService.INTERVIEW_TEXT_TASK_TYPE,
                AiGatewaySystemBootstrapService.INTERVIEW_REPLY_SCENE_CODE,
                null,
                legacyProviderId,
                "gemini-2.5-flash",
                10,
                100,
                AiExecutionMode.SYNC_BLOCKING.name(),
                true,
                BigDecimal.valueOf(0.2d),
                null,
                null,
                "{\"style\":\"legacy-reply\"}"
        );
        aiModelRouteRepository.insertRoute(
                AiGatewaySystemBootstrapService.LEGACY_INTERVIEW_SUMMARY_ROUTE_CODE,
                AiGatewaySystemBootstrapService.INTERVIEW_SUMMARY_TASK_TYPE,
                AiGatewaySystemBootstrapService.INTERVIEW_SUMMARY_SCENE_CODE,
                null,
                legacyProviderId,
                "gemini-2.5-flash",
                10,
                100,
                AiExecutionMode.SYNC_BLOCKING.name(),
                true,
                BigDecimal.valueOf(0.2d),
                null,
                null,
                "{\"style\":\"legacy-summary\"}"
        );
        aiModelRouteRepository.insertRoute(
                AiGatewaySystemBootstrapService.LEGACY_INTERVIEW_STT_OPENAI_ROUTE_CODE,
                AiGatewaySystemBootstrapService.STT_TASK_TYPE,
                AiGatewaySystemBootstrapService.INTERVIEW_STT_SCENE_CODE,
                null,
                legacyProviderId,
                "gemini-2.5-flash",
                20,
                100,
                AiExecutionMode.SYNC_BLOCKING.name(),
                true,
                BigDecimal.valueOf(0.2d),
                null,
                null,
                "{\"style\":\"legacy-stt\"}"
        );
        promptTemplateRepository.insertPromptTemplate(
                AiGatewaySystemBootstrapService.INTERVIEW_TEXT_TASK_TYPE,
                AiGatewaySystemBootstrapService.LEGACY_INTERVIEW_OPENING_TEMPLATE_NAME,
                1,
                "ACTIVE",
                AiPromptTemplateFormat.TEXT.name(),
                "{\"firstQuestion\":\"legacy\"}",
                "legacy interview opening",
                "{\"targetRole\":\"legacy\"}",
                null
        );
        promptTemplateRepository.insertPromptTemplate(
                AiGatewaySystemBootstrapService.INTERVIEW_TEXT_TASK_TYPE,
                AiGatewaySystemBootstrapService.LEGACY_INTERVIEW_REPLY_TEMPLATE_NAME,
                1,
                "ACTIVE",
                AiPromptTemplateFormat.TEXT.name(),
                "{\"followUpQuestion\":\"legacy\"}",
                "legacy interview reply",
                "{\"currentAnswer\":\"legacy\"}",
                null
        );
        promptTemplateRepository.insertPromptTemplate(
                AiGatewaySystemBootstrapService.INTERVIEW_SUMMARY_TASK_TYPE,
                AiGatewaySystemBootstrapService.LEGACY_INTERVIEW_SUMMARY_TEMPLATE_NAME,
                1,
                "ACTIVE",
                AiPromptTemplateFormat.TEXT.name(),
                "{\"overallScore\":80}",
                "legacy interview summary",
                "{\"historyLines\":\"legacy\"}",
                null
        );

        bootstrapService.run(new DefaultApplicationArguments(new String[0]));

        assertThat(aiModelRouteRepository.findRouteByCode(AiGatewaySystemBootstrapService.LEGACY_INTERVIEW_OPENING_ROUTE_CODE).orElseThrow().enabled()).isFalse();
        assertThat(aiModelRouteRepository.findRouteByCode(AiGatewaySystemBootstrapService.LEGACY_INTERVIEW_REPLY_ROUTE_CODE).orElseThrow().enabled()).isFalse();
        assertThat(aiModelRouteRepository.findRouteByCode(AiGatewaySystemBootstrapService.LEGACY_INTERVIEW_SUMMARY_ROUTE_CODE).orElseThrow().enabled()).isFalse();
        assertThat(aiModelRouteRepository.findRouteByCode(AiGatewaySystemBootstrapService.LEGACY_INTERVIEW_STT_OPENAI_ROUTE_CODE).orElseThrow().enabled()).isFalse();

        assertThat(promptTemplateRepository.findActivePromptTemplate(
                AiGatewaySystemBootstrapService.INTERVIEW_TEXT_TASK_TYPE,
                AiGatewaySystemBootstrapService.LEGACY_INTERVIEW_OPENING_TEMPLATE_NAME
        )).isEmpty();
        assertThat(promptTemplateRepository.findActivePromptTemplate(
                AiGatewaySystemBootstrapService.INTERVIEW_TEXT_TASK_TYPE,
                AiGatewaySystemBootstrapService.LEGACY_INTERVIEW_REPLY_TEMPLATE_NAME
        )).isEmpty();
        assertThat(promptTemplateRepository.findActivePromptTemplate(
                AiGatewaySystemBootstrapService.INTERVIEW_SUMMARY_TASK_TYPE,
                AiGatewaySystemBootstrapService.LEGACY_INTERVIEW_SUMMARY_TEMPLATE_NAME
        )).isEmpty();
    }
}
