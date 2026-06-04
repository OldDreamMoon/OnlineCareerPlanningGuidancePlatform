package com.bishe.server.profile.service;

import com.bishe.server.ai.gateway.AiGatewayService;
import com.bishe.server.ai.history.AiHistoryRepository;
import com.bishe.server.ai.interview.AiInterviewRepository;
import com.bishe.server.ai.quota.AiQuotaService;
import com.bishe.server.ai.quota.AiTaskType;
import com.bishe.server.common.TraceId;
import com.bishe.server.dashboard.AdminOperationsDashboardCacheService;
import com.bishe.server.featureflag.FeatureFlagService;
import com.bishe.server.profile.dto.StudentProfileResponse;
import com.bishe.server.profile.repository.StudentProfileRepository;
import com.bishe.server.skill.repository.SkillRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.DigestUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * 学生动态画像刷新服务：聚合技能、面试与社区信号并落画像快照。
 */
@Service
public class StudentPortraitRefreshService {

    private static final Logger log = LoggerFactory.getLogger(StudentPortraitRefreshService.class);

    private static final String PORTRAIT_SUMMARY_SCENE_CODE = "STUDENT_PORTRAIT_SUMMARY";
    private static final String PORTRAIT_SUMMARY_VERSION = "LLM_V1";
    private static final String PORTRAIT_TEMPLATE_VERSION = "TEMPLATE_V1";

    private final StudentProfileRepository studentProfileRepository;
    private final SkillRepository skillRepository;
    private final AiHistoryRepository aiHistoryRepository;
    private final AiInterviewRepository aiInterviewRepository;
    private final StudentPortraitSnapshotCacheService studentPortraitSnapshotCacheService;
    private final StudentPublicProfileCacheService studentPublicProfileCacheService;
    private final AdminOperationsDashboardCacheService adminOperationsDashboardCacheService;
    private final FeatureFlagService featureFlagService;
    private final AiGatewayService aiGatewayService;
    private final AiQuotaService aiQuotaService;
    private final ObjectMapper objectMapper;

    public StudentPortraitRefreshService(
            StudentProfileRepository studentProfileRepository,
            SkillRepository skillRepository,
            AiHistoryRepository aiHistoryRepository,
            AiInterviewRepository aiInterviewRepository,
            StudentPortraitSnapshotCacheService studentPortraitSnapshotCacheService,
            StudentPublicProfileCacheService studentPublicProfileCacheService,
            AdminOperationsDashboardCacheService adminOperationsDashboardCacheService,
            FeatureFlagService featureFlagService,
            AiGatewayService aiGatewayService,
            AiQuotaService aiQuotaService,
            ObjectMapper objectMapper
    ) {
        this.studentProfileRepository = studentProfileRepository;
        this.skillRepository = skillRepository;
        this.aiHistoryRepository = aiHistoryRepository;
        this.aiInterviewRepository = aiInterviewRepository;
        this.studentPortraitSnapshotCacheService = studentPortraitSnapshotCacheService;
        this.studentPublicProfileCacheService = studentPublicProfileCacheService;
        this.adminOperationsDashboardCacheService = adminOperationsDashboardCacheService;
        this.featureFlagService = featureFlagService;
        this.aiGatewayService = aiGatewayService;
        this.aiQuotaService = aiQuotaService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public boolean refreshNow(long studentUserId) {
        return refreshNow(studentUserId, PortraitRefreshPolicy.FULL);
    }

    @Transactional
    public boolean refreshNowWithoutLlm(long studentUserId) {
        return refreshNow(studentUserId, PortraitRefreshPolicy.TEMPLATE_ONLY);
    }

    @Transactional
    public boolean refreshNowLowPriority(long studentUserId) {
        return refreshNow(studentUserId, PortraitRefreshPolicy.LOW_PRIORITY);
    }

    private boolean refreshNow(long studentUserId, PortraitRefreshPolicy refreshPolicy) {
        // 画像刷新必须先拿到学生基础资料，没有 profile 就不生成空快照。
        StudentProfileRepository.StudentProfileRow profileRow = studentProfileRepository.findStudentProfileByUserId(studentUserId).orElse(null);
        if (profileRow == null) {
            return false;
        }

        StudentProfileRepository.PortraitSnapshotRow previousSnapshot = studentProfileRepository.findPortraitSnapshot(studentUserId).orElse(null);
        Instant now = Instant.now();
        if (shouldSkipRefresh(previousSnapshot, refreshPolicy, now)) {
            log.debug(
                    "student portrait refresh skipped userId={} policy={} updatedAt={}",
                    studentUserId,
                    refreshPolicy.name(),
                    previousSnapshot == null ? null : previousSnapshot.updatedAt()
            );
            return false;
        }
        // 统一取最近 7 天行为信号，再叠加最新简历和面试摘要。
        Instant windowStart = now.minus(7, ChronoUnit.DAYS);
        StudentProfileRepository.CommunityStatsRow communityStats = studentProfileRepository.countCommunityStats7d(studentUserId, windowStart);
        int interviewMessages7d = studentProfileRepository.countInterviewMessages7d(studentUserId, windowStart);
        SkillRepository.SkillProgressSummary skillSummary = skillRepository.countProgressSummary(studentUserId);
        AiHistoryRepository.ResumeHistoryDetailRow latestResume = aiHistoryRepository.findLatestResumeDetail(studentUserId).orElse(null);
        AiInterviewRepository.InterviewSessionRow latestInterview = aiInterviewRepository.findLatestCompletedSummary(studentUserId).orElse(null);

        List<StudentProfileResponse.PortraitTagItem> tags = buildPortraitTags(
                profileRow,
                skillSummary,
                communityStats,
                interviewMessages7d,
                latestResume,
                latestInterview
        );
        // facts 是画像的中间事实层，后面模板摘要、LLM 摘要和证据 JSON 都复用它。
        PortraitFactBundle facts = buildPortraitFacts(
                profileRow,
                skillSummary,
                communityStats,
                interviewMessages7d,
                latestResume,
                latestInterview,
                tags,
                now
        );
        PortraitSummaryBundle summary = resolveSummaryBundle(studentUserId, profileRow, previousSnapshot, facts, refreshPolicy);
        Map<String, Object> evidence = buildEvidence(
                profileRow,
                skillSummary,
                communityStats,
                interviewMessages7d,
                latestResume,
                latestInterview,
                facts,
                summary
        );
        // 快照落库后立即清前台画像、公开主页和后台看板缓存，避免页面继续读旧信号。
        studentProfileRepository.savePortraitSnapshot(studentUserId, toJson(tags), toJson(evidence));
        studentPortraitSnapshotCacheService.evictNow(studentUserId);
        studentPortraitSnapshotCacheService.evictAfterCommit(studentUserId);
        studentPublicProfileCacheService.evictNow(studentUserId);
        studentPublicProfileCacheService.evictAfterCommit(studentUserId);
        adminOperationsDashboardCacheService.evictAllNow();
        adminOperationsDashboardCacheService.evictAllAfterCommit();
        return true;
    }

    @Scheduled(cron = "${profile.portrait.daily-refresh-cron:0 30 3 * * *}")
    public void refreshAllDaily() {
        if (!featureFlagService.isStudentPortraitAsyncRefreshEnabled()) {
            log.debug("student portrait daily refresh skipped because async refresh flag disabled");
            return;
        }
        // 每日任务只做模板级刷新，避免批量调 LLM 造成成本和限流压力。
        List<Long> studentUserIds = studentProfileRepository.findAllStudentUserIds();
        for (Long studentUserId : studentUserIds) {
            try {
                refreshNow(studentUserId, PortraitRefreshPolicy.TEMPLATE_ONLY);
            } catch (Exception ex) {
                log.warn("student portrait refresh failed userId={}", studentUserId, ex);
            }
        }
    }

    private List<StudentProfileResponse.PortraitTagItem> buildPortraitTags(
            StudentProfileRepository.StudentProfileRow profileRow,
            SkillRepository.SkillProgressSummary skillSummary,
            StudentProfileRepository.CommunityStatsRow communityStats,
            int interviewMessages7d,
            AiHistoryRepository.ResumeHistoryDetailRow latestResume,
            AiInterviewRepository.InterviewSessionRow latestInterview
    ) {
        Map<String, StudentProfileResponse.PortraitTagItem> tags = new LinkedHashMap<>();
        // 这里把分散的技能、面试、社区行为折成少量稳定标签，前端展示和推荐都能复用。
        int communityScore = communityStats.postCount() * 5 + communityStats.commentCount() * 2 + communityStats.likesReceivedCount();
        int skillSignal = skillSummary.masteredCount() + skillSummary.learningCount();

        if (skillSummary.masteredCount() >= 1 || skillSignal >= 2) {
            putTag(tags, new StudentProfileResponse.PortraitTagItem(
                    "SKILL_PROGRESS_ACTIVE",
                    "技能成长清晰",
                    "SKILL_PROGRESS",
                    confidence(0.72 + skillSummary.masteredCount() * 0.10 + skillSummary.learningCount() * 0.05)
            ));
        }

        if (interviewMessages7d >= 4) {
            putTag(tags, new StudentProfileResponse.PortraitTagItem(
                    "INTERVIEW_ACTIVE",
                    "模拟面试积极",
                    "AI_INTERVIEW",
                    confidence(0.66 + interviewMessages7d * 0.04)
            ));
        }

        if (communityScore >= 8 || (communityStats.postCount() >= 1 && communityStats.commentCount() >= 1) || communityStats.commentCount() >= 3) {
            putTag(tags, new StudentProfileResponse.PortraitTagItem(
                    "COMMUNITY_ACTIVE",
                    "社区互动积极",
                    "COMMUNITY",
                    confidence(0.68 + communityScore * 0.02)
            ));
        }

        applyDirectionTags(tags, profileRow);
        applyResumeTags(tags, latestResume);
        applyInterviewTags(tags, latestInterview);
        return new ArrayList<>(tags.values());
    }

    private PortraitFactBundle buildPortraitFacts(
            StudentProfileRepository.StudentProfileRow profileRow,
            SkillRepository.SkillProgressSummary skillSummary,
            StudentProfileRepository.CommunityStatsRow communityStats,
            int interviewMessages7d,
            AiHistoryRepository.ResumeHistoryDetailRow latestResume,
            AiInterviewRepository.InterviewSessionRow latestInterview,
            List<StudentProfileResponse.PortraitTagItem> tags,
            Instant now
    ) {
        // facts 只保留可解释字段，避免 LLM 摘要直接依赖散乱的数据库行。
        List<String> strengthTags = buildStrengthTags(profileRow, skillSummary, interviewMessages7d, communityStats, tags);
        List<String> riskTags = buildRiskTags(tags, latestResume, latestInterview, strengthTags);
        String signalLevel = determineSignalLevel(profileRow, skillSummary, communityStats, interviewMessages7d, latestResume, latestInterview);
        String freshnessLevel = determineFreshnessLevel(now, skillSummary, communityStats, interviewMessages7d, latestResume, latestInterview, profileRow);
        String directionFocus = resolveDirectionFocus(profileRow, tags);
        List<String> nextActions = buildTemplateNextActions(signalLevel, freshnessLevel, directionFocus, tags);
        String headline = buildTemplateHeadline(signalLevel, directionFocus, strengthTags, riskTags);
        String summary = buildTemplateSummary(directionFocus, strengthTags, riskTags, skillSummary, communityStats, interviewMessages7d, signalLevel, freshnessLevel);
        String evidenceContext = buildEvidenceContext(profileRow, skillSummary, communityStats, interviewMessages7d, strengthTags, riskTags, signalLevel, freshnessLevel);
        String summaryFingerprint = buildSummaryFingerprint(profileRow, skillSummary, communityStats, interviewMessages7d, tags, strengthTags, riskTags, signalLevel, freshnessLevel);
        return new PortraitFactBundle(
                strengthTags,
                riskTags,
                signalLevel,
                freshnessLevel,
                directionFocus,
                headline,
                summary,
                nextActions,
                evidenceContext,
                summaryFingerprint
        );
    }

    private PortraitSummaryBundle resolveSummaryBundle(
            long studentUserId,
            StudentProfileRepository.StudentProfileRow profileRow,
            StudentProfileRepository.PortraitSnapshotRow previousSnapshot,
            PortraitFactBundle facts,
            PortraitRefreshPolicy refreshPolicy
    ) {
        FeatureFlagService.StudentPortraitSummaryMode summaryMode = featureFlagService.getStudentPortraitSummaryMode();
        PortraitSummaryBundle previousSummary = readStoredSummary(previousSnapshot);
        if (summaryMode == FeatureFlagService.StudentPortraitSummaryMode.OFF) {
            // 关闭摘要时仍保留 fingerprint，后续重新开启可以判断是否需要重算。
            return new PortraitSummaryBundle(null, null, List.of(), null, facts.summaryFingerprint(), summaryMode.name());
        }

        PortraitSummaryBundle templateSummary = new PortraitSummaryBundle(
                facts.headline(),
                facts.summary(),
                facts.nextActions(),
                PORTRAIT_TEMPLATE_VERSION,
                facts.summaryFingerprint(),
                summaryMode.name()
        );

        if (canReuseExistingLlmSummary(previousSummary, facts.summaryFingerprint())) {
            // 输入事实没变时复用上一版 LLM 摘要，减少无意义的模型请求。
            return previousSummary;
        }
        if (!refreshPolicy.allowLlmSummary()
                || summaryMode != FeatureFlagService.StudentPortraitSummaryMode.LLM
                || "WEAK".equals(facts.signalLevel())) {
            // 弱信号或禁用 LLM 时直接使用模板摘要，保证画像刷新不依赖模型可用性。
            return templateSummary;
        }

        try {
            // LLM 只负责润色画像表达，底层 strength/risk/action 仍来自规则事实层。
            AiGatewayService.StudentPortraitSummaryGatewayResult gatewayResult = aiGatewayService.generateStudentPortraitSummary(
                    profileRow.targetPosition(),
                    facts.strengthTags(),
                    facts.riskTags(),
                    facts.signalLevel(),
                    facts.freshnessLevel(),
                    facts.evidenceContext(),
                    facts.headline(),
                    facts.summary(),
                    facts.nextActions(),
                    null,
                    profileRow.tier()
            );
            PortraitSummaryBundle llmSummary = new PortraitSummaryBundle(
                    firstNonBlank(gatewayResult.headline(), facts.headline()),
                    firstNonBlank(gatewayResult.summary(), facts.summary()),
                    normalizeSummaryActions(gatewayResult.nextActions(), facts.nextActions()),
                    PORTRAIT_SUMMARY_VERSION,
                    facts.summaryFingerprint(),
                    summaryMode.name()
            );
            recordPortraitSummaryLog(studentUserId, profileRow.tier(), llmSummary, gatewayResult.gatewayResult());
            return llmSummary;
        } catch (Exception ex) {
            log.warn("student portrait llm summary fallback applied userId={} reason={}", studentUserId, ex.getMessage());
            return templateSummary;
        }
    }

    private void recordPortraitSummaryLog(
            long studentUserId,
            String tier,
            PortraitSummaryBundle summary,
            AiGatewayService.AiGatewayResult gatewayResult
    ) {
        if (gatewayResult == null) {
            return;
        }
        try {
            aiQuotaService.recordTaskSuccess(
                    TraceId.next(),
                    studentUserId,
                    AiTaskType.PORTRAIT_SUMMARY,
                    tier,
                    0,
                    gatewayResult,
                    0,
                    summary.headline(),
                    toJson(Map.of(
                            "headline", summary.headline(),
                            "summary", summary.summary(),
                            "nextActions", summary.nextActions(),
                            "summaryVersion", summary.summaryVersion()
                    ))
            );
        } catch (Exception ex) {
            log.warn("student portrait summary call log skipped userId={} reason={}", studentUserId, ex.getMessage());
        }
    }

    private Map<String, Object> buildEvidence(
            StudentProfileRepository.StudentProfileRow profileRow,
            SkillRepository.SkillProgressSummary skillSummary,
            StudentProfileRepository.CommunityStatsRow communityStats,
            int interviewMessages7d,
            AiHistoryRepository.ResumeHistoryDetailRow latestResume,
            AiInterviewRepository.InterviewSessionRow latestInterview,
            PortraitFactBundle facts,
            PortraitSummaryBundle summary
    ) {
        Map<String, Object> evidence = new LinkedHashMap<>();
        // evidence 是画像解释层，后续推荐、公开主页和答辩排查都能反查这些依据。
        evidence.put("masteredSkills", skillSummary.masteredCount());
        evidence.put("learningSkills", skillSummary.learningCount());
        evidence.put("interviewMessages7d", interviewMessages7d);
        evidence.put("posts7d", communityStats.postCount());
        evidence.put("comments7d", communityStats.commentCount());
        evidence.put("likesReceived7d", communityStats.likesReceivedCount());
        evidence.put("targetPosition", profileRow.targetPosition());
        evidence.put("skillTags", profileRow.skillTags());
        evidence.put("strengthTags", facts.strengthTags());
        evidence.put("riskTags", facts.riskTags());
        evidence.put("signalLevel", facts.signalLevel());
        evidence.put("freshnessLevel", facts.freshnessLevel());
        evidence.put("headline", summary.headline());
        evidence.put("summary", summary.summary());
        evidence.put("nextActions", summary.nextActions());
        evidence.put("summaryVersion", summary.summaryVersion());
        evidence.put("summaryFingerprint", summary.summaryFingerprint());
        evidence.put("summaryRequestedMode", summary.requestedMode());
        evidence.put("directionFocus", facts.directionFocus());
        evidence.put("evidenceContext", facts.evidenceContext());
        evidence.put("summarySceneCode", summary.summaryVersion() != null && summary.summaryVersion().startsWith("LLM_")
                ? PORTRAIT_SUMMARY_SCENE_CODE
                : null);
        if (latestResume != null) {
            JsonNode payload = readJson(latestResume.resultPayloadJson());
            evidence.put("latestResumeRecordId", latestResume.id());
            evidence.put("latestResumeTargetRole", readText(payload, "targetRole"));
            evidence.put("latestResumeSuggestionsCount", readStringList(payload.path("suggestions")).size());
            evidence.put("latestResumeAt", latestResume.createdAt() == null ? null : latestResume.createdAt().toEpochMilli());
        }
        if (latestInterview != null) {
            evidence.put("latestInterviewSessionId", latestInterview.sessionId());
            evidence.put("latestInterviewTargetRole", latestInterview.targetRole());
            evidence.put("latestInterviewWeaknessCount", readStringList(latestInterview.summaryWeaknessesJson()).size());
            evidence.put("latestInterviewSuggestionCount", readStringList(latestInterview.summarySuggestionsJson()).size());
            evidence.put("latestInterviewAt", latestInterview.updatedAt() == null ? null : latestInterview.updatedAt().toInstant().toEpochMilli());
        }
        return evidence;
    }

    private List<String> buildStrengthTags(
            StudentProfileRepository.StudentProfileRow profileRow,
            SkillRepository.SkillProgressSummary skillSummary,
            int interviewMessages7d,
            StudentProfileRepository.CommunityStatsRow communityStats,
            List<StudentProfileResponse.PortraitTagItem> tags
    ) {
        LinkedHashSet<String> strengths = new LinkedHashSet<>();
        tags.stream()
                .filter(item -> isStrengthTagCode(item.code()))
                .map(StudentProfileResponse.PortraitTagItem::label)
                .forEach(strengths::add);
        if (strengths.isEmpty() && hasText(profileRow.targetPosition())) {
            strengths.add("目标方向已明确");
        }
        if (strengths.isEmpty() && skillSummary.masteredCount() > 0) {
            strengths.add("已有技能积累");
        }
        if (strengths.isEmpty() && interviewMessages7d >= 1) {
            strengths.add("开始形成面试练习信号");
        }
        if (strengths.isEmpty() && communityStats.postCount() + communityStats.commentCount() > 0) {
            strengths.add("开始形成社区互动信号");
        }
        return strengths.stream().limit(3).toList();
    }

    private List<String> buildRiskTags(
            List<StudentProfileResponse.PortraitTagItem> tags,
            AiHistoryRepository.ResumeHistoryDetailRow latestResume,
            AiInterviewRepository.InterviewSessionRow latestInterview,
            List<String> strengthTags
    ) {
        LinkedHashSet<String> risks = new LinkedHashSet<>();
        tags.stream()
                .filter(item -> isRiskTagCode(item.code()))
                .map(StudentProfileResponse.PortraitTagItem::label)
                .forEach(risks::add);
        if (risks.isEmpty() && latestResume == null && latestInterview == null && strengthTags.isEmpty()) {
            risks.add("有效成长信号仍偏少");
        }
        return risks.stream().limit(3).toList();
    }

    private String determineSignalLevel(
            StudentProfileRepository.StudentProfileRow profileRow,
            SkillRepository.SkillProgressSummary skillSummary,
            StudentProfileRepository.CommunityStatsRow communityStats,
            int interviewMessages7d,
            AiHistoryRepository.ResumeHistoryDetailRow latestResume,
            AiInterviewRepository.InterviewSessionRow latestInterview
    ) {
        int signalScore = 0;
        if (hasText(profileRow.targetPosition())) {
            signalScore += 1;
        }
        if (hasText(profileRow.selfIntro())) {
            signalScore += 1;
        }
        if (skillSummary.masteredCount() + skillSummary.learningCount() >= 2) {
            signalScore += 1;
        }
        if (communityStats.postCount() + communityStats.commentCount() + communityStats.likesReceivedCount() >= 3) {
            signalScore += 1;
        }
        if (interviewMessages7d >= 4) {
            signalScore += 1;
        }
        if (latestResume != null) {
            signalScore += 1;
        }
        if (latestInterview != null) {
            signalScore += 1;
        }
        if (signalScore >= 5) {
            return "STRONG";
        }
        if (signalScore >= 3) {
            return "NORMAL";
        }
        return "WEAK";
    }

    private String determineFreshnessLevel(
            Instant now,
            SkillRepository.SkillProgressSummary skillSummary,
            StudentProfileRepository.CommunityStatsRow communityStats,
            int interviewMessages7d,
            AiHistoryRepository.ResumeHistoryDetailRow latestResume,
            AiInterviewRepository.InterviewSessionRow latestInterview,
            StudentProfileRepository.StudentProfileRow profileRow
    ) {
        Instant newestSignalAt = newestSignalAt(latestResume, latestInterview);
        if (interviewMessages7d > 0 || communityStats.postCount() + communityStats.commentCount() > 0 || isWithinDays(newestSignalAt, now, 7)) {
            return "FRESH";
        }
        if (isWithinDays(newestSignalAt, now, 30)
                || skillSummary.masteredCount() + skillSummary.learningCount() > 0
                || hasText(profileRow.targetPosition())
                || hasText(profileRow.selfIntro())) {
            return "RECENT";
        }
        return "STALE";
    }

    private Instant newestSignalAt(
            AiHistoryRepository.ResumeHistoryDetailRow latestResume,
            AiInterviewRepository.InterviewSessionRow latestInterview
    ) {
        Instant newest = latestResume == null ? null : latestResume.createdAt();
        if (latestInterview != null && latestInterview.updatedAt() != null) {
            Instant interviewAt = latestInterview.updatedAt().toInstant();
            if (newest == null || interviewAt.isAfter(newest)) {
                newest = interviewAt;
            }
        }
        return newest;
    }

    private boolean isWithinDays(Instant timestamp, Instant now, long days) {
        return timestamp != null && !timestamp.isBefore(now.minus(days, ChronoUnit.DAYS));
    }

    private String resolveDirectionFocus(
            StudentProfileRepository.StudentProfileRow profileRow,
            List<StudentProfileResponse.PortraitTagItem> tags
    ) {
        if (hasText(profileRow.targetPosition())) {
            return profileRow.targetPosition().trim();
        }
        return tags.stream()
                .filter(item -> item.code() != null && item.code().startsWith("TARGET_DIRECTION"))
                .map(StudentProfileResponse.PortraitTagItem::label)
                .findFirst()
                .orElse("求职方向");
    }

    private String buildTemplateHeadline(
            String signalLevel,
            String directionFocus,
            List<String> strengthTags,
            List<String> riskTags
    ) {
        String primaryRisk = riskTags.isEmpty() ? null : riskTags.get(0);
        String primaryStrength = strengthTags.isEmpty() ? null : strengthTags.get(0);
        if ("WEAK".equals(signalLevel)) {
            return "你的成长画像还在建立中，先把「" + directionFocus + "」方向的关键行动信号补起来。";
        }
        if (primaryStrength != null && primaryRisk != null) {
            return "你在「" + directionFocus + "」方向已经出现「" + primaryStrength + "」信号，当前最需要补的是「" + primaryRisk + "」。";
        }
        if (primaryStrength != null) {
            return "你在「" + directionFocus + "」方向已经出现「" + primaryStrength + "」信号，可以继续把优势做深。";
        }
        if (primaryRisk != null) {
            return "你在「" + directionFocus + "」方向已有基础，但当前最需要优先补齐「" + primaryRisk + "」。";
        }
        return "你的「" + directionFocus + "」方向画像已完成首轮建立，接下来建议继续补充更稳定的成长信号。";
    }

    private String buildTemplateSummary(
            String directionFocus,
            List<String> strengthTags,
            List<String> riskTags,
            SkillRepository.SkillProgressSummary skillSummary,
            StudentProfileRepository.CommunityStatsRow communityStats,
            int interviewMessages7d,
            String signalLevel,
            String freshnessLevel
    ) {
        List<String> sentences = new ArrayList<>();
        sentences.add("当前画像聚焦在「" + directionFocus + "」方向。");
        if (!strengthTags.isEmpty()) {
            sentences.add("已经显现的优势包括：" + String.join("、", strengthTags) + "。");
        }
        if (!riskTags.isEmpty()) {
            sentences.add("当前更值得优先补强的是：" + String.join("、", riskTags) + "。");
        }
        sentences.add("近 7 天你累计完成 " + interviewMessages7d + " 条面试消息、"
                + (communityStats.postCount() + communityStats.commentCount()) + " 次社区互动，当前掌握 " + skillSummary.masteredCount()
                + " 项技能，仍在学习 " + skillSummary.learningCount() + " 项。");
        if ("WEAK".equals(signalLevel)) {
            sentences.add("整体信号仍偏少，建议继续补齐简历、面试或社区侧的有效样本。");
        }
        if ("STALE".equals(freshnessLevel)) {
            sentences.add("最近可用信号偏旧，建议重新跑一轮简历优化或模拟面试来刷新画像。");
        } else if ("RECENT".equals(freshnessLevel)) {
            sentences.add("当前画像仍可参考，但继续补充新的行为样本会更稳。");
        }
        return String.join("", sentences);
    }

    private List<String> buildTemplateNextActions(
            String signalLevel,
            String freshnessLevel,
            String directionFocus,
            List<StudentProfileResponse.PortraitTagItem> tags
    ) {
        LinkedHashSet<String> actions = new LinkedHashSet<>();
        for (StudentProfileResponse.PortraitTagItem tag : tags) {
            String action = mapRiskAction(tag.code(), directionFocus);
            if (action != null) {
                actions.add(action);
            }
        }
        if ("WEAK".equals(signalLevel)) {
            actions.add("先补齐目标岗位、技能标签和自我介绍，再完成一轮简历优化或模拟面试，给画像补足基础信号。");
        }
        if ("STALE".equals(freshnessLevel)) {
            actions.add("最近画像信号已经偏旧，建议重新跑一轮简历优化或模拟面试，刷新当前状态。");
        }
        if (actions.isEmpty()) {
            actions.add("围绕「" + directionFocus + "」挑 1 个最重要项目，补齐背景、动作、结果三段式表达。");
            actions.add("把最近一轮练习里暴露的问题整理成 2 到 3 条可执行改动，下周继续复盘。");
        }
        return actions.stream().limit(3).toList();
    }

    private String buildEvidenceContext(
            StudentProfileRepository.StudentProfileRow profileRow,
            SkillRepository.SkillProgressSummary skillSummary,
            StudentProfileRepository.CommunityStatsRow communityStats,
            int interviewMessages7d,
            List<String> strengthTags,
            List<String> riskTags,
            String signalLevel,
            String freshnessLevel
    ) {
        List<String> lines = new ArrayList<>();
        if (hasText(profileRow.targetPosition())) {
            lines.add("目标岗位：" + profileRow.targetPosition().trim());
        }
        lines.add("已掌握技能：" + skillSummary.masteredCount() + " 项");
        lines.add("学习中技能：" + skillSummary.learningCount() + " 项");
        lines.add("近 7 天面试消息：" + interviewMessages7d + " 条");
        lines.add("近 7 天社区发帖：" + communityStats.postCount() + " 次");
        lines.add("近 7 天社区评论：" + communityStats.commentCount() + " 次");
        lines.add("近 7 天获赞：" + communityStats.likesReceivedCount() + " 次");
        if (!strengthTags.isEmpty()) {
            lines.add("优势标签：" + String.join("、", strengthTags));
        }
        if (!riskTags.isEmpty()) {
            lines.add("风险标签：" + String.join("、", riskTags));
        }
        lines.add("信号强度：" + signalLevel);
        lines.add("画像新鲜度：" + freshnessLevel);
        return String.join("\n", lines);
    }

    private String buildSummaryFingerprint(
            StudentProfileRepository.StudentProfileRow profileRow,
            SkillRepository.SkillProgressSummary skillSummary,
            StudentProfileRepository.CommunityStatsRow communityStats,
            int interviewMessages7d,
            List<StudentProfileResponse.PortraitTagItem> tags,
            List<String> strengthTags,
            List<String> riskTags,
            String signalLevel,
            String freshnessLevel
    ) {
        List<String> fragments = new ArrayList<>();
        fragments.add(normalize(profileRow.targetPosition()));
        fragments.add(normalize(profileRow.skillTags()));
        fragments.add(signalLevel);
        fragments.add(freshnessLevel);
        fragments.add("skills:" + skillSummary.masteredCount() + ":" + skillSummary.learningCount());
        fragments.add("community:" + communityStats.postCount() + ":" + communityStats.commentCount());
        fragments.add("interview:" + interviewMessages7d);
        tags.stream().map(StudentProfileResponse.PortraitTagItem::code).filter(this::hasText).sorted().forEach(fragments::add);
        strengthTags.forEach(fragments::add);
        riskTags.forEach(fragments::add);
        return DigestUtils.md5DigestAsHex(String.join("|", fragments).getBytes(StandardCharsets.UTF_8));
    }

    private boolean shouldSkipRefresh(
            StudentProfileRepository.PortraitSnapshotRow previousSnapshot,
            PortraitRefreshPolicy refreshPolicy,
            Instant now
    ) {
        if (refreshPolicy == null
                || previousSnapshot == null
                || previousSnapshot.updatedAt() == null
                || refreshPolicy.minRefreshInterval() == null
                || refreshPolicy.minRefreshInterval().isZero()
                || refreshPolicy.minRefreshInterval().isNegative()) {
            return false;
        }
        return previousSnapshot.updatedAt().isAfter(now.minus(refreshPolicy.minRefreshInterval()));
    }

    private PortraitSummaryBundle readStoredSummary(StudentProfileRepository.PortraitSnapshotRow previousSnapshot) {
        if (previousSnapshot == null) {
            return new PortraitSummaryBundle(null, null, List.of(), null, null, null);
        }
        JsonNode evidenceNode = readJson(previousSnapshot.evidenceJson());
        return new PortraitSummaryBundle(
                readText(evidenceNode, "headline"),
                readText(evidenceNode, "summary"),
                readStringList(evidenceNode.path("nextActions")),
                readText(evidenceNode, "summaryVersion"),
                readText(evidenceNode, "summaryFingerprint"),
                readText(evidenceNode, "summaryRequestedMode")
        );
    }

    private boolean canReuseExistingLlmSummary(PortraitSummaryBundle previousSummary, String currentFingerprint) {
        return previousSummary != null
                && hasText(previousSummary.summaryVersion())
                && previousSummary.summaryVersion().startsWith("LLM_")
                && hasText(previousSummary.summaryFingerprint())
                && previousSummary.summaryFingerprint().equals(currentFingerprint)
                && hasText(previousSummary.headline())
                && hasText(previousSummary.summary())
                && previousSummary.nextActions() != null
                && !previousSummary.nextActions().isEmpty();
    }

    private List<String> normalizeSummaryActions(List<String> providerActions, List<String> fallbackActions) {
        List<String> normalized = providerActions == null ? List.of() : providerActions.stream()
                .map(this::normalizeText)
                .filter(this::hasText)
                .distinct()
                .limit(3)
                .toList();
        return normalized.isEmpty() ? fallbackActions : normalized;
    }

    private boolean isStrengthTagCode(String code) {
        if (!hasText(code)) {
            return false;
        }
        return switch (code.trim().toUpperCase()) {
            case "SKILL_PROGRESS_ACTIVE", "INTERVIEW_ACTIVE", "COMMUNITY_ACTIVE" -> true;
            default -> false;
        };
    }

    private boolean isRiskTagCode(String code) {
        if (!hasText(code)) {
            return false;
        }
        return switch (code.trim().toUpperCase()) {
            case "RESUME_EXPRESSION_NEEDS_IMPROVEMENT",
                    "RESUME_PROJECT_EXPRESSION_NEEDS_IMPROVEMENT",
                    "RESUME_DIRECTION_NEEDS_ALIGNMENT",
                    "INTERVIEW_PROJECT_EXPRESSION_GAP",
                    "INTERVIEW_SYSTEM_DESIGN_GAP",
                    "INTERVIEW_COMMUNICATION_GAP" -> true;
            default -> false;
        };
    }

    private String mapRiskAction(String code, String directionFocus) {
        if (!hasText(code)) {
            return null;
        }
        return switch (code.trim().toUpperCase()) {
            case "RESUME_EXPRESSION_NEEDS_IMPROVEMENT" ->
                    "给最近一份简历补上量化结果、技术取舍和最终影响，避免只写职责。";
            case "RESUME_PROJECT_EXPRESSION_NEEDS_IMPROVEMENT" ->
                    "挑 1 个核心项目，按背景、动作、结果三段式重写成适合「" + directionFocus + "」投递的版本。";
            case "RESUME_DIRECTION_NEEDS_ALIGNMENT" ->
                    "先收敛 1 个主投岗位方向，再按该方向调整简历标题、技能顺序和项目排序。";
            case "INTERVIEW_PROJECT_EXPRESSION_GAP" ->
                    "围绕一个核心项目练习 2 分钟讲清背景、难点、个人动作和结果，提升项目表达完整度。";
            case "INTERVIEW_SYSTEM_DESIGN_GAP" ->
                    "准备 1 个能展开到缓存、数据库或系统设计取舍的案例，练习先讲约束再讲方案。";
            case "INTERVIEW_COMMUNICATION_GAP" ->
                    "下一轮回答先给结论，再按 2 到 3 个要点展开，避免信息跳跃和结构发散。";
            default -> null;
        };
    }

    private void applyDirectionTags(Map<String, StudentProfileResponse.PortraitTagItem> tags, StudentProfileRepository.StudentProfileRow profileRow) {
        String directionText = normalize(profileRow.targetPosition()) + " " + normalize(profileRow.skillTags()) + " " + normalize(profileRow.selfIntro());
        if (directionText.isBlank()) {
            return;
        }
        if (containsAny(directionText, "前端", "react", "vue", "typescript", "javascript")) {
            putTag(tags, tag("TARGET_DIRECTION_FRONTEND", "前端求职导向", "PROFILE", 0.88));
        }
        if (containsAny(directionText, "后端", "java", "spring", "mysql", "redis", "golang")) {
            putTag(tags, tag("TARGET_DIRECTION_BACKEND", "后端求职导向", "PROFILE", 0.90));
        }
        if (containsAny(directionText, "数据分析", "bi", "sql", "可视化", "分析")) {
            putTag(tags, tag("DATA_ANALYSIS_ORIENTATION", "数据分析导向", "PROFILE", 0.87));
        }
        if (containsAny(directionText, "产品", "需求", "原型", "商业分析")) {
            putTag(tags, tag("TARGET_DIRECTION_PRODUCT", "产品方向导向", "PROFILE", 0.84));
        }
        if (containsAny(directionText, "算法", "机器学习", "推荐", "大模型", "ai")) {
            putTag(tags, tag("TARGET_DIRECTION_AI", "算法 / AI 求职导向", "PROFILE", 0.86));
        }
        if (containsAny(directionText, "测试", "测开", "自动化测试", "质量")) {
            putTag(tags, tag("TARGET_DIRECTION_TEST", "测试开发导向", "PROFILE", 0.82));
        }
    }

    private void applyResumeTags(Map<String, StudentProfileResponse.PortraitTagItem> tags, AiHistoryRepository.ResumeHistoryDetailRow latestResume) {
        if (latestResume == null) {
            return;
        }
        JsonNode payload = readJson(latestResume.resultPayloadJson());
        String summary = normalize(readText(payload, "summary")) + " " + normalize(latestResume.resultSummary());
        List<String> suggestions = readStringList(payload.path("suggestions"));
        String suggestionText = normalize(String.join(" ", suggestions));
        String combined = (summary + " " + suggestionText).trim();
        if (combined.isBlank()) {
            return;
        }
        if (containsAny(combined, "量化", "结果", "指标", "亮点", "成果")) {
            putTag(tags, tag("RESUME_EXPRESSION_NEEDS_IMPROVEMENT", "简历成果表达待强化", "AI_RESUME", 0.86));
        }
        if (containsAny(combined, "项目", "背景-行动-结果", "技术取舍", "难点", "排障")) {
            putTag(tags, tag("RESUME_PROJECT_EXPRESSION_NEEDS_IMPROVEMENT", "简历项目表达待强化", "AI_RESUME", 0.84));
        }
        if (containsAny(combined, "岗位", "方向", "匹配", "目标岗位")) {
            putTag(tags, tag("RESUME_DIRECTION_NEEDS_ALIGNMENT", "简历岗位匹配仍需收口", "AI_RESUME", 0.78));
        }
    }

    private void applyInterviewTags(Map<String, StudentProfileResponse.PortraitTagItem> tags, AiInterviewRepository.InterviewSessionRow latestInterview) {
        if (latestInterview == null) {
            return;
        }
        String weaknessText = normalize(String.join(" ", readStringList(latestInterview.summaryWeaknessesJson())));
        String suggestionText = normalize(String.join(" ", readStringList(latestInterview.summarySuggestionsJson())));
        String combined = (weaknessText + " " + suggestionText).trim();
        if (combined.isBlank()) {
            return;
        }
        if (containsAny(combined, "项目", "表达", "案例", "亮点", "复盘")) {
            putTag(tags, tag("INTERVIEW_PROJECT_EXPRESSION_GAP", "项目表达存在短板", "AI_INTERVIEW", 0.86));
        }
        if (containsAny(combined, "系统设计", "架构", "缓存", "数据库", "并发", "容量")) {
            putTag(tags, tag("INTERVIEW_SYSTEM_DESIGN_GAP", "系统设计表达待补强", "AI_INTERVIEW", 0.88));
        }
        if (containsAny(combined, "沟通", "结构", "回答", "条理", "追问")) {
            putTag(tags, tag("INTERVIEW_COMMUNICATION_GAP", "面试表达结构待补强", "AI_INTERVIEW", 0.82));
        }
    }

    private StudentProfileResponse.PortraitTagItem tag(String code, String label, String source, double rawConfidence) {
        return new StudentProfileResponse.PortraitTagItem(code, label, source, confidence(rawConfidence));
    }

    private void putTag(Map<String, StudentProfileResponse.PortraitTagItem> tags, StudentProfileResponse.PortraitTagItem item) {
        tags.putIfAbsent(item.code(), item);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new IllegalStateException("failed to serialize portrait snapshot", ex);
        }
    }

    private double confidence(double rawValue) {
        double normalized = Math.max(0.50, Math.min(rawValue, 0.95));
        return BigDecimal.valueOf(normalized).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    private JsonNode readJson(String json) {
        if (json == null || json.isBlank()) {
            return objectMapper.createObjectNode();
        }
        try {
            return objectMapper.readTree(json);
        } catch (Exception ex) {
            return objectMapper.createObjectNode();
        }
    }

    private String readText(JsonNode node, String fieldName) {
        if (node == null || fieldName == null || fieldName.isBlank()) {
            return null;
        }
        return normalizeText(node.path(fieldName).asText(null));
    }

    private List<String> readStringList(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (JsonNode item : node) {
            String value = normalizeText(item.asText(null));
            if (value != null) {
                values.add(value);
            }
        }
        return List.copyOf(values);
    }

    private List<String> readStringList(String json) {
        return readStringList(readJson(json));
    }

    private boolean containsAny(String text, String... keywords) {
        String normalized = normalize(text);
        if (normalized.isBlank()) {
            return false;
        }
        for (String keyword : keywords) {
            if (keyword != null && !keyword.isBlank() && normalized.contains(keyword.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toLowerCase();
    }

    private String normalizeText(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private record PortraitFactBundle(
            List<String> strengthTags,
            List<String> riskTags,
            String signalLevel,
            String freshnessLevel,
            String directionFocus,
            String headline,
            String summary,
            List<String> nextActions,
            String evidenceContext,
            String summaryFingerprint
    ) {
    }

    private record PortraitSummaryBundle(
            String headline,
            String summary,
            List<String> nextActions,
            String summaryVersion,
            String summaryFingerprint,
            String requestedMode
    ) {
    }

    private record PortraitRefreshPolicy(
            String name,
            boolean allowLlmSummary,
            Duration minRefreshInterval
    ) {
        private static final PortraitRefreshPolicy FULL =
                new PortraitRefreshPolicy("FULL", true, Duration.ZERO);
        private static final PortraitRefreshPolicy TEMPLATE_ONLY =
                new PortraitRefreshPolicy("TEMPLATE_ONLY", false, Duration.ZERO);
        private static final PortraitRefreshPolicy LOW_PRIORITY =
                new PortraitRefreshPolicy("LOW_PRIORITY", false, Duration.ofMinutes(10));
    }
}
