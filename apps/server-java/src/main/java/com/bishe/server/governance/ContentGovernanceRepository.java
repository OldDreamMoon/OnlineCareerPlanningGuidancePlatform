package com.bishe.server.governance;

import com.bishe.server.auth.model.UserAccountStatus;
import com.bishe.server.auth.repository.jpa.UserAccountJpaRepository;
import com.bishe.server.auth.repository.jpa.entity.UserAccountEntity;
import com.bishe.server.consult.repository.jpa.entity.AuditLogEntity;
import com.bishe.server.community.repository.jpa.CommunityCommentJpaRepository;
import com.bishe.server.community.repository.jpa.CommunityPostJpaRepository;
import com.bishe.server.governance.jpa.ContentModerationEventJpaRepository;
import com.bishe.server.governance.jpa.ContentReportJpaRepository;
import com.bishe.server.governance.jpa.entity.ContentModerationEventEntity;
import com.bishe.server.governance.jpa.entity.ContentReportActionEntity;
import com.bishe.server.governance.jpa.entity.ContentReportEntity;
import com.bishe.server.profile.repository.jpa.entity.CommentEntity;
import com.bishe.server.profile.repository.jpa.entity.PostEntity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * 内容治理剩余仓储：审查事件、举报、待审队列与审计日志。
 */
@Repository
public class ContentGovernanceRepository {

    private static final List<String> OPEN_REPORT_STATUSES = List.of("PENDING", "ACCEPTED");

    private final EntityManager entityManager;
    private final ContentReportJpaRepository contentReportJpaRepository;
    private final ContentModerationEventJpaRepository contentModerationEventJpaRepository;
    private final CommunityPostJpaRepository communityPostJpaRepository;
    private final CommunityCommentJpaRepository communityCommentJpaRepository;
    private final UserAccountJpaRepository userAccountJpaRepository;

    public ContentGovernanceRepository(
            EntityManager entityManager,
            ContentReportJpaRepository contentReportJpaRepository,
            ContentModerationEventJpaRepository contentModerationEventJpaRepository,
            CommunityPostJpaRepository communityPostJpaRepository,
            CommunityCommentJpaRepository communityCommentJpaRepository,
            UserAccountJpaRepository userAccountJpaRepository
    ) {
        this.entityManager = entityManager;
        this.contentReportJpaRepository = contentReportJpaRepository;
        this.contentModerationEventJpaRepository = contentModerationEventJpaRepository;
        this.communityPostJpaRepository = communityPostJpaRepository;
        this.communityCommentJpaRepository = communityCommentJpaRepository;
        this.userAccountJpaRepository = userAccountJpaRepository;
    }

    @Transactional
    public long insertModerationEvent(
            String traceId,
            String sourceType,
            String targetType,
            String targetId,
            String riskLevel,
            String action,
            String reasonCode,
            String maskedText,
            long operatorUserId
    ) {
        ContentModerationEventEntity entity = ContentModerationEventEntity.create(
                traceId,
                sourceType,
                targetType,
                targetId,
                riskLevel,
                action,
                reasonCode,
                maskedText,
                operatorUserId
        );
        return contentModerationEventJpaRepository.saveAndFlush(entity).getId();
    }

    @Transactional
    public void updateModerationEventTarget(long eventId, String targetId) {
        contentModerationEventJpaRepository.findById(eventId)
                .ifPresent(entity -> entity.updateTargetId(targetId));
    }

    @Transactional
    public void updateModerationEventDecision(long eventId, String action, String riskLevel, String reasonCode) {
        contentModerationEventJpaRepository.findById(eventId)
                .ifPresent(entity -> entity.updateDecision(action, riskLevel, reasonCode));
    }

    public boolean existsTarget(String targetType, String targetId) {
        Long numericId = parseLongOrNull(targetId);
        if (numericId == null) {
            return false;
        }
        return switch (targetType) {
            case "POST" -> communityPostJpaRepository.findByIdAndDeletedFalse(numericId).isPresent();
            case "COMMENT" -> findReadableComment(numericId).isPresent();
            case "USER" -> userAccountJpaRepository.findByIdAndDeletedFalse(numericId).isPresent();
            default -> false;
        };
    }

    public Optional<ExistingReportRow> findExistingReport(long reporterUserId, String targetType, String targetId, String reasonCode) {
        return contentReportJpaRepository
                .findFirstByReporterUserIdAndTargetTypeAndTargetIdAndReasonCode(reporterUserId, targetType, targetId, reasonCode)
                .map(report -> new ExistingReportRow(report.getId(), report.getStatus()));
    }

    public long countRecentReportsByReporter(long reporterUserId, Instant since) {
        return contentReportJpaRepository.countByReporterUserIdAndCreatedAtGreaterThanEqual(reporterUserId, since);
    }

    public List<Instant> findRecentReportCreatedAtByReporter(long reporterUserId, Instant since) {
        return contentReportJpaRepository.findByReporterUserIdAndCreatedAtGreaterThanEqualOrderByCreatedAtAscIdAsc(reporterUserId, since)
                .stream()
                .map(ContentReportEntity::getCreatedAt)
                .toList();
    }

    @Transactional
    public long insertReport(long reporterUserId, String targetType, String targetId, String reasonCode, String detail) {
        try {
            ContentReportEntity entity = ContentReportEntity.create(reporterUserId, targetType, targetId, reasonCode, detail);
            return contentReportJpaRepository.saveAndFlush(entity).getId();
        } catch (DataIntegrityViolationException ex) {
            throw new DuplicateKeyException("content report already exists", ex);
        }
    }

    public long countOpenReportsByTarget(String targetType, String targetId) {
        return contentReportJpaRepository.countByTargetTypeAndTargetIdAndStatusIn(targetType, targetId, OPEN_REPORT_STATUSES);
    }

    public List<MyReportRow> findMyReports(long reporterUserId, String status, int page, int size) {
        List<ContentReportEntity> reports = findReportsByReporter(reporterUserId, status, page, size);
        ReportContext context = buildReportContext(reports, false);
        List<MyReportRow> rows = new ArrayList<>();
        for (ContentReportEntity report : reports) {
            rows.add(new MyReportRow(
                    report.getId(),
                    report.getTargetType(),
                    report.getTargetId(),
                    resolveContentPostId(report, context),
                    resolveMyReportContentTitle(report, context),
                    resolveMyReportContentBody(report, context),
                    report.getReasonCode(),
                    report.getDetail(),
                    report.getStatus(),
                    report.getLatestAction(),
                    report.getCreatedAt(),
                    report.getUpdatedAt()
            ));
        }
        return rows;
    }

    public long countMyReports(long reporterUserId, String status) {
        StringBuilder jpql = new StringBuilder("""
                select count(report)
                  from ContentReportEntity report
                 where report.reporterUserId = :reporterUserId
                """);
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("reporterUserId", reporterUserId);
        if (StringUtils.hasText(status)) {
            jpql.append(" and report.status = :status");
            params.put("status", status);
        }
        return executeCountQuery(jpql.toString(), params);
    }

    public List<AdminReportRow> findAdminReports(String status, String targetType, int page, int size) {
        List<ContentReportEntity> reports = findAdminReportEntities(status, targetType, page, size);
        ReportContext context = buildReportContext(reports, false);
        List<AdminReportRow> rows = new ArrayList<>();
        for (ContentReportEntity report : reports) {
            rows.add(new AdminReportRow(
                    report.getId(),
                    report.getTargetType(),
                    report.getTargetId(),
                    resolveContentPostId(report, context),
                    resolveAdminReportListContentTitle(report, context),
                    resolveAdminReportListContentBody(report, context),
                    report.getReasonCode(),
                    report.getStatus(),
                    report.getLatestAction(),
                    context.reportCounts().getOrDefault(targetKey(report.getTargetType(), report.getTargetId()), 0L),
                    report.getCreatedAt(),
                    report.getUpdatedAt()
            ));
        }
        return rows;
    }

    public long countAdminReports(String status, String targetType) {
        StringBuilder jpql = new StringBuilder("""
                select count(report)
                  from ContentReportEntity report
                 where 1 = 1
                """);
        Map<String, Object> params = new LinkedHashMap<>();
        if (StringUtils.hasText(status)) {
            jpql.append(" and report.status = :status");
            params.put("status", status);
        }
        if (StringUtils.hasText(targetType)) {
            jpql.append(" and report.targetType = :targetType");
            params.put("targetType", targetType);
        }
        return executeCountQuery(jpql.toString(), params);
    }

    public Optional<AdminReportDetailRow> findAdminReportDetail(long reportId) {
        Optional<ContentReportEntity> optionalReport = contentReportJpaRepository.findById(reportId);
        if (optionalReport.isEmpty()) {
            return Optional.empty();
        }
        ContentReportEntity report = optionalReport.get();
        ReportContext context = buildReportContext(List.of(report), true);
        UserAccountEntity reporter = context.reporterUsers().get(report.getReporterUserId());
        long reportCount = context.reportCounts().getOrDefault(targetKey(report.getTargetType(), report.getTargetId()), 0L);
        return Optional.of(new AdminReportDetailRow(
                report.getId(),
                report.getReporterUserId(),
                reporter == null ? null : reporter.getDisplayName(),
                report.getTargetType(),
                report.getTargetId(),
                resolveContentPostId(report, context),
                resolveAdminReportDetailContentTitle(report, context),
                resolveAdminReportDetailContentBody(report, context),
                report.getReasonCode(),
                report.getDetail(),
                report.getStatus(),
                report.getLatestAction(),
                reportCount,
                report.getCreatedAt(),
                report.getUpdatedAt()
        ));
    }

    public Optional<CommunityNotificationTargetRow> findCommunityNotificationTarget(String targetType, String targetId) {
        Long numericId = parseLongOrNull(targetId);
        if (numericId == null) {
            return Optional.empty();
        }
        if ("POST".equals(targetType)) {
            return communityPostJpaRepository.findByIdAndDeletedFalse(numericId)
                    .map(post -> new CommunityNotificationTargetRow(post.getUserId(), String.valueOf(post.getId()), post.getTitle()));
        }
        if (!"COMMENT".equals(targetType)) {
            return Optional.empty();
        }
        Optional<CommentEntity> optionalComment = findReadableComment(numericId);
        if (optionalComment.isEmpty()) {
            return Optional.empty();
        }
        CommentEntity comment = optionalComment.get();
        return communityPostJpaRepository.findByIdAndDeletedFalse(comment.getPostId())
                .map(post -> new CommunityNotificationTargetRow(comment.getUserId(), String.valueOf(post.getId()), post.getTitle()));
    }

    public Optional<ReportDecisionTargetRow> findReportForDecision(long reportId) {
        return contentReportJpaRepository.findById(reportId)
                .map(report -> new ReportDecisionTargetRow(report.getId(), report.getTargetType(), report.getTargetId(), report.getStatus()));
    }

    @Transactional
    public void updateReportStatus(long reportId, String status, String latestAction, boolean closed) {
        contentReportJpaRepository.findById(reportId)
                .ifPresent(report -> report.updateStatus(status, latestAction, closed));
    }

    @Transactional
    public void insertReportAction(long reportId, long operatorUserId, String decision, String action, String comment) {
        entityManager.persist(ContentReportActionEntity.create(reportId, operatorUserId, decision, action, comment));
    }

    public List<ReportActionRow> listReportActions(long reportId) {
        List<ContentReportActionEntity> actions = entityManager.createQuery("""
                select action
                  from ContentReportActionEntity action
                 where action.reportId = :reportId
              order by action.createdAt asc, action.id asc
                """, ContentReportActionEntity.class)
                .setParameter("reportId", reportId)
                .getResultList();
        Map<Long, UserAccountEntity> operatorUsers = loadUsersByIds(extractOperatorUserIds(actions));
        List<ReportActionRow> rows = new ArrayList<>();
        for (ContentReportActionEntity action : actions) {
            UserAccountEntity operator = operatorUsers.get(action.getOperatorUserId());
            rows.add(new ReportActionRow(
                    action.getId(),
                    action.getOperatorUserId(),
                    operator == null ? null : operator.getDisplayName(),
                    action.getDecision(),
                    action.getAction(),
                    action.getComment(),
                    action.getCreatedAt()
            ));
        }
        return rows;
    }

    public Optional<TargetStateRow> findTargetState(String targetType, String targetId) {
        Long numericId = parseLongOrNull(targetId);
        if (numericId == null) {
            return Optional.empty();
        }
        if ("POST".equals(targetType)) {
            return communityPostJpaRepository.findByIdAndDeletedFalse(numericId)
                    .map(post -> new TargetStateRow("POST", post.getModerationStatus(), post.getRiskLevel()));
        }
        if ("COMMENT".equals(targetType)) {
            return findReadableComment(numericId)
                    .map(comment -> new TargetStateRow("COMMENT", comment.getModerationStatus(), comment.getRiskLevel()));
        }
        if (!"USER".equals(targetType)) {
            return Optional.empty();
        }
        return userAccountJpaRepository.findByIdAndDeletedFalse(numericId)
                .map(user -> new TargetStateRow("USER", user.getStatus().name(), null));
    }

    @Transactional
    public void updateTargetModeration(String targetType, String targetId, String moderationStatus, String riskLevel, Long eventId, String userStatus) {
        Long numericId = parseLongOrNull(targetId);
        if (numericId == null) {
            return;
        }
        switch (targetType) {
            case "POST" -> entityManager.createQuery("""
                    update PostEntity post
                       set post.moderationStatus = :moderationStatus,
                           post.riskLevel = :riskLevel,
                           post.lastModerationEventId = :eventId
                     where post.id = :targetId
                       and post.deleted = false
                    """)
                    .setParameter("moderationStatus", moderationStatus)
                    .setParameter("riskLevel", riskLevel)
                    .setParameter("eventId", eventId)
                    .setParameter("targetId", numericId)
                    .executeUpdate();
            case "COMMENT" -> entityManager.createQuery("""
                    update CommentEntity comment
                       set comment.moderationStatus = :moderationStatus,
                           comment.riskLevel = :riskLevel,
                           comment.lastModerationEventId = :eventId
                     where comment.id = :targetId
                       and comment.deleted = false
                    """)
                    .setParameter("moderationStatus", moderationStatus)
                    .setParameter("riskLevel", riskLevel)
                    .setParameter("eventId", eventId)
                    .setParameter("targetId", numericId)
                    .executeUpdate();
            case "USER" -> userAccountJpaRepository.findByIdAndDeletedFalse(numericId)
                    .ifPresent(user -> user.setStatus(UserAccountStatus.valueOf(userStatus)));
            default -> throw new IllegalArgumentException("unsupported targetType: " + targetType);
        }
    }

    public List<ReviewQueueItemRow> findReviewQueue(String sourceType, int page, int size) {
        List<Long> eventIds = queryReviewQueueEventIds(sourceType, page, size);
        if (eventIds.isEmpty()) {
            return List.of();
        }
        Map<Long, ContentModerationEventEntity> events = loadModerationEventsByIds(eventIds);
        ReviewQueueContext context = buildReviewQueueContext(events.values());
        List<ReviewQueueItemRow> rows = new ArrayList<>();
        for (Long eventId : eventIds) {
            ContentModerationEventEntity event = events.get(eventId);
            if (event == null) {
                continue;
            }
            rows.add(toReviewQueueItemRow(event, context));
        }
        return rows;
    }

    public long countReviewQueue(String sourceType) {
        StringBuilder jpql = new StringBuilder(reviewQueueBaseCountJpql());
        if (StringUtils.hasText(sourceType)) {
            jpql.append(" and event.sourceType = :sourceType");
        }
        TypedQuery<Long> query = entityManager.createQuery(jpql.toString(), Long.class);
        if (StringUtils.hasText(sourceType)) {
            query.setParameter("sourceType", sourceType);
        }
        Long count = query.getSingleResult();
        return count == null ? 0L : count;
    }

    public Optional<ReviewQueueItemRow> findReviewQueueItem(long eventId) {
        Optional<ContentModerationEventEntity> optionalEvent = contentModerationEventJpaRepository.findById(eventId);
        if (optionalEvent.isEmpty()) {
            return Optional.empty();
        }
        ContentModerationEventEntity event = optionalEvent.get();
        ReviewQueueContext context = buildReviewQueueContext(List.of(event));
        if (!isActiveReviewQueueEvent(event, context)) {
            return Optional.empty();
        }
        return Optional.of(toReviewQueueItemRow(event, context));
    }

    public Optional<ReviewQueueDetailRow> findReviewQueueDetail(long eventId) {
        Optional<ContentModerationEventEntity> optionalEvent = contentModerationEventJpaRepository.findById(eventId);
        if (optionalEvent.isEmpty()) {
            return Optional.empty();
        }
        ContentModerationEventEntity event = optionalEvent.get();
        ReviewQueueContext context = buildReviewQueueContext(List.of(event));
        if (!isActiveReviewQueueEvent(event, context)) {
            return Optional.empty();
        }
        return Optional.of(toReviewQueueDetailRow(event, context));
    }

    public List<AuditLogRow> findAuditLogs(String targetType, String targetId, String actionType, String traceId, int page, int size) {
        StringBuilder jpql = new StringBuilder("""
                select log
                  from AuditLogEntity log
                 where 1 = 1
                """);
        Map<String, Object> params = new LinkedHashMap<>();
        appendAuditLogFilters(jpql, params, targetType, targetId, actionType, traceId);
        jpql.append(" order by log.createdAt desc, log.id desc");
        TypedQuery<AuditLogEntity> query = entityManager.createQuery(jpql.toString(), AuditLogEntity.class);
        applyParameters(query, params);
        query.setFirstResult((page - 1) * size);
        query.setMaxResults(size);
        List<AuditLogEntity> logs = query.getResultList();
        Map<Long, UserAccountEntity> operatorUsers = loadUsersByIds(extractAuditOperatorIds(logs));
        List<AuditLogRow> rows = new ArrayList<>();
        for (AuditLogEntity log : logs) {
            UserAccountEntity operator = operatorUsers.get(log.getOperatorUserId());
            rows.add(new AuditLogRow(
                    log.getId(),
                    log.getTraceId(),
                    log.getOperatorUserId(),
                    operator == null ? null : operator.getDisplayName(),
                    log.getActionType(),
                    log.getTargetType(),
                    log.getTargetId(),
                    log.getDetailJson(),
                    log.getCreatedAt()
            ));
        }
        return rows;
    }

    public long countAuditLogs(String targetType, String targetId, String actionType, String traceId) {
        StringBuilder jpql = new StringBuilder("""
                select count(log)
                  from AuditLogEntity log
                 where 1 = 1
                """);
        Map<String, Object> params = new LinkedHashMap<>();
        appendAuditLogFilters(jpql, params, targetType, targetId, actionType, traceId);
        return executeCountQuery(jpql.toString(), params);
    }

    @Transactional
    public void insertAuditLog(String traceId, long operatorUserId, String actionType, String targetType, String targetId, String detailJson) {
        entityManager.persist(AuditLogEntity.create(traceId, operatorUserId, actionType, targetType, targetId, detailJson));
    }

    private List<ContentReportEntity> findReportsByReporter(long reporterUserId, String status, int page, int size) {
        StringBuilder jpql = new StringBuilder("""
                select report
                  from ContentReportEntity report
                 where report.reporterUserId = :reporterUserId
                """);
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("reporterUserId", reporterUserId);
        if (StringUtils.hasText(status)) {
            jpql.append(" and report.status = :status");
            params.put("status", status);
        }
        jpql.append(" order by report.updatedAt desc, report.id desc");
        TypedQuery<ContentReportEntity> query = entityManager.createQuery(jpql.toString(), ContentReportEntity.class);
        applyParameters(query, params);
        query.setFirstResult((page - 1) * size);
        query.setMaxResults(size);
        return query.getResultList();
    }

    private List<ContentReportEntity> findAdminReportEntities(String status, String targetType, int page, int size) {
        StringBuilder jpql = new StringBuilder("""
                select report
                  from ContentReportEntity report
                 where 1 = 1
                """);
        Map<String, Object> params = new LinkedHashMap<>();
        if (StringUtils.hasText(status)) {
            jpql.append(" and report.status = :status");
            params.put("status", status);
        }
        if (StringUtils.hasText(targetType)) {
            jpql.append(" and report.targetType = :targetType");
            params.put("targetType", targetType);
        }
        jpql.append(" order by report.updatedAt desc, report.id desc");
        TypedQuery<ContentReportEntity> query = entityManager.createQuery(jpql.toString(), ContentReportEntity.class);
        applyParameters(query, params);
        query.setFirstResult((page - 1) * size);
        query.setMaxResults(size);
        return query.getResultList();
    }

    private ReportContext buildReportContext(List<ContentReportEntity> reports, boolean includeReporterUsers) {
        if (reports.isEmpty()) {
            return ReportContext.empty();
        }

        Set<Long> postIds = new LinkedHashSet<>();
        Set<Long> commentIds = new LinkedHashSet<>();
        Set<Long> targetUserIds = new LinkedHashSet<>();
        Set<Long> reporterUserIds = new LinkedHashSet<>();

        for (ContentReportEntity report : reports) {
            switch (report.getTargetType()) {
                case "POST" -> addIfPresent(postIds, parseLongOrNull(report.getTargetId()));
                case "COMMENT" -> addIfPresent(commentIds, parseLongOrNull(report.getTargetId()));
                case "USER" -> addIfPresent(targetUserIds, parseLongOrNull(report.getTargetId()));
                default -> {
                }
            }
            if (includeReporterUsers) {
                addIfPresent(reporterUserIds, report.getReporterUserId());
            }
        }

        Map<Long, PostEntity> posts = loadPostsByIds(postIds);
        Map<Long, CommentEntity> comments = loadCommentsByIds(commentIds);
        Set<Long> contextPostIds = new LinkedHashSet<>();
        for (CommentEntity comment : comments.values()) {
            addIfPresent(contextPostIds, comment.getPostId());
            addIfPresent(targetUserIds, comment.getUserId());
        }
        Map<Long, PostEntity> contextPosts = loadPostsByIds(contextPostIds);
        for (PostEntity post : posts.values()) {
            addIfPresent(targetUserIds, post.getUserId());
        }
        Map<Long, UserAccountEntity> targetUsers = loadUsersByIds(targetUserIds);
        Map<Long, UserAccountEntity> reporterUsers = includeReporterUsers ? loadUsersByIds(reporterUserIds) : Map.of();
        Map<String, Long> reportCounts = countReportsByTargets(reports);
        return new ReportContext(posts, comments, contextPosts, targetUsers, reporterUsers, reportCounts);
    }

    private String resolveContentPostId(ContentReportEntity report, ReportContext context) {
        return switch (report.getTargetType()) {
            case "POST" -> report.getTargetId();
            case "COMMENT" -> {
                CommentEntity comment = context.comments().get(parseLongOrNull(report.getTargetId()));
                yield comment == null ? null : String.valueOf(comment.getPostId());
            }
            default -> null;
        };
    }

    private String resolveMyReportContentTitle(ContentReportEntity report, ReportContext context) {
        return switch (report.getTargetType()) {
            case "POST" -> {
                PostEntity post = context.posts().get(parseLongOrNull(report.getTargetId()));
                yield post == null ? null : post.getTitle();
            }
            case "COMMENT" -> {
                CommentEntity comment = context.comments().get(parseLongOrNull(report.getTargetId()));
                PostEntity contextPost = comment == null ? null : context.contextPosts().get(comment.getPostId());
                yield contextPost == null ? null : contextPost.getTitle();
            }
            case "USER" -> {
                UserAccountEntity targetUser = context.targetUsers().get(parseLongOrNull(report.getTargetId()));
                yield targetUser == null ? null : targetUser.getDisplayName();
            }
            default -> null;
        };
    }

    private String resolveMyReportContentBody(ContentReportEntity report, ReportContext context) {
        return switch (report.getTargetType()) {
            case "POST" -> {
                PostEntity post = context.posts().get(parseLongOrNull(report.getTargetId()));
                yield post == null ? null : post.getContent();
            }
            case "COMMENT" -> {
                CommentEntity comment = context.comments().get(parseLongOrNull(report.getTargetId()));
                yield comment == null ? null : comment.getContent();
            }
            case "USER" -> {
                UserAccountEntity targetUser = context.targetUsers().get(parseLongOrNull(report.getTargetId()));
                yield targetUser == null ? null : "用户邮箱：" + safeText(targetUser.getEmail(), "—");
            }
            default -> null;
        };
    }

    private String resolveAdminReportListContentTitle(ContentReportEntity report, ReportContext context) {
        return switch (report.getTargetType()) {
            case "POST" -> {
                PostEntity post = context.posts().get(parseLongOrNull(report.getTargetId()));
                yield post == null ? null : post.getTitle();
            }
            case "COMMENT" -> {
                CommentEntity comment = context.comments().get(parseLongOrNull(report.getTargetId()));
                PostEntity contextPost = comment == null ? null : context.contextPosts().get(comment.getPostId());
                yield contextPost == null ? null : contextPost.getTitle();
            }
            default -> null;
        };
    }

    private String resolveAdminReportListContentBody(ContentReportEntity report, ReportContext context) {
        return switch (report.getTargetType()) {
            case "POST" -> {
                PostEntity post = context.posts().get(parseLongOrNull(report.getTargetId()));
                yield post == null ? null : post.getContent();
            }
            case "COMMENT" -> {
                CommentEntity comment = context.comments().get(parseLongOrNull(report.getTargetId()));
                yield comment == null ? null : comment.getContent();
            }
            default -> null;
        };
    }

    private String resolveAdminReportDetailContentTitle(ContentReportEntity report, ReportContext context) {
        if (!"USER".equals(report.getTargetType())) {
            return resolveMyReportContentTitle(report, context);
        }
        UserAccountEntity targetUser = context.targetUsers().get(parseLongOrNull(report.getTargetId()));
        return targetUser == null ? null : targetUser.getDisplayName();
    }

    private String resolveAdminReportDetailContentBody(ContentReportEntity report, ReportContext context) {
        if (!"USER".equals(report.getTargetType())) {
            return resolveReportBodyForPostOrComment(report, context);
        }
        UserAccountEntity targetUser = context.targetUsers().get(parseLongOrNull(report.getTargetId()));
        return targetUser == null ? null : "邮箱：" + safeText(targetUser.getEmail(), "—");
    }

    private String resolveReportBodyForPostOrComment(ContentReportEntity report, ReportContext context) {
        return switch (report.getTargetType()) {
            case "POST" -> {
                PostEntity post = context.posts().get(parseLongOrNull(report.getTargetId()));
                yield post == null ? null : post.getContent();
            }
            case "COMMENT" -> {
                CommentEntity comment = context.comments().get(parseLongOrNull(report.getTargetId()));
                yield comment == null ? null : comment.getContent();
            }
            default -> null;
        };
    }

    private Map<String, Long> countReportsByTargets(List<ContentReportEntity> reports) {
        LinkedHashSet<String> uniqueKeys = new LinkedHashSet<>();
        List<TargetQueryParam> params = new ArrayList<>();
        for (ContentReportEntity report : reports) {
            String key = targetKey(report.getTargetType(), report.getTargetId());
            if (uniqueKeys.add(key)) {
                params.add(new TargetQueryParam(report.getTargetType(), report.getTargetId()));
            }
        }
        if (params.isEmpty()) {
            return Map.of();
        }

        StringBuilder jpql = new StringBuilder("""
                select report.targetType,
                       report.targetId,
                       count(report)
                  from ContentReportEntity report
                 where
                """);
        for (int index = 0; index < params.size(); index++) {
            if (index > 0) {
                jpql.append(" or ");
            }
            jpql.append("(report.targetType = :targetType").append(index)
                    .append(" and report.targetId = :targetId").append(index)
                    .append(")");
        }
        jpql.append(" group by report.targetType, report.targetId");

        TypedQuery<Object[]> query = entityManager.createQuery(jpql.toString(), Object[].class);
        for (int index = 0; index < params.size(); index++) {
            query.setParameter("targetType" + index, params.get(index).targetType());
            query.setParameter("targetId" + index, params.get(index).targetId());
        }

        Map<String, Long> counts = new HashMap<>();
        for (Object[] row : query.getResultList()) {
            counts.put(targetKey((String) row[0], (String) row[1]), ((Number) row[2]).longValue());
        }
        return counts;
    }

    private List<Long> queryReviewQueueEventIds(String sourceType, int page, int size) {
        StringBuilder jpql = new StringBuilder(reviewQueueBaseIdsJpql());
        if (StringUtils.hasText(sourceType)) {
            jpql.append(" and event.sourceType = :sourceType");
        }
        jpql.append(" order by event.createdAt desc, event.id desc");
        TypedQuery<Long> query = entityManager.createQuery(jpql.toString(), Long.class);
        if (StringUtils.hasText(sourceType)) {
            query.setParameter("sourceType", sourceType);
        }
        query.setFirstResult((page - 1) * size);
        query.setMaxResults(size);
        return query.getResultList();
    }

    private String reviewQueueBaseIdsJpql() {
        return """
                select event.id
                  from ContentModerationEventEntity event
             left join PostEntity post
                    on event.targetType = 'POST'
                   and concat('', post.id) = event.targetId
             left join CommentEntity comment
                    on event.targetType = 'COMMENT'
                   and concat('', comment.id) = event.targetId
                 where event.action = 'REVIEW'
                   and (
                        event.sourceType = 'AI_OUTPUT'
                     or (event.targetType = 'POST' and post.deleted = false and post.moderationStatus = 'REVIEW' and post.lastModerationEventId = event.id)
                     or (event.targetType = 'COMMENT' and comment.deleted = false and comment.moderationStatus = 'REVIEW' and comment.lastModerationEventId = event.id)
                   )
                """;
    }

    private String reviewQueueBaseCountJpql() {
        return """
                select count(event)
                  from ContentModerationEventEntity event
             left join PostEntity post
                    on event.targetType = 'POST'
                   and concat('', post.id) = event.targetId
             left join CommentEntity comment
                    on event.targetType = 'COMMENT'
                   and concat('', comment.id) = event.targetId
                 where event.action = 'REVIEW'
                   and (
                        event.sourceType = 'AI_OUTPUT'
                     or (event.targetType = 'POST' and post.deleted = false and post.moderationStatus = 'REVIEW' and post.lastModerationEventId = event.id)
                     or (event.targetType = 'COMMENT' and comment.deleted = false and comment.moderationStatus = 'REVIEW' and comment.lastModerationEventId = event.id)
                   )
                """;
    }

    private Map<Long, ContentModerationEventEntity> loadModerationEventsByIds(List<Long> eventIds) {
        Map<Long, ContentModerationEventEntity> events = new HashMap<>();
        for (ContentModerationEventEntity entity : contentModerationEventJpaRepository.findAllById(eventIds)) {
            events.put(entity.getId(), entity);
        }
        return events;
    }

    private ReviewQueueContext buildReviewQueueContext(Collection<ContentModerationEventEntity> events) {
        if (events.isEmpty()) {
            return ReviewQueueContext.empty();
        }
        Set<Long> postIds = new LinkedHashSet<>();
        Set<Long> commentIds = new LinkedHashSet<>();

        for (ContentModerationEventEntity event : events) {
            if ("POST".equals(event.getTargetType())) {
                addIfPresent(postIds, parseLongOrNull(event.getTargetId()));
            } else if ("COMMENT".equals(event.getTargetType())) {
                addIfPresent(commentIds, parseLongOrNull(event.getTargetId()));
            }
        }

        Map<Long, PostEntity> posts = loadPostsByIds(postIds);
        Map<Long, CommentEntity> comments = loadCommentsByIds(commentIds);
        Set<Long> contextPostIds = new LinkedHashSet<>();
        Set<Long> authorUserIds = new LinkedHashSet<>();
        for (CommentEntity comment : comments.values()) {
            addIfPresent(contextPostIds, comment.getPostId());
            addIfPresent(authorUserIds, comment.getUserId());
        }
        for (PostEntity post : posts.values()) {
            addIfPresent(authorUserIds, post.getUserId());
        }
        Map<Long, PostEntity> contextPosts = loadPostsByIds(contextPostIds);
        Map<Long, UserAccountEntity> users = loadUsersByIds(authorUserIds);
        return new ReviewQueueContext(posts, comments, contextPosts, users);
    }

    private boolean isActiveReviewQueueEvent(ContentModerationEventEntity event, ReviewQueueContext context) {
        if (!"REVIEW".equals(event.getAction())) {
            return false;
        }
        if ("AI_OUTPUT".equals(event.getSourceType())) {
            return true;
        }
        if ("POST".equals(event.getTargetType())) {
            PostEntity post = context.posts().get(parseLongOrNull(event.getTargetId()));
            return post != null
                    && !post.isDeleted()
                    && "REVIEW".equals(post.getModerationStatus())
                    && Objects.equals(post.getLastModerationEventId(), event.getId());
        }
        if ("COMMENT".equals(event.getTargetType())) {
            CommentEntity comment = context.comments().get(parseLongOrNull(event.getTargetId()));
            return comment != null
                    && !comment.isDeleted()
                    && "REVIEW".equals(comment.getModerationStatus())
                    && Objects.equals(comment.getLastModerationEventId(), event.getId());
        }
        return false;
    }

    private ReviewQueueItemRow toReviewQueueItemRow(ContentModerationEventEntity event, ReviewQueueContext context) {
        return new ReviewQueueItemRow(
                event.getId(),
                event.getSourceType(),
                event.getTargetType(),
                resolveReviewTargetId(event),
                event.getRiskLevel(),
                event.getReasonCode(),
                resolveReviewPreview(event, context),
                event.getCreatedAt()
        );
    }

    private ReviewQueueDetailRow toReviewQueueDetailRow(ContentModerationEventEntity event, ReviewQueueContext context) {
        String contentTitle = null;
        String contentBody = "";
        String postId = null;
        String postTitle = null;
        String postBody = null;
        Long authorUserId = null;
        String authorDisplayName = null;
        String authorRole = null;

        if ("AI_OUTPUT".equals(event.getSourceType())) {
            contentBody = safeText(event.getMaskedText(), "");
        } else if ("POST".equals(event.getTargetType())) {
            PostEntity post = context.posts().get(parseLongOrNull(event.getTargetId()));
            if (post != null) {
                contentTitle = post.getTitle();
                contentBody = safeText(post.getContent(), "");
                postId = String.valueOf(post.getId());
                postTitle = post.getTitle();
                postBody = post.getContent();
                authorUserId = post.getUserId();
            }
        } else if ("COMMENT".equals(event.getTargetType())) {
            CommentEntity comment = context.comments().get(parseLongOrNull(event.getTargetId()));
            if (comment != null) {
                contentBody = safeText(comment.getContent(), "");
                PostEntity contextPost = context.contextPosts().get(comment.getPostId());
                postId = contextPost == null ? null : String.valueOf(contextPost.getId());
                postTitle = contextPost == null ? null : contextPost.getTitle();
                postBody = contextPost == null ? null : contextPost.getContent();
                contentTitle = postTitle;
                authorUserId = comment.getUserId();
            }
        }

        if (authorUserId != null) {
            UserAccountEntity author = context.users().get(authorUserId);
            if (author != null) {
                authorDisplayName = author.getDisplayName();
                authorRole = author.getRole().name();
            }
        }

        return new ReviewQueueDetailRow(
                event.getId(),
                event.getSourceType(),
                event.getTargetType(),
                resolveReviewTargetId(event),
                event.getRiskLevel(),
                event.getReasonCode(),
                resolveReviewPreview(event, context),
                contentTitle,
                contentBody,
                postId,
                postTitle,
                postBody,
                authorUserId,
                authorDisplayName,
                authorRole,
                event.getCreatedAt()
        );
    }

    private String resolveReviewTargetId(ContentModerationEventEntity event) {
        if (!"AI_OUTPUT".equals(event.getSourceType())) {
            return event.getTargetId();
        }
        return StringUtils.hasText(event.getTargetId()) ? event.getTargetId() : "TRACE:" + event.getTraceId();
    }

    private String resolveReviewPreview(ContentModerationEventEntity event, ReviewQueueContext context) {
        if ("AI_OUTPUT".equals(event.getSourceType())) {
            return truncate120(safeText(event.getMaskedText(), ""));
        }
        if ("POST".equals(event.getTargetType())) {
            PostEntity post = context.posts().get(parseLongOrNull(event.getTargetId()));
            return post == null ? "" : truncate120(safeText(post.getContent(), ""));
        }
        if ("COMMENT".equals(event.getTargetType())) {
            CommentEntity comment = context.comments().get(parseLongOrNull(event.getTargetId()));
            return comment == null ? "" : truncate120(safeText(comment.getContent(), ""));
        }
        return "";
    }

    private void appendAuditLogFilters(
            StringBuilder jpql,
            Map<String, Object> params,
            String targetType,
            String targetId,
            String actionType,
            String traceId
    ) {
        if (StringUtils.hasText(targetType)) {
            jpql.append(" and log.targetType = :targetType");
            params.put("targetType", targetType);
        }
        if (StringUtils.hasText(targetId)) {
            jpql.append(" and log.targetId = :targetId");
            params.put("targetId", targetId);
        }
        if (StringUtils.hasText(actionType)) {
            jpql.append(" and log.actionType = :actionType");
            params.put("actionType", actionType);
        }
        if (StringUtils.hasText(traceId)) {
            jpql.append(" and log.traceId = :traceId");
            params.put("traceId", traceId);
        }
    }

    private long executeCountQuery(String jpql, Map<String, Object> params) {
        TypedQuery<Long> query = entityManager.createQuery(jpql, Long.class);
        applyParameters(query, params);
        Long count = query.getSingleResult();
        return count == null ? 0L : count;
    }

    private <T> void applyParameters(TypedQuery<T> query, Map<String, Object> params) {
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            query.setParameter(entry.getKey(), entry.getValue());
        }
    }

    private Optional<CommentEntity> findReadableComment(Long commentId) {
        if (commentId == null) {
            return Optional.empty();
        }
        return communityCommentJpaRepository.findById(commentId)
                .filter(comment -> !comment.isDeleted());
    }

    private Map<Long, PostEntity> loadPostsByIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, PostEntity> result = new HashMap<>();
        for (PostEntity post : communityPostJpaRepository.findAllById(ids)) {
            if (!post.isDeleted()) {
                result.put(post.getId(), post);
            }
        }
        return result;
    }

    private Map<Long, CommentEntity> loadCommentsByIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, CommentEntity> result = new HashMap<>();
        for (CommentEntity comment : communityCommentJpaRepository.findAllById(ids)) {
            if (!comment.isDeleted()) {
                result.put(comment.getId(), comment);
            }
        }
        return result;
    }

    private Map<Long, UserAccountEntity> loadUsersByIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, UserAccountEntity> result = new HashMap<>();
        for (UserAccountEntity user : userAccountJpaRepository.findAllById(ids)) {
            if (!user.isDeleted()) {
                result.put(user.getId(), user);
            }
        }
        return result;
    }

    private Set<Long> extractOperatorUserIds(List<ContentReportActionEntity> actions) {
        Set<Long> userIds = new LinkedHashSet<>();
        for (ContentReportActionEntity action : actions) {
            addIfPresent(userIds, action.getOperatorUserId());
        }
        return userIds;
    }

    private Set<Long> extractAuditOperatorIds(List<AuditLogEntity> logs) {
        Set<Long> userIds = new LinkedHashSet<>();
        for (AuditLogEntity log : logs) {
            addIfPresent(userIds, log.getOperatorUserId());
        }
        return userIds;
    }

    private void addIfPresent(Set<Long> values, Long value) {
        if (values != null && value != null && value > 0) {
            values.add(value);
        }
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

    private String targetKey(String targetType, String targetId) {
        return targetType + "::" + targetId;
    }

    private String safeText(String value, String fallback) {
        return value == null ? fallback : value;
    }

    private String truncate120(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.length() <= 120 ? value : value.substring(0, 120);
    }

    private record TargetQueryParam(String targetType, String targetId) {
    }

    private record ReportContext(
            Map<Long, PostEntity> posts,
            Map<Long, CommentEntity> comments,
            Map<Long, PostEntity> contextPosts,
            Map<Long, UserAccountEntity> targetUsers,
            Map<Long, UserAccountEntity> reporterUsers,
            Map<String, Long> reportCounts
    ) {
        private static ReportContext empty() {
            return new ReportContext(Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of());
        }
    }

    private record ReviewQueueContext(
            Map<Long, PostEntity> posts,
            Map<Long, CommentEntity> comments,
            Map<Long, PostEntity> contextPosts,
            Map<Long, UserAccountEntity> users
    ) {
        private static ReviewQueueContext empty() {
            return new ReviewQueueContext(Map.of(), Map.of(), Map.of(), Map.of());
        }
    }

    public record ExistingReportRow(long reportId, String status) {
    }

    public record MyReportRow(
            long reportId,
            String targetType,
            String targetId,
            String contentPostId,
            String contentTitle,
            String contentBody,
            String reasonCode,
            String reportDetail,
            String status,
            String latestAction,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    public record AdminReportRow(
            long reportId,
            String targetType,
            String targetId,
            String contentPostId,
            String contentTitle,
            String contentBody,
            String reasonCode,
            String status,
            String latestAction,
            long reportCount,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    public record AdminReportDetailRow(
            long reportId,
            long reporterUserId,
            String reporterDisplayName,
            String targetType,
            String targetId,
            String contentPostId,
            String contentTitle,
            String contentBody,
            String reasonCode,
            String reportDetail,
            String status,
            String latestAction,
            long reportCount,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    public record ReportDecisionTargetRow(long reportId, String targetType, String targetId, String status) {
    }

    public record CommunityNotificationTargetRow(
            long ownerUserId,
            String postId,
            String postTitle
    ) {
    }

    public record ReportActionRow(
            long actionId,
            long operatorUserId,
            String operatorDisplayName,
            String decision,
            String action,
            String comment,
            Instant createdAt
    ) {
    }

    public record AuditLogRow(
            long auditLogId,
            String traceId,
            long operatorUserId,
            String operatorDisplayName,
            String actionType,
            String targetType,
            String targetId,
            String detailJson,
            Instant createdAt
    ) {
    }

    public record TargetStateRow(String targetType, String status, String riskLevel) {
    }

    public record ReviewQueueItemRow(
            long eventId,
            String sourceType,
            String targetType,
            String targetId,
            String riskLevel,
            String reasonCode,
            String preview,
            Instant createdAt
    ) {
    }

    public record ReviewQueueDetailRow(
            long eventId,
            String sourceType,
            String targetType,
            String targetId,
            String riskLevel,
            String reasonCode,
            String preview,
            String contentTitle,
            String contentBody,
            String postId,
            String postTitle,
            String postBody,
            Long authorUserId,
            String authorDisplayName,
            String authorRole,
            Instant createdAt
    ) {
    }
}
