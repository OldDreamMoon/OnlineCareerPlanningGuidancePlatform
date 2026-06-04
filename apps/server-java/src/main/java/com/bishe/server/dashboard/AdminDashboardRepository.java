package com.bishe.server.dashboard;

import com.bishe.server.auth.model.UserRole;
import com.bishe.server.consult.repository.jpa.ConsultOrderJpaRepository;
import com.bishe.server.dashboard.jpa.AdminDashboardAiCallJpaRepository;
import com.bishe.server.dashboard.jpa.AdminDashboardCommunityJpaRepository;
import com.bishe.server.dashboard.jpa.AdminDashboardStudentPortraitJpaRepository;
import com.bishe.server.dashboard.jpa.AdminDashboardUserJpaRepository;
import com.bishe.server.governance.jpa.ContentModerationEventJpaRepository;
import com.bishe.server.governance.jpa.ContentReportJpaRepository;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 管理端数据看板仓储：聚合用户、AI、咨询、社区与治理侧的运营指标原始数据。
 */
@Repository
public class AdminDashboardRepository {

    private final AdminDashboardUserJpaRepository userRepository;
    private final AdminDashboardAiCallJpaRepository aiCallRepository;
    private final ConsultOrderJpaRepository consultOrderRepository;
    private final AdminDashboardCommunityJpaRepository communityRepository;
    private final ContentModerationEventJpaRepository moderationEventRepository;
    private final ContentReportJpaRepository contentReportRepository;
    private final AdminDashboardStudentPortraitJpaRepository studentPortraitRepository;

    public AdminDashboardRepository(
            AdminDashboardUserJpaRepository userRepository,
            AdminDashboardAiCallJpaRepository aiCallRepository,
            ConsultOrderJpaRepository consultOrderRepository,
            AdminDashboardCommunityJpaRepository communityRepository,
            ContentModerationEventJpaRepository moderationEventRepository,
            ContentReportJpaRepository contentReportRepository,
            AdminDashboardStudentPortraitJpaRepository studentPortraitRepository
    ) {
        this.userRepository = userRepository;
        this.aiCallRepository = aiCallRepository;
        this.consultOrderRepository = consultOrderRepository;
        this.communityRepository = communityRepository;
        this.moderationEventRepository = moderationEventRepository;
        this.contentReportRepository = contentReportRepository;
        this.studentPortraitRepository = studentPortraitRepository;
    }

    public List<StudentLifecycleRow> findStudentLifecycleRows(Timestamp startAt, Timestamp endAt) {
        Instant start = toInstant(startAt);
        Instant end = toInstant(endAt);
        return userRepository.findStudentLifecycle(UserRole.STUDENT, start, end).stream()
                .map(view -> new StudentLifecycleRow(
                        defaultLong(view.getUserId()),
                        view.getCreatedAt(),
                        view.getLastLoginAt()
                ))
                .toList();
    }

    public Map<Long, Instant> findFirstSuccessfulAiCallAt(Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        List<Long> normalizedIds = userIds.stream().filter(Objects::nonNull).distinct().toList();
        if (normalizedIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, Instant> result = new HashMap<>();
        aiCallRepository.findFirstSuccessfulAiCallAt(normalizedIds).forEach(view -> result.put(
                defaultLong(view.getUserId()),
                view.getFirstSuccessAt()
        ));
        return result;
    }

    public List<Long> findActiveStudentIds(Timestamp startAt, Timestamp endAt) {
        Instant start = toInstant(startAt);
        Instant end = toInstant(endAt);
        return userRepository.findActiveStudentIds(UserRole.STUDENT, start, end);
    }

    public List<Long> findPaidConsultStudentIds(Timestamp startAt, Timestamp endAt) {
        Instant start = toInstant(startAt);
        Instant end = toInstant(endAt);
        return consultOrderRepository.findPaidStudentUserIds(start, end);
    }

    public AiCallSummaryRow summarizeAiCalls(Timestamp startAt, Timestamp endAt) {
        AdminDashboardAiCallJpaRepository.AiCallSummaryView summary = aiCallRepository.summarizeAiCalls(
                toInstant(startAt),
                toInstant(endAt)
        );
        return new AiCallSummaryRow(summary.getTotalCount(), summary.getSuccessCount());
    }

    public CommunityCoverageRow summarizeCommunityAiCoverage(Timestamp startAt, Timestamp endAt) {
        Instant start = toInstant(startAt);
        Instant end = toInstant(endAt);
        return new CommunityCoverageRow(
                communityRepository.countVisiblePosts(start, end),
                communityRepository.countAiCoveredPosts(start, end)
        );
    }

    public ModerationSummaryRow summarizeModeration(Timestamp startAt, Timestamp endAt) {
        ContentModerationEventJpaRepository.ModerationSummaryView summary = moderationEventRepository.summarizeModeration(
                toInstant(startAt),
                toInstant(endAt)
        );
        return new ModerationSummaryRow(summary.getTotalCount(), summary.getBlockedCount());
    }

    public List<Double> listClosedReportDurationsHours(Timestamp startAt, Timestamp endAt) {
        Instant start = toInstant(startAt);
        Instant end = toInstant(endAt);
        return contentReportRepository.findClosedReportDurations(start, end).stream()
                .map(view -> toDurationHours(view.getCreatedAt(), view.getClosedAt()))
                .toList();
    }

    public long countTotalStudents() {
        return userRepository.countByRoleAndDeletedFalse(UserRole.STUDENT);
    }

    public long countPortraitCompletedStudents() {
        return studentPortraitRepository.countCompletedStudentPortraits(UserRole.STUDENT);
    }

    public long countActiveStudentsSince(Timestamp startAt) {
        return userRepository.countActiveStudentsSince(UserRole.STUDENT, toInstant(startAt));
    }

    private long defaultLong(Long value) {
        return value == null ? 0L : value;
    }

    private double toDurationHours(Instant createdAt, Instant closedAt) {
        if (createdAt == null || closedAt == null || closedAt.isBefore(createdAt)) {
            return 0D;
        }
        long seconds = java.time.Duration.between(createdAt, closedAt).getSeconds();
        return seconds / 3600D;
    }

    private static Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    public record StudentLifecycleRow(
            long userId,
            Instant createdAt,
            Instant lastLoginAt
    ) {
    }

    public record AiCallSummaryRow(
            long totalCount,
            long successCount
    ) {
    }

    public record CommunityCoverageRow(
            long totalPosts,
            long aiCoveredPosts
    ) {
    }

    public record ModerationSummaryRow(
            long totalCount,
            long blockedCount
    ) {
    }
}
