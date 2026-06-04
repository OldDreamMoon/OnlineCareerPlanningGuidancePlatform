package com.bishe.server.mentor.service;

import com.bishe.server.ai.history.AiHistoryRepository;
import com.bishe.server.ai.interview.AiInterviewRepository;
import com.bishe.server.common.exception.ApiException;
import com.bishe.server.common.util.TextListCodec;
import com.bishe.server.mentor.dto.MentorDetailResponse;
import com.bishe.server.mentor.dto.MentorFavoriteToggleResponse;
import com.bishe.server.mentor.dto.MentorFavoritesResponse;
import com.bishe.server.mentor.dto.MentorListResponse;
import com.bishe.server.mentor.dto.MentorPrepSheetGenerateRequest;
import com.bishe.server.mentor.dto.MentorPrepSheetGenerateResponse;
import com.bishe.server.mentor.dto.MentorRecommendationsResponse;
import com.bishe.server.mentor.dto.MentorServicePackageResponse;
import com.bishe.server.mentor.repository.MentorFavoriteRepository;
import com.bishe.server.mentor.repository.MentorRecommendationRepository;
import com.bishe.server.mentor.repository.MentorRepository;
import com.bishe.server.mentor.repository.MentorServicePackageRepository;
import com.bishe.server.profile.repository.StudentProfileRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 导师广场服务：提供学生端列表、详情、推荐、收藏与准备单草稿。
 */
@Service
public class MentorService {

    private static final Logger log = LoggerFactory.getLogger(MentorService.class);

    private static final List<String> ALLOWED_SCENES = List.of(
            "不限",
            "简历诊断",
            "项目表达",
            "模拟面试复盘",
            "岗位方向选择",
            "校招投递策略",
            "转行 / 跨专业求职",
            "Offer 对比与决策"
    );
    private static final Set<String> ALLOWED_SCENE_SET = Set.copyOf(ALLOWED_SCENES);
    private static final String RECOMMENDATION_ENTITY_STUDENT = "STUDENT";
    private static final String RECOMMENDATION_ENTITY_MENTOR = "MENTOR";
    private static final String RECOMMENDATION_RERANK_VERSION = "EMBEDDING_RECALL_RULE_RERANK_V1";
    private static final int RECOMMENDATION_RECALL_LIMIT = 12;

    private final MentorRepository mentorRepository;
    private final MentorFavoriteRepository mentorFavoriteRepository;
    private final MentorServicePackageRepository mentorServicePackageRepository;
    private final MentorRecommendationRepository mentorRecommendationRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final ObjectMapper objectMapper;
    private final MentorAvatarService mentorAvatarService;
    private final MentorPublicDetailCacheService mentorPublicDetailCacheService;
    private final MentorPublicListCacheService mentorPublicListCacheService;
    private final MentorPrepSheetAiService mentorPrepSheetAiService;
    private final AiHistoryRepository aiHistoryRepository;
    private final AiInterviewRepository aiInterviewRepository;
    private final RecommendationEmbeddingService recommendationEmbeddingService;

    public MentorService(
            MentorRepository mentorRepository,
            MentorFavoriteRepository mentorFavoriteRepository,
            MentorServicePackageRepository mentorServicePackageRepository,
            MentorRecommendationRepository mentorRecommendationRepository,
            StudentProfileRepository studentProfileRepository,
            ObjectMapper objectMapper,
            MentorAvatarService mentorAvatarService,
            MentorPublicDetailCacheService mentorPublicDetailCacheService,
            MentorPublicListCacheService mentorPublicListCacheService,
            MentorPrepSheetAiService mentorPrepSheetAiService,
            AiHistoryRepository aiHistoryRepository,
            AiInterviewRepository aiInterviewRepository,
            RecommendationEmbeddingService recommendationEmbeddingService
    ) {
        this.mentorRepository = mentorRepository;
        this.mentorFavoriteRepository = mentorFavoriteRepository;
        this.mentorServicePackageRepository = mentorServicePackageRepository;
        this.mentorRecommendationRepository = mentorRecommendationRepository;
        this.studentProfileRepository = studentProfileRepository;
        this.objectMapper = objectMapper;
        this.mentorAvatarService = mentorAvatarService;
        this.mentorPublicDetailCacheService = mentorPublicDetailCacheService;
        this.mentorPublicListCacheService = mentorPublicListCacheService;
        this.mentorPrepSheetAiService = mentorPrepSheetAiService;
        this.aiHistoryRepository = aiHistoryRepository;
        this.aiInterviewRepository = aiInterviewRepository;
        this.recommendationEmbeddingService = recommendationEmbeddingService;
    }

    public MentorListResponse listMentors(
            long studentUserId,
            int page,
            int size,
            String keyword,
            String expertise,
            String scene,
            Integer minPrice,
            Integer maxPrice,
            Boolean available,
            Boolean favorited
    ) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 50);
        String normalizedScene = normalizeSceneFilter(scene);
        if (Boolean.TRUE.equals(favorited)) {
            return loadMentorListFromDatabase(studentUserId, keyword, expertise, normalizedScene, minPrice, maxPrice, available, true, safePage, safeSize);
        }
        MentorListResponse publicSnapshot = mentorPublicListCacheService.getMentorList(
                keyword,
                expertise,
                normalizedScene,
                minPrice,
                maxPrice,
                available,
                safePage,
                safeSize,
                () -> loadMentorListFromDatabase(0L, keyword, expertise, normalizedScene, minPrice, maxPrice, available, null, safePage, safeSize)
        );
        return mergeFavoritedFlags(studentUserId, publicSnapshot);
    }

    public MentorDetailResponse getMentorDetail(long studentUserId, long mentorUserId) {
        MentorPublicDetailCacheService.MentorPublicDetailSnapshot mentor = requirePublicMentorDetail(mentorUserId);
        boolean favorited = mentorFavoriteRepository.isFavorited(studentUserId, mentorUserId);
        List<MentorServicePackageResponse> packages = MentorServicePackageSupport.ensurePackages(
                mentor.packages(),
                mentor.priceFen(),
                mentor.serviceScenes()
        );
        return new MentorDetailResponse(
                mentor.userId(),
                mentor.displayName(),
                mentor.realName(),
                mentor.showRealName(),
                mentor.companyName(),
                mentor.jobTitle(),
                mentorAvatarService.resolveAvatarUrl(
                        mentor.userId(),
                        mentor.avatarUrl(),
                        mentor.displayName(),
                        mentor.avatarObjectKey(),
                        mentor.avatarUpdatedAt()
                ),
                mentor.expertiseTags(),
                mentor.serviceScenes(),
                mentor.bio(),
                mentor.suitableFor(),
                mentor.notSuitableFor(),
                mentor.prepMaterials(),
                mentor.replyRhythm(),
                mentor.priceFen(),
                packages,
                normalizeRating(mentor.avgRating()),
                mentor.totalOrders(),
                mentor.available(),
                favorited,
                mentorRepository.findRecentReviews(mentorUserId, 3)
                        .stream()
                        .map(item -> new MentorDetailResponse.RecentReviewItem(
                                item.orderNo(),
                                item.studentDisplayName(),
                                item.rating(),
                                item.comment(),
                                toIso(item.createdAt())
                        ))
                        .toList()
        );
    }

    public MentorDetailResponse getMentorDetail(long mentorUserId) {
        return getMentorDetail(0L, mentorUserId);
    }

    public MentorRecommendationsResponse getRecommendations(
            long studentUserId,
            String keyword,
            String expertise,
            String scene,
            Integer minPrice,
            Integer maxPrice,
            Boolean available,
            Boolean favorited
    ) {
        String normalizedScene = normalizeSceneFilter(scene);
        // 学生画像、简历、面试和方向偏好统一聚合成推荐输入，后续召回和解释共用同一口径。
        StudentSignals signals = loadStudentSignals(studentUserId);
        List<Long> scopedMentorUserIds = resolveFavoritedMentorScope(studentUserId, favorited);
        if (Boolean.TRUE.equals(favorited) && scopedMentorUserIds.isEmpty()) {
            // 收藏范围为空时也返回依据说明，前端可以解释“为什么没有推荐”。
            boolean weakSignal = isWeakSignal(signals, normalizedScene);
            String basisSummary = buildBasisSummary(signals, normalizedScene, weakSignal, true);
            List<String> basisTags = buildBasisTags(signals, normalizedScene);
            persistStudentRecommendationSnapshot(buildStudentRecommendationSnapshot(studentUserId, signals));
            logRecommendationRun(
                    studentUserId,
                    normalizedScene,
                    keyword,
                    expertise,
                    minPrice,
                    maxPrice,
                    available,
                    favorited,
                    weakSignal,
                    basisSummary,
                    0,
                    List.of(),
                    List.of()
            );
            return new MentorRecommendationsResponse(
                    normalizedScene == null ? "不限" : normalizedScene,
                    basisSummary,
                    weakSignal,
                    basisTags,
                    List.of()
            );
        }
        List<MentorRepository.MentorListRow> candidates = applyFavoritedFlags(
                mentorRepository.findMentorCandidates(
                        keyword,
                        expertise,
                        normalizedScene,
                        minPrice,
                        maxPrice,
                        available,
                        scopedMentorUserIds,
                        120
                ),
                studentUserId,
                Boolean.TRUE.equals(favorited) ? new LinkedHashSet<>(scopedMentorUserIds) : null
        );

        boolean weakSignal = isWeakSignal(signals, normalizedScene);
        String basisSummary = buildBasisSummary(signals, normalizedScene, weakSignal, candidates.isEmpty());
        List<String> basisTags = buildBasisTags(signals, normalizedScene);
        StudentRecommendationSnapshot studentSnapshot = buildStudentRecommendationSnapshot(studentUserId, signals);
        // 学生侧快照落库后，后台可以追踪本次推荐到底用了哪些输入信号。
        persistStudentRecommendationSnapshot(studentSnapshot);

        if (candidates.isEmpty()) {
            logRecommendationRun(
                    studentUserId,
                    normalizedScene,
                    keyword,
                    expertise,
                    minPrice,
                    maxPrice,
                    available,
                    favorited,
                    weakSignal,
                    basisSummary,
                    candidates.size(),
                    List.of(),
                    List.of()
            );
            return new MentorRecommendationsResponse(
                    normalizedScene == null ? "不限" : normalizedScene,
                    basisSummary,
                    weakSignal,
                    basisTags,
                    List.of()
            );
        }

        double[] queryVector = buildRecommendationQueryVector(studentSnapshot.contentText(), normalizedScene, keyword, expertise);
        // 召回阶段先用本地向量和关键词缩小候选，再交给规则层做业务重排。
        List<MentorRecallCandidate> recalledCandidates = recallMentorCandidates(candidates, signals, normalizedScene, queryVector);

        // 推荐采用“向量召回 + 业务规则重排”，最后只返回少量可解释结果给学生决策。
        List<ScoredMentor> scoredMentors = recalledCandidates.stream()
                .map(candidate -> scoreMentor(candidate, signals, normalizedScene, minPrice, maxPrice))
                .sorted(Comparator
                        .comparingInt(ScoredMentor::score).reversed()
                        .thenComparing((ScoredMentor item) -> item.mentor().avgRating() == null ? BigDecimal.ZERO : item.mentor().avgRating(), Comparator.reverseOrder())
                        .thenComparing(Comparator.comparingInt((ScoredMentor item) -> item.mentor().totalOrders()).reversed()))
                .limit(3)
                .toList();
        logRecommendationRun(
                studentUserId,
                normalizedScene,
                keyword,
                expertise,
                minPrice,
                maxPrice,
                available,
                favorited,
                weakSignal,
                basisSummary,
                candidates.size(),
                recalledCandidates,
                scoredMentors
        );

        List<MentorRecommendationsResponse.RecommendationItem> records = scoredMentors.stream()
                .map(this::toRecommendationItem)
                .toList();

        return new MentorRecommendationsResponse(
                normalizedScene == null ? "不限" : normalizedScene,
                basisSummary,
                weakSignal,
                basisTags,
                records
        );
    }

    public MentorFavoritesResponse getFavorites(long studentUserId) {
        List<Long> mentorUserIds = mentorFavoriteRepository.findFavoriteMentorUserIds(studentUserId);
        return new MentorFavoritesResponse(mentorUserIds.size(), mentorUserIds);
    }

    @Transactional
    public MentorFavoriteToggleResponse favoriteMentor(long studentUserId, long mentorUserId) {
        requireMentorDetail(mentorUserId);
        mentorFavoriteRepository.addFavorite(studentUserId, mentorUserId);
        return new MentorFavoriteToggleResponse(mentorUserId, true, mentorFavoriteRepository.countFavoritesByStudent(studentUserId));
    }

    @Transactional
    public MentorFavoriteToggleResponse unfavoriteMentor(long studentUserId, long mentorUserId) {
        mentorFavoriteRepository.removeFavorite(studentUserId, mentorUserId);
        return new MentorFavoriteToggleResponse(mentorUserId, false, mentorFavoriteRepository.countFavoritesByStudent(studentUserId));
    }

    public MentorPrepSheetGenerateResponse generatePrepSheet(long studentUserId, MentorPrepSheetGenerateRequest request) {
        long mentorUserId = request.mentorUserId() == null ? 0L : request.mentorUserId();
        if (mentorUserId <= 0L) {
            throw new ApiException("BIZ-1001", "mentorUserId invalid", HttpStatus.BAD_REQUEST);
        }

        String normalizedScene = normalizePrepScene(request.scene());
        MentorRepository.MentorDetailRow mentor = requireMentorDetail(mentorUserId);
        // 准备单复用推荐同一套学生信号，避免广场和创单页上下文不一致。
        StudentSignals signals = loadStudentSignals(studentUserId);

        String targetPosition = TextListCodec.normalizeText(request.targetPosition());
        if (targetPosition == null) {
            targetPosition = signals.targetPosition() == null ? "待明确岗位方向" : signals.targetPosition();
        }

        List<String> signalTags = buildBasisTags(signals, normalizedScene).stream().limit(4).toList();
        String mentorFocus = firstNonBlank(
                mentor.expertiseTags().isEmpty() ? null : mentor.expertiseTags().get(0),
                mentor.serviceScenes().isEmpty() ? null : mentor.serviceScenes().get(0),
                "求职咨询"
        );

        String summaryDraft = buildPrepSummary(normalizedScene, targetPosition, mentor, signals, mentorFocus);
        List<String> coreQuestions = buildPrepQuestions(normalizedScene, targetPosition, mentor, mentorFocus);
        List<String> suggestedMaterials = buildSuggestedMaterials(normalizedScene, signals);
        List<String> expectedOutcomes = buildExpectedOutcomes(normalizedScene);
        // 规则草稿作为稳定底稿，AI 增强失败时仍能返回可编辑准备单。
        MentorPrepSheetAiService.MentorPrepSheetContent generatedContent = mentorPrepSheetAiService.generate(
                studentUserId,
                new MentorPrepSheetAiService.MentorPrepSheetAiRequest(
                        normalizedScene,
                        targetPosition,
                        signalTags,
                        buildMentorPrepMentorContext(mentor),
                        buildMentorPrepStudentContext(targetPosition, signals, signalTags),
                        buildLatestResumeContext(studentUserId),
                        buildLatestInterviewSummaryContext(studentUserId)
                ),
                new MentorPrepSheetAiService.MentorPrepSheetContent(
                        summaryDraft,
                        coreQuestions,
                        suggestedMaterials,
                        expectedOutcomes
                )
        );

        return new MentorPrepSheetGenerateResponse(
                mentor.userId(),
                mentor.displayName(),
                mentor.companyName(),
                mentor.jobTitle(),
                normalizedScene,
                targetPosition,
                generatedContent.summaryDraft(),
                generatedContent.coreQuestions(),
                generatedContent.suggestedMaterials(),
                generatedContent.expectedOutcomes(),
                signalTags
        );
    }

    private MentorRepository.MentorDetailRow requireMentorDetail(long mentorUserId) {
        return mentorRepository.findMentorDetail(mentorUserId)
                .orElseThrow(() -> new ApiException("BIZ-1002", "mentor not found", HttpStatus.NOT_FOUND));
    }

    private MentorPublicDetailCacheService.MentorPublicDetailSnapshot requirePublicMentorDetail(long mentorUserId) {
        MentorPublicDetailCacheService.MentorPublicDetailSnapshot mentor = mentorPublicDetailCacheService.getPublicDetail(
                mentorUserId,
                () -> mentorRepository.findMentorDetail(mentorUserId)
                        .map(this::toPublicDetailSnapshot)
                        .orElse(null)
        );
        if (mentor == null) {
            throw new ApiException("BIZ-1002", "mentor not found", HttpStatus.NOT_FOUND);
        }
        return mentor;
    }

    private MentorPublicDetailCacheService.MentorPublicDetailSnapshot toPublicDetailSnapshot(MentorRepository.MentorDetailRow mentor) {
        List<MentorServicePackageResponse> packages = MentorServicePackageSupport.ensurePackages(
                MentorServicePackageSupport.toResponses(mentorServicePackageRepository.findPackagesByMentorUserId(mentor.userId(), true)),
                mentor.priceFen(),
                mentor.serviceScenes()
        );
        return new MentorPublicDetailCacheService.MentorPublicDetailSnapshot(
                mentor.userId(),
                mentor.displayName(),
                mentor.realName(),
                mentor.showRealName(),
                mentor.companyName(),
                mentor.jobTitle(),
                mentor.avatarUrl(),
                mentor.avatarObjectKey(),
                mentor.avatarUpdatedAt(),
                mentor.expertiseTags(),
                mentor.serviceScenes(),
                mentor.bio(),
                mentor.suitableFor(),
                mentor.notSuitableFor(),
                mentor.prepMaterials(),
                mentor.replyRhythm(),
                mentor.priceFen(),
                packages,
                normalizeRating(mentor.avgRating()),
                mentor.totalOrders(),
                mentor.available()
        );
    }

    private MentorListResponse loadMentorListFromDatabase(
            long studentUserId,
            String keyword,
            String expertise,
            String normalizedScene,
            Integer minPrice,
            Integer maxPrice,
            Boolean available,
            Boolean favorited,
            int safePage,
            int safeSize
    ) {
        List<Long> scopedMentorUserIds = resolveFavoritedMentorScope(studentUserId, favorited);
        if (Boolean.TRUE.equals(favorited) && scopedMentorUserIds.isEmpty()) {
            return new MentorListResponse(List.of(), 0, safePage, safeSize);
        }
        long total = mentorRepository.countMentors(keyword, expertise, normalizedScene, minPrice, maxPrice, available, scopedMentorUserIds);
        List<MentorRepository.MentorListRow> mentors = applyFavoritedFlags(
                mentorRepository.findMentors(keyword, expertise, normalizedScene, minPrice, maxPrice, available, scopedMentorUserIds, safePage, safeSize),
                studentUserId,
                Boolean.TRUE.equals(favorited) ? new LinkedHashSet<>(scopedMentorUserIds) : null
        );
        return new MentorListResponse(
                mentors.stream()
                        .map(this::toMentorListItem)
                        .toList(),
                total,
                safePage,
                safeSize
        );
    }

    private MentorListResponse mergeFavoritedFlags(long studentUserId, MentorListResponse response) {
        if (response == null) {
            return new MentorListResponse(List.of(), 0, 1, 10);
        }
        if (response.records().isEmpty() || studentUserId <= 0) {
            return response;
        }
        Set<Long> favoritedMentorUserIds = mentorFavoriteRepository.findFavoritedMentorUserIds(
                studentUserId,
                response.records().stream().map(MentorListResponse.MentorItem::userId).toList()
        );
        return new MentorListResponse(
                response.records().stream()
                        .map(item -> copyMentorListItem(item, favoritedMentorUserIds.contains(item.userId())))
                        .toList(),
                response.total(),
                response.page(),
                response.size()
        );
    }

    private MentorListResponse.MentorItem toMentorListItem(MentorRepository.MentorListRow item) {
        return new MentorListResponse.MentorItem(
                item.userId(),
                item.displayName(),
                item.realName(),
                item.showRealName(),
                item.companyName(),
                item.jobTitle(),
                mentorAvatarService.resolveAvatarUrl(
                        item.userId(),
                        item.avatarUrl(),
                        item.displayName(),
                        item.avatarObjectKey(),
                        item.avatarUpdatedAt()
                ),
                item.expertiseTags(),
                item.serviceScenes(),
                item.bio(),
                item.priceFen(),
                normalizeRating(item.avgRating()),
                item.totalOrders(),
                item.available(),
                item.favorited()
        );
    }

    private MentorListResponse.MentorItem copyMentorListItem(MentorListResponse.MentorItem item, boolean favorited) {
        return new MentorListResponse.MentorItem(
                item.userId(),
                item.displayName(),
                item.realName(),
                item.showRealName(),
                item.companyName(),
                item.jobTitle(),
                item.avatarUrl(),
                item.expertiseTags(),
                item.serviceScenes(),
                item.bio(),
                item.priceFen(),
                item.avgRating(),
                item.totalOrders(),
                item.available(),
                favorited
        );
    }

    private MentorRecommendationsResponse.RecommendationItem toRecommendationItem(ScoredMentor scored) {
        MentorRepository.MentorListRow mentor = scored.mentor();
        return new MentorRecommendationsResponse.RecommendationItem(
                mentor.userId(),
                mentor.displayName(),
                mentor.realName(),
                mentor.showRealName(),
                mentor.companyName(),
                mentor.jobTitle(),
                mentorAvatarService.resolveAvatarUrl(
                        mentor.userId(),
                        mentor.avatarUrl(),
                        mentor.displayName(),
                        mentor.avatarObjectKey(),
                        mentor.avatarUpdatedAt()
                ),
                mentor.expertiseTags(),
                mentor.serviceScenes(),
                mentor.bio(),
                mentor.priceFen(),
                normalizeRating(mentor.avgRating()),
                mentor.totalOrders(),
                mentor.available(),
                mentor.favorited(),
                scored.score(),
                scored.reasons(),
                scored.risk(),
                scored.explainText()
        );
    }

    private List<Long> resolveFavoritedMentorScope(long studentUserId, Boolean favorited) {
        if (!Boolean.TRUE.equals(favorited)) {
            return null;
        }
        return mentorFavoriteRepository.findFavoriteMentorUserIds(studentUserId);
    }

    private List<MentorRepository.MentorListRow> applyFavoritedFlags(
            List<MentorRepository.MentorListRow> mentors,
            long studentUserId,
            Set<Long> preloadedFavoritedMentorUserIds
    ) {
        if (mentors.isEmpty()) {
            return mentors;
        }
        Set<Long> favoritedMentorUserIds = preloadedFavoritedMentorUserIds;
        if (favoritedMentorUserIds == null) {
            favoritedMentorUserIds = mentorFavoriteRepository.findFavoritedMentorUserIds(
                    studentUserId,
                    mentors.stream().map(MentorRepository.MentorListRow::userId).toList()
            );
        }
        if (favoritedMentorUserIds.isEmpty()) {
            return mentors;
        }
        Set<Long> finalFavoritedMentorUserIds = favoritedMentorUserIds;
        return mentors.stream()
                .map(mentor -> copyMentorListRow(mentor, finalFavoritedMentorUserIds.contains(mentor.userId())))
                .toList();
    }

    private MentorRepository.MentorListRow copyMentorListRow(MentorRepository.MentorListRow mentor, boolean favorited) {
        return new MentorRepository.MentorListRow(
                mentor.userId(),
                mentor.displayName(),
                mentor.realName(),
                mentor.showRealName(),
                mentor.companyName(),
                mentor.jobTitle(),
                mentor.avatarUrl(),
                mentor.avatarObjectKey(),
                mentor.avatarUpdatedAt(),
                mentor.expertiseTags(),
                mentor.serviceScenes(),
                mentor.bio(),
                mentor.priceFen(),
                mentor.avgRating(),
                mentor.totalOrders(),
                mentor.available(),
                favorited
        );
    }

    private StudentSignals loadStudentSignals(long studentUserId) {
        // 推荐和准备单都从这里取学生信号，避免多处各自拼画像口径。
        StudentProfileRepository.StudentProfileRow profileRow = studentProfileRepository.findStudentProfileByUserId(studentUserId).orElse(null);
        StudentProfileRepository.PortraitSnapshotRow portraitRow = studentProfileRepository.findPortraitSnapshot(studentUserId).orElse(null);
        AiHistoryRepository.ResumeHistoryDetailRow latestResumeRow = aiHistoryRepository.findLatestResumeDetail(studentUserId).orElse(null);
        AiInterviewRepository.InterviewSessionRow latestInterviewRow = aiInterviewRepository.findLatestCompletedSummary(studentUserId).orElse(null);
        String targetPosition = profileRow == null ? null : TextListCodec.normalizeText(profileRow.targetPosition());
        List<String> skillTags = profileRow == null ? List.of() : TextListCodec.split(profileRow.skillTags());
        String selfIntro = profileRow == null ? null : TextListCodec.normalizeText(profileRow.selfIntro());
        List<String> portraitLabels = parsePortraitLabels(portraitRow == null ? null : portraitRow.portraitTagsJson());
        PortraitDetails portraitDetails = parsePortraitDetails(portraitRow == null ? null : portraitRow.evidenceJson());
        LatestResumeSignals latestResumeSignals = parseLatestResumeSignals(latestResumeRow);
        LatestInterviewSignals latestInterviewSignals = parseLatestInterviewSignals(latestInterviewRow);
        List<String> directionKeywords = buildDirectionKeywords(targetPosition, skillTags, selfIntro);
        List<String> profileKeywords = buildProfileKeywords(
                targetPosition,
                skillTags,
                selfIntro,
                portraitLabels,
                portraitDetails.strengthTags(),
                portraitDetails.riskTags(),
                portraitSignalLevelLabel(portraitDetails.signalLevel()),
                portraitFreshnessLevelLabel(portraitDetails.freshnessLevel()),
                portraitDetails.headline(),
                portraitDetails.summary(),
                portraitDetails.nextActions(),
                latestResumeSignals.summary(),
                latestResumeSignals.suggestions(),
                latestInterviewSignals.weaknesses(),
                latestInterviewSignals.suggestions()
        );
        List<String> resumeKeywords = buildResumeKeywords(latestResumeSignals);
        List<String> interviewKeywords = buildInterviewKeywords(latestInterviewSignals);
        return new StudentSignals(
                targetPosition,
                skillTags,
                selfIntro,
                portraitLabels,
                portraitDetails.strengthTags(),
                portraitDetails.riskTags(),
                portraitDetails.signalLevel(),
                portraitDetails.freshnessLevel(),
                portraitDetails.headline(),
                portraitDetails.summary(),
                portraitDetails.nextActions(),
                latestResumeSignals.recordId(),
                latestResumeSignals.targetRole(),
                latestResumeSignals.summary(),
                latestResumeSignals.suggestions(),
                latestInterviewSignals.sessionId(),
                latestInterviewSignals.targetRole(),
                latestInterviewSignals.weaknesses(),
                latestInterviewSignals.suggestions(),
                directionKeywords,
                profileKeywords,
                resumeKeywords,
                interviewKeywords
        );
    }

    private PortraitDetails parsePortraitDetails(String evidenceJson) {
        JsonNode payload = parseJson(evidenceJson);
        return new PortraitDetails(
                readJsonStringList(payload.path("strengthTags")),
                readJsonStringList(payload.path("riskTags")),
                textValue(payload, "signalLevel"),
                textValue(payload, "freshnessLevel"),
                textValue(payload, "headline"),
                textValue(payload, "summary"),
                readJsonStringList(payload.path("nextActions"))
        );
    }

    private List<String> parsePortraitLabels(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            JsonNode root = objectMapper.readTree(json);
            if (!root.isArray()) {
                return List.of();
            }
            List<String> labels = new ArrayList<>();
            for (JsonNode item : root) {
                String label = TextListCodec.normalizeText(item.path("label").asText(null));
                if (label != null) {
                    labels.add(label);
                }
            }
            return List.copyOf(new LinkedHashSet<>(labels));
        } catch (Exception ex) {
            return List.of();
        }
    }

    private LatestResumeSignals parseLatestResumeSignals(AiHistoryRepository.ResumeHistoryDetailRow row) {
        if (row == null) {
            return LatestResumeSignals.empty();
        }
        JsonNode payload = parseJson(row.resultPayloadJson());
        String summary = firstNonBlank(textValue(payload, "summary"), row.resultSummary());
        return new LatestResumeSignals(
                row.id(),
                firstNonBlank(textValue(payload, "targetRole"), textValue(payload, "targetContext")),
                summary,
                readJsonStringList(payload.path("suggestions"))
        );
    }

    private LatestInterviewSignals parseLatestInterviewSignals(AiInterviewRepository.InterviewSessionRow row) {
        if (row == null) {
            return LatestInterviewSignals.empty();
        }
        return new LatestInterviewSignals(
                TextListCodec.normalizeText(row.sessionId()),
                TextListCodec.normalizeText(row.targetRole()),
                readJsonStringList(row.summaryWeaknessesJson()),
                readJsonStringList(row.summarySuggestionsJson())
        );
    }

    private ScoredMentor scoreMentor(
            MentorRecallCandidate recallCandidate,
            StudentSignals signals,
            String scene,
            Integer minPrice,
            Integer maxPrice
    ) {
        MentorRepository.MentorListRow mentor = recallCandidate.mentor();
        // 总分拆成场景、画像语义、方向、质量、排期和价格，方便后面解释推荐理由。
        int sceneScore = computeSceneScore(mentor.serviceScenes(), scene);
        int profileMatchScore = computeProfileMatchScore(recallCandidate, signals, scene);
        int directionScore = computeDirectionScore(recallCandidate, signals);
        int qualityScore = computeQualityScore(mentor.avgRating(), mentor.totalOrders());
        int availabilityScore = mentor.available() ? 5 : 1;
        int priceScore = computePriceScore(mentor.priceFen(), minPrice, maxPrice);
        int totalScore = Math.min(100, sceneScore + profileMatchScore + directionScore + qualityScore + availabilityScore + priceScore);

        List<String> reasons = buildRecommendationReasons(mentor, signals, scene, recallCandidate, sceneScore, profileMatchScore, directionScore, qualityScore);
        String risk = buildRecommendationRisk(mentor, minPrice, maxPrice);
        String explainText = buildRecommendationExplainText(mentor, signals, scene, reasons, risk, recallCandidate);
        return new ScoredMentor(mentor, totalScore, reasons, risk, explainText, recallCandidate.semanticSimilarity());
    }

    private int computeSceneScore(List<String> mentorScenes, String scene) {
        if (scene == null) {
            return mentorScenes.isEmpty() ? 12 : 18;
        }
        return mentorScenes.stream().anyMatch(scene::equals) ? 35 : 0;
    }

    private int computeProfileMatchScore(MentorRecallCandidate recallCandidate, StudentSignals signals, String scene) {
        List<String> sceneKeywords = buildSceneKeywords(scene);
        boolean hasSemanticSignal = !signals.profileKeywords().isEmpty()
                || signals.hasPortraitInsight()
                || signals.hasResumeSignal()
                || signals.hasInterviewSignal();
        if (!hasSemanticSignal) {
            return sceneKeywords.isEmpty() ? 6 : 8;
        }
        int base = sceneKeywords.isEmpty() ? 4 : 6;
        int keywordPart = Math.min(12, recallCandidate.profileHitCount() * 3);
        int semanticPart = mapSemanticSimilarityToProfileScore(recallCandidate.semanticSimilarity());
        return Math.min(25, Math.max(sceneKeywords.isEmpty() ? 6 : 8, base + keywordPart + semanticPart));
    }

    private int computeDirectionScore(MentorRecallCandidate recallCandidate, StudentSignals signals) {
        if (signals.directionKeywords().isEmpty()) {
            return 8;
        }
        int keywordPart = Math.min(12, recallCandidate.directionHitCount() * 4);
        int semanticBoost = recallCandidate.semanticSimilarity() >= 0.32 ? 2 : 0;
        return Math.min(20, 6 + keywordPart + semanticBoost);
    }

    private int computeQualityScore(BigDecimal rating, int totalOrders) {
        double safeRating = rating == null ? 0.0 : rating.doubleValue();
        double ratingPart = Math.min(7.0, Math.max(0.0, safeRating / 5.0 * 7.0));
        double ordersPart = Math.min(3.0, Math.max(0.0, totalOrders / 120.0 * 3.0));
        return (int) Math.round(Math.min(10.0, ratingPart + ordersPart));
    }

    private int computePriceScore(int priceFen, Integer minPrice, Integer maxPrice) {
        if (minPrice != null || maxPrice != null) {
            if (minPrice != null && priceFen < minPrice) {
                return 3;
            }
            if (maxPrice != null && priceFen > maxPrice) {
                return 0;
            }
            return 5;
        }
        if (priceFen <= 19900) {
            return 5;
        }
        if (priceFen <= 29900) {
            return 4;
        }
        if (priceFen <= 39900) {
            return 3;
        }
        return 2;
    }

    private List<String> buildRecommendationReasons(
            MentorRepository.MentorListRow mentor,
            StudentSignals signals,
            String scene,
            MentorRecallCandidate recallCandidate,
            int sceneScore,
            int profileMatchScore,
            int directionScore,
            int qualityScore
    ) {
        // 推荐理由只输出学生能理解的业务语言，不直接暴露内部向量分数。
        List<String> reasons = new ArrayList<>();
        if (scene != null && sceneScore >= 35) {
            reasons.add("匹配你的「" + scene + "」问题场景");
        }
        if (signals.targetPosition() != null && directionScore >= 12) {
            reasons.add("与你的目标岗位「" + signals.targetPosition() + "」高度相关");
        }
        if (signals.hasResumeSignal() && recallCandidate.resumeHitCount() > 0) {
            reasons.add("匹配你最近简历诊断暴露的问题");
        }
        if (signals.hasInterviewSignal() && recallCandidate.interviewHitCount() > 0) {
            reasons.add("适合继续补你当前面试短板");
        }
        if (!signals.skillTags().isEmpty() && recallCandidate.directionHitCount() > 0) {
            reasons.add("覆盖你当前最想强化的技能方向");
        }
        if (signals.hasPortraitInsight() && (profileMatchScore >= 14 || recallCandidate.semanticSimilarity() >= 0.40)) {
            reasons.add(signals.hasPortraitSummary() ? "和你的成长画像总结有明显契合点" : "和你的成长画像标签有明显契合点");
        }
        if (mentor.available()) {
            reasons.add("当前可接单，适合尽快约咨询");
        }
        if (qualityScore >= 8) {
            reasons.add("评分与历史咨询量都比较稳定");
        }
        if (reasons.isEmpty()) {
            reasons.add("公开资料较完整，适合做第一轮判断");
        }
        return reasons.stream().limit(3).toList();
    }

    private String buildRecommendationRisk(MentorRepository.MentorListRow mentor, Integer minPrice, Integer maxPrice) {
        if (!mentor.available()) {
            return "当前排期较满，预约等待时间可能更长";
        }
        if (maxPrice != null && mentor.priceFen() > maxPrice) {
            return "价格高于你当前筛选区间";
        }
        if (minPrice != null && mentor.priceFen() < minPrice) {
            return "价格明显低于你的当前筛选区间";
        }
        if (mentor.totalOrders() < 3) {
            return "公开评价样本相对较少";
        }
        return null;
    }

    private String buildRecommendationExplainText(
            MentorRepository.MentorListRow mentor,
            StudentSignals signals,
            String scene,
            List<String> reasons,
            String risk,
            MentorRecallCandidate recallCandidate
    ) {
        String firstReason = reasons.isEmpty() ? "公开资料匹配度较高" : reasons.get(0);
        if (scene != null) {
            return firstReason + "，并且他的公开服务场景里明确覆盖了「" + scene + "」。";
        }
        if (signals.hasResumeSignal() && recallCandidate.resumeHitCount() > 0) {
            return firstReason + "，推荐时已纳入你最近一次简历诊断的重点问题。";
        }
        if (signals.hasInterviewSignal() && recallCandidate.interviewHitCount() > 0) {
            return firstReason + "，推荐时已纳入你最近一次面试复盘暴露的短板。";
        }
        if (signals.targetPosition() != null) {
            return firstReason + "，适合作为你在「" + signals.targetPosition() + "」方向上的优先候选。";
        }
        if (risk != null) {
            return firstReason + "，但也要注意：" + risk + "。";
        }
        return firstReason + "，适合先收藏或进一步查看详情。";
    }

    private int mapSemanticSimilarityToProfileScore(double similarity) {
        if (similarity >= 0.72) {
            return 13;
        }
        if (similarity >= 0.55) {
            return 10;
        }
        if (similarity >= 0.38) {
            return 7;
        }
        if (similarity >= 0.22) {
            return 4;
        }
        if (similarity >= 0.10) {
            return 2;
        }
        return 0;
    }

    private String buildBasisSummary(StudentSignals signals, String scene, boolean weakSignal, boolean emptyResult) {
        List<String> factors = new ArrayList<>();
        if (scene != null) {
            factors.add("当前问题场景");
        }
        if (signals.targetPosition() != null) {
            factors.add("目标岗位");
        }
        if (!signals.skillTags().isEmpty()) {
            factors.add("技能标签");
        }
        if (signals.hasPortraitSummary()) {
            factors.add("成长画像总结");
        } else if (!signals.portraitLabels().isEmpty()) {
            factors.add("成长画像标签");
        }
        if (!signals.strengthTags().isEmpty()) {
            factors.add("当前优势");
        }
        if (!signals.riskTags().isEmpty()) {
            factors.add("待补短板");
        }
        if (signals.hasResumeSignal()) {
            factors.add("最近简历诊断");
        }
        if (signals.hasInterviewSignal()) {
            factors.add("最近面试复盘");
        }

        if (emptyResult) {
            return factors.isEmpty()
                    ? "当前筛选条件较严格，暂时没有可推荐的导师，可以先放宽价格或取消仅看收藏。"
                    : "本次按「" + String.join("、", factors) + "」筛选后暂无合适导师，可适当放宽条件后重新计算。";
        }

        if (factors.isEmpty()) {
            return "当前缺少足够的个人画像信号，本次推荐主要依据公共导师数据。";
        }
        if (weakSignal) {
            return "本次推荐主要依据「" + String.join("、", factors) + "」，但你的成长画像信号仍偏弱，建议先补齐简历优化、模拟面试或社区互动后再重新计算。";
        }
        StringBuilder summary = new StringBuilder("本次推荐基于「" + String.join("、", factors) + "」综合生成。");
        if (signals.hasPortraitSummary() || !signals.strengthTags().isEmpty() || !signals.riskTags().isEmpty()) {
            summary.append("推荐时已优先纳入你的成长画像结论、优势与待补点。");
        }
        if ("STALE".equalsIgnoreCase(signals.freshnessLevel())) {
            summary.append("当前画像信号偏旧，近期补一轮新的简历或面试样本后，推荐会更稳。");
        }
        return summary.toString();
    }

    private List<String> buildBasisTags(StudentSignals signals, String scene) {
        LinkedHashSet<String> tags = new LinkedHashSet<>();
        if (scene != null) {
            tags.add("问题场景：" + scene);
        }
        if (signals.targetPosition() != null) {
            tags.add("目标岗位：" + signals.targetPosition());
        }
        signals.skillTags().stream().limit(2).forEach(tag -> tags.add("技能：" + tag));
        if (signals.signalLevel() != null) {
            tags.add("画像信号：" + portraitSignalLevelLabel(signals.signalLevel()));
        }
        signals.strengthTags().stream().limit(1).forEach(tag -> tags.add("优势：" + tag));
        signals.riskTags().stream().limit(1).forEach(tag -> tags.add("待补：" + tag));
        if (signals.strengthTags().isEmpty() && signals.riskTags().isEmpty()) {
            signals.portraitLabels().stream().limit(1).forEach(label -> tags.add("画像标签：" + label));
        }
        if (signals.hasResumeSignal()) {
            tags.add("最近简历诊断");
        }
        if (signals.hasInterviewSignal()) {
            tags.add("最近面试复盘");
        }
        if (tags.isEmpty()) {
            tags.add("公共导师数据");
        }
        return List.copyOf(tags);
    }

    private boolean isWeakSignal(StudentSignals signals, String scene) {
        // 弱信号会提示前端“推荐依据不足”，避免把低置信推荐包装得过满。
        return scene == null
                && signals.targetPosition() == null
                && signals.skillTags().isEmpty()
                && signals.selfIntro() == null
                && !signals.hasPortraitTagSignals()
                && !signals.hasResumeSignal()
                && !signals.hasInterviewSignal();
    }

    private List<String> buildProfileKeywords(
            String targetPosition,
            List<String> skillTags,
            String selfIntro,
            List<String> portraitLabels,
            List<String> strengthTags,
            List<String> riskTags,
            String signalLevel,
            String freshnessLevel,
            String portraitHeadline,
            String portraitSummary,
            List<String> portraitNextActions,
            String latestResumeSummary,
            List<String> latestResumeSuggestions,
            List<String> latestInterviewWeaknesses,
            List<String> latestInterviewSuggestions
    ) {
        LinkedHashSet<String> keywords = new LinkedHashSet<>();
        appendKeywords(keywords, targetPosition);
        skillTags.forEach(tag -> appendKeywords(keywords, tag));
        appendKeywords(keywords, selfIntro);
        portraitLabels.forEach(label -> appendKeywords(keywords, label));
        strengthTags.forEach(tag -> appendKeywords(keywords, tag));
        riskTags.forEach(tag -> appendKeywords(keywords, tag));
        appendKeywords(keywords, signalLevel);
        appendKeywords(keywords, freshnessLevel);
        appendKeywords(keywords, portraitHeadline);
        appendKeywords(keywords, portraitSummary);
        portraitNextActions.forEach(tag -> appendKeywords(keywords, tag));
        appendKeywords(keywords, latestResumeSummary);
        latestResumeSuggestions.forEach(tag -> appendKeywords(keywords, tag));
        latestInterviewWeaknesses.forEach(tag -> appendKeywords(keywords, tag));
        latestInterviewSuggestions.forEach(tag -> appendKeywords(keywords, tag));
        return List.copyOf(keywords);
    }

    private List<String> buildDirectionKeywords(String targetPosition, List<String> skillTags, String selfIntro) {
        LinkedHashSet<String> keywords = new LinkedHashSet<>();
        appendKeywords(keywords, targetPosition);
        skillTags.forEach(tag -> appendKeywords(keywords, tag));
        appendKeywords(keywords, selfIntro);
        return List.copyOf(keywords);
    }

    private List<String> buildResumeKeywords(LatestResumeSignals signals) {
        LinkedHashSet<String> keywords = new LinkedHashSet<>();
        appendKeywords(keywords, signals.targetRole());
        appendKeywords(keywords, signals.summary());
        signals.suggestions().forEach(item -> appendKeywords(keywords, item));
        return List.copyOf(keywords);
    }

    private List<String> buildInterviewKeywords(LatestInterviewSignals signals) {
        LinkedHashSet<String> keywords = new LinkedHashSet<>();
        appendKeywords(keywords, signals.targetRole());
        signals.weaknesses().forEach(item -> appendKeywords(keywords, item));
        signals.suggestions().forEach(item -> appendKeywords(keywords, item));
        return List.copyOf(keywords);
    }

    private void appendKeywords(Set<String> bucket, String text) {
        String normalized = normalizeToSearchableText(text);
        if (normalized.isBlank()) {
            return;
        }
        addIfContains(bucket, normalized, "前端", "react", "vue", "javascript", "typescript");
        addIfContains(bucket, normalized, "后端", "java", "golang", "go", "spring", "node", "mysql", "数据库", "微服务");
        addIfContains(bucket, normalized, "全栈", "node", "react", "typescript");
        addIfContains(bucket, normalized, "产品", "需求", "商业化", "原型");
        addIfContains(bucket, normalized, "设计", "交互", "ux", "ui", "作品集");
        addIfContains(bucket, normalized, "运营", "增长", "内容", "数据分析");
        addIfContains(bucket, normalized, "算法", "机器学习", "推荐", "大模型");
        addIfContains(bucket, normalized, "测试", "自动化测试", "ci/cd", "质量");
        addIfContains(bucket, normalized, "云原生", "kubernetes", "架构", "系统设计");
        addIfContains(bucket, normalized, "简历", "项目", "面试", "校招", "offer", "转行", "跨专业");

        String[] rawTokens = normalized.split("[\\s,，/|·]+");
        for (String rawToken : rawTokens) {
            String token = TextListCodec.normalizeText(rawToken);
            if (token != null && token.length() >= 2 && token.length() <= 20) {
                bucket.add(token.toLowerCase(Locale.ROOT));
            }
        }
    }

    private void addIfContains(Set<String> bucket, String normalizedText, String anchor, String... relatedKeywords) {
        if (!normalizedText.contains(anchor.toLowerCase(Locale.ROOT))) {
            return;
        }
        bucket.add(anchor.toLowerCase(Locale.ROOT));
        for (String relatedKeyword : relatedKeywords) {
            bucket.add(relatedKeyword.toLowerCase(Locale.ROOT));
        }
    }

    private List<String> buildSceneKeywords(String scene) {
        if (scene == null) {
            return List.of();
        }
        return switch (scene) {
            case "简历诊断" -> List.of("简历", "量化", "亮点", "经历");
            case "项目表达" -> List.of("项目", "表达", "亮点", "复盘");
            case "模拟面试复盘" -> List.of("面试", "复盘", "追问", "表达");
            case "岗位方向选择" -> List.of("方向", "岗位", "规划", "选择");
            case "校招投递策略" -> List.of("校招", "投递", "策略", "简历");
            case "转行 / 跨专业求职" -> List.of("转行", "跨专业", "0基础", "求职");
            case "Offer 对比与决策" -> List.of("offer", "决策", "选择", "比较");
            default -> List.of();
        };
    }

    private StudentRecommendationSnapshot buildStudentRecommendationSnapshot(long studentUserId, StudentSignals signals) {
        // 快照文本既用于向量化，也用于后台追踪推荐输入，不依赖页面临时状态。
        List<String> lines = new ArrayList<>();
        appendLabeledLine(lines, "目标岗位", signals.targetPosition());
        appendLabeledList(lines, "技能标签", signals.skillTags(), 6);
        appendLabeledLine(lines, "自我介绍", truncateContext(signals.selfIntro(), 260));
        appendLabeledList(lines, "画像标签", signals.portraitLabels(), 6);
        appendLabeledLine(lines, "画像信号强度", portraitSignalLevelLabel(signals.signalLevel()));
        appendLabeledLine(lines, "画像新鲜度", portraitFreshnessLevelLabel(signals.freshnessLevel()));
        appendLabeledLine(lines, "成长画像标题", truncateContext(signals.portraitHeadline(), 180));
        appendLabeledLine(lines, "成长画像总结", truncateContext(signals.portraitSummary(), 260));
        appendLabeledList(lines, "成长画像优势", signals.strengthTags(), 4);
        appendLabeledList(lines, "成长画像待补点", signals.riskTags(), 4);
        appendLabeledList(lines, "成长画像下一步建议", signals.portraitNextActions(), 3);
        appendLabeledLine(lines, "最近简历目标岗位", signals.latestResumeTargetRole());
        appendLabeledLine(lines, "最近简历总结", truncateContext(signals.latestResumeSummary(), 220));
        appendLabeledList(lines, "最近简历建议", signals.latestResumeSuggestions(), 4);
        appendLabeledLine(lines, "最近面试目标岗位", signals.latestInterviewTargetRole());
        appendLabeledList(lines, "最近面试短板", signals.latestInterviewWeaknesses(), 4);
        appendLabeledList(lines, "最近面试建议", signals.latestInterviewSuggestions(), 4);
        String contentText = String.join("\n", lines);

        return new StudentRecommendationSnapshot(
                studentUserId,
                contentText,
                signals.targetPosition(),
                serializeJson(signals.skillTags()),
                serializeJson(signals.portraitLabels()),
                signals.latestResumeRecordId(),
                signals.latestResumeTargetRole(),
                signals.latestResumeSummary(),
                serializeJson(signals.latestResumeSuggestions()),
                signals.latestInterviewSessionId(),
                signals.latestInterviewTargetRole(),
                serializeJson(signals.latestInterviewWeaknesses()),
                serializeJson(signals.latestInterviewSuggestions()),
                serializeJson(buildSignalFlags(signals)),
                recommendationEmbeddingService.hashContent(contentText)
        );
    }

    private MentorRecommendationSnapshot buildMentorRecommendationSnapshot(MentorRepository.MentorListRow mentor) {
        List<String> lines = new ArrayList<>();
        appendLabeledLine(lines, "导师", mentor.displayName());
        appendLabeledLine(lines, "公司", mentor.companyName());
        appendLabeledLine(lines, "职位", mentor.jobTitle());
        appendLabeledList(lines, "擅长方向", mentor.expertiseTags(), 6);
        appendLabeledList(lines, "服务场景", mentor.serviceScenes(), 6);
        appendLabeledLine(lines, "导师简介", truncateContext(mentor.bio(), 260));
        String contentText = String.join("\n", lines);
        return new MentorRecommendationSnapshot(
                mentor.userId(),
                contentText,
                serializeJson(mentor.expertiseTags()),
                serializeJson(mentor.serviceScenes()),
                recommendationEmbeddingService.hashContent(contentText),
                computeQualityScore(mentor.avgRating(), mentor.totalOrders()),
                mentor.priceFen(),
                mentor.available()
        );
    }

    private void persistStudentRecommendationSnapshot(StudentRecommendationSnapshot snapshot) {
        try {
            mentorRecommendationRepository.saveStudentSnapshot(new MentorRecommendationRepository.StudentRecommendationSnapshotCommand(
                    snapshot.studentUserId(),
                    snapshot.contentText(),
                    snapshot.targetPosition(),
                    snapshot.skillTagsJson(),
                    snapshot.portraitTagsJson(),
                    snapshot.latestResumeRecordId(),
                    snapshot.latestResumeTargetRole(),
                    snapshot.latestResumeSummary(),
                    snapshot.latestResumeSuggestionsJson(),
                    snapshot.latestInterviewSessionId(),
                    snapshot.latestInterviewTargetRole(),
                    snapshot.latestInterviewWeaknessesJson(),
                    snapshot.latestInterviewSuggestionsJson(),
                    snapshot.signalFlagsJson(),
                    snapshot.contentHash()
            ));
            resolveOrCreateEmbeddingVector(RECOMMENDATION_ENTITY_STUDENT, snapshot.studentUserId(), snapshot.contentText(), snapshot.contentHash());
        } catch (Exception ex) {
            log.warn("mentor recommendation student snapshot persistence skipped studentUserId={}", snapshot.studentUserId(), ex);
        }
    }

    private List<MentorRecallCandidate> recallMentorCandidates(
            List<MentorRepository.MentorListRow> candidates,
            StudentSignals signals,
            String scene,
            double[] queryVector
    ) {
        if (candidates.isEmpty()) {
            return List.of();
        }

        // 每个导师都先转成可搜索文本和向量，再统一计算语义相似度与关键词命中数。
        List<MentorRecallCandidate> profiles = new ArrayList<>();
        for (MentorRepository.MentorListRow mentor : candidates) {
            MentorRecommendationSnapshot snapshot = buildMentorRecommendationSnapshot(mentor);
            double[] mentorVector = persistMentorSnapshotAndResolveVector(snapshot);
            String searchableText = buildSearchableText(mentor);
            double semanticSimilarity = recommendationEmbeddingService.cosineSimilarity(queryVector, mentorVector);
            int profileHitCount = countKeywordHits(searchableText, signals.profileKeywords());
            int directionHitCount = countKeywordHits(searchableText, signals.directionKeywords());
            int resumeHitCount = countKeywordHits(searchableText, signals.resumeKeywords());
            int interviewHitCount = countKeywordHits(searchableText, signals.interviewKeywords());
            double recallScore = buildRecallScore(mentor, scene, semanticSimilarity, profileHitCount, directionHitCount, resumeHitCount, interviewHitCount);
            profiles.add(new MentorRecallCandidate(
                    mentor,
                    searchableText,
                    semanticSimilarity,
                    recallScore,
                    profileHitCount,
                    directionHitCount,
                    resumeHitCount,
                    interviewHitCount,
                    0
            ));
        }

        boolean fallbackToQualityOrder = isWeakSignal(signals, scene)
                || profiles.stream().mapToDouble(MentorRecallCandidate::semanticSimilarity).max().orElse(0.0) < 0.10;

        // 学生侧信号太弱时退回场景、可接单和服务质量排序，避免低质量语义分误导结果。
        List<MentorRecallCandidate> ordered = (fallbackToQualityOrder ? profiles.stream()
                .sorted(Comparator
                        .comparing((MentorRecallCandidate item) -> scene != null && item.mentor().serviceScenes().stream().anyMatch(scene::equals))
                        .reversed()
                        .thenComparing((MentorRecallCandidate item) -> item.mentor().available(), Comparator.reverseOrder())
                        .thenComparing((MentorRecallCandidate item) -> computeQualityScore(item.mentor().avgRating(), item.mentor().totalOrders()), Comparator.reverseOrder()))
                : profiles.stream()
                .sorted(Comparator
                        .comparingDouble(MentorRecallCandidate::recallScore).reversed()
                        .thenComparing((MentorRecallCandidate item) -> item.mentor().available(), Comparator.reverseOrder())
                        .thenComparing((MentorRecallCandidate item) -> computeQualityScore(item.mentor().avgRating(), item.mentor().totalOrders()), Comparator.reverseOrder())))
                .limit(Math.min(RECOMMENDATION_RECALL_LIMIT, profiles.size()))
                .toList();

        List<MentorRecallCandidate> ranked = new ArrayList<>();
        for (int index = 0; index < ordered.size(); index++) {
            MentorRecallCandidate item = ordered.get(index);
            ranked.add(new MentorRecallCandidate(
                    item.mentor(),
                    item.searchableText(),
                    item.semanticSimilarity(),
                    item.recallScore(),
                    item.profileHitCount(),
                    item.directionHitCount(),
                    item.resumeHitCount(),
                    item.interviewHitCount(),
                    index + 1
            ));
        }
        return List.copyOf(ranked);
    }

    private double[] buildRecommendationQueryVector(String baseSnapshotText, String scene, String keyword, String expertise) {
        List<String> lines = new ArrayList<>();
        appendLabeledLine(lines, "学生画像", baseSnapshotText);
        appendLabeledLine(lines, "当前问题场景", scene);
        appendLabeledLine(lines, "搜索关键词", TextListCodec.normalizeText(keyword));
        appendLabeledLine(lines, "额外方向", TextListCodec.normalizeText(expertise));
        return recommendationEmbeddingService.embed(String.join("\n", lines));
    }

    private double[] persistMentorSnapshotAndResolveVector(MentorRecommendationSnapshot snapshot) {
        try {
            mentorRecommendationRepository.saveMentorSnapshot(new MentorRecommendationRepository.MentorRecommendationSnapshotCommand(
                    snapshot.mentorUserId(),
                    snapshot.contentText(),
                    snapshot.expertiseTagsJson(),
                    snapshot.serviceScenesJson(),
                    snapshot.qualityScore(),
                    snapshot.priceFen(),
                    snapshot.available(),
                    snapshot.contentHash()
            ));
            return resolveOrCreateEmbeddingVector(RECOMMENDATION_ENTITY_MENTOR, snapshot.mentorUserId(), snapshot.contentText(), snapshot.contentHash());
        } catch (Exception ex) {
            log.warn("mentor recommendation mentor snapshot persistence skipped mentorUserId={}", snapshot.mentorUserId(), ex);
            return recommendationEmbeddingService.embed(snapshot.contentText());
        }
    }

    private double[] resolveOrCreateEmbeddingVector(String entityType, long entityId, String contentText, String contentHash) {
        try {
            // contentHash 相同就复用已落库向量，减少重复计算和推荐接口耗时。
            return mentorRecommendationRepository.findEmbeddingVector(entityType, entityId, recommendationEmbeddingService.modelCode())
                    .filter(row -> contentHash.equals(row.contentHash()))
                    .map(row -> recommendationEmbeddingService.deserializeVector(row.vectorJson()))
                    .orElseGet(() -> {
                        double[] vector = recommendationEmbeddingService.embed(contentText);
                        mentorRecommendationRepository.saveEmbeddingVector(new MentorRecommendationRepository.EmbeddingVectorCommand(
                                entityType,
                                entityId,
                                recommendationEmbeddingService.modelCode(),
                                recommendationEmbeddingService.vectorDim(),
                                recommendationEmbeddingService.serializeVector(vector),
                                contentHash
                        ));
                        return vector;
                    });
        } catch (Exception ex) {
            log.warn("mentor recommendation embedding persistence skipped entityType={}, entityId={}", entityType, entityId, ex);
            return recommendationEmbeddingService.embed(contentText);
        }
    }

    private double buildRecallScore(
            MentorRepository.MentorListRow mentor,
            String scene,
            double semanticSimilarity,
            int profileHitCount,
            int directionHitCount,
            int resumeHitCount,
            int interviewHitCount
    ) {
        // 召回分以语义相似度为主，再给场景、关键词、可用性和服务质量一些业务加权。
        double score = semanticSimilarity;
        if (scene != null && mentor.serviceScenes().stream().anyMatch(scene::equals)) {
            score += 0.24;
        }
        score += Math.min(0.12, directionHitCount * 0.03);
        score += Math.min(0.08, profileHitCount * 0.02);
        score += Math.min(0.08, (resumeHitCount + interviewHitCount) * 0.02);
        score += mentor.available() ? 0.05 : 0.0;
        score += computeQualityScore(mentor.avgRating(), mentor.totalOrders()) / 100.0;
        return score;
    }

    private void logRecommendationRun(
            long studentUserId,
            String scene,
            String keyword,
            String expertise,
            Integer minPrice,
            Integer maxPrice,
            Boolean available,
            Boolean favorited,
            boolean weakSignal,
            String basisSummary,
            int candidateCount,
            List<MentorRecallCandidate> recalledCandidates,
            List<ScoredMentor> scoredMentors
    ) {
        try {
            long runId = mentorRecommendationRepository.createRecommendationRun(new MentorRecommendationRepository.RecommendationRunCommand(
                    studentUserId,
                    scene,
                    TextListCodec.normalizeText(keyword),
                    TextListCodec.normalizeText(expertise),
                    serializeJson(buildRecommendationFilterPayload(minPrice, maxPrice, available, favorited)),
                    recommendationEmbeddingService.modelCode(),
                    RECOMMENDATION_RERANK_VERSION,
                    weakSignal,
                    candidateCount,
                    recalledCandidates.size(),
                    serializeJson(scoredMentors.stream().map(item -> item.mentor().userId()).toList()),
                    basisSummary
            ));
            if (runId <= 0L) {
                return;
            }

            for (MentorRecallCandidate candidate : recalledCandidates) {
                mentorRecommendationRepository.appendRecommendationEvent(new MentorRecommendationRepository.RecommendationEventCommand(
                        runId,
                        candidate.mentor().userId(),
                        "RECALL",
                        candidate.recallRank(),
                        candidate.recallScore(),
                        serializeJson(buildRecallDetailPayload(scene, candidate))
                ));
            }

            for (int index = 0; index < scoredMentors.size(); index++) {
                ScoredMentor scored = scoredMentors.get(index);
                mentorRecommendationRepository.appendRecommendationEvent(new MentorRecommendationRepository.RecommendationEventCommand(
                        runId,
                        scored.mentor().userId(),
                        "RERANK",
                        index + 1,
                        (double) scored.score(),
                        serializeJson(buildRerankDetailPayload(scored))
                ));
            }
        } catch (Exception ex) {
            log.warn("mentor recommendation run logging skipped studentUserId={}", studentUserId, ex);
        }
    }

    private Object buildRecommendationFilterPayload(Integer minPrice, Integer maxPrice, Boolean available, Boolean favorited) {
        java.util.LinkedHashMap<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("minPrice", minPrice);
        payload.put("maxPrice", maxPrice);
        payload.put("available", available);
        payload.put("favorited", favorited);
        return payload;
    }

    private Object buildRecallDetailPayload(String scene, MentorRecallCandidate candidate) {
        java.util.LinkedHashMap<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("sceneMatched", scene != null && candidate.mentor().serviceScenes().stream().anyMatch(scene::equals));
        payload.put("semanticSimilarity", candidate.semanticSimilarity());
        payload.put("recallScore", candidate.recallScore());
        payload.put("profileHitCount", candidate.profileHitCount());
        payload.put("directionHitCount", candidate.directionHitCount());
        payload.put("resumeHitCount", candidate.resumeHitCount());
        payload.put("interviewHitCount", candidate.interviewHitCount());
        return payload;
    }

    private Object buildRerankDetailPayload(ScoredMentor scored) {
        java.util.LinkedHashMap<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("semanticSimilarity", scored.semanticSimilarity());
        payload.put("reasons", scored.reasons());
        payload.put("risk", scored.risk());
        payload.put("explainText", scored.explainText());
        return payload;
    }

    private java.util.LinkedHashMap<String, Object> buildSignalFlags(StudentSignals signals) {
        java.util.LinkedHashMap<String, Object> flags = new java.util.LinkedHashMap<>();
        flags.put("hasResumeSignal", signals.hasResumeSignal());
        flags.put("hasInterviewSignal", signals.hasInterviewSignal());
        flags.put("hasPortraitSignal", signals.hasPortraitInsight());
        flags.put("hasPortraitTagSignal", signals.hasPortraitTagSignals());
        flags.put("hasPortraitSummary", signals.hasPortraitSummary());
        flags.put("hasDirectionSignal", !signals.directionKeywords().isEmpty());
        flags.put("portraitSignalLevel", signals.signalLevel());
        flags.put("portraitFreshnessLevel", signals.freshnessLevel());
        flags.put("portraitStrengthTags", signals.strengthTags());
        flags.put("portraitRiskTags", signals.riskTags());
        flags.put("portraitHeadline", truncateContext(signals.portraitHeadline(), 120));
        flags.put("portraitSummary", truncateContext(signals.portraitSummary(), 200));
        flags.put("portraitNextActions", signals.portraitNextActions());
        return flags;
    }

    private String serializeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new IllegalStateException("failed to serialize mentor recommendation payload", ex);
        }
    }

    private String buildPrepSummary(
            String scene,
            String targetPosition,
            MentorRepository.MentorDetailRow mentor,
            StudentSignals signals,
            String mentorFocus
    ) {
        String introHint = signals.selfIntro() == null ? "" : "我当前的背景是：" + signals.selfIntro() + "。";
        return "我目前正在准备「" + targetPosition + "」方向的求职，希望围绕「" + scene + "」做一次更聚焦的咨询。"
                + introHint
                + "我选择 " + mentor.displayName() + " 导师，是因为他在「" + mentorFocus + "」方面更有经验。"
                + "希望这次沟通能帮我明确下一步应该优先优化哪些问题。";
    }

    private List<String> buildPrepQuestions(
            String scene,
            String targetPosition,
            MentorRepository.MentorDetailRow mentor,
            String mentorFocus
    ) {
        return switch (scene) {
            case "简历诊断" -> List.of(
                    "针对「" + targetPosition + "」岗位，我的简历里哪些内容最需要优先补强？",
                    "我现有项目经历应该怎样改写，才能更突出技术深度和结果价值？",
                    mentor.displayName() + " 导师视角下，企业在初筛时最容易卡住我的问题是什么？"
            );
            case "项目表达" -> List.of(
                    "我该如何把现有项目讲得更像「" + mentorFocus + "」相关的真实经历？",
                    "哪些项目细节最值得在面试和简历里重点展开？",
                    "如果时间有限，我最应该优先补哪一类项目材料或数据证明？"
            );
            case "模拟面试复盘" -> List.of(
                    "如果按「" + targetPosition + "」岗位来面试，我当前最容易被追问卡住的点有哪些？",
                    "回答项目题和场景题时，我应该怎样组织表达更有说服力？",
                    "从导师视角看，下一轮面试前我最值得刻意训练的部分是什么？"
            );
            case "岗位方向选择" -> List.of(
                    "结合我的背景，「" + targetPosition + "」是否仍然是最值得继续投入的方向？",
                    "我应该如何判断自己更适合哪条细分赛道或岗位类型？",
                    "未来 1 到 2 个月内，我最值得补的关键能力是什么？"
            );
            case "校招投递策略" -> List.of(
                    "我当前适合优先投哪些类型的公司和岗位层级？",
                    "在校招节奏里，我应该怎样安排简历优化、投递和面试准备的顺序？",
                    "如果只能优先准备三件事，最影响转化率的是什么？"
            );
            case "转行 / 跨专业求职" -> List.of(
                    "以我目前背景，转向「" + targetPosition + "」最现实的切入方式是什么？",
                    "我应该先补哪些证明材料，才能降低跨专业/转行带来的质疑？",
                    "导师建议我在简历、自我介绍和项目准备上分别先做哪些动作？"
            );
            case "Offer 对比与决策" -> List.of(
                    "如果有多个机会，我应该优先看哪些维度来做决定？",
                    "从长期成长看，哪些 offer 信息最值得重点比较？",
                    "如果只能选一个最稳妥的方向，导师会建议我怎样判断取舍？"
            );
            default -> List.of(
                    "这次咨询里，我最值得优先和导师讨论的核心问题是什么？",
                    "针对「" + targetPosition + "」方向，我下一步最重要的优化动作是什么？",
                    "如果只做三件事，导师建议我先补哪些内容？"
            );
        };
    }

    private List<String> buildSuggestedMaterials(String scene, StudentSignals signals) {
        LinkedHashSet<String> materials = new LinkedHashSet<>();
        materials.add("我的最新简历");
        if (signals.targetPosition() != null) {
            materials.add("目标岗位 JD");
        }
        if ("模拟面试复盘".equals(scene)) {
            materials.add("面试复盘记录");
        }
        if ("项目表达".equals(scene) || "简历诊断".equals(scene)) {
            materials.add("项目介绍");
        }
        if ("Offer 对比与决策".equals(scene)) {
            materials.add("候选 offer 信息");
        }
        if (materials.size() < 3) {
            materials.add("其他补充材料");
        }
        return List.copyOf(materials);
    }

    private List<String> buildExpectedOutcomes(String scene) {
        return switch (scene) {
            case "简历诊断" -> List.of("获得简历修改建议", "获得综合咨询建议");
            case "模拟面试复盘" -> List.of("获得面试复盘建议", "获得综合咨询建议");
            case "岗位方向选择" -> List.of("获得求职方向建议", "获得综合咨询建议");
            default -> List.of("获得综合咨询建议");
        };
    }

    private String buildMentorPrepMentorContext(MentorRepository.MentorDetailRow mentor) {
        List<String> lines = new ArrayList<>();
        appendLabeledLine(lines, "导师", mentor.displayName());
        appendLabeledLine(lines, "公司", mentor.companyName());
        appendLabeledLine(lines, "职位", mentor.jobTitle());
        appendLabeledList(lines, "擅长方向", mentor.expertiseTags(), 5);
        appendLabeledList(lines, "服务场景", mentor.serviceScenes(), 5);
        appendLabeledLine(lines, "导师简介", truncateContext(mentor.bio(), 240));
        appendLabeledLine(lines, "适合人群", truncateContext(mentor.suitableFor(), 160));
        appendLabeledLine(lines, "不太适合", truncateContext(mentor.notSuitableFor(), 160));
        appendLabeledLine(lines, "建议准备材料", truncateContext(mentor.prepMaterials(), 160));
        appendLabeledLine(lines, "回复节奏", truncateContext(mentor.replyRhythm(), 120));
        return String.join("\n", lines);
    }

    private String buildMentorPrepStudentContext(String targetPosition, StudentSignals signals, List<String> signalTags) {
        List<String> lines = new ArrayList<>();
        appendLabeledLine(lines, "目标岗位", targetPosition);
        appendLabeledList(lines, "技能标签", signals.skillTags(), 4);
        appendLabeledList(lines, "画像标签", signals.portraitLabels(), 4);
        appendLabeledLine(lines, "画像信号强度", portraitSignalLevelLabel(signals.signalLevel()));
        appendLabeledLine(lines, "画像新鲜度", portraitFreshnessLevelLabel(signals.freshnessLevel()));
        appendLabeledLine(lines, "成长画像标题", truncateContext(signals.portraitHeadline(), 120));
        appendLabeledLine(lines, "成长画像总结", truncateContext(signals.portraitSummary(), 180));
        appendLabeledList(lines, "成长画像优势", signals.strengthTags(), 3);
        appendLabeledList(lines, "成长画像待补点", signals.riskTags(), 3);
        appendLabeledList(lines, "成长画像下一步建议", signals.portraitNextActions(), 3);
        appendLabeledLine(lines, "学生自我介绍", truncateContext(signals.selfIntro(), 260));
        appendLabeledList(lines, "当前匹配信号", signalTags, 4);
        return String.join("\n", lines);
    }

    private String buildLatestResumeContext(long studentUserId) {
        return aiHistoryRepository.findLatestResumeDetail(studentUserId)
                .map(row -> {
                    JsonNode payload = parseJson(row.resultPayloadJson());
                    List<String> lines = new ArrayList<>();
                    appendLabeledLine(lines, "最近简历优化目标岗位", textValue(payload, "targetRole"));
                    appendLabeledLine(lines, "最近简历优化语境", textValue(payload, "targetContext"));
                    appendLabeledLine(lines, "最近简历总结", truncateContext(firstNonBlank(textValue(payload, "summary"), row.resultSummary()), 240));
                    appendLabeledList(lines, "最近简历建议", readJsonStringList(payload.path("suggestions")), 3);
                    return String.join("\n", lines);
                })
                .orElse("");
    }

    private String buildLatestInterviewSummaryContext(long studentUserId) {
        return aiInterviewRepository.findLatestCompletedSummary(studentUserId)
                .map(session -> {
                    List<String> lines = new ArrayList<>();
                    appendLabeledLine(lines, "最近面试目标岗位", session.targetRole());
                    if (session.summaryOverallScore() != null) {
                        lines.add("最近面试总分：" + session.summaryOverallScore());
                    }
                    appendLabeledList(lines, "最近面试亮点", readJsonStringList(session.summaryStrengthsJson()), 2);
                    appendLabeledList(lines, "最近面试短板", readJsonStringList(session.summaryWeaknessesJson()), 2);
                    appendLabeledList(lines, "最近面试建议", readJsonStringList(session.summarySuggestionsJson()), 3);
                    return String.join("\n", lines);
                })
                .orElse("");
    }

    private String buildSearchableText(MentorRepository.MentorListRow mentor) {
        return normalizeToSearchableText(String.join(
                " ",
                safe(mentor.companyName()),
                safe(mentor.jobTitle()),
                safe(mentor.bio()),
                String.join(" ", mentor.expertiseTags()),
                String.join(" ", mentor.serviceScenes())
        ));
    }

    private int countKeywordHits(String searchableText, List<String> keywords) {
        int hits = 0;
        LinkedHashSet<String> uniqueKeywords = new LinkedHashSet<>();
        for (String keyword : keywords) {
            String normalizedKeyword = TextListCodec.normalizeText(keyword);
            if (normalizedKeyword != null) {
                uniqueKeywords.add(normalizedKeyword.toLowerCase(Locale.ROOT));
            }
        }
        for (String keyword : uniqueKeywords) {
            if (searchableText.contains(keyword)) {
                hits++;
            }
        }
        return hits;
    }

    private String normalizeSceneFilter(String rawScene) {
        String scene = TextListCodec.normalizeText(rawScene);
        if (scene == null || "不限".equals(scene)) {
            return null;
        }
        if (!ALLOWED_SCENE_SET.contains(scene)) {
            throw new ApiException("BIZ-1001", "scene invalid", HttpStatus.BAD_REQUEST);
        }
        return scene;
    }

    private String normalizePrepScene(String rawScene) {
        String scene = TextListCodec.normalizeText(rawScene);
        if (scene == null) {
            throw new ApiException("BIZ-1001", "scene invalid", HttpStatus.BAD_REQUEST);
        }
        if ("不限".equals(scene)) {
            return "综合咨询";
        }
        if (!ALLOWED_SCENE_SET.contains(scene)) {
            throw new ApiException("BIZ-1001", "scene invalid", HttpStatus.BAD_REQUEST);
        }
        return scene;
    }

    private String normalizeToSearchableText(String text) {
        return safe(text).toLowerCase(Locale.ROOT);
    }

    private String portraitSignalLevelLabel(String signalLevel) {
        if (signalLevel == null || signalLevel.isBlank()) {
            return null;
        }
        return switch (signalLevel.trim().toUpperCase(Locale.ROOT)) {
            case "STRONG" -> "强";
            case "NORMAL" -> "中";
            case "WEAK" -> "弱";
            default -> TextListCodec.normalizeText(signalLevel);
        };
    }

    private String portraitFreshnessLevelLabel(String freshnessLevel) {
        if (freshnessLevel == null || freshnessLevel.isBlank()) {
            return null;
        }
        return switch (freshnessLevel.trim().toUpperCase(Locale.ROOT)) {
            case "FRESH" -> "新鲜";
            case "RECENT" -> "近期";
            case "STALE" -> "偏旧";
            default -> TextListCodec.normalizeText(freshnessLevel);
        };
    }

    private JsonNode parseJson(String json) {
        if (json == null || json.isBlank()) {
            return objectMapper.createObjectNode();
        }
        try {
            return objectMapper.readTree(json);
        } catch (Exception ex) {
            return objectMapper.createObjectNode();
        }
    }

    private String textValue(JsonNode node, String fieldName) {
        if (node == null || fieldName == null || fieldName.isBlank()) {
            return null;
        }
        return TextListCodec.normalizeText(node.path(fieldName).asText(null));
    }

    private List<String> readJsonStringList(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (JsonNode item : node) {
            String value = TextListCodec.normalizeText(item.asText(null));
            if (value != null) {
                values.add(value);
            }
        }
        return List.copyOf(new LinkedHashSet<>(values));
    }

    private List<String> readJsonStringList(String json) {
        return readJsonStringList(parseJson(json));
    }

    private void appendLabeledLine(List<String> lines, String label, String value) {
        String normalized = TextListCodec.normalizeText(value);
        if (normalized != null) {
            lines.add(label + "：" + normalized);
        }
    }

    private void appendLabeledList(List<String> lines, String label, List<String> values, int limit) {
        if (values == null || values.isEmpty()) {
            return;
        }
        List<String> normalized = values.stream()
                .map(TextListCodec::normalizeText)
                .filter(item -> item != null && !item.isBlank())
                .limit(Math.max(limit, 1))
                .toList();
        if (!normalized.isEmpty()) {
            lines.add(label + "：" + String.join("、", normalized));
        }
    }

    private String truncateContext(String text, int maxLength) {
        String normalized = TextListCodec.normalizeText(text);
        if (normalized == null || maxLength <= 0 || normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, maxLength) + "...";
    }

    private BigDecimal normalizeRating(BigDecimal rating) {
        return rating == null ? BigDecimal.ZERO.setScale(2, java.math.RoundingMode.HALF_UP) : rating.setScale(2, java.math.RoundingMode.HALF_UP);
    }

    private Long toIso(Instant instant) {
        return com.bishe.server.common.TimePayloads.toEpochMillis(instant);
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            String normalized = TextListCodec.normalizeText(value);
            if (normalized != null) {
                return normalized;
            }
        }
        return "";
    }

    private record StudentSignals(
            String targetPosition,
            List<String> skillTags,
            String selfIntro,
            List<String> portraitLabels,
            List<String> strengthTags,
            List<String> riskTags,
            String signalLevel,
            String freshnessLevel,
            String portraitHeadline,
            String portraitSummary,
            List<String> portraitNextActions,
            Long latestResumeRecordId,
            String latestResumeTargetRole,
            String latestResumeSummary,
            List<String> latestResumeSuggestions,
            String latestInterviewSessionId,
            String latestInterviewTargetRole,
            List<String> latestInterviewWeaknesses,
            List<String> latestInterviewSuggestions,
            List<String> directionKeywords,
            List<String> profileKeywords,
            List<String> resumeKeywords,
            List<String> interviewKeywords
    ) {

        private boolean hasPortraitTagSignals() {
            return !portraitLabels.isEmpty()
                    || !strengthTags.isEmpty()
                    || !riskTags.isEmpty();
        }

        private boolean hasPortraitSummary() {
            return portraitHeadline != null
                    || portraitSummary != null
                    || (portraitNextActions != null && !portraitNextActions.isEmpty());
        }

        private boolean hasPortraitInsight() {
            return hasPortraitTagSignals() || hasPortraitSummary();
        }

        private boolean hasResumeSignal() {
            return latestResumeRecordId != null
                    || latestResumeSummary != null
                    || (latestResumeSuggestions != null && !latestResumeSuggestions.isEmpty());
        }

        private boolean hasInterviewSignal() {
            return latestInterviewSessionId != null
                    || (latestInterviewWeaknesses != null && !latestInterviewWeaknesses.isEmpty())
                    || (latestInterviewSuggestions != null && !latestInterviewSuggestions.isEmpty());
        }
    }

    private record ScoredMentor(
            MentorRepository.MentorListRow mentor,
            int score,
            List<String> reasons,
            String risk,
            String explainText,
            double semanticSimilarity
    ) {
    }

    private record LatestResumeSignals(
            Long recordId,
            String targetRole,
            String summary,
            List<String> suggestions
    ) {

        private static LatestResumeSignals empty() {
            return new LatestResumeSignals(null, null, null, List.of());
        }
    }

    private record LatestInterviewSignals(
            String sessionId,
            String targetRole,
            List<String> weaknesses,
            List<String> suggestions
    ) {

        private static LatestInterviewSignals empty() {
            return new LatestInterviewSignals(null, null, List.of(), List.of());
        }
    }

    private record PortraitDetails(
            List<String> strengthTags,
            List<String> riskTags,
            String signalLevel,
            String freshnessLevel,
            String headline,
            String summary,
            List<String> nextActions
    ) {
    }

    private record StudentRecommendationSnapshot(
            long studentUserId,
            String contentText,
            String targetPosition,
            String skillTagsJson,
            String portraitTagsJson,
            Long latestResumeRecordId,
            String latestResumeTargetRole,
            String latestResumeSummary,
            String latestResumeSuggestionsJson,
            String latestInterviewSessionId,
            String latestInterviewTargetRole,
            String latestInterviewWeaknessesJson,
            String latestInterviewSuggestionsJson,
            String signalFlagsJson,
            String contentHash
    ) {
    }

    private record MentorRecommendationSnapshot(
            long mentorUserId,
            String contentText,
            String expertiseTagsJson,
            String serviceScenesJson,
            String contentHash,
            int qualityScore,
            int priceFen,
            boolean available
    ) {
    }

    private record MentorRecallCandidate(
            MentorRepository.MentorListRow mentor,
            String searchableText,
            double semanticSimilarity,
            double recallScore,
            int profileHitCount,
            int directionHitCount,
            int resumeHitCount,
            int interviewHitCount,
            int recallRank
    ) {
    }
}
