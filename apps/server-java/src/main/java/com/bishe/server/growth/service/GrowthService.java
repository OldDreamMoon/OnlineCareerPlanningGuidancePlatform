package com.bishe.server.growth.service;

import com.bishe.server.common.TimePayloads;
import com.bishe.server.common.exception.ApiException;
import com.bishe.server.growth.dto.AdminGrantPointsResponse;
import com.bishe.server.growth.dto.DailyTaskCompleteResponse;
import com.bishe.server.growth.dto.DailyTaskItemResponse;
import com.bishe.server.growth.dto.GrowthCheckinResponse;
import com.bishe.server.growth.dto.GrowthCheckinOverviewResponse;
import com.bishe.server.growth.dto.PointsLedgerResponse;
import com.bishe.server.growth.repository.GrowthRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 成长中心服务：签到、每日任务、积分账本。
 */
@Service
public class GrowthService {

    private static final Logger log = LoggerFactory.getLogger(GrowthService.class);
    private static final int CHECKIN_BASE_POINTS = 10;
    private static final String TEST_GRANT_REASON_CODE = "TEST_GRANT";
    private static final String CHECKIN_REASON_CODE = "CHECKIN";
    private static final String TASK_RESUME_OPTIMIZE = "TASK_RESUME_OPTIMIZE";
    private static final String TASK_SKILL_PROGRESS = "TASK_SKILL_PROGRESS";
    private static final String TASK_COMMUNITY_INTERACT = "TASK_COMMUNITY_INTERACT";

    private final GrowthRepository growthRepository;
    private final GrowthCenterCacheService growthCenterCacheService;

    public GrowthService(GrowthRepository growthRepository, GrowthCenterCacheService growthCenterCacheService) {
        this.growthRepository = growthRepository;
        this.growthCenterCacheService = growthCenterCacheService;
    }

    @Transactional
    public GrowthCheckinResponse checkin(long userId) {
        ensureStudentExists(userId);

        LocalDate today = LocalDate.now();
        if (growthRepository.existsCheckin(userId, today)) {
            throw new ApiException("BIZ-1301", "already checked in today", HttpStatus.BAD_REQUEST);
        }

        int streak = growthRepository.findLatestCheckin(userId)
                .map(last -> last.checkinDate().equals(today.minusDays(1)) ? last.streakCount() + 1 : 1)
                .orElse(1);
        Optional<GrowthRepository.CheckinRewardRuleRow> triggeredReward = growthRepository.findEnabledCheckinRewardRules().stream()
                .filter(rule -> rule.streakDays() == streak)
                .findFirst();
        int bonusPoints = triggeredReward.map(GrowthRepository.CheckinRewardRuleRow::bonusPoints).orElse(0);
        int totalPointsEarned = CHECKIN_BASE_POINTS + bonusPoints;

        growthRepository.insertCheckin(userId, today, streak, totalPointsEarned);
        int newBalance = growthRepository.appendPointsLedger(userId, CHECKIN_BASE_POINTS, CHECKIN_REASON_CODE);
        if (bonusPoints > 0 && triggeredReward.isPresent()) {
            newBalance = growthRepository.appendPointsLedger(userId, bonusPoints, triggeredReward.get().rewardCode());
        }
        evictCheckinOverviewCache(userId);
        evictPointsLedgerSummaryCache(userId);

        GrowthCheckinResponse.RewardPayload rewardPayload = triggeredReward
                .map(rule -> new GrowthCheckinResponse.RewardPayload(
                        rule.rewardCode(),
                        rule.rewardTitle(),
                        rule.rewardDescription(),
                        rule.streakDays(),
                        rule.bonusPoints()
                ))
                .orElse(null);

        return new GrowthCheckinResponse(
                streak,
                totalPointsEarned,
                today.toString(),
                CHECKIN_BASE_POINTS,
                bonusPoints,
                newBalance,
                rewardPayload
        );
    }

    public GrowthCheckinOverviewResponse getCheckinOverview(long userId) {
        ensureStudentExists(userId);
        LocalDate today = LocalDate.now();
        return growthCenterCacheService.getCheckinOverview(
                userId,
                today,
                () -> buildCheckinOverview(userId, today)
        );
    }

    private GrowthCheckinOverviewResponse buildCheckinOverview(long userId, LocalDate today) {
        YearMonth currentMonth = YearMonth.from(today);
        LocalDate monthStart = currentMonth.atDay(1);
        LocalDate monthEnd = currentMonth.atEndOfMonth();
        int growthJourneyDays = growthRepository.findStudentJourneyStartDate(userId)
                .map(startDate -> Math.max((int) ChronoUnit.DAYS.between(startDate, today) + 1, 1))
                .orElse(1);

        List<GrowthRepository.CheckinRow> monthCheckins = growthRepository.findCheckinsBetween(userId, monthStart, monthEnd);
        List<Integer> checkedInDays = monthCheckins.stream()
                .map(checkin -> checkin.checkinDate().getDayOfMonth())
                .toList();
        Optional<GrowthRepository.CheckinRow> latestCheckin = growthRepository.findLatestCheckin(userId);
        int currentStreak = latestCheckin
                .map(checkin -> resolveActiveStreak(checkin, today))
                .orElse(0);
        boolean signedInToday = latestCheckin
                .map(checkin -> checkin.checkinDate().equals(today))
                .orElse(false);
        List<GrowthRepository.CheckinRewardRuleRow> rewardRuleRows = growthRepository.findEnabledCheckinRewardRules();
        List<GrowthCheckinOverviewResponse.RewardRuleItem> rewardRules = rewardRuleRows.stream()
                .map(rule -> new GrowthCheckinOverviewResponse.RewardRuleItem(
                        rule.rewardCode(),
                        rule.rewardTitle(),
                        rule.rewardDescription(),
                        rule.streakDays(),
                        rule.bonusPoints()
                ))
                .toList();
        GrowthCheckinOverviewResponse.NextRewardItem nextReward = rewardRuleRows.stream()
                .filter(rule -> rule.streakDays() > currentStreak)
                .findFirst()
                .map(rule -> new GrowthCheckinOverviewResponse.NextRewardItem(
                        rule.rewardCode(),
                        rule.rewardTitle(),
                        rule.rewardDescription(),
                        rule.streakDays(),
                        rule.bonusPoints(),
                        rule.streakDays() - currentStreak
                ))
                .orElse(null);

        return new GrowthCheckinOverviewResponse(
                signedInToday,
                growthJourneyDays,
                currentStreak,
                latestCheckin.map(checkin -> checkin.checkinDate().toString()).orElse(null),
                currentMonth.toString(),
                currentMonth.lengthOfMonth(),
                checkedInDays,
                CHECKIN_BASE_POINTS,
                rewardRules,
                nextReward
        );
    }

    @Transactional
    public List<DailyTaskItemResponse> getDailyTasks(long userId) {
        ensureStudentExists(userId);
        LocalDate today = LocalDate.now();
        return growthCenterCacheService.getDailyTasks(
                userId,
                today,
                () -> buildDailyTasks(userId, today)
        );
    }

    private List<DailyTaskItemResponse> buildDailyTasks(long userId, LocalDate today) {
        List<GrowthRepository.DailyTaskRow> tasks = growthRepository.findActiveDailyTasks();
        if (syncAutoCompletedDailyTasks(userId, tasks, today)) {
            evictPointsLedgerSummaryCache(userId);
        }
        Set<String> completedReasonCodes = growthRepository.findLedgerReasonsOnDate(
                userId,
                tasks.stream().map(GrowthRepository.DailyTaskRow::taskCode).toList(),
                today
        );

        return tasks.stream()
                .map(task -> new DailyTaskItemResponse(
                        task.id(),
                        task.taskCode(),
                        task.title(),
                        task.description(),
                        task.points(),
                        completedReasonCodes.contains(task.taskCode())
                ))
                .toList();
    }

    @Transactional
    public DailyTaskCompleteResponse completeDailyTask(long userId, long taskId) {
        ensureStudentExists(userId);

        GrowthRepository.DailyTaskRow task = growthRepository.findActiveDailyTask(taskId)
                .orElseThrow(() -> new ApiException("BIZ-1002", "daily task not found", HttpStatus.NOT_FOUND));
        growthRepository.lockStudentUser(userId);
        if (growthRepository.hasLedgerReasonOnDate(userId, task.taskCode(), LocalDate.now())) {
            throw new ApiException("BIZ-1302", "daily task already completed", HttpStatus.BAD_REQUEST);
        }

        int newBalance = growthRepository.appendPointsLedger(userId, task.points(), task.taskCode());
        evictDailyTasksCache(userId);
        evictPointsLedgerSummaryCache(userId);
        return new DailyTaskCompleteResponse(task.points(), newBalance);
    }

    @Transactional
    public AdminGrantPointsResponse grantPointsForTesting(long userId, int points, String reasonCode) {
        ensureStudentExists(userId);

        String normalizedReasonCode = normalizeGrantReasonCode(reasonCode);
        int newBalance = growthRepository.appendPointsLedger(userId, points, normalizedReasonCode);
        evictDailyTasksCache(userId);
        evictPointsLedgerSummaryCache(userId);
        log.info(
                "growth points granted for testing userId={}, deltaPoints={}, reasonCode={}, newBalance={}",
                userId,
                points,
                normalizedReasonCode,
                newBalance
        );
        return new AdminGrantPointsResponse(userId, points, newBalance, normalizedReasonCode);
    }

    @Transactional
    public PointsLedgerResponse getPointsLedger(long userId, int page, int size) {
        ensureStudentExists(userId);
        if (syncAutoCompletedDailyTasks(userId, growthRepository.findActiveDailyTasks(), LocalDate.now())) {
            evictDailyTasksCache(userId);
            evictPointsLedgerSummaryCache(userId);
        }

        LocalDate today = LocalDate.now();
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 50);
        int offset = (safePage - 1) * safeSize;

        GrowthCenterCacheService.PointsLedgerSummarySnapshot summary = growthCenterCacheService.getPointsLedgerSummary(
                userId,
                today,
                () -> buildPointsLedgerSummary(userId)
        );
        List<PointsLedgerResponse.LedgerRecordItem> records = growthRepository.findLedgerRecords(userId, safeSize, offset).stream()
                .map(row -> new PointsLedgerResponse.LedgerRecordItem(
                        row.deltaPoints(),
                        row.reasonCode(),
                        row.balanceAfter(),
                        TimePayloads.toEpochMillis(row.createdAt())
                ))
                .toList();
        return new PointsLedgerResponse(summary.balance(), records, summary.total());
    }

    private String normalizeGrantReasonCode(String reasonCode) {
        if (reasonCode == null || reasonCode.isBlank()) {
            return TEST_GRANT_REASON_CODE;
        }
        String normalized = reasonCode.trim().toUpperCase(java.util.Locale.ROOT);
        if (!normalized.matches("[A-Z0-9_]{3,50}")) {
            throw new ApiException("BIZ-1001", "reasonCode invalid", HttpStatus.BAD_REQUEST);
        }
        return normalized;
    }

    private void ensureStudentExists(long userId) {
        if (!growthRepository.existsStudentUser(userId)) {
            throw new ApiException("BIZ-1002", "student not found", HttpStatus.NOT_FOUND);
        }
    }

    private boolean syncAutoCompletedDailyTasks(long userId, List<GrowthRepository.DailyTaskRow> tasks, LocalDate targetDate) {
        if (tasks.isEmpty()) {
            return false;
        }

        growthRepository.lockStudentUser(userId);
        Set<String> completedReasonCodes = growthRepository.findLedgerReasonsOnDate(
                userId,
                tasks.stream().map(GrowthRepository.DailyTaskRow::taskCode).toList(),
                targetDate
        );
        boolean autoCompleted = false;

        for (GrowthRepository.DailyTaskRow task : tasks) {
            if (completedReasonCodes.contains(task.taskCode())) {
                continue;
            }
            if (!hasTaskRealActivityOnDate(userId, task.taskCode(), targetDate)) {
                continue;
            }

            int newBalance = growthRepository.appendPointsLedger(userId, task.points(), task.taskCode());
            completedReasonCodes.add(task.taskCode());
            autoCompleted = true;
            log.info(
                    "growth daily task auto completed userId={}, taskCode={}, points={}, newBalance={}",
                    userId,
                    task.taskCode(),
                    task.points(),
                    newBalance
            );
        }
        return autoCompleted;
    }

    private void evictCheckinOverviewCache(long userId) {
        growthCenterCacheService.evictCheckinOverviewNow(userId);
        growthCenterCacheService.evictCheckinOverviewAfterCommit(userId);
    }

    private void evictDailyTasksCache(long userId) {
        growthCenterCacheService.evictDailyTasksNow(userId);
        growthCenterCacheService.evictDailyTasksAfterCommit(userId);
    }

    private GrowthCenterCacheService.PointsLedgerSummarySnapshot buildPointsLedgerSummary(long userId) {
        return new GrowthCenterCacheService.PointsLedgerSummarySnapshot(
                growthRepository.getCurrentBalance(userId),
                growthRepository.countLedgerRecords(userId)
        );
    }

    private void evictPointsLedgerSummaryCache(long userId) {
        growthCenterCacheService.evictPointsLedgerSummaryNow(userId);
        growthCenterCacheService.evictPointsLedgerSummaryAfterCommit(userId);
    }

    private boolean hasTaskRealActivityOnDate(long userId, String taskCode, LocalDate targetDate) {
        return switch (taskCode) {
            case TASK_RESUME_OPTIMIZE -> growthRepository.hasResumeOptimizeActivityOnDate(userId, targetDate);
            case TASK_SKILL_PROGRESS -> growthRepository.hasSkillProgressActivityOnDate(userId, targetDate);
            case TASK_COMMUNITY_INTERACT -> growthRepository.hasCommunityInteractionOnDate(userId, targetDate);
            default -> false;
        };
    }

    private int resolveActiveStreak(GrowthRepository.CheckinRow checkin, LocalDate today) {
        if (checkin.checkinDate().equals(today) || checkin.checkinDate().equals(today.minusDays(1))) {
            return checkin.streakCount();
        }
        return 0;
    }
}
