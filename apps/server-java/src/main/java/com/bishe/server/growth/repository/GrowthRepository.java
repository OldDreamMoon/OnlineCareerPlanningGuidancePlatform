package com.bishe.server.growth.repository;

import com.bishe.server.ai.gateway.task.jpa.AiAsyncTaskJobJpaRepository;
import com.bishe.server.ai.quota.jpa.AiCallLogJpaRepository;
import com.bishe.server.auth.model.UserRole;
import com.bishe.server.auth.repository.jpa.UserAccountJpaRepository;
import com.bishe.server.growth.repository.jpa.DailyTaskJpaRepository;
import com.bishe.server.growth.repository.jpa.GrowthCheckinJpaRepository;
import com.bishe.server.growth.repository.jpa.GrowthCheckinRewardRuleJpaRepository;
import com.bishe.server.growth.repository.jpa.PointsLedgerJpaRepository;
import com.bishe.server.growth.repository.jpa.entity.DailyTaskEntity;
import com.bishe.server.growth.repository.jpa.entity.GrowthCheckinEntity;
import com.bishe.server.growth.repository.jpa.entity.GrowthCheckinRewardRuleEntity;
import com.bishe.server.growth.repository.jpa.entity.PointsLedgerEntity;
import com.bishe.server.profile.repository.jpa.CommentJpaRepository;
import com.bishe.server.profile.repository.jpa.PostJpaRepository;
import com.bishe.server.skill.repository.jpa.SkillProgressJpaRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 成长中心仓储：签到、每日任务、积分账本。
 */
@Repository
public class GrowthRepository {

    private static final ZoneId SYSTEM_ZONE = ZoneId.systemDefault();

    private final UserAccountJpaRepository userAccountJpaRepository;
    private final GrowthCheckinJpaRepository growthCheckinJpaRepository;
    private final DailyTaskJpaRepository dailyTaskJpaRepository;
    private final PointsLedgerJpaRepository pointsLedgerJpaRepository;
    private final GrowthCheckinRewardRuleJpaRepository growthCheckinRewardRuleJpaRepository;
    private final AiCallLogJpaRepository aiCallLogJpaRepository;
    private final AiAsyncTaskJobJpaRepository aiAsyncTaskJobJpaRepository;
    private final SkillProgressJpaRepository skillProgressJpaRepository;
    private final PostJpaRepository postJpaRepository;
    private final CommentJpaRepository commentJpaRepository;

    public GrowthRepository(
            UserAccountJpaRepository userAccountJpaRepository,
            GrowthCheckinJpaRepository growthCheckinJpaRepository,
            DailyTaskJpaRepository dailyTaskJpaRepository,
            PointsLedgerJpaRepository pointsLedgerJpaRepository,
            GrowthCheckinRewardRuleJpaRepository growthCheckinRewardRuleJpaRepository,
            AiCallLogJpaRepository aiCallLogJpaRepository,
            AiAsyncTaskJobJpaRepository aiAsyncTaskJobJpaRepository,
            SkillProgressJpaRepository skillProgressJpaRepository,
            PostJpaRepository postJpaRepository,
            CommentJpaRepository commentJpaRepository
    ) {
        this.userAccountJpaRepository = userAccountJpaRepository;
        this.growthCheckinJpaRepository = growthCheckinJpaRepository;
        this.dailyTaskJpaRepository = dailyTaskJpaRepository;
        this.pointsLedgerJpaRepository = pointsLedgerJpaRepository;
        this.growthCheckinRewardRuleJpaRepository = growthCheckinRewardRuleJpaRepository;
        this.aiCallLogJpaRepository = aiCallLogJpaRepository;
        this.aiAsyncTaskJobJpaRepository = aiAsyncTaskJobJpaRepository;
        this.skillProgressJpaRepository = skillProgressJpaRepository;
        this.postJpaRepository = postJpaRepository;
        this.commentJpaRepository = commentJpaRepository;
    }

    public boolean existsStudentUser(long userId) {
        return userAccountJpaRepository.existsByIdAndRoleAndDeletedFalse(userId, UserRole.STUDENT);
    }

    public void lockStudentUser(long userId) {
        userAccountJpaRepository.lockIdByIdAndRoleAndDeletedFalse(userId, UserRole.STUDENT);
    }

    public boolean existsCheckin(long userId, LocalDate checkinDate) {
        return growthCheckinJpaRepository.existsCheckin(userId, checkinDate.toString());
    }

    public Optional<LocalDate> findStudentJourneyStartDate(long userId) {
        return userAccountJpaRepository.findByIdAndRoleAndDeletedFalse(userId, UserRole.STUDENT)
                .map(entity -> entity.getCreatedAt().atZone(SYSTEM_ZONE).toLocalDate());
    }

    public Optional<CheckinRow> findLatestCheckin(long userId) {
        return growthCheckinJpaRepository.findLatestCheckin(userId).stream()
                .findFirst()
                .map(this::toCheckinRow);
    }

    public List<CheckinRow> findCheckinsBetween(long userId, LocalDate startDate, LocalDate endDate) {
        return growthCheckinJpaRepository.findCheckinsBetween(
                        userId,
                        startDate.toString(),
                        endDate.toString()
                )
                .stream()
                .map(this::toCheckinRow)
                .toList();
    }

    public void insertCheckin(long userId, LocalDate checkinDate, int streakCount, int pointsEarned) {
        growthCheckinJpaRepository.insertCheckin(
                userId,
                checkinDate.toString(),
                streakCount,
                pointsEarned
        );
    }

    public List<DailyTaskRow> findActiveDailyTasks() {
        return dailyTaskJpaRepository.findByActiveTrueOrderBySortOrderAscIdAsc().stream()
                .map(this::toDailyTaskRow)
                .toList();
    }

    public Optional<DailyTaskRow> findActiveDailyTask(long taskId) {
        return dailyTaskJpaRepository.findByIdAndActiveTrue(taskId).map(this::toDailyTaskRow);
    }

    public boolean hasLedgerReasonOnDate(long userId, String reasonCode, LocalDate targetDate) {
        Instant startAt = startOfDay(targetDate);
        Instant endAt = endOfDay(targetDate);
        return pointsLedgerJpaRepository.existsByStudentUserIdAndReasonCodeAndCreatedAtBetween(
                userId,
                reasonCode,
                startAt,
                endAt
        );
    }

    public Set<String> findLedgerReasonsOnDate(long userId, Collection<String> reasonCodes, LocalDate targetDate) {
        if (reasonCodes.isEmpty()) {
            return Set.of();
        }
        Instant startAt = startOfDay(targetDate);
        Instant endAt = endOfDay(targetDate);
        return new LinkedHashSet<>(
                pointsLedgerJpaRepository.findDistinctReasonCodesForDay(userId, reasonCodes, startAt, endAt)
        );
    }

    public boolean hasResumeOptimizeActivityOnDate(long userId, LocalDate targetDate) {
        Instant startAt = startOfDay(targetDate);
        Instant endAt = endOfDay(targetDate);
        return aiCallLogJpaRepository.countByUserIdAndTaskTypeAndStatusAndUserDeletedAtIsNullAndCreatedAtBetween(
                userId,
                "RESUME",
                "SUCCESS",
                startAt,
                endAt
        ) > 0
                || aiAsyncTaskJobJpaRepository.countByUserIdAndTaskTypeAndCreatedAtBetween(
                userId,
                "RESUME",
                startAt,
                endAt
        ) > 0;
    }

    public boolean hasSkillProgressActivityOnDate(long userId, LocalDate targetDate) {
        Instant startAt = startOfDay(targetDate);
        Instant endAt = endOfDay(targetDate);
        return skillProgressJpaRepository.countByStudentUserIdAndUpdatedAtBetween(userId, startAt, endAt) > 0;
    }

    public boolean hasCommunityInteractionOnDate(long userId, LocalDate targetDate) {
        Instant startAt = startOfDay(targetDate);
        Instant endAt = endOfDay(targetDate);
        return postJpaRepository.existsByUserIdAndDeletedFalseAndCreatedAtBetween(userId, startAt, endAt)
                || commentJpaRepository.existsByUserIdAndDeletedFalseAndCreatedAtBetween(userId, startAt, endAt);
    }

    public int getCurrentBalance(long userId) {
        return pointsLedgerJpaRepository.findFirstByStudentUserIdOrderByIdDesc(userId)
                .map(PointsLedgerEntity::getBalanceAfter)
                .orElse(0);
    }

    public int appendPointsLedger(long userId, int deltaPoints, String reasonCode) {
        int nextBalance = getCurrentBalance(userId) + deltaPoints;
        pointsLedgerJpaRepository.saveAndFlush(
                PointsLedgerEntity.create(userId, deltaPoints, reasonCode, nextBalance)
        );
        return nextBalance;
    }

    public List<PointsLedgerRow> findLedgerRecords(long userId, int limit, int offset) {
        int safeLimit = Math.max(limit, 1);
        int safeOffset = Math.max(offset, 0);
        PageRequest pageRequest = PageRequest.of(safeOffset / safeLimit, safeLimit);
        return pointsLedgerJpaRepository.findLedgerRecords(userId, pageRequest).stream()
                .map(this::toPointsLedgerRow)
                .toList();
    }

    public long countLedgerRecords(long userId) {
        return pointsLedgerJpaRepository.countByStudentUserId(userId);
    }

    public List<CheckinRewardRuleRow> findEnabledCheckinRewardRules() {
        return growthCheckinRewardRuleJpaRepository.findByEnabledTrueOrderByStreakDaysAscSortOrderAscIdAsc()
                .stream()
                .map(this::toCheckinRewardRuleRow)
                .toList();
    }

    private CheckinRow toCheckinRow(GrowthCheckinEntity entity) {
        return new CheckinRow(
                entity.getCheckinDate(),
                entity.getStreakCount(),
                entity.getPointsEarned()
        );
    }

    private DailyTaskRow toDailyTaskRow(DailyTaskEntity entity) {
        return new DailyTaskRow(
                entity.getId(),
                entity.getTaskCode(),
                entity.getTitle(),
                entity.getDescription(),
                entity.getPoints(),
                entity.getSortOrder()
        );
    }

    private PointsLedgerRow toPointsLedgerRow(PointsLedgerEntity entity) {
        return new PointsLedgerRow(
                entity.getDeltaPoints(),
                entity.getReasonCode(),
                entity.getBalanceAfter(),
                entity.getCreatedAt()
        );
    }

    private CheckinRewardRuleRow toCheckinRewardRuleRow(GrowthCheckinRewardRuleEntity entity) {
        return new CheckinRewardRuleRow(
                entity.getRewardCode(),
                entity.getStreakDays(),
                entity.getBonusPoints(),
                entity.getRewardTitle(),
                entity.getRewardDescription(),
                entity.getSortOrder()
        );
    }

    private Instant startOfDay(LocalDate targetDate) {
        return targetDate.atStartOfDay(SYSTEM_ZONE).toInstant();
    }

    private Instant endOfDay(LocalDate targetDate) {
        return targetDate.plusDays(1).atStartOfDay(SYSTEM_ZONE).minusNanos(1).toInstant();
    }

    public record CheckinRow(
            LocalDate checkinDate,
            int streakCount,
            int pointsEarned
    ) {
    }

    public record DailyTaskRow(
            long id,
            String taskCode,
            String title,
            String description,
            int points,
            int sortOrder
    ) {
    }

    public record PointsLedgerRow(
            int deltaPoints,
            String reasonCode,
            int balanceAfter,
            Instant createdAt
    ) {
    }

    public record CheckinRewardRuleRow(
            String rewardCode,
            int streakDays,
            int bonusPoints,
            String rewardTitle,
            String rewardDescription,
            int sortOrder
    ) {
    }
}
