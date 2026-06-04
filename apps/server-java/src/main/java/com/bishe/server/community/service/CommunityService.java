package com.bishe.server.community.service;

import com.bishe.server.ai.gateway.AiGatewayService;
import com.bishe.server.auth.model.UserAccountStatus;
import com.bishe.server.auth.model.UserRole;
import com.bishe.server.auth.repository.UserRepository;
import com.bishe.server.common.exception.ApiException;
import com.bishe.server.common.util.TextListCodec;
import com.bishe.server.community.dto.CommunityCommentCreateRequest;
import com.bishe.server.community.dto.CommunityCommentCreateResponse;
import com.bishe.server.community.dto.CommunityLeaderboardResponse;
import com.bishe.server.community.dto.CommunityLikeResponse;
import com.bishe.server.community.dto.CommunityPostCreateRequest;
import com.bishe.server.community.dto.CommunityPostCreateResponse;
import com.bishe.server.community.dto.CommunityPostDetailResponse;
import com.bishe.server.community.dto.CommunityPostListResponse;
import com.bishe.server.community.dto.CommunityPostStatusUpdateRequest;
import com.bishe.server.community.dto.CommunityPostStatusUpdateResponse;
import com.bishe.server.community.repository.CommunityRepository;
import com.bishe.server.dashboard.AdminOperationsDashboardCacheService;
import com.bishe.server.featureflag.FeatureFlagService;
import com.bishe.server.growth.service.GrowthCenterCacheService;
import com.bishe.server.governance.ContentGovernanceService;
import com.bishe.server.governance.ModerationDecision;
import com.bishe.server.notification.model.NotificationCategory;
import com.bishe.server.notification.model.NotificationPriority;
import com.bishe.server.notification.service.NotificationService;
import com.bishe.server.notification.service.PlatformNotificationPublishService;
import com.bishe.server.mentor.service.MentorAvatarService;
import com.bishe.server.profile.service.StudentPortraitRefreshService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 社区服务：帖子、评论、点赞与 7 日贡献榜实现。
 */
@Service
public class CommunityService {

    private static final Logger log = LoggerFactory.getLogger(CommunityService.class);
    private static final String COMMUNITY_AI_BOT_EMAIL = "community-ai-bot@system.local";
    private static final String COMMUNITY_AI_BOT_DISPLAY_NAME = "AI 助手";
    private static final String COMMUNITY_AI_BOT_TIER = "FREE";
    private static final String DEFAULT_SCENARIO_CODE = "GENERAL_HELP";
    private static final Set<String> POST_RESOLVED_STATUSES = Set.of("OPEN", "RESOLVED", "CLOSED");

    private final CommunityRepository communityRepository;
    private final ContentGovernanceService contentGovernanceService;
    private final StudentPortraitRefreshService studentPortraitRefreshService;
    private final FeatureFlagService featureFlagService;
    private final AiGatewayService aiGatewayService;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final AdminOperationsDashboardCacheService adminOperationsDashboardCacheService;
    private final GrowthCenterCacheService growthCenterCacheService;
    private final CommunityLeaderboardCacheService communityLeaderboardCacheService;
    private final CommunityPostListCacheService communityPostListCacheService;
    private final CommunityPostDetailCacheService communityPostDetailCacheService;
    private final CommunityPostCommentListCacheService communityPostCommentListCacheService;
    private final MentorAvatarService mentorAvatarService;

    public CommunityService(
            CommunityRepository communityRepository,
            ContentGovernanceService contentGovernanceService,
            StudentPortraitRefreshService studentPortraitRefreshService,
            FeatureFlagService featureFlagService,
            AiGatewayService aiGatewayService,
            UserRepository userRepository,
            NotificationService notificationService,
            AdminOperationsDashboardCacheService adminOperationsDashboardCacheService,
            GrowthCenterCacheService growthCenterCacheService,
            CommunityLeaderboardCacheService communityLeaderboardCacheService,
            CommunityPostListCacheService communityPostListCacheService,
            CommunityPostDetailCacheService communityPostDetailCacheService,
            CommunityPostCommentListCacheService communityPostCommentListCacheService,
            MentorAvatarService mentorAvatarService
    ) {
        this.communityRepository = communityRepository;
        this.contentGovernanceService = contentGovernanceService;
        this.studentPortraitRefreshService = studentPortraitRefreshService;
        this.featureFlagService = featureFlagService;
        this.aiGatewayService = aiGatewayService;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
        this.adminOperationsDashboardCacheService = adminOperationsDashboardCacheService;
        this.growthCenterCacheService = growthCenterCacheService;
        this.communityLeaderboardCacheService = communityLeaderboardCacheService;
        this.communityPostListCacheService = communityPostListCacheService;
        this.communityPostDetailCacheService = communityPostDetailCacheService;
        this.communityPostCommentListCacheService = communityPostCommentListCacheService;
        this.mentorAvatarService = mentorAvatarService;
    }

    @Transactional(noRollbackFor = ApiException.class)
    public CommunityPostCreateResponse createPost(String traceId, long userId, CommunityPostCreateRequest request) {
        String title = request.title().trim();
        String scenarioCode = normalizeScenarioCode(request.scenarioCode());
        String content = request.content().trim();
        // 发帖先经过社区治理；REVIEW 状态用 202 返回待审信息，不回滚已写入的审核事件。
        ModerationDecision moderationDecision = contentGovernanceService.moderateCommunityPost(traceId, userId, title, content);
        if (moderationDecision.isBlock()) {
            throw new ApiException(
                    "MOD-1001",
                    "post blocked by moderation policy",
                    HttpStatus.BAD_REQUEST,
                    Map.of("moderation", moderationDecision.toCommunityPayload()),
                    traceId
            );
        }
        long postId = communityRepository.createPost(
                userId,
                title,
                scenarioCode,
                content,
                TextListCodec.join(request.tags()),
                "OPEN",
                moderationDecision.communityVisibilityStatus(),
                moderationDecision.riskLevel(),
                moderationDecision.eventId()
        );
        contentGovernanceService.bindModerationTarget(moderationDecision.eventId(), postId);
        refreshPortraitIfStudent(userId);
        evictGrowthDailyTasks(userId);

        // 待审帖子已经落库，但不进入公开列表，前端展示“等待审核”即可。
        CommunityPostCreateResponse pendingResponse = new CommunityPostCreateResponse(
                postId,
                moderationDecision.toCommunityPayload(),
                false,
                null
        );
        if (moderationDecision.isReview()) {
            throw new ApiException("MOD-1003", "post pending moderation review", HttpStatus.ACCEPTED, pendingResponse, traceId);
        }

        // AI 首评是增强能力，失败不影响用户发帖成功。
        AiFirstCommentCreation aiFirstCommentCreation = tryCreateAiFirstComment(traceId, postId, title, content);
        evictCommunityDerivedCaches(postId);
        return new CommunityPostCreateResponse(
                postId,
                moderationDecision.toCommunityPayload(),
                aiFirstCommentCreation.created(),
                aiFirstCommentCreation.commentId()
        );
    }

    public CommunityPostListResponse listPosts(long viewerUserId, int page, int size, String keyword, String tag, String scenarioCode) {
        int normalizedPage = Math.max(page, 1);
        int normalizedSize = Math.min(Math.max(size, 1), 50);
        String normalizedScenarioCode = normalizeScenarioFilter(scenarioCode);
        if (communityRepository.countOwnNonPublicPosts(viewerUserId, keyword, tag, normalizedScenarioCode) > 0) {
            // 本人存在待审/非公开帖子时绕开公共缓存，保证作者能看见自己的处理状态。
            return loadVisiblePostList(viewerUserId, normalizedPage, normalizedSize, keyword, tag, normalizedScenarioCode);
        }
        CommunityPostListResponse publicSnapshot = communityPostListCacheService.getPostList(
                keyword,
                tag,
                normalizedScenarioCode,
                normalizedPage,
                normalizedSize,
                () -> loadVisiblePostList(0L, normalizedPage, normalizedSize, keyword, tag, normalizedScenarioCode)
        );
        return mergeViewerState(viewerUserId, publicSnapshot);
    }

    public CommunityPostDetailResponse getPostDetail(long viewerUserId, String viewerRole, long postId) {
        if ("ADMIN".equalsIgnoreCase(viewerRole)) {
            // 管理员详情用于治理核查，允许读取非公开内容，但不参与公共缓存。
            CommunityRepository.PostSummaryRow detailRow = communityRepository.findAdminReadablePostDetail(postId)
                    .orElseThrow(() -> new ApiException("BIZ-1002", "post not found", HttpStatus.NOT_FOUND));
            boolean likedByMe = viewerUserId > 0 && communityRepository.hasLike(postId, viewerUserId);
            return toPostDetailResponse(
                    toPostDetailSnapshot(detailRow),
                    likedByMe,
                    communityRepository.findAdminReadableComments(postId).stream().map(this::toCommentItem).toList()
            );
        }

        CommunityPostDetailCacheService.CommunityPostDetailSnapshot publicDetailSnapshot = communityPostDetailCacheService.getPublicDetail(
                postId,
                () -> loadVisiblePostDetailSnapshot(0L, postId)
        );
        CommunityPostDetailCacheService.CommunityPostDetailSnapshot detailSnapshot = publicDetailSnapshot;
        if (detailSnapshot == null) {
            detailSnapshot = loadVisiblePostDetailSnapshot(viewerUserId, postId);
        }
        if (detailSnapshot == null) {
            throw new ApiException("BIZ-1002", "post not found", HttpStatus.NOT_FOUND);
        }
        boolean likedByMe = viewerUserId > 0 && communityRepository.hasLike(postId, viewerUserId);
        boolean usePublicCommentCache = publicDetailSnapshot != null;
        return toPostDetailResponse(detailSnapshot, likedByMe, loadVisibleComments(postId, usePublicCommentCache));
    }

    @Transactional(noRollbackFor = ApiException.class)
    public CommunityCommentCreateResponse createComment(String traceId, long userId, long postId, CommunityCommentCreateRequest request) {
        // 回复前先确认帖子可见且未关闭，避免给待审或关闭主题继续追加互动。
        CommunityRepository.PostContextRow postContext = requireReplyablePost(postId);
        String content = request.content().trim();
        ModerationDecision moderationDecision = contentGovernanceService.moderateCommunityComment(traceId, userId, content);
        if (moderationDecision.isBlock()) {
            throw new ApiException(
                    "MOD-1001",
                    "comment blocked by moderation policy",
                    HttpStatus.BAD_REQUEST,
                    Map.of("moderation", moderationDecision.toCommunityPayload()),
                    traceId
            );
        }
        long commentId = communityRepository.createComment(
                postId,
                userId,
                content,
                moderationDecision.communityVisibilityStatus(),
                moderationDecision.riskLevel(),
                moderationDecision.eventId()
        );
        contentGovernanceService.bindModerationTarget(moderationDecision.eventId(), commentId);
        refreshPortraitIfStudent(userId);
        evictGrowthDailyTasks(userId);
        CommunityCommentCreateResponse response = new CommunityCommentCreateResponse(commentId, moderationDecision.toCommunityPayload());
        if (moderationDecision.isReview()) {
            throw new ApiException("MOD-1003", "comment pending moderation review", HttpStatus.ACCEPTED, response, traceId);
        }
        publishReplyNotification(postContext, userId, commentId);
        evictCommunityDerivedCaches(postId);
        evictCommunityCommentListCache(postId);
        return response;
    }

    @Transactional
    public CommunityPostStatusUpdateResponse updatePostStatus(long userId, long postId, CommunityPostStatusUpdateRequest request) {
        CommunityRepository.PostContextRow postContext = communityRepository.findPostContext(postId)
                .orElseThrow(() -> new ApiException("BIZ-1002", "post not found", HttpStatus.NOT_FOUND));
        if (postContext.authorUserId() != userId) {
            throw new ApiException("AUTH-1004", "permission denied", HttpStatus.FORBIDDEN);
        }
        if (!"PASS".equalsIgnoreCase(postContext.moderationStatus())) {
            throw new ApiException("BIZ-1001", "post status update requires visible post", HttpStatus.BAD_REQUEST);
        }
        String resolvedStatus = normalizeResolvedStatus(request.resolvedStatus());
        communityRepository.updateResolvedStatus(postId, resolvedStatus);
        communityPostListCacheService.evictAllNow();
        communityPostListCacheService.evictAllAfterCommit();
        communityPostDetailCacheService.evictNow(postId);
        communityPostDetailCacheService.evictAfterCommit(postId);
        return new CommunityPostStatusUpdateResponse(postId, resolvedStatus, toIso(Instant.now()));
    }

    @Transactional
    public CommunityLikeResponse likePost(long userId, long postId) {
        ensureVisiblePost(postId);
        if (!communityRepository.hasLike(postId, userId)) {
            communityRepository.addLike(postId, userId);
        }
        refreshPostAuthorPortrait(postId);
        evictCommunityDerivedCaches(postId);
        return new CommunityLikeResponse(true, communityRepository.countLikes(postId));
    }

    @Transactional
    public CommunityLikeResponse unlikePost(long userId, long postId) {
        ensureVisiblePost(postId);
        communityRepository.removeLike(postId, userId);
        refreshPostAuthorPortrait(postId);
        evictCommunityDerivedCaches(postId);
        return new CommunityLikeResponse(false, communityRepository.countLikes(postId));
    }

    public CommunityLeaderboardResponse getLeaderboard(String window, int page, int size) {
        String normalizedWindow = (window == null || window.isBlank()) ? "7d" : window.trim().toLowerCase();
        if (!"7d".equals(normalizedWindow)) {
            throw new ApiException("BIZ-1001", "window invalid", HttpStatus.BAD_REQUEST);
        }
        int normalizedPage = Math.max(page, 1);
        int normalizedSize = Math.min(Math.max(size, 1), 50);
        Instant windowStart = Instant.now().minus(7, ChronoUnit.DAYS);
        return communityLeaderboardCacheService.getLeaderboard(
                normalizedWindow,
                normalizedPage,
                normalizedSize,
                () -> buildLeaderboardResponse(windowStart, normalizedPage, normalizedSize)
        );
    }

    private AiFirstCommentCreation tryCreateAiFirstComment(String traceId, long postId, String title, String content) {
        if (!featureFlagService.isCommunityAiFirstReplyEnabled()) {
            return AiFirstCommentCreation.notCreated();
        }
        try {
            // AI 首评使用系统 bot 身份入库，输出仍走 AI_OUTPUT 治理。
            long aiBotUserId = ensureCommunityAiBotUserId();
            AiGatewayService.CommunityPreAnswerGatewayResult gatewayResult = aiGatewayService.generateCommunityPreAnswer(
                    title,
                    content,
                    null,
                    COMMUNITY_AI_BOT_TIER
            );
            String aiDraftComment = normalizeGeneratedComment(gatewayResult.draftComment());
            if (!StringUtils.hasText(aiDraftComment)) {
                return AiFirstCommentCreation.notCreated();
            }
            ModerationDecision moderationDecision = contentGovernanceService.moderateAiOutput(traceId, aiBotUserId, "COMMENT", aiDraftComment);
            if (moderationDecision.isBlock()) {
                return AiFirstCommentCreation.notCreated();
            }
            String visibleComment = resolveVisibleAiComment(aiDraftComment, moderationDecision);
            if (!StringUtils.hasText(visibleComment)) {
                return AiFirstCommentCreation.notCreated();
            }
            long commentId = communityRepository.createAiComment(
                    postId,
                    aiBotUserId,
                    visibleComment,
                    "PASS",
                    moderationDecision.riskLevel(),
                    moderationDecision.eventId()
            );
            contentGovernanceService.bindModerationTarget(moderationDecision.eventId(), commentId);
            return new AiFirstCommentCreation(true, commentId);
        } catch (Exception exception) {
            log.warn("community ai first comment skipped, traceId={}, postId={}, reason={}", traceId, postId, exception.getMessage());
            return AiFirstCommentCreation.notCreated();
        }
    }

    private long ensureCommunityAiBotUserId() {
        return userRepository.findByEmail(COMMUNITY_AI_BOT_EMAIL)
                .map(user -> user.id())
                .orElseGet(() -> createCommunityAiBotUser());
    }

    private long createCommunityAiBotUser() {
        try {
            return userRepository.save(
                    COMMUNITY_AI_BOT_EMAIL,
                    "!community-ai-bot-disabled-login!",
                    UserRole.MENTOR,
                    COMMUNITY_AI_BOT_DISPLAY_NAME,
                    COMMUNITY_AI_BOT_DISPLAY_NAME,
                    COMMUNITY_AI_BOT_TIER,
                    UserAccountStatus.ACTIVE
            );
        } catch (DuplicateKeyException exception) {
            return userRepository.findByEmail(COMMUNITY_AI_BOT_EMAIL)
                    .map(user -> user.id())
                    .orElseThrow(() -> exception);
        }
    }

    private String normalizeGeneratedComment(String rawComment) {
        return rawComment == null ? null : rawComment.trim();
    }

    private String resolveVisibleAiComment(String originalComment, ModerationDecision moderationDecision) {
        if (moderationDecision == null) {
            return originalComment;
        }
        if ("MASK".equalsIgnoreCase(moderationDecision.action()) && StringUtils.hasText(moderationDecision.maskedText())) {
            return moderationDecision.maskedText().trim();
        }
        return originalComment;
    }

    private void refreshPortraitIfStudent(long userId) {
        studentPortraitRefreshService.refreshNowLowPriority(userId);
    }

    private void refreshPortraitIfStudentIfPresent(Long userId) {
        if (userId != null) {
            refreshPortraitIfStudent(userId.longValue());
        }
    }

    private void refreshPostAuthorPortrait(long postId) {
        communityRepository.findPostAuthorUserId(postId).ifPresent(this::refreshPortraitIfStudentIfPresent);
    }

    private void evictGrowthDailyTasks(long userId) {
        growthCenterCacheService.evictDailyTasksNow(userId);
        growthCenterCacheService.evictDailyTasksAfterCommit(userId);
    }

    private void ensureVisiblePost(long postId) {
        if (!communityRepository.existsVisiblePost(postId)) {
            throw new ApiException("BIZ-1002", "post not found", HttpStatus.NOT_FOUND);
        }
    }

    private CommunityRepository.PostContextRow requireReplyablePost(long postId) {
        CommunityRepository.PostContextRow postContext = communityRepository.findPostContext(postId)
                .orElseThrow(() -> new ApiException("BIZ-1002", "post not found", HttpStatus.NOT_FOUND));
        if (!"PASS".equalsIgnoreCase(postContext.moderationStatus())) {
            throw new ApiException("BIZ-1002", "post not found", HttpStatus.NOT_FOUND);
        }
        if ("CLOSED".equalsIgnoreCase(postContext.resolvedStatus())) {
            throw new ApiException("BIZ-1001", "post closed", HttpStatus.BAD_REQUEST);
        }
        return postContext;
    }

    private void publishReplyNotification(CommunityRepository.PostContextRow postContext, long actorUserId, long commentId) {
        if (postContext.authorUserId() == actorUserId) {
            return;
        }
        // 只通知原帖作者；自己回复自己的帖子不产生通知噪声。
        var actor = userRepository.findById(actorUserId).orElse(null);
        String actorDisplayName = actor == null || !StringUtils.hasText(actor.displayName()) ? "有新用户" : actor.displayName();
        String actorRole = actor == null || actor.role() == null ? null : actor.role().name();

        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("postId", postContext.postId());
        payload.put("commentId", commentId);
        payload.put("postTitle", postContext.title());
        payload.put("scenarioCode", postContext.scenarioCode());
        payload.put("actorDisplayName", actorDisplayName);
        payload.put("actorRole", actorRole);

        notificationService.publish(new PlatformNotificationPublishService.NotificationPublishCommand(
                "COMMUNITY_POST_REPLIED",
                NotificationCategory.COMMUNITY,
                "COMMUNITY_POST",
                String.valueOf(postContext.postId()),
                actorUserId,
                List.of(postContext.authorUserId()),
                NotificationPriority.NORMAL,
                actorDisplayName + " 回复了你的帖子",
                "《" + postContext.title() + "》收到了新回复，回到社区继续讨论吧。",
                "COMMUNITY_POST",
                String.valueOf(postContext.postId()),
                "VIEW_COMMUNITY_POST",
                payload,
                "community-reply:" + postContext.postId() + ":" + commentId,
                Instant.now()
        ));
    }

    private CommunityPostListResponse loadVisiblePostList(
            long viewerUserId,
            int page,
            int size,
            String keyword,
            String tag,
            String scenarioCode
    ) {
        long total = communityRepository.countVisiblePosts(viewerUserId, keyword, tag, scenarioCode);
        List<CommunityPostListResponse.PostItem> records = communityRepository.findVisiblePosts(
                viewerUserId,
                keyword,
                tag,
                scenarioCode,
                page,
                size
        ).stream().map(this::toPostItem).toList();
        return new CommunityPostListResponse(records, total, page, size);
    }

    private CommunityPostDetailCacheService.CommunityPostDetailSnapshot loadVisiblePostDetailSnapshot(long viewerUserId, long postId) {
        return communityRepository.findVisiblePostDetail(viewerUserId, postId)
                .map(this::toPostDetailSnapshot)
                .orElse(null);
    }

    private List<CommunityPostDetailResponse.CommentItem> loadVisibleComments(long postId, boolean usePublicCommentCache) {
        if (!usePublicCommentCache) {
            return loadVisibleCommentsFromDb(postId);
        }
        return communityPostCommentListCacheService.getVisibleComments(
                        postId,
                        () -> loadVisibleCommentsFromDb(postId).stream().map(this::toCommentSnapshot).toList()
                ).stream()
                .map(this::toCommentItem)
                .toList();
    }

    private List<CommunityPostDetailResponse.CommentItem> loadVisibleCommentsFromDb(long postId) {
        return communityRepository.findVisibleComments(postId)
                .stream()
                .map(this::toCommentItem)
                .toList();
    }

    private CommunityPostListResponse mergeViewerState(long viewerUserId, CommunityPostListResponse publicSnapshot) {
        if (publicSnapshot == null || viewerUserId <= 0 || publicSnapshot.records() == null || publicSnapshot.records().isEmpty()) {
            return publicSnapshot;
        }
        // liked/authored/participated 这类 viewer 态只在返回前合成，公共缓存保持可复用。
        List<Long> postIds = publicSnapshot.records().stream()
                .map(CommunityPostListResponse.PostItem::postId)
                .filter(java.util.Objects::nonNull)
                .toList();
        Set<Long> likedPostIds = communityRepository.findLikedPostIds(viewerUserId, postIds);
        Set<Long> participatedPostIds = communityRepository.findParticipatedPostIds(viewerUserId, postIds);
        List<CommunityPostListResponse.PostItem> records = publicSnapshot.records().stream()
                .map(item -> {
                    boolean authoredByMe = item.authorUserId() != null && item.authorUserId() == viewerUserId;
                    boolean likedByMe = item.postId() != null && likedPostIds.contains(item.postId());
                    boolean participatedByMe = authoredByMe || (item.postId() != null && participatedPostIds.contains(item.postId()));
                    return new CommunityPostListResponse.PostItem(
                            item.postId(),
                            item.authorUserId(),
                            item.authorDisplayName(),
                            item.authorRealName(),
                            item.authorShowRealName(),
                            item.authorRole(),
                            item.authorAvatarUrl(),
                            item.title(),
                            item.scenarioCode(),
                            item.resolvedStatus(),
                            item.content(),
                            item.tags(),
                            item.commentCount(),
                            item.likeCount(),
                            likedByMe,
                            item.moderationStatus(),
                            item.riskLevel(),
                            item.hasMentorReply(),
                            authoredByMe,
                            participatedByMe,
                            item.createdAt(),
                            item.updatedAt()
                    );
                })
                .toList();
        return new CommunityPostListResponse(records, publicSnapshot.total(), publicSnapshot.page(), publicSnapshot.size());
    }

    private CommunityPostListResponse.PostItem toPostItem(CommunityRepository.PostSummaryRow row) {
        return new CommunityPostListResponse.PostItem(
                row.postId(),
                row.authorUserId(),
                row.authorDisplayName(),
                row.authorRealName(),
                row.authorShowRealName(),
                row.authorRole(),
                resolveMentorAvatarUrl(
                        row.hasMentorProfile(),
                        row.authorUserId(),
                        row.authorRole(),
                        row.authorAvatarUrl(),
                        row.authorDisplayName(),
                        row.authorAvatarObjectKey(),
                        row.authorAvatarUpdatedAt()
                ),
                row.title(),
                row.scenarioCode(),
                row.resolvedStatus(),
                row.content(),
                TextListCodec.split(row.tags()),
                row.commentCount(),
                row.likeCount(),
                row.likedByMe(),
                row.moderationStatus(),
                row.riskLevel(),
                row.hasMentorReply(),
                row.authoredByMe(),
                row.participatedByMe(),
                toIso(row.createdAt()),
                toIso(row.updatedAt())
        );
    }

    private CommunityPostDetailCacheService.CommunityPostDetailSnapshot toPostDetailSnapshot(CommunityRepository.PostSummaryRow row) {
        return new CommunityPostDetailCacheService.CommunityPostDetailSnapshot(
                row.postId(),
                row.authorUserId(),
                row.authorDisplayName(),
                row.authorRealName(),
                row.authorShowRealName(),
                row.authorRole(),
                resolveMentorAvatarUrl(
                        row.hasMentorProfile(),
                        row.authorUserId(),
                        row.authorRole(),
                        row.authorAvatarUrl(),
                        row.authorDisplayName(),
                        row.authorAvatarObjectKey(),
                        row.authorAvatarUpdatedAt()
                ),
                row.title(),
                row.scenarioCode(),
                row.resolvedStatus(),
                row.moderationStatus(),
                row.riskLevel(),
                row.content(),
                TextListCodec.split(row.tags()),
                row.commentCount(),
                row.likeCount(),
                row.createdAt(),
                row.updatedAt()
        );
    }

    private CommunityPostDetailResponse toPostDetailResponse(
            CommunityPostDetailCacheService.CommunityPostDetailSnapshot snapshot,
            boolean likedByMe,
            List<CommunityPostDetailResponse.CommentItem> comments
    ) {
        return new CommunityPostDetailResponse(
                snapshot.postId(),
                snapshot.authorUserId(),
                snapshot.authorDisplayName(),
                snapshot.authorRealName(),
                snapshot.authorShowRealName(),
                snapshot.authorRole(),
                snapshot.authorAvatarUrl(),
                snapshot.title(),
                snapshot.scenarioCode(),
                snapshot.resolvedStatus(),
                snapshot.moderationStatus(),
                snapshot.riskLevel(),
                snapshot.content(),
                snapshot.tags(),
                snapshot.commentCount(),
                snapshot.likeCount(),
                likedByMe,
                comments,
                toIso(snapshot.createdAt()),
                toIso(snapshot.updatedAt())
        );
    }

    private CommunityPostDetailResponse.CommentItem toCommentItem(CommunityRepository.CommentRow row) {
        return new CommunityPostDetailResponse.CommentItem(
                row.commentId(),
                row.userId(),
                row.displayName(),
                row.realName(),
                row.showRealName(),
                row.role(),
                resolveMentorAvatarUrl(
                        row.hasMentorProfile(),
                        row.userId(),
                        row.role(),
                        row.avatarUrl(),
                        row.displayName(),
                        row.avatarObjectKey(),
                        row.avatarUpdatedAt()
                ),
                row.content(),
                row.ai(),
                toIso(row.createdAt())
        );
    }

    private CommunityPostCommentListCacheService.CommentSnapshot toCommentSnapshot(CommunityPostDetailResponse.CommentItem item) {
        return new CommunityPostCommentListCacheService.CommentSnapshot(
                item.commentId(),
                item.userId(),
                item.displayName(),
                item.realName(),
                item.showRealName(),
                item.role(),
                item.avatarUrl(),
                item.content(),
                item.ai(),
                item.createdAt() == null ? null : Instant.ofEpochMilli(item.createdAt())
        );
    }

    private CommunityPostDetailResponse.CommentItem toCommentItem(CommunityPostCommentListCacheService.CommentSnapshot snapshot) {
        return new CommunityPostDetailResponse.CommentItem(
                snapshot.commentId(),
                snapshot.userId(),
                snapshot.displayName(),
                snapshot.realName(),
                snapshot.showRealName(),
                snapshot.role(),
                snapshot.avatarUrl(),
                snapshot.content(),
                snapshot.ai(),
                toIso(snapshot.createdAt())
        );
    }

    private String resolveMentorAvatarUrl(
            boolean hasMentorProfile,
            long authorUserId,
            String authorRole,
            String rawAvatarUrl,
            String displayName,
            String avatarObjectKey,
            Instant avatarUpdatedAt
    ) {
        if (!hasMentorProfile || !"MENTOR".equalsIgnoreCase(authorRole)) {
            return null;
        }
        return mentorAvatarService.resolveAvatarUrl(
                authorUserId,
                rawAvatarUrl,
                displayName,
                avatarObjectKey,
                avatarUpdatedAt
        );
    }

    private Long toIso(Instant instant) {
        return com.bishe.server.common.TimePayloads.toEpochMillis(instant);
    }

    private String normalizeScenarioCode(String rawScenarioCode) {
        if (!StringUtils.hasText(rawScenarioCode)) {
            return DEFAULT_SCENARIO_CODE;
        }
        String normalized = rawScenarioCode.trim()
                .toUpperCase()
                .replaceAll("[^A-Z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
        if (!StringUtils.hasText(normalized)) {
            return DEFAULT_SCENARIO_CODE;
        }
        return normalized.length() > 60 ? normalized.substring(0, 60) : normalized;
    }

    private String normalizeScenarioFilter(String rawScenarioCode) {
        if (!StringUtils.hasText(rawScenarioCode)) {
            return null;
        }
        return normalizeScenarioCode(rawScenarioCode);
    }

    private String normalizeResolvedStatus(String rawResolvedStatus) {
        if (!StringUtils.hasText(rawResolvedStatus)) {
            throw new ApiException("BIZ-1001", "resolvedStatus invalid", HttpStatus.BAD_REQUEST);
        }
        String normalized = rawResolvedStatus.trim().toUpperCase();
        if (!POST_RESOLVED_STATUSES.contains(normalized)) {
            throw new ApiException("BIZ-1001", "resolvedStatus invalid", HttpStatus.BAD_REQUEST);
        }
        return normalized;
    }

    private record AiFirstCommentCreation(boolean created, Long commentId) {
        private static AiFirstCommentCreation notCreated() {
            return new AiFirstCommentCreation(false, null);
        }
    }

    private CommunityLeaderboardResponse buildLeaderboardResponse(Instant windowStart, int page, int size) {
        long total = communityRepository.countLeaderboardRows(windowStart);
        int rankStart = (page - 1) * size;
        List<CommunityRepository.LeaderboardRow> rows = communityRepository.findLeaderboardRows(windowStart, page, size);
        List<CommunityLeaderboardResponse.LeaderboardItem> records = new java.util.ArrayList<>();
        for (int index = 0; index < rows.size(); index++) {
            CommunityRepository.LeaderboardRow row = rows.get(index);
            records.add(new CommunityLeaderboardResponse.LeaderboardItem(
                    rankStart + index + 1,
                    row.studentUserId(),
                    row.displayName(),
                    row.score(),
                    row.postCount(),
                    row.commentCount(),
                    row.likeReceivedCount(),
                    toIso(row.latestActivityAt())
            ));
        }
        return new CommunityLeaderboardResponse("7d", "post*5 + comment*2 + like*1", records, total, page, size);
    }

    private void evictCommunityDerivedCaches(long postId) {
        adminOperationsDashboardCacheService.evictAllNow();
        adminOperationsDashboardCacheService.evictAllAfterCommit();
        communityLeaderboardCacheService.evictAllNow();
        communityLeaderboardCacheService.evictAllAfterCommit();
        communityPostListCacheService.evictAllNow();
        communityPostListCacheService.evictAllAfterCommit();
        communityPostDetailCacheService.evictNow(postId);
        communityPostDetailCacheService.evictAfterCommit(postId);
    }

    private void evictCommunityCommentListCache(long postId) {
        communityPostCommentListCacheService.evictNow(postId);
        communityPostCommentListCacheService.evictAfterCommit(postId);
    }
}
