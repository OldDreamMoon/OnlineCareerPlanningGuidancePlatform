package com.bishe.server.governance;

import com.bishe.server.adminconsole.AdminConsoleSnapshotCacheService;
import com.bishe.server.community.service.CommunityPostCommentListCacheService;
import com.bishe.server.community.service.CommunityLeaderboardCacheService;
import com.bishe.server.community.service.CommunityPostDetailCacheService;
import com.bishe.server.community.service.CommunityPostListCacheService;
import com.bishe.server.dashboard.AdminOperationsDashboardCacheService;
import com.bishe.server.dashboard.AdminWorkbenchCacheService;
import com.bishe.server.common.exception.ApiException;
import com.bishe.server.notification.model.NotificationCategory;
import com.bishe.server.notification.model.NotificationPriority;
import com.bishe.server.notification.service.NotificationService;
import com.bishe.server.notification.service.PlatformNotificationPublishService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 内容治理服务：统一审查规则、举报闭环、待审队列与后台配置。
 */
@Service
public class ContentGovernanceService {

    private static final Set<String> TARGET_TYPES = Set.of("POST", "COMMENT", "USER");
    private static final Set<String> REPORT_DECISIONS = Set.of("ACCEPTED", "REJECTED", "CLOSED");
    private static final Set<String> REPORT_ACTIONS = Set.of("TAKE_DOWN", "RESTORE", "NO_ACTION");
    private static final Set<String> REVIEW_DECISIONS = Set.of("APPROVE", "REJECT");
    private static final Set<String> SOURCE_SCOPES = Set.of("ALL", "AI_INPUT", "AI_OUTPUT", "COMMUNITY_POST", "COMMUNITY_COMMENT");
    private static final Set<String> RISK_LEVELS = Set.of("LOW", "MEDIUM", "HIGH", "CRITICAL");
    private static final Set<String> MODERATION_ACTIONS = Set.of("PASS", "MASK", "BLOCK", "REVIEW");
    private static final Set<String> TERM_TYPES = Set.of("POLITICS", "PORNOGRAPHY", "TERROR", "VIOLENCE", "FRAUD", "ABUSE", "ADVERTISEMENT", "ILLEGAL", "OTHER");
    private static final Set<String> IMPORT_FILE_FORMATS = Set.of("CSV", "PLAIN_TEXT");
    private static final Set<String> SENSITIVE_TERM_STATUS_FILTERS = Set.of("enabled", "disabled", "whitelist");
    private static final int REPORT_RATE_LIMIT_COUNT = 5;
    private static final Duration REPORT_RATE_LIMIT_WINDOW = Duration.ofMinutes(10);

    private final ContentGovernanceRepository governanceRepository;
    private final ModerationPolicyRepository moderationPolicyRepository;
    private final SensitiveTermRepository sensitiveTermRepository;
    private final ContentGovernanceConfigCacheService contentGovernanceConfigCacheService;
    private final AdminConsoleSnapshotCacheService adminConsoleSnapshotCacheService;
    private final AdminWorkbenchCacheService adminWorkbenchCacheService;
    private final AdminOperationsDashboardCacheService adminOperationsDashboardCacheService;
    private final CommunityLeaderboardCacheService communityLeaderboardCacheService;
    private final CommunityPostListCacheService communityPostListCacheService;
    private final CommunityPostDetailCacheService communityPostDetailCacheService;
    private final CommunityPostCommentListCacheService communityPostCommentListCacheService;
    private final ContentReportRateLimitService contentReportRateLimitService;
    private final ContentReportDuplicateGuardService contentReportDuplicateGuardService;
    private final ObjectMapper objectMapper;
    private final NotificationService notificationService;

    public ContentGovernanceService(
            ContentGovernanceRepository governanceRepository,
            ModerationPolicyRepository moderationPolicyRepository,
            SensitiveTermRepository sensitiveTermRepository,
            ContentGovernanceConfigCacheService contentGovernanceConfigCacheService,
            AdminConsoleSnapshotCacheService adminConsoleSnapshotCacheService,
            AdminWorkbenchCacheService adminWorkbenchCacheService,
            AdminOperationsDashboardCacheService adminOperationsDashboardCacheService,
            CommunityLeaderboardCacheService communityLeaderboardCacheService,
            CommunityPostListCacheService communityPostListCacheService,
            CommunityPostDetailCacheService communityPostDetailCacheService,
            CommunityPostCommentListCacheService communityPostCommentListCacheService,
            ContentReportRateLimitService contentReportRateLimitService,
            ContentReportDuplicateGuardService contentReportDuplicateGuardService,
            ObjectMapper objectMapper,
            NotificationService notificationService
    ) {
        this.governanceRepository = governanceRepository;
        this.moderationPolicyRepository = moderationPolicyRepository;
        this.sensitiveTermRepository = sensitiveTermRepository;
        this.contentGovernanceConfigCacheService = contentGovernanceConfigCacheService;
        this.adminConsoleSnapshotCacheService = adminConsoleSnapshotCacheService;
        this.adminWorkbenchCacheService = adminWorkbenchCacheService;
        this.adminOperationsDashboardCacheService = adminOperationsDashboardCacheService;
        this.communityLeaderboardCacheService = communityLeaderboardCacheService;
        this.communityPostListCacheService = communityPostListCacheService;
        this.communityPostDetailCacheService = communityPostDetailCacheService;
        this.communityPostCommentListCacheService = communityPostCommentListCacheService;
        this.contentReportRateLimitService = contentReportRateLimitService;
        this.contentReportDuplicateGuardService = contentReportDuplicateGuardService;
        this.objectMapper = objectMapper;
        this.notificationService = notificationService;
    }

    public ModerationDecision moderateCommunityPost(String traceId, long operatorUserId, String title, String content) {
        return moderateText(traceId, operatorUserId, "COMMUNITY_POST", "POST", title + "\n" + content, true, true);
    }

    public ModerationDecision moderateCommunityComment(String traceId, long operatorUserId, String content) {
        return moderateText(traceId, operatorUserId, "COMMUNITY_COMMENT", "COMMENT", content, true, true);
    }

    public ModerationDecision moderateAiInput(String traceId, long operatorUserId, String content) {
        return moderateText(traceId, operatorUserId, "AI_INPUT", "AI_REQUEST", content, false, true);
    }

    public ModerationDecision moderateAiOutput(String traceId, long operatorUserId, String targetType, String content) {
        String normalizedTargetType = StringUtils.hasText(targetType) ? targetType.trim().toUpperCase(Locale.ROOT) : "AI_CONTENT";
        return moderateText(traceId, operatorUserId, "AI_OUTPUT", normalizedTargetType, content, false, true);
    }

    public ModerationDecision previewAiOutput(String traceId, long operatorUserId, String targetType, String content) {
        String normalizedTargetType = StringUtils.hasText(targetType) ? targetType.trim().toUpperCase(Locale.ROOT) : "AI_CONTENT";
        return moderateText(traceId, operatorUserId, "AI_OUTPUT", normalizedTargetType, content, false, false);
    }

    public String maskTextForSource(String sourceType, String originalText) {
        if (!StringUtils.hasText(originalText)) {
            return originalText;
        }
        List<SensitiveTermRow> matchedTerms = findMatchedTermsForSource(sourceType, originalText);
        if (matchedTerms.isEmpty()) {
            return originalText;
        }
        return applyMask(originalText, matchedTerms);
    }

    public void bindModerationTarget(long eventId, long targetId) {
        governanceRepository.updateModerationEventTarget(eventId, String.valueOf(targetId));
    }

    @Transactional
    public CommunityReportCreateResponse submitReport(String traceId, long reporterUserId, CommunityReportCreateRequest request) {
        String targetType = normalizeEnum("targetType", request.targetType(), TARGET_TYPES);
        String targetId = normalizeRequired("targetId", request.targetId());
        String reasonCode = normalizeRequired("reasonCode", request.reasonCode()).toUpperCase(Locale.ROOT);
        String detail = normalizeOptional(request.detail());

        // 举报先校验目标存在，再进入限流和重复举报保护。
        if (!governanceRepository.existsTarget(targetType, targetId)) {
            throw new ApiException("BIZ-1002", "report target not found", HttpStatus.NOT_FOUND, null, traceId);
        }

        Instant now = Instant.now();
        Instant windowStart = now.minus(REPORT_RATE_LIMIT_WINDOW);
        boolean allowed = contentReportRateLimitService.tryAcquire(
                reporterUserId,
                now,
                REPORT_RATE_LIMIT_COUNT,
                REPORT_RATE_LIMIT_WINDOW,
                () -> governanceRepository.findRecentReportCreatedAtByReporter(reporterUserId, windowStart)
        );
        if (!allowed) {
            throw new ApiException("MOD-1004", "report rate limited", HttpStatus.BAD_REQUEST, null, traceId);
        }

        ContentGovernanceRepository.ExistingReportRow existing = contentReportDuplicateGuardService.findDuplicate(
                reporterUserId,
                targetType,
                targetId,
                reasonCode,
                () -> governanceRepository.findExistingReport(reporterUserId, targetType, targetId, reasonCode)
        );
        if (existing != null) {
            return new CommunityReportCreateResponse(existing.reportId(), existing.status());
        }

        long reportId;
        try {
            reportId = governanceRepository.insertReport(reporterUserId, targetType, targetId, reasonCode, detail);
        } catch (DuplicateKeyException ex) {
            ContentGovernanceRepository.ExistingReportRow duplicate = contentReportDuplicateGuardService.findDuplicate(
                    reporterUserId,
                    targetType,
                    targetId,
                    reasonCode,
                    () -> governanceRepository.findExistingReport(reporterUserId, targetType, targetId, reasonCode)
            );
            if (duplicate == null) {
                throw ex;
            }
            return new CommunityReportCreateResponse(duplicate.reportId(), duplicate.status());
        }
        contentReportDuplicateGuardService.rememberAfterCommit(
                reporterUserId,
                targetType,
                targetId,
                reasonCode,
                new ContentGovernanceRepository.ExistingReportRow(reportId, "PENDING")
        );

        // 举报可能触发自动隐藏，也会影响后台待办和社区公开缓存。
        tryAutoHideTarget(traceId, targetType, targetId);
        Long affectedPostId = resolveCommunityPostId(
                targetType,
                targetId,
                governanceRepository.findCommunityNotificationTarget(targetType, targetId).orElse(null)
        );
        evictAdminWorkbenchCache();
        evictCommunityLeaderboardCache();
        evictCommunityPostListCache();
        evictCommunityPostDetailCache(affectedPostId);
        evictCommunityPostCommentListCache(affectedPostId);
        return new CommunityReportCreateResponse(reportId, "PENDING");
    }

    public CommunityReportListResponse getMyReports(long reporterUserId, int page, int size, String status) {
        int normalizedPage = Math.max(page, 1);
        int normalizedSize = Math.min(Math.max(size, 1), 50);
        String normalizedStatus = normalizeOptionalEnum(status, Set.of("PENDING", "ACCEPTED", "REJECTED", "CLOSED"));
        List<CommunityReportListResponse.ReportItem> records = governanceRepository
                .findMyReports(reporterUserId, normalizedStatus, normalizedPage, normalizedSize)
                .stream()
                .map(row -> new CommunityReportListResponse.ReportItem(
                        row.reportId(),
                        row.targetType(),
                        row.targetId(),
                        row.contentPostId(),
                        row.contentTitle(),
                        row.contentBody(),
                        row.reasonCode(),
                        normalizeOptional(row.reportDetail()),
                        row.status(),
                        row.latestAction(),
                        toIso(row.createdAt()),
                        toIso(row.updatedAt())
                ))
                .toList();
        long total = governanceRepository.countMyReports(reporterUserId, normalizedStatus);
        return new CommunityReportListResponse(records, total, normalizedPage, normalizedSize);
    }

    public AdminContentReportListResponse getAdminReports(int page, int size, String status, String targetType) {
        int normalizedPage = Math.max(page, 1);
        int normalizedSize = Math.min(Math.max(size, 1), 50);
        String normalizedStatus = normalizeOptionalEnum(status, Set.of("PENDING", "ACCEPTED", "REJECTED", "CLOSED"));
        String normalizedTargetType = normalizeOptionalEnum(targetType, TARGET_TYPES);
        List<AdminContentReportListResponse.ReportItem> records = governanceRepository
                .findAdminReports(normalizedStatus, normalizedTargetType, normalizedPage, normalizedSize)
                .stream()
                .map(row -> new AdminContentReportListResponse.ReportItem(
                        row.reportId(),
                        row.targetType(),
                        row.targetId(),
                        row.contentPostId(),
                        row.contentTitle(),
                        row.contentBody(),
                        row.reasonCode(),
                        row.status(),
                        row.latestAction(),
                        row.reportCount(),
                        toIso(row.createdAt()),
                        toIso(row.updatedAt())
                ))
                .toList();
        long total = governanceRepository.countAdminReports(normalizedStatus, normalizedTargetType);
        return new AdminContentReportListResponse(records, total, normalizedPage, normalizedSize);
    }


    public AdminContentReportDetailResponse getReportDetail(String traceId, long reportId) {
        ContentGovernanceRepository.AdminReportDetailRow row = governanceRepository.findAdminReportDetail(reportId)
                .orElseThrow(() -> new ApiException("BIZ-1002", "report not found", HttpStatus.NOT_FOUND, null, traceId));
        ContentGovernanceRepository.TargetStateRow targetState = governanceRepository.findTargetState(row.targetType(), row.targetId()).orElse(null);
        return new AdminContentReportDetailResponse(
                row.reportId(),
                row.reporterUserId(),
                StringUtils.hasText(row.reporterDisplayName()) ? row.reporterDisplayName() : "用户#" + row.reporterUserId(),
                row.targetType(),
                row.targetId(),
                row.contentPostId(),
                row.contentTitle(),
                row.contentBody(),
                row.reasonCode(),
                normalizeOptional(row.reportDetail()),
                row.status(),
                row.latestAction(),
                row.reportCount(),
                targetState == null ? null : targetState.status(),
                targetState == null ? null : targetState.riskLevel(),
                toIso(row.createdAt()),
                toIso(row.updatedAt())
        );
    }

    public AdminReportActionHistoryResponse getReportActions(String traceId, long reportId) {
        governanceRepository.findReportForDecision(reportId)
                .orElseThrow(() -> new ApiException("BIZ-1002", "report not found", HttpStatus.NOT_FOUND, null, traceId));
        List<AdminReportActionHistoryResponse.ActionItem> records = governanceRepository.listReportActions(reportId)
                .stream()
                .map(row -> new AdminReportActionHistoryResponse.ActionItem(
                        row.actionId(),
                        row.operatorUserId(),
                        StringUtils.hasText(row.operatorDisplayName()) ? row.operatorDisplayName() : "管理员#" + row.operatorUserId(),
                        row.decision(),
                        row.action(),
                        normalizeOptional(row.comment()),
                        toIso(row.createdAt())
                ))
                .toList();
        return new AdminReportActionHistoryResponse(records);
    }

    public AdminAuditLogListResponse getAuditLogs(int page, int size, String targetType, String targetId, String actionType, String traceId) {
        int normalizedPage = Math.max(page, 1);
        int normalizedSize = Math.min(Math.max(size, 1), 50);
        String normalizedTargetType = normalizeOptionalEnum(targetType, TARGET_TYPES);
        String normalizedTargetId = normalizeOptional(targetId);
        String normalizedActionType = actionType == null || actionType.isBlank() ? null : actionType.trim().toUpperCase(Locale.ROOT);
        String normalizedTraceId = normalizeOptional(traceId);
        List<AdminAuditLogListResponse.AuditLogItem> records = governanceRepository
                .findAuditLogs(normalizedTargetType, normalizedTargetId, normalizedActionType, normalizedTraceId, normalizedPage, normalizedSize)
                .stream()
                .map(row -> new AdminAuditLogListResponse.AuditLogItem(
                        row.auditLogId(),
                        row.traceId(),
                        row.operatorUserId(),
                        resolveAuditOperatorDisplayName(row.operatorUserId(), row.operatorDisplayName()),
                        row.actionType(),
                        row.targetType(),
                        row.targetId(),
                        row.detailJson(),
                        toIso(row.createdAt())
                ))
                .toList();
        long total = governanceRepository.countAuditLogs(normalizedTargetType, normalizedTargetId, normalizedActionType, normalizedTraceId);
        return new AdminAuditLogListResponse(records, total, normalizedPage, normalizedSize);
    }

    @Transactional
    public void decideReport(String traceId, long operatorUserId, long reportId, AdminReportDecisionRequest request) {
        String decision = normalizeEnum("decision", request.decision(), REPORT_DECISIONS);
        String action = normalizeEnum("action", request.action(), REPORT_ACTIONS);
        String comment = normalizeOptional(request.comment());

        // 举报处置同时更新举报状态、动作历史、目标状态和审计日志。
        ContentGovernanceRepository.AdminReportDetailRow reportDetail = governanceRepository.findAdminReportDetail(reportId)
                .orElseThrow(() -> new ApiException("BIZ-1002", "report not found", HttpStatus.NOT_FOUND, null, traceId));
        ContentGovernanceRepository.ReportDecisionTargetRow report = governanceRepository.findReportForDecision(reportId)
                .orElseThrow(() -> new ApiException("BIZ-1002", "report not found", HttpStatus.NOT_FOUND, null, traceId));
        ContentGovernanceRepository.CommunityNotificationTargetRow communityTarget = governanceRepository
                .findCommunityNotificationTarget(report.targetType(), report.targetId())
                .orElse(null);

        governanceRepository.updateReportStatus(reportId, decision, action, true);
        governanceRepository.insertReportAction(reportId, operatorUserId, decision, action, comment);
        contentReportDuplicateGuardService.evictNow(
                reportDetail.reporterUserId(),
                report.targetType(),
                report.targetId(),
                reportDetail.reasonCode()
        );
        contentReportDuplicateGuardService.evictAfterCommit(
                reportDetail.reporterUserId(),
                report.targetType(),
                report.targetId(),
                reportDetail.reasonCode()
        );

        if (!"NO_ACTION".equals(action)) {
            applyTargetAction(traceId, operatorUserId, report.targetType(), report.targetId(), action);
        }

        governanceRepository.insertAuditLog(
                traceId,
                operatorUserId,
                "REPORT_DECISION",
                report.targetType(),
                report.targetId(),
                toJson(Map.of(
                        "reportId", reportId,
                        "decision", decision,
                        "action", action,
                        "comment", comment == null ? "" : comment
                ))
        );

        publishReportDecisionNotification(reportId, decision, action, reportDetail);
        if (!"NO_ACTION".equals(action) && communityTarget != null) {
            // 真正下架/恢复内容时，除举报人外还要通知内容作者。
            publishCommunityModerationNotificationFromReport(decision, action, report.targetType(), report.targetId(), communityTarget);
        }
        Long affectedPostId = resolveCommunityPostId(report.targetType(), report.targetId(), communityTarget);
        evictAdminWorkbenchCache();
        evictCommunityLeaderboardCache();
        evictCommunityPostListCache();
        evictCommunityPostDetailCache(affectedPostId);
        evictCommunityPostCommentListCache(affectedPostId);
    }

    public AdminReviewQueueResponse getReviewQueue(int page, int size, String sourceType) {
        int normalizedPage = Math.max(page, 1);
        int normalizedSize = Math.min(Math.max(size, 1), 50);
        String normalizedSourceType = normalizeOptionalEnum(sourceType, Set.of("COMMUNITY_POST", "COMMUNITY_COMMENT", "AI_OUTPUT"));
        List<AdminReviewQueueResponse.ReviewItem> records = governanceRepository.findReviewQueue(normalizedSourceType, normalizedPage, normalizedSize)
                .stream()
                .map(row -> new AdminReviewQueueResponse.ReviewItem(
                        row.eventId(),
                        row.sourceType(),
                        row.targetType(),
                        row.targetId(),
                        row.riskLevel(),
                        row.reasonCode(),
                        row.preview(),
                        toIso(row.createdAt())
                ))
                .toList();
        long total = governanceRepository.countReviewQueue(normalizedSourceType);
        return new AdminReviewQueueResponse(records, total, normalizedPage, normalizedSize);
    }

    public AdminReviewQueueDetailResponse getReviewQueueItem(String traceId, long itemId) {
        ContentGovernanceRepository.ReviewQueueDetailRow item = governanceRepository.findReviewQueueDetail(itemId)
                .orElseThrow(() -> new ApiException("BIZ-1002", "review item not found", HttpStatus.NOT_FOUND, null, traceId));
        return new AdminReviewQueueDetailResponse(
                item.eventId(),
                item.sourceType(),
                item.targetType(),
                item.targetId(),
                item.riskLevel(),
                item.reasonCode(),
                item.preview(),
                item.contentTitle(),
                item.contentBody(),
                item.postId(),
                item.postTitle(),
                item.postBody(),
                item.authorUserId(),
                item.authorDisplayName(),
                item.authorRole(),
                toIso(item.createdAt())
        );
    }

    @Transactional
    public void decideReviewQueueItem(String traceId, long operatorUserId, long itemId, AdminReviewDecisionRequest request) {
        String decision = normalizeEnum("decision", request.decision(), REVIEW_DECISIONS);
        String comment = normalizeOptional(request.comment());
        ContentGovernanceRepository.ReviewQueueDetailRow item = governanceRepository.findReviewQueueDetail(itemId)
                .orElseThrow(() -> new ApiException("BIZ-1002", "review item not found", HttpStatus.NOT_FOUND, null, traceId));

        // 待审队列只开放通过/拒绝，落库时转换成 PASS/BLOCK 的目标审核态。
        String action = "APPROVE".equals(decision) ? "PASS" : "BLOCK";
        String riskLevel = "APPROVE".equals(decision) ? "LOW" : "HIGH";
        String reasonCode = "APPROVE".equals(decision) ? "MANUAL_APPROVE" : "MANUAL_REJECT";
        long eventId = governanceRepository.insertModerationEvent(
                traceId,
                item.sourceType(),
                item.targetType(),
                item.targetId(),
                riskLevel,
                action,
                reasonCode,
                null,
                operatorUserId
        );
        governanceRepository.updateModerationEventDecision(itemId, action, riskLevel, reasonCode);
        if (Set.of("POST", "COMMENT", "USER").contains(item.targetType())) {
            governanceRepository.updateTargetModeration(item.targetType(), item.targetId(), action, riskLevel, eventId, null);
        }
        governanceRepository.insertAuditLog(
                traceId,
                operatorUserId,
                "CONTENT_REVIEW_DECISION",
                item.targetType(),
                item.targetId(),
                toJson(Map.of(
                        "reviewItemId", itemId,
                        "decision", decision,
                        "comment", comment == null ? "" : comment
                ))
        );

        publishReviewDecisionNotification(decision, action, item);
        Long affectedPostId = parseLongOrNull(item.postId());
        evictAdminWorkbenchCache();
        evictCommunityLeaderboardCache();
        evictCommunityPostListCache();
        evictCommunityPostDetailCache(affectedPostId);
        evictCommunityPostCommentListCache(affectedPostId);
    }

    private void evictAdminWorkbenchCache() {
        adminWorkbenchCacheService.evictAllNow();
        adminWorkbenchCacheService.evictAllAfterCommit();
        adminOperationsDashboardCacheService.evictAllNow();
        adminOperationsDashboardCacheService.evictAllAfterCommit();
    }

    private void evictCommunityLeaderboardCache() {
        communityLeaderboardCacheService.evictAllNow();
        communityLeaderboardCacheService.evictAllAfterCommit();
    }

    private void evictCommunityPostListCache() {
        communityPostListCacheService.evictAllNow();
        communityPostListCacheService.evictAllAfterCommit();
    }

    private void evictCommunityPostDetailCache(Long postId) {
        if (postId == null || postId <= 0) {
            return;
        }
        communityPostDetailCacheService.evictNow(postId);
        communityPostDetailCacheService.evictAfterCommit(postId);
    }

    private void evictCommunityPostCommentListCache(Long postId) {
        if (postId == null || postId <= 0) {
            return;
        }
        communityPostCommentListCacheService.evictNow(postId);
        communityPostCommentListCacheService.evictAfterCommit(postId);
    }

    private Long resolveCommunityPostId(
            String targetType,
            String targetId,
            ContentGovernanceRepository.CommunityNotificationTargetRow communityTarget
    ) {
        if ("POST".equals(targetType)) {
            return parseLongOrNull(targetId);
        }
        if ("COMMENT".equals(targetType) && communityTarget != null) {
            return parseLongOrNull(communityTarget.postId());
        }
        return null;
    }

    private Long parseLongOrNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private void publishReportDecisionNotification(
            long reportId,
            String decision,
            String action,
            ContentGovernanceRepository.AdminReportDetailRow reportDetail
    ) {
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("reportId", reportId);
        payload.put("targetType", reportDetail.targetType());
        payload.put("targetId", reportDetail.targetId());
        payload.put("contentPostId", reportDetail.contentPostId());
        payload.put("contentTitle", reportDetail.contentTitle());
        payload.put("reasonCode", reportDetail.reasonCode());
        payload.put("decision", decision);
        payload.put("action", action);

        notificationService.publish(new PlatformNotificationPublishService.NotificationPublishCommand(
                "COMMUNITY_REPORT_UPDATED",
                NotificationCategory.COMMUNITY,
                "COMMUNITY_REPORT",
                String.valueOf(reportId),
                null,
                List.of(reportDetail.reporterUserId()),
                NotificationPriority.NORMAL,
                "你的举报处理结果已更新",
                buildReportDecisionContent(decision, action, reportDetail.contentTitle()),
                "COMMUNITY_REPORT",
                String.valueOf(reportId),
                "VIEW_COMMUNITY_REPORTS",
                payload,
                "community-report:" + reportId + ":" + decision + ":" + action,
                Instant.now()
        ));
    }

    private void publishCommunityModerationNotificationFromReport(
            String decision,
            String action,
            String targetType,
            String targetId,
            ContentGovernanceRepository.CommunityNotificationTargetRow communityTarget
    ) {
        if (communityTarget.ownerUserId() <= 0 || !StringUtils.hasText(communityTarget.postId())) {
            return;
        }
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("targetType", targetType);
        payload.put("targetId", targetId);
        payload.put("postId", communityTarget.postId());
        payload.put("postTitle", communityTarget.postTitle());
        payload.put("decision", decision);
        payload.put("action", action);

        notificationService.publish(new PlatformNotificationPublishService.NotificationPublishCommand(
                "COMMUNITY_MODERATION_UPDATED",
                NotificationCategory.COMMUNITY,
                "COMMUNITY_POST",
                communityTarget.postId(),
                null,
                List.of(communityTarget.ownerUserId()),
                NotificationPriority.HIGH,
                "你的社区内容审核结果已更新",
                buildModerationContent(action, communityTarget.postTitle()),
                "COMMUNITY_POST",
                communityTarget.postId(),
                "VIEW_COMMUNITY_POST",
                payload,
                "community-moderation:report:" + targetType + ":" + targetId + ":" + action + ":" + decision,
                Instant.now()
        ));
    }

    private void publishReviewDecisionNotification(String decision, String action, ContentGovernanceRepository.ReviewQueueDetailRow item) {
        if (!item.sourceType().startsWith("COMMUNITY_") || item.authorUserId() == null || item.authorUserId() <= 0) {
            return;
        }
        String postId = StringUtils.hasText(item.postId()) ? item.postId() : item.targetId();
        if (!StringUtils.hasText(postId)) {
            return;
        }

        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("sourceType", item.sourceType());
        payload.put("targetType", item.targetType());
        payload.put("targetId", item.targetId());
        payload.put("postId", postId);
        payload.put("postTitle", item.postTitle());
        payload.put("decision", decision);
        payload.put("action", action);

        notificationService.publish(new PlatformNotificationPublishService.NotificationPublishCommand(
                "COMMUNITY_MODERATION_UPDATED",
                NotificationCategory.COMMUNITY,
                "COMMUNITY_POST",
                postId,
                null,
                List.of(item.authorUserId()),
                NotificationPriority.HIGH,
                "你的社区内容审核结果已更新",
                buildModerationContent(action, item.postTitle()),
                "COMMUNITY_POST",
                postId,
                "VIEW_COMMUNITY_POST",
                payload,
                "community-moderation:review:" + item.eventId() + ":" + action,
                Instant.now()
        ));
    }

    private String buildReportDecisionContent(String decision, String action, String contentTitle) {
        String safeTitle = StringUtils.hasText(contentTitle) ? "《" + contentTitle + "》" : "相关内容";
        return switch (decision) {
            case "ACCEPTED" -> "你提交的关于" + safeTitle + "的举报已被采纳，平台已执行 " + action + " 处理。";
            case "REJECTED" -> "你提交的关于" + safeTitle + "的举报已处理完成，当前未认定为违规内容。";
            default -> "你提交的关于" + safeTitle + "的举报流程已关闭，可在治理反馈页回看结果。";
        };
    }

    private String buildModerationContent(String action, String postTitle) {
        String safeTitle = StringUtils.hasText(postTitle) ? "《" + postTitle + "》" : "你的社区内容";
        return switch (action) {
            case "PASS", "RESTORE" -> safeTitle + " 的审核结果已更新，目前可正常展示。";
            case "BLOCK", "TAKE_DOWN" -> safeTitle + " 的审核结果已更新，当前已被限制展示。";
            default -> safeTitle + " 的审核结果已更新，请回到社区查看最新状态。";
        };
    }

    public ModerationPoliciesResponse getPolicies() {
        Map<String, String> map = contentGovernanceConfigCacheService.getPolicyMap(moderationPolicyRepository::findPolicyMap);
        return new ModerationPoliciesResponse(
                parseBoolean(map.get("ai_input_enabled"), true),
                parseBoolean(map.get("ai_output_enabled"), true),
                parseBoolean(map.get("community_strict_review_enabled"), true),
                parseInt(map.get("auto_hide_report_threshold"), 3)
        );
    }

    @Transactional
    public ModerationPoliciesResponse updatePolicies(long operatorUserId, ModerationPoliciesResponse request) {
        moderationPolicyRepository.upsertPolicy("ai_input_enabled", Boolean.toString(request.aiInputEnabled()), "AI 输入审查开关", operatorUserId);
        moderationPolicyRepository.upsertPolicy("ai_output_enabled", Boolean.toString(request.aiOutputEnabled()), "AI 输出审查开关", operatorUserId);
        moderationPolicyRepository.upsertPolicy("community_strict_review_enabled", Boolean.toString(request.communityStrictReviewEnabled()), "社区内容审查开关", operatorUserId);
        moderationPolicyRepository.upsertPolicy("auto_hide_report_threshold", Integer.toString(request.autoHideReportThreshold()), "举报自动隐藏阈值", operatorUserId);
        contentGovernanceConfigCacheService.evictPoliciesNow();
        contentGovernanceConfigCacheService.evictPoliciesAfterCommit();
        adminConsoleSnapshotCacheService.evictNow();
        adminConsoleSnapshotCacheService.evictAfterCommit();
        return new ModerationPoliciesResponse(
                request.aiInputEnabled(),
                request.aiOutputEnabled(),
                request.communityStrictReviewEnabled(),
                request.autoHideReportThreshold()
        );
    }

    public SensitiveTermsResponse listSensitiveTerms(int page, int size, String keyword, String termType, String riskLevel, String status) {
        int normalizedPage = Math.max(page, 1);
        int normalizedSize = Math.min(Math.max(size, 1), 50);
        String normalizedKeyword = normalizeSearchText(normalizeOptional(keyword));
        String normalizedTermType = normalizeOptionalEnum(termType, TERM_TYPES);
        String normalizedRiskLevel = normalizeOptionalEnum(riskLevel, RISK_LEVELS);
        String normalizedStatus = normalizeOptionalLowercaseEnum(status, SENSITIVE_TERM_STATUS_FILTERS);
        List<SensitiveTermRow> allRows = contentGovernanceConfigCacheService
                .getAllSensitiveTerms(sensitiveTermRepository::listSensitiveTerms)
                .stream()
                .toList();
        List<SensitiveTermRow> filteredRows = allRows.stream()
                .filter(row -> matchesSensitiveTermKeyword(row, normalizedKeyword))
                .filter(row -> normalizedTermType == null || normalizedTermType.equals(row.termType()))
                .filter(row -> normalizedRiskLevel == null || normalizedRiskLevel.equals(row.riskLevel()))
                .filter(row -> matchesSensitiveTermStatus(row, normalizedStatus))
                .toList();
        long total = filteredRows.size();
        int fromIndex = Math.min((normalizedPage - 1) * normalizedSize, filteredRows.size());
        int toIndex = Math.min(fromIndex + normalizedSize, filteredRows.size());
        List<SensitiveTermsResponse.TermItem> records = filteredRows.subList(fromIndex, toIndex).stream()
                .map(this::toSensitiveTermItem)
                .toList();
        SensitiveTermsResponse.Summary summary = new SensitiveTermsResponse.Summary(
                allRows.size(),
                allRows.stream().filter(SensitiveTermRow::enabled).count(),
                allRows.stream().filter(SensitiveTermRow::whitelist).count(),
                allRows.stream().map(SensitiveTermRow::termType).distinct().count()
        );
        return new SensitiveTermsResponse(records, total, normalizedPage, normalizedSize, summary);
    }

    @Transactional
    public SensitiveTermsResponse.TermItem createSensitiveTerm(SensitiveTermCreateRequest request) {
        String term = normalizeRequired("term", request.term());
        String termType = normalizeEnum("termType", request.termType(), TERM_TYPES);
        String riskLevel = normalizeEnum("riskLevel", request.riskLevel(), RISK_LEVELS);
        String action = normalizeEnum("action", request.action(), MODERATION_ACTIONS);
        String sourceScope = normalizeEnum("sourceScope", request.sourceScope(), SOURCE_SCOPES);
        long termId;
        try {
            termId = sensitiveTermRepository.insertSensitiveTerm(term, termType, riskLevel, action, sourceScope, request.whitelist(), request.enabled());
        } catch (DuplicateKeyException ex) {
            throw new ApiException("BIZ-1001", "sensitive term already exists", HttpStatus.BAD_REQUEST);
        }
        contentGovernanceConfigCacheService.evictSensitiveTermsNow();
        contentGovernanceConfigCacheService.evictSensitiveTermsAfterCommit();
        return sensitiveTermRepository.findSensitiveTerm(termId)
                .map(this::toSensitiveTermItem)
                .orElseThrow(() -> new ApiException("BIZ-5000", "failed to load created sensitive term", HttpStatus.INTERNAL_SERVER_ERROR));
    }

    @Transactional
    public SensitiveTermsResponse.TermItem updateSensitiveTerm(long termId, SensitiveTermCreateRequest request) {
        String term = normalizeRequired("term", request.term());
        String termType = normalizeEnum("termType", request.termType(), TERM_TYPES);
        String riskLevel = normalizeEnum("riskLevel", request.riskLevel(), RISK_LEVELS);
        String action = normalizeEnum("action", request.action(), MODERATION_ACTIONS);
        String sourceScope = normalizeEnum("sourceScope", request.sourceScope(), SOURCE_SCOPES);
        try {
            int updated = sensitiveTermRepository.updateSensitiveTerm(termId, term, termType, riskLevel, action, sourceScope, request.whitelist(), request.enabled());
            if (updated == 0) {
                throw new ApiException("BIZ-1002", "sensitive term not found", HttpStatus.NOT_FOUND);
            }
        } catch (DuplicateKeyException ex) {
            throw new ApiException("BIZ-1001", "sensitive term already exists", HttpStatus.BAD_REQUEST);
        }
        contentGovernanceConfigCacheService.evictSensitiveTermsNow();
        contentGovernanceConfigCacheService.evictSensitiveTermsAfterCommit();
        return sensitiveTermRepository.findSensitiveTerm(termId)
                .map(this::toSensitiveTermItem)
                .orElseThrow(() -> new ApiException("BIZ-5000", "failed to load updated sensitive term", HttpStatus.INTERNAL_SERVER_ERROR));
    }

    @Transactional
    public int batchUpdateSensitiveTermStatus(List<Long> termIds, boolean enabled) {
        List<Long> normalizedIds = normalizeTermIds(termIds);
        int updatedCount = sensitiveTermRepository.updateSensitiveTermsEnabled(normalizedIds, enabled);
        if (updatedCount > 0) {
            contentGovernanceConfigCacheService.evictSensitiveTermsNow();
            contentGovernanceConfigCacheService.evictSensitiveTermsAfterCommit();
        }
        return updatedCount;
    }

    @Transactional
    public int batchDeleteSensitiveTerms(List<Long> termIds) {
        List<Long> normalizedIds = normalizeTermIds(termIds);
        int deletedCount = sensitiveTermRepository.batchDeleteSensitiveTerms(normalizedIds);
        if (deletedCount > 0) {
            contentGovernanceConfigCacheService.evictSensitiveTermsNow();
            contentGovernanceConfigCacheService.evictSensitiveTermsAfterCommit();
        }
        return deletedCount;
    }

    @Transactional
    public SensitiveTermImportResponse importSensitiveTerms(
            long operatorUserId,
            MultipartFile file,
            String fileFormat,
            String termType,
            String riskLevel,
            String action,
            String sourceScope,
            Boolean whitelist,
            Boolean enabled
    ) {
        if (file == null || file.isEmpty()) {
            throw new ApiException("BIZ-1001", "import file is required", HttpStatus.BAD_REQUEST);
        }

        String fileText;
        try {
            fileText = new String(file.getBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new ApiException("BIZ-5000", "failed to read import file", HttpStatus.INTERNAL_SERVER_ERROR);
        }

        String normalizedText = fileText.replace("\uFEFF", "");
        String normalizedFileFormat = resolveSensitiveTermImportFormat(fileFormat, file.getOriginalFilename());
        SensitiveTermImportResponse response = "PLAIN_TEXT".equals(normalizedFileFormat)
                ? importPlainTextSensitiveTerms(
                        normalizedText,
                        termType,
                        riskLevel,
                        action,
                        sourceScope,
                        whitelist,
                        enabled
                )
                : importCsvSensitiveTerms(normalizedText);

        if (response.createdCount() > 0 || response.updatedCount() > 0) {
            contentGovernanceConfigCacheService.evictSensitiveTermsNow();
            contentGovernanceConfigCacheService.evictSensitiveTermsAfterCommit();
        }
        return response;
    }

    private SensitiveTermImportResponse importCsvSensitiveTerms(String normalizedCsvText) {
        String[] rawLines = normalizedCsvText.split("\\r?\\n");
        int totalRows = 0;
        int createdCount = 0;
        int updatedCount = 0;
        int skippedCount = 0;
        List<String> errors = new ArrayList<>();

        for (int index = 0; index < rawLines.length; index++) {
            String line = rawLines[index];
            if (!StringUtils.hasText(line)) {
                continue;
            }
            List<String> cells = parseCsvLine(line);
            if (cells.stream().allMatch(value -> !StringUtils.hasText(value))) {
                continue;
            }
            if (index == 0 && !cells.isEmpty() && "term".equalsIgnoreCase(cells.get(0).trim())) {
                continue;
            }

            totalRows++;
            try {
                if (cells.size() < 6) {
                    throw new ApiException("BIZ-1001", "csv columns not enough", HttpStatus.BAD_REQUEST);
                }

                boolean legacyFormat = cells.size() == 6;
                String term = normalizeRequired("term", cells.get(0));
                String termType = normalizeEnum("termType", legacyFormat ? "OTHER" : getCell(cells, 1, "OTHER"), TERM_TYPES);
                int baseIndex = legacyFormat ? 1 : 2;
                String riskLevel = normalizeEnum("riskLevel", getCell(cells, baseIndex, "HIGH"), RISK_LEVELS);
                String action = normalizeEnum("action", getCell(cells, baseIndex + 1, "BLOCK"), MODERATION_ACTIONS);
                String sourceScope = normalizeEnum("sourceScope", getCell(cells, baseIndex + 2, "COMMUNITY_POST"), SOURCE_SCOPES);
                boolean whitelist = parseBooleanCell(getCell(cells, baseIndex + 3, "false"), "whitelist");
                boolean enabled = parseBooleanCell(getCell(cells, baseIndex + 4, "true"), "enabled");

                UpsertResult upsertResult = upsertSensitiveTerm(term, termType, riskLevel, action, sourceScope, whitelist, enabled);
                createdCount += upsertResult.created() ? 1 : 0;
                updatedCount += upsertResult.created() ? 0 : 1;
            } catch (RuntimeException ex) {
                skippedCount++;
                if (errors.size() < 20) {
                    String message = ex instanceof ApiException apiException ? apiException.getMessage() : "invalid row";
                    errors.add("第 " + (index + 1) + " 行导入失败：" + message);
                }
            }
        }
        return new SensitiveTermImportResponse(totalRows, createdCount, updatedCount, skippedCount, errors);
    }

    private SensitiveTermImportResponse importPlainTextSensitiveTerms(
            String normalizedText,
            String termType,
            String riskLevel,
            String action,
            String sourceScope,
            Boolean whitelist,
            Boolean enabled
    ) {
        String normalizedTermType = normalizeEnum("termType", StringUtils.hasText(termType) ? termType : "OTHER", TERM_TYPES);
        String normalizedRiskLevel = normalizeEnum("riskLevel", StringUtils.hasText(riskLevel) ? riskLevel : "HIGH", RISK_LEVELS);
        String normalizedAction = normalizeEnum("action", StringUtils.hasText(action) ? action : "BLOCK", MODERATION_ACTIONS);
        String normalizedSourceScope = normalizeEnum("sourceScope", StringUtils.hasText(sourceScope) ? sourceScope : "COMMUNITY_POST", SOURCE_SCOPES);
        boolean normalizedWhitelist = whitelist != null && whitelist;
        boolean normalizedEnabled = enabled == null || enabled;

        String[] rawLines = normalizedText.split("\\r?\\n");
        int totalRows = 0;
        int createdCount = 0;
        int updatedCount = 0;
        int skippedCount = 0;
        List<String> errors = new ArrayList<>();

        for (int index = 0; index < rawLines.length; index++) {
            String line = rawLines[index];
            if (!StringUtils.hasText(line)) {
                continue;
            }
            String term = line.trim();
            if (!StringUtils.hasText(term) || term.startsWith("#")) {
                continue;
            }
            totalRows++;
            try {
                String normalizedTerm = normalizeRequired("term", term);
                UpsertResult upsertResult = upsertSensitiveTerm(
                        normalizedTerm,
                        normalizedTermType,
                        normalizedRiskLevel,
                        normalizedAction,
                        normalizedSourceScope,
                        normalizedWhitelist,
                        normalizedEnabled
                );
                createdCount += upsertResult.created() ? 1 : 0;
                updatedCount += upsertResult.created() ? 0 : 1;
            } catch (RuntimeException ex) {
                skippedCount++;
                if (errors.size() < 20) {
                    String message = ex instanceof ApiException apiException ? apiException.getMessage() : "invalid row";
                    errors.add("第 " + (index + 1) + " 行导入失败：" + message);
                }
            }
        }
        return new SensitiveTermImportResponse(totalRows, createdCount, updatedCount, skippedCount, errors);
    }

    public SensitiveTermsExportPayload exportSensitiveTerms() {
        List<SensitiveTermRow> records = contentGovernanceConfigCacheService
                .getAllSensitiveTerms(sensitiveTermRepository::listSensitiveTerms);
        StringBuilder builder = new StringBuilder();
        builder.append("term,termType,riskLevel,action,sourceScope,whitelist,enabled\n");
        for (SensitiveTermRow row : records) {
            builder.append(csvCell(row.term())).append(',')
                    .append(csvCell(row.termType())).append(',')
                    .append(csvCell(row.riskLevel())).append(',')
                    .append(csvCell(row.action())).append(',')
                    .append(csvCell(row.sourceScope())).append(',')
                    .append(row.whitelist()).append(',')
                    .append(row.enabled()).append('\n');
        }
        String filename = "sensitive-terms-" + Instant.now().toString().replace(":", "-") + ".csv";
        return new SensitiveTermsExportPayload(filename, builder.toString().getBytes(StandardCharsets.UTF_8));
    }

    @Transactional
    public void deleteSensitiveTerm(long termId) {
        if (sensitiveTermRepository.deleteSensitiveTerm(termId) == 0) {
            throw new ApiException("BIZ-1002", "sensitive term not found", HttpStatus.NOT_FOUND);
        }
        contentGovernanceConfigCacheService.evictSensitiveTermsNow();
        contentGovernanceConfigCacheService.evictSensitiveTermsAfterCommit();
    }

    private SensitiveTermsResponse.TermItem toSensitiveTermItem(SensitiveTermRow row) {
        return new SensitiveTermsResponse.TermItem(
                row.id(),
                row.term(),
                row.termType(),
                row.riskLevel(),
                row.action(),
                row.sourceScope(),
                row.whitelist(),
                row.enabled(),
                toIso(row.createdAt()),
                toIso(row.updatedAt())
        );
    }

    private ModerationDecision moderateText(
            String traceId,
            long operatorUserId,
            String sourceType,
            String targetType,
            String originalText,
            boolean communitySource,
            boolean persistEvent
    ) {
        ModerationPoliciesResponse policies = getPolicies();
        if (!isModerationEnabled(sourceType, communitySource, policies)) {
            // 策略关闭时仍可记录 PASS 事件，方便后台解释为什么没有拦截。
            long eventId = persistEvent ? governanceRepository.insertModerationEvent(
                    traceId,
                    sourceType,
                    targetType,
                    null,
                    "LOW",
                    "PASS",
                    "POLICY_DISABLED",
                    null,
                    operatorUserId
            ) : 0L;
            return new ModerationDecision(eventId, sourceType, targetType, "LOW", "PASS", "POLICY_DISABLED", null);
        }

        List<SensitiveTermRow> matchedTerms = findMatchedTermsForSource(sourceType, originalText);
        if (matchedTerms.isEmpty()) {
            // 无风险词命中时记录 RULE_CLEAR，和策略关闭的 PASS 区分开。
            long eventId = persistEvent ? governanceRepository.insertModerationEvent(
                    traceId,
                    sourceType,
                    targetType,
                    null,
                    "LOW",
                    "PASS",
                    "RULE_CLEAR",
                    null,
                    operatorUserId
            ) : 0L;
            return new ModerationDecision(eventId, sourceType, targetType, "LOW", "PASS", "RULE_CLEAR", null);
        }

        // 多个风险词命中时按动作强度、风险等级、词长选择最终判定词。
        SensitiveTermRow winningTerm = matchedTerms.stream()
                .sorted(Comparator
                        .comparingInt((SensitiveTermRow row) -> actionPriority(row.action())).reversed()
                        .thenComparingInt(row -> riskPriority(row.riskLevel())).reversed()
                        .thenComparingInt(row -> row.term().length()).reversed())
                .findFirst()
                .orElseThrow();

        String finalAction = normalizeActionForSource(sourceType, communitySource, winningTerm.action());
        String reasonCode = switch (finalAction) {
            case "BLOCK" -> "POLICY_HIGH_RISK";
            case "REVIEW" -> "POLICY_REVIEW_REQUIRED";
            case "MASK" -> "POLICY_MASK_REQUIRED";
            default -> "RULE_CLEAR";
        };
        String maskedText = "MASK".equals(finalAction) ? applyMask(originalText, matchedTerms) : null;
        long eventId = persistEvent ? governanceRepository.insertModerationEvent(
                traceId,
                sourceType,
                targetType,
                null,
                winningTerm.riskLevel(),
                finalAction,
                reasonCode,
                maskedText,
                operatorUserId
        ) : 0L;
        return new ModerationDecision(eventId, sourceType, targetType, winningTerm.riskLevel(), finalAction, reasonCode, maskedText);
    }

    private boolean isModerationEnabled(String sourceType, boolean communitySource, ModerationPoliciesResponse policies) {
        return switch (sourceType) {
            case "AI_INPUT" -> policies.aiInputEnabled();
            case "AI_OUTPUT" -> policies.aiOutputEnabled();
            default -> !communitySource || policies.communityStrictReviewEnabled();
        };
    }

    private List<SensitiveTermRow> findMatchedTermsForSource(String sourceType, String originalText) {
        List<SensitiveTermRow> terms = contentGovernanceConfigCacheService
                .getEnabledTermsForSource(sourceType, () -> sensitiveTermRepository.findEnabledTermsForSource(sourceType));
        List<SensitiveTermRow> whitelistTerms = terms.stream().filter(SensitiveTermRow::whitelist).toList();
        List<SensitiveTermRow> riskyTerms = terms.stream().filter(term -> !term.whitelist()).toList();
        // 白名单先 mask 掉，后续风险词匹配不再误伤被允许的固定表达。
        String searchableText = maskWhitelist(normalizeSearchText(originalText), whitelistTerms);
        return riskyTerms.stream()
                .filter(term -> searchableText.contains(term.term().trim().toLowerCase(Locale.ROOT)))
                .toList();
    }

    private String normalizeActionForSource(String sourceType, boolean communitySource, String rawAction) {
        if (communitySource && "MASK".equals(rawAction)) {
            // 社区内容不做静默打码，转人工 REVIEW 让管理员决定公开与否。
            return "REVIEW";
        }
        if ("AI_INPUT".equals(sourceType) && ("MASK".equals(rawAction) || "REVIEW".equals(rawAction))) {
            // 用户输入给模型前必须更保守，命中 mask/review 都直接阻断。
            return "BLOCK";
        }
        if ("AI_OUTPUT".equals(sourceType) && "REVIEW".equals(rawAction)) {
            // AI 输出的 REVIEW 降成 MASK，避免模型结果卡住用户主流程。
            return "MASK";
        }
        return rawAction;
    }

    private String resolveAuditOperatorDisplayName(long operatorUserId, String operatorDisplayName) {
        if (StringUtils.hasText(operatorDisplayName)) {
            return operatorDisplayName;
        }
        if (operatorUserId <= 0) {
            return "系统";
        }
        return "管理员#" + operatorUserId;
    }

    private void tryAutoHideTarget(String traceId, String targetType, String targetId) {
        if (!Set.of("POST", "COMMENT").contains(targetType)) {
            return;
        }
        ModerationPoliciesResponse policies = getPolicies();
        long reportCount = governanceRepository.countOpenReportsByTarget(targetType, targetId);
        if (reportCount < Math.max(1, policies.autoHideReportThreshold())) {
            return;
        }
        ContentGovernanceRepository.TargetStateRow targetState = governanceRepository.findTargetState(targetType, targetId).orElse(null);
        if (targetState == null || !"PASS".equalsIgnoreCase(targetState.status())) {
            return;
        }
        // 开放举报数达到阈值时，PASS 内容自动切到 REVIEW，等待管理员复核。
        String sourceType = "POST".equals(targetType) ? "COMMUNITY_POST" : "COMMUNITY_COMMENT";
        long eventId = governanceRepository.insertModerationEvent(
                traceId,
                sourceType,
                targetType,
                targetId,
                "MEDIUM",
                "REVIEW",
                "REPORT_THRESHOLD_AUTO_HIDE",
                null,
                0L
        );
        governanceRepository.updateTargetModeration(targetType, targetId, "REVIEW", "MEDIUM", eventId, null);
        governanceRepository.insertAuditLog(
                traceId,
                0L,
                "AUTO_HIDE",
                targetType,
                targetId,
                toJson(Map.of(
                        "reasonCode", "REPORT_THRESHOLD_AUTO_HIDE",
                        "reportCount", reportCount,
                        "threshold", policies.autoHideReportThreshold()
                ))
        );
    }

    private List<Long> normalizeTermIds(List<Long> termIds) {
        if (termIds == null || termIds.isEmpty()) {
            throw new ApiException("BIZ-1001", "termIds is required", HttpStatus.BAD_REQUEST);
        }
        return termIds.stream().filter(id -> id != null && id > 0).distinct().toList();
    }

    private List<String> parseCsvLine(String line) {
        List<String> cells = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (int index = 0; index < line.length(); index++) {
            char currentChar = line.charAt(index);
            if (currentChar == '"') {
                if (inQuotes && index + 1 < line.length() && line.charAt(index + 1) == '"') {
                    current.append('"');
                    index++;
                } else {
                    inQuotes = !inQuotes;
                }
                continue;
            }
            if (currentChar == ',' && !inQuotes) {
                cells.add(current.toString().trim());
                current.setLength(0);
                continue;
            }
            current.append(currentChar);
        }
        cells.add(current.toString().trim());
        return cells;
    }

    private String getCell(List<String> cells, int index, String fallback) {
        if (index >= cells.size() || !StringUtils.hasText(cells.get(index))) {
            return fallback;
        }
        return cells.get(index);
    }

    private boolean parseBooleanCell(String raw, String fieldName) {
        if (!StringUtils.hasText(raw)) {
            return false;
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "true", "1", "yes", "y" -> true;
            case "false", "0", "no", "n" -> false;
            default -> throw new ApiException("BIZ-1001", fieldName + " must be boolean", HttpStatus.BAD_REQUEST);
        };
    }

    private String csvCell(String value) {
        String safe = value == null ? "" : value;
        return "\"" + safe.replace("\"", "\"\"") + "\"";
    }

    private String resolveSensitiveTermImportFormat(String fileFormat, String filename) {
        if (StringUtils.hasText(fileFormat)) {
            return normalizeEnum("fileFormat", fileFormat, IMPORT_FILE_FORMATS);
        }
        if (StringUtils.hasText(filename)) {
            String normalizedFilename = filename.trim().toLowerCase(Locale.ROOT);
            if (normalizedFilename.endsWith(".txt") || normalizedFilename.endsWith(".text")) {
                return "PLAIN_TEXT";
            }
        }
        return "CSV";
    }

    private UpsertResult upsertSensitiveTerm(
            String term,
            String termType,
            String riskLevel,
            String action,
            String sourceScope,
            boolean whitelist,
            boolean enabled
    ) {
        Long existingId = sensitiveTermRepository.findSensitiveTermId(term, sourceScope, whitelist).orElse(null);
        if (existingId == null) {
            sensitiveTermRepository.insertSensitiveTerm(term, termType, riskLevel, action, sourceScope, whitelist, enabled);
            return new UpsertResult(true);
        }
        sensitiveTermRepository.updateSensitiveTerm(existingId, term, termType, riskLevel, action, sourceScope, whitelist, enabled);
        return new UpsertResult(false);
    }

    private record UpsertResult(boolean created) {
    }

    public record SensitiveTermsExportPayload(String filename, byte[] content) {
    }

    private void applyTargetAction(String traceId, long operatorUserId, String targetType, String targetId, String action) {
        if ("TAKE_DOWN".equals(action)) {
            // 用户目标走账号状态，内容目标走 moderation 状态，二者不要混用字段。
            if ("USER".equals(targetType)) {
                governanceRepository.updateTargetModeration(targetType, targetId, null, null, null, "SUSPENDED");
            } else {
                long eventId = governanceRepository.insertModerationEvent(
                        traceId,
                        "POST".equals(targetType) ? "COMMUNITY_POST" : "COMMUNITY_COMMENT",
                        targetType,
                        targetId,
                        "HIGH",
                        "BLOCK",
                        "REPORT_DECISION_TAKE_DOWN",
                        null,
                        operatorUserId
                );
                governanceRepository.updateTargetModeration(targetType, targetId, "BLOCK", "HIGH", eventId, null);
            }
            return;
        }
        if ("RESTORE".equals(action)) {
            // 恢复同样保留一条 moderation event，审计链能看到人工恢复原因。
            if ("USER".equals(targetType)) {
                governanceRepository.updateTargetModeration(targetType, targetId, null, null, null, "ACTIVE");
            } else {
                long eventId = governanceRepository.insertModerationEvent(
                        traceId,
                        "POST".equals(targetType) ? "COMMUNITY_POST" : "COMMUNITY_COMMENT",
                        targetType,
                        targetId,
                        "LOW",
                        "PASS",
                        "REPORT_DECISION_RESTORE",
                        null,
                        operatorUserId
                );
                governanceRepository.updateTargetModeration(targetType, targetId, "PASS", "LOW", eventId, null);
            }
        }
    }

    private String maskWhitelist(String searchableText, List<SensitiveTermRow> whitelistTerms) {
        String result = searchableText;
        for (SensitiveTermRow term : whitelistTerms) {
            String normalizedTerm = term.term().trim().toLowerCase(Locale.ROOT);
            if (!normalizedTerm.isEmpty()) {
                result = result.replace(normalizedTerm, " ".repeat(normalizedTerm.length()));
            }
        }
        return result;
    }

    private String applyMask(String originalText, List<SensitiveTermRow> matchedTerms) {
        String masked = originalText;
        List<String> terms = matchedTerms.stream()
                .map(row -> row.term().trim())
                .filter(StringUtils::hasText)
                .distinct()
                .sorted(Comparator.comparingInt(String::length).reversed())
                .toList();
        for (String term : terms) {
            masked = Pattern.compile("(?i)" + Pattern.quote(term))
                    .matcher(masked)
                    .replaceAll("*".repeat(term.length()));
        }
        return masked;
    }

    private String normalizeSearchText(String rawText) {
        return rawText == null ? "" : rawText.toLowerCase(Locale.ROOT);
    }

    private boolean matchesSensitiveTermKeyword(SensitiveTermRow row, String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return true;
        }
        String searchableText = String.join(" ",
                row.term(),
                row.termType(),
                getSensitiveTermTypeLabel(row.termType()),
                row.riskLevel(),
                getRiskLevelLabel(row.riskLevel()),
                row.action(),
                getModerationActionLabel(row.action()),
                row.sourceScope(),
                getSourceScopeLabel(row.sourceScope()),
                row.enabled() ? "enabled 启用 启用中" : "disabled 停用 已停用",
                row.whitelist() ? "whitelist 白名单" : "非白名单"
        ).toLowerCase(Locale.ROOT);
        return searchableText.contains(keyword);
    }

    private boolean matchesSensitiveTermStatus(SensitiveTermRow row, String status) {
        if (status == null) {
            return true;
        }
        return switch (status) {
            case "enabled" -> row.enabled();
            case "disabled" -> !row.enabled();
            case "whitelist" -> row.whitelist();
            default -> true;
        };
    }

    private String getSensitiveTermTypeLabel(String termType) {
        return switch (termType) {
            case "POLITICS" -> "政治";
            case "PORNOGRAPHY" -> "色情";
            case "TERROR" -> "恐怖";
            case "VIOLENCE" -> "暴力";
            case "FRAUD" -> "欺诈";
            case "ABUSE" -> "辱骂";
            case "ADVERTISEMENT" -> "广告";
            case "ILLEGAL" -> "违规";
            default -> "其他";
        };
    }

    private String getRiskLevelLabel(String riskLevel) {
        return switch (riskLevel) {
            case "CRITICAL" -> "极高风险";
            case "HIGH" -> "高风险";
            case "MEDIUM" -> "中风险";
            default -> "低风险";
        };
    }

    private String getModerationActionLabel(String action) {
        return switch (action) {
            case "PASS" -> "放行";
            case "MASK" -> "脱敏";
            case "BLOCK" -> "拦截";
            case "REVIEW" -> "转人工审核";
            default -> action;
        };
    }

    private String getSourceScopeLabel(String sourceScope) {
        return switch (sourceScope) {
            case "COMMUNITY_POST" -> "帖子";
            case "COMMUNITY_COMMENT" -> "评论";
            case "AI_INPUT" -> "智能输入内容";
            case "AI_OUTPUT" -> "智能生成内容";
            case "ALL" -> "全局";
            default -> sourceScope;
        };
    }

    private String normalizeRequired(String fieldName, String value) {
        if (value == null || value.isBlank()) {
            throw new ApiException("BIZ-1001", fieldName + " invalid", HttpStatus.BAD_REQUEST);
        }
        return value.trim();
    }

    private String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String normalizeEnum(String fieldName, String rawValue, Set<String> allowed) {
        String normalized = normalizeRequired(fieldName, rawValue).toUpperCase(Locale.ROOT);
        if (!allowed.contains(normalized)) {
            throw new ApiException("BIZ-1001", fieldName + " invalid", HttpStatus.BAD_REQUEST);
        }
        return normalized;
    }

    private String normalizeOptionalEnum(String rawValue, Set<String> allowed) {
        if (rawValue == null || rawValue.isBlank()) {
            return null;
        }
        return normalizeEnum("filter", rawValue, allowed);
    }

    private String normalizeOptionalLowercaseEnum(String rawValue, Set<String> allowed) {
        if (rawValue == null || rawValue.isBlank()) {
            return null;
        }
        String normalized = normalizeRequired("filter", rawValue).toLowerCase(Locale.ROOT);
        if (!allowed.contains(normalized)) {
            throw new ApiException("BIZ-1001", "filter invalid", HttpStatus.BAD_REQUEST);
        }
        return normalized;
    }

    private int actionPriority(String action) {
        return switch (action) {
            case "BLOCK" -> 4;
            case "REVIEW" -> 3;
            case "MASK" -> 2;
            default -> 1;
        };
    }

    private int riskPriority(String riskLevel) {
        return switch (riskLevel) {
            case "CRITICAL" -> 4;
            case "HIGH" -> 3;
            case "MEDIUM" -> 2;
            default -> 1;
        };
    }

    private boolean parseBoolean(String value, boolean defaultValue) {
        if (!StringUtils.hasText(value)) {
            return defaultValue;
        }
        return Boolean.parseBoolean(value);
    }

    private int parseInt(String value, int defaultValue) {
        if (!StringUtils.hasText(value)) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new ApiException("BIZ-5000", "failed to serialize governance detail", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private Long toIso(Instant instant) {
        return com.bishe.server.common.TimePayloads.toEpochMillis(instant);
    }
}
