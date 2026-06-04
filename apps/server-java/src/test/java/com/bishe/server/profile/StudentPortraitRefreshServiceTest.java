package com.bishe.server.profile;

import com.bishe.server.ai.history.AiHistoryRepository;
import com.bishe.server.ai.interview.AiInterviewRepository;
import com.bishe.server.ai.gateway.AiGatewayService;
import com.bishe.server.ai.quota.AiQuotaService;
import com.bishe.server.dashboard.AdminOperationsDashboardCacheService;
import com.bishe.server.featureflag.FeatureFlagService;
import com.bishe.server.profile.repository.StudentProfileRepository;
import com.bishe.server.profile.service.StudentPortraitRefreshService;
import com.bishe.server.profile.service.StudentPortraitSnapshotCacheService;
import com.bishe.server.profile.service.StudentPublicProfileCacheService;
import com.fasterxml.jackson.databind.JsonNode;
import com.bishe.server.skill.repository.SkillRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StudentPortraitRefreshServiceTest {

    private final StudentProfileRepository studentProfileRepository = mock(StudentProfileRepository.class);
    private final SkillRepository skillRepository = mock(SkillRepository.class);
    private final AiHistoryRepository aiHistoryRepository = mock(AiHistoryRepository.class);
    private final AiInterviewRepository aiInterviewRepository = mock(AiInterviewRepository.class);
    private final StudentPortraitSnapshotCacheService portraitCacheService = mock(StudentPortraitSnapshotCacheService.class);
    private final StudentPublicProfileCacheService publicProfileCacheService = mock(StudentPublicProfileCacheService.class);
    private final AdminOperationsDashboardCacheService adminOperationsDashboardCacheService = mock(AdminOperationsDashboardCacheService.class);
    private final FeatureFlagService featureFlagService = mock(FeatureFlagService.class);
    private final AiGatewayService aiGatewayService = mock(AiGatewayService.class);
    private final AiQuotaService aiQuotaService = mock(AiQuotaService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final StudentPortraitRefreshService service = new StudentPortraitRefreshService(
            studentProfileRepository,
            skillRepository,
            aiHistoryRepository,
            aiInterviewRepository,
            portraitCacheService,
            publicProfileCacheService,
            adminOperationsDashboardCacheService,
            featureFlagService,
            aiGatewayService,
            aiQuotaService,
            objectMapper
    );

    @Test
    void refreshNow_shouldIncludePortraitSummaryAndStructuredSignals() throws Exception {
        when(featureFlagService.getStudentPortraitSummaryMode()).thenReturn(FeatureFlagService.StudentPortraitSummaryMode.TEMPLATE);
        when(studentProfileRepository.findStudentProfileByUserId(21L)).thenReturn(Optional.of(studentProfile()));
        when(studentProfileRepository.countCommunityStats7d(eq(21L), org.mockito.ArgumentMatchers.any())).thenReturn(new StudentProfileRepository.CommunityStatsRow(1, 2, 4));
        when(studentProfileRepository.countInterviewMessages7d(eq(21L), org.mockito.ArgumentMatchers.any())).thenReturn(5);
        when(skillRepository.countProgressSummary(21L)).thenReturn(new SkillRepository.SkillProgressSummary(2, 3));
        when(aiHistoryRepository.findLatestResumeDetail(21L)).thenReturn(Optional.of(new AiHistoryRepository.ResumeHistoryDetailRow(
                301L,
                "项目亮点仍不够突出，建议补量化结果与技术取舍",
                """
                {"targetRole":"Java 后端开发实习生","summary":"项目表达还偏流水账","suggestions":["补充量化指标","把技术取舍讲清楚"]}
                """,
                4,
                "openai-compatible",
                "gpt-4o-mini",
                1280,
                Instant.parse("2026-04-02T10:00:00Z")
        )));
        when(aiInterviewRepository.findLatestCompletedSummary(21L)).thenReturn(Optional.of(new AiInterviewRepository.InterviewSessionRow(
                88L,
                "sess_mqa_student_001",
                21L,
                "Java 后端开发实习生",
                "INTERVIEW_TEXT",
                null,
                null,
                "COMPLETED",
                10,
                4,
                true,
                10,
                5,
                82,
                "[\"项目背景说明还算完整\"]",
                "[\"缓存一致性解释不稳\",\"回答结构偏散\"]",
                "[\"先讲约束再讲方案\",\"回答时补结论先行\"]",
                "openai-compatible",
                "gpt-4o-mini",
                1680L,
                java.sql.Timestamp.from(Instant.parse("2026-04-02T12:00:00Z")),
                "ROUND_LIMIT",
                true,
                null,
                java.sql.Timestamp.from(Instant.parse("2026-04-02T09:00:00Z")),
                java.sql.Timestamp.from(Instant.parse("2026-04-02T12:00:00Z"))
        )));

        boolean refreshed = service.refreshNow(21L);

        assertThat(refreshed).isTrue();
        ArgumentCaptor<String> portraitTagsCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> evidenceCaptor = ArgumentCaptor.forClass(String.class);
        verify(studentProfileRepository).savePortraitSnapshot(eq(21L), portraitTagsCaptor.capture(), evidenceCaptor.capture());
        String portraitTagsJson = portraitTagsCaptor.getValue();
        String evidenceJson = evidenceCaptor.getValue();
        JsonNode evidence = objectMapper.readTree(evidenceJson);
        assertThat(portraitTagsJson)
                .contains("TARGET_DIRECTION_BACKEND")
                .contains("RESUME_EXPRESSION_NEEDS_IMPROVEMENT")
                .contains("INTERVIEW_SYSTEM_DESIGN_GAP")
                .contains("INTERVIEW_COMMUNICATION_GAP");
        assertThat(evidence.path("latestResumeRecordId").asLong()).isEqualTo(301L);
        assertThat(evidence.path("latestInterviewSessionId").asText()).isEqualTo("sess_mqa_student_001");
        assertThat(evidence.path("strengthTags")).isNotEmpty();
        assertThat(evidence.path("riskTags")).isNotEmpty();
        assertThat(evidence.path("signalLevel").asText()).isEqualTo("STRONG");
        assertThat(evidence.path("freshnessLevel").asText()).isEqualTo("FRESH");
        assertThat(evidence.path("headline").asText()).contains("Java 后端开发实习生");
        assertThat(evidence.path("summary").asText()).contains("当前画像聚焦");
        assertThat(evidence.path("nextActions")).hasSizeGreaterThan(0);
        assertThat(evidence.path("summaryVersion").asText()).isEqualTo("TEMPLATE_V1");
        assertThat(evidence.path("summaryRequestedMode").asText()).isEqualTo("TEMPLATE");
        verifyNoInteractions(aiGatewayService, aiQuotaService);
    }

    @Test
    void refreshNowWithoutLlm_shouldBypassGatewayEvenWhenSummaryModeIsLlm() throws Exception {
        when(featureFlagService.getStudentPortraitSummaryMode()).thenReturn(FeatureFlagService.StudentPortraitSummaryMode.LLM);
        when(studentProfileRepository.findStudentProfileByUserId(21L)).thenReturn(Optional.of(studentProfile()));
        when(studentProfileRepository.countCommunityStats7d(eq(21L), org.mockito.ArgumentMatchers.any())).thenReturn(new StudentProfileRepository.CommunityStatsRow(1, 2, 4));
        when(studentProfileRepository.countInterviewMessages7d(eq(21L), org.mockito.ArgumentMatchers.any())).thenReturn(5);
        when(skillRepository.countProgressSummary(21L)).thenReturn(new SkillRepository.SkillProgressSummary(2, 3));
        when(aiHistoryRepository.findLatestResumeDetail(21L)).thenReturn(Optional.empty());
        when(aiInterviewRepository.findLatestCompletedSummary(21L)).thenReturn(Optional.empty());

        boolean refreshed = service.refreshNowWithoutLlm(21L);

        assertThat(refreshed).isTrue();
        ArgumentCaptor<String> evidenceCaptor = ArgumentCaptor.forClass(String.class);
        verify(studentProfileRepository).savePortraitSnapshot(eq(21L), org.mockito.ArgumentMatchers.anyString(), evidenceCaptor.capture());
        JsonNode evidence = objectMapper.readTree(evidenceCaptor.getValue());
        assertThat(evidence.path("summaryVersion").asText()).isEqualTo("TEMPLATE_V1");
        assertThat(evidence.path("summaryRequestedMode").asText()).isEqualTo("LLM");
        verifyNoInteractions(aiGatewayService, aiQuotaService);
    }

    @Test
    void refreshNowLowPriority_shouldSkipWhenSnapshotJustUpdated() {
        when(studentProfileRepository.findStudentProfileByUserId(21L)).thenReturn(Optional.of(studentProfile()));
        when(studentProfileRepository.findPortraitSnapshot(21L)).thenReturn(Optional.of(new StudentProfileRepository.PortraitSnapshotRow(
                "[]",
                "{}",
                Instant.now()
        )));

        boolean refreshed = service.refreshNowLowPriority(21L);

        assertThat(refreshed).isFalse();
        verify(studentProfileRepository).findStudentProfileByUserId(21L);
        verify(studentProfileRepository).findPortraitSnapshot(21L);
        verifyNoInteractions(skillRepository, aiHistoryRepository, aiInterviewRepository, aiGatewayService, aiQuotaService);
    }

    private StudentProfileRepository.StudentProfileRow studentProfile() {
        return new StudentProfileRepository.StudentProfileRow(
                21L,
                "刘佳宁",
                "student.liujianing@bishe.local",
                "FREE",
                "刘佳宁",
                "求职中",
                "某大学",
                "某大学",
                "软件工程",
                "大四",
                "3.7/4.0",
                "Java 后端开发实习生",
                null,
                "https://github.com/liujianing",
                null,
                null,
                null,
                null,
                "Java,Spring Boot,Redis,MySQL",
                "希望把课程项目整理成更像真实业务后端项目的表达。",
                null,
                null,
                null,
                null
        );
    }
}
