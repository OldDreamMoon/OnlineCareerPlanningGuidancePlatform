package com.bishe.server.dashboard;

import com.bishe.server.common.exception.ApiException;
import com.bishe.server.common.TimePayloads;
import com.bishe.server.ai.gateway.AiGatewayAdminService;
import com.bishe.server.community.repository.CommunityRepository;
import com.bishe.server.consult.service.AdminPaymentService;
import com.bishe.server.consult.service.ConsultAfterSalesService;
import com.bishe.server.governance.ContentGovernanceService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 管理端数据看板服务：基于 PRD 中定义的运营口径返回最小可演示的数据看板。
 */
@Service
public class AdminDashboardService {

    private final AdminDashboardRepository repository;
    private final CommunityRepository communityRepository;
    private final AdminWorkbenchCacheService adminWorkbenchCacheService;
    private final AdminOperationsDashboardCacheService adminOperationsDashboardCacheService;
    private final ContentGovernanceService contentGovernanceService;
    private final ConsultAfterSalesService consultAfterSalesService;
    private final AdminPaymentService adminPaymentService;
    private final AiGatewayAdminService aiGatewayAdminService;

    public AdminDashboardService(
            AdminDashboardRepository repository,
            CommunityRepository communityRepository,
            AdminWorkbenchCacheService adminWorkbenchCacheService,
            AdminOperationsDashboardCacheService adminOperationsDashboardCacheService,
            ContentGovernanceService contentGovernanceService,
            ConsultAfterSalesService consultAfterSalesService,
            AdminPaymentService adminPaymentService,
            AiGatewayAdminService aiGatewayAdminService
    ) {
        this.repository = repository;
        this.communityRepository = communityRepository;
        this.adminWorkbenchCacheService = adminWorkbenchCacheService;
        this.adminOperationsDashboardCacheService = adminOperationsDashboardCacheService;
        this.contentGovernanceService = contentGovernanceService;
        this.consultAfterSalesService = consultAfterSalesService;
        this.adminPaymentService = adminPaymentService;
        this.aiGatewayAdminService = aiGatewayAdminService;
    }

    public WorkbenchPayload getWorkbench(Integer rawHours, String rawTimezone) {
        int hours = rawHours == null || rawHours <= 0 ? 24 : Math.min(rawHours, 168);
        String timezone = rawTimezone == null || rawTimezone.isBlank() ? "Asia/Shanghai" : rawTimezone.trim();

        // 待办工作台是后台首页高频入口，按 hours/timezone 做短 TTL 缓存。
        return adminWorkbenchCacheService.getWorkbench(hours, timezone, () -> buildWorkbenchPayload(hours, timezone));
    }

    private WorkbenchPayload buildWorkbenchPayload(int hours, String timezone) {
        // 只取各域待处理数量，不拉列表，保证首页摘要足够轻。
        long pendingReports = contentGovernanceService.getAdminReports(1, 1, "PENDING", null).total();
        long reviewQueue = contentGovernanceService.getReviewQueue(1, 1, null).total();
        long afterSales = consultAfterSalesService.getAdminRequests(1, 1, null, "PENDING").total();
        long reconciliation = adminPaymentService.getOrders(1, 1, null, null, "REVIEW_REQUIRED").total();
        AiGatewayAdminService.ProviderRuntimeStatsPayload runtimeStats = aiGatewayAdminService.getProviderRuntimeStats(hours, timezone);
        long unhealthyProviders = runtimeStats.degradedProviders() + runtimeStats.downProviders() + runtimeStats.disabledProviders();

        return new WorkbenchPayload(
                TimePayloads.toEpochMillis(Instant.now()),
                pendingReports,
                reviewQueue,
                afterSales,
                reconciliation,
                pendingReports + reviewQueue + afterSales + reconciliation,
                new ProviderRuntimeSummaryPayload(
                        runtimeStats.hours(),
                        runtimeStats.timezone(),
                        runtimeStats.totalProviders(),
                        runtimeStats.healthyProviders(),
                        runtimeStats.degradedProviders(),
                        runtimeStats.downProviders(),
                        runtimeStats.disabledProviders(),
                        runtimeStats.idleProviders(),
                        unhealthyProviders
                )
        );
    }

    public OperationsDashboardPayload getOperationsDashboard(String rawPeriod) {
        String period = normalizePeriod(rawPeriod);
        // 运营看板按 today/week/month 固定窗口缓存，避免多表指标频繁重算。
        return adminOperationsDashboardCacheService.getOperationsDashboard(period, () -> buildOperationsDashboardPayload(period));
    }

    private OperationsDashboardPayload buildOperationsDashboardPayload(String period) {
        TimeWindow window = resolveTimeWindow(period);

        // 激活率按新学生 24 小时内首次 AI 成功调用计算。
        List<AdminDashboardRepository.StudentLifecycleRow> newStudents = repository.findStudentLifecycleRows(window.startAt(), window.endAt());
        Map<Long, Instant> newStudentFirstAiSuccess = repository.findFirstSuccessfulAiCallAt(newStudents.stream()
                .map(AdminDashboardRepository.StudentLifecycleRow::userId)
                .toList());
        long activatedNewStudents = countActivatedWithin24Hours(newStudents, newStudentFirstAiSuccess);

        // 7 日留存用已满 7 天的激活 cohort，避免把刚注册用户算进分母。
        TimeWindow retentionCohortWindow = shiftWindow(window, 7);
        List<AdminDashboardRepository.StudentLifecycleRow> retentionCohort = repository.findStudentLifecycleRows(
                retentionCohortWindow.startAt(),
                retentionCohortWindow.endAt()
        );
        Map<Long, Instant> cohortFirstAiSuccess = repository.findFirstSuccessfulAiCallAt(retentionCohort.stream()
                .map(AdminDashboardRepository.StudentLifecycleRow::userId)
                .toList());
        long activatedCohortStudents = countActivatedWithin24Hours(retentionCohort, cohortFirstAiSuccess);
        long retainedCohortStudents = countRetainedOnDay7(retentionCohort, cohortFirstAiSuccess);

        Set<Long> activeStudents = new HashSet<>(repository.findActiveStudentIds(window.startAt(), window.endAt()));
        Set<Long> paidConsultStudents = new HashSet<>(repository.findPaidConsultStudentIds(window.startAt(), window.endAt()));
        // 咨询转化只统计窗口内活跃学生里已支付咨询的人数。
        long consultConvertedStudents = activeStudents.stream().filter(paidConsultStudents::contains).count();

        AdminDashboardRepository.AiCallSummaryRow aiSummary = repository.summarizeAiCalls(window.startAt(), window.endAt());
        AdminDashboardRepository.CommunityCoverageRow communityCoverage = repository.summarizeCommunityAiCoverage(window.startAt(), window.endAt());
        AdminDashboardRepository.ModerationSummaryRow moderationSummary = repository.summarizeModeration(window.startAt(), window.endAt());
        List<Double> reportDurations = repository.listClosedReportDurationsHours(window.startAt(), window.endAt());

        long totalStudents = repository.countTotalStudents();
        long portraitCompletedStudents = repository.countPortraitCompletedStudents();

        Instant leaderboardWindowStart = Instant.now().minus(java.time.Duration.ofDays(7));
        long activeStudents7d = repository.countActiveStudentsSince(Timestamp.from(leaderboardWindowStart));
        long leaderboardCoveredStudents7d = communityRepository.countLeaderboardRows(leaderboardWindowStart);

        return new OperationsDashboardPayload(
                period,
                TimePayloads.toEpochMillis(Instant.now()),
                TimePayloads.toEpochMillis(window.startAt().toInstant()),
                TimePayloads.toEpochMillis(window.endAt().toInstant()),
                new OverviewPayload(
                        newStudents.size(),
                        activatedNewStudents,
                        activeStudents.size(),
                        consultConvertedStudents,
                        aiSummary.totalCount(),
                        aiSummary.successCount(),
                        communityCoverage.totalPosts(),
                        communityCoverage.aiCoveredPosts(),
                        moderationSummary.totalCount(),
                        moderationSummary.blockedCount(),
                        reportDurations.size(),
                        totalStudents,
                        portraitCompletedStudents,
                        activeStudents7d,
                        leaderboardCoveredStudents7d
                ),
                buildRateMetric(
                        "激活率",
                        activatedNewStudents,
                        newStudents.size(),
                        "统计当前窗口内新注册学生在 24 小时内至少完成一次 AI 成功调用的比例。"
                ),
                buildRateMetric(
                        "7日留存",
                        retainedCohortStudents,
                        activatedCohortStudents,
                        "基于已满 7 天的激活学生 cohort，使用 last_login_at 估算第 7 天是否仍有回访。"
                ),
                buildRateMetric(
                        "咨询转化率",
                        consultConvertedStudents,
                        activeStudents.size(),
                        "统计当前窗口内活跃学生中，产生已支付咨询订单的学生占比。"
                ),
                buildRateMetric(
                        "AI 调用成功率",
                        aiSummary.successCount(),
                        aiSummary.totalCount(),
                        "统计当前窗口内 ai_call_logs 成功完成占比。"
                ),
                buildRateMetric(
                        "社区 AI 覆盖率",
                        communityCoverage.aiCoveredPosts(),
                        communityCoverage.totalPosts(),
                        "统计当前窗口内新帖中，已出现 AI 自动首答评论的帖子占比。"
                ),
                buildRateMetric(
                        "内容审查拦截率",
                        moderationSummary.blockedCount(),
                        moderationSummary.totalCount(),
                        "统计当前窗口内内容审查事件中 action=BLOCK 的占比。"
                ),
                buildDurationMetric(
                        "举报处置时效中位数",
                        reportDurations,
                        "统计当前窗口内已关闭举报从创建到关闭的中位耗时。"
                ),
                buildRateMetric(
                        "画像完整率",
                        portraitCompletedStudents,
                        totalStudents,
                        "统计当前所有学生中，至少形成 1 个画像标签的比例。"
                ),
                buildRateMetric(
                        "社区榜单覆盖率",
                        leaderboardCoveredStudents7d,
                        activeStudents7d,
                        "固定按近 7 天贡献榜口径计算：有贡献分的学生占近 7 天活跃学生比例。"
                )
        );
    }

    private long countActivatedWithin24Hours(
            List<AdminDashboardRepository.StudentLifecycleRow> students,
            Map<Long, Instant> firstSuccessMap
    ) {
        // 首次 AI 成功调用必须落在注册后的 24 小时内，早于注册的异常数据不计入。
        return students.stream()
                .filter(student -> {
                    Instant firstSuccessAt = firstSuccessMap.get(student.userId());
                    return firstSuccessAt != null
                            && !firstSuccessAt.isBefore(student.createdAt())
                            && !firstSuccessAt.isAfter(student.createdAt().plus(java.time.Duration.ofHours(24)));
                })
                .count();
    }

    private long countRetainedOnDay7(
            List<AdminDashboardRepository.StudentLifecycleRow> students,
            Map<Long, Instant> firstSuccessMap
    ) {
        return students.stream()
                .filter(student -> {
                    Instant firstSuccessAt = firstSuccessMap.get(student.userId());
                    if (firstSuccessAt == null
                            || firstSuccessAt.isBefore(student.createdAt())
                            || firstSuccessAt.isAfter(student.createdAt().plus(java.time.Duration.ofHours(24)))) {
                        return false;
                    }
                    Instant lastLoginAt = student.lastLoginAt();
                    return lastLoginAt != null && !lastLoginAt.isBefore(student.createdAt().plus(java.time.Duration.ofDays(7)));
                })
                .count();
    }

    private MetricPayload buildRateMetric(String label, long numerator, long denominator, String note) {
        return new MetricPayload(label, formatPercent(numerator, denominator), numerator, denominator, note);
    }

    private DurationMetricPayload buildDurationMetric(String label, List<Double> values, String note) {
        if (values == null || values.isEmpty()) {
            return new DurationMetricPayload(label, "0.0h", 0, note);
        }
        // 处置时效使用中位数，避免少量异常长工单拉高指标。
        List<Double> sortedValues = values.stream().sorted().toList();
        double median;
        int size = sortedValues.size();
        if (size % 2 == 1) {
            median = sortedValues.get(size / 2);
        } else {
            median = (sortedValues.get(size / 2 - 1) + sortedValues.get(size / 2)) / 2D;
        }
        return new DurationMetricPayload(label, formatHours(median), size, note);
    }

    private String formatPercent(long numerator, long denominator) {
        if (denominator <= 0) {
            return "0.0%";
        }
        return BigDecimal.valueOf(numerator)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(denominator), 1, RoundingMode.HALF_UP)
                .stripTrailingZeros()
                .toPlainString() + "%";
    }

    private String formatHours(double hours) {
        return BigDecimal.valueOf(hours)
                .setScale(1, RoundingMode.HALF_UP)
                .stripTrailingZeros()
                .toPlainString() + "h";
    }

    private String normalizePeriod(String rawValue) {
        String candidate = rawValue == null || rawValue.isBlank() ? "week" : rawValue.trim().toLowerCase(Locale.ROOT);
        if (!List.of("today", "week", "month").contains(candidate)) {
            throw new ApiException("BIZ-1001", "period invalid", HttpStatus.BAD_REQUEST);
        }
        return candidate;
    }

    private TimeWindow resolveTimeWindow(String period) {
        LocalDate today = LocalDate.now();
        LocalDate startDate;
        switch (period) {
            case "month" -> startDate = today.withDayOfMonth(1);
            case "week" -> startDate = today.with(DayOfWeek.MONDAY);
            default -> startDate = today;
        }
        return new TimeWindow(
                Timestamp.valueOf(startDate.atStartOfDay()),
                Timestamp.valueOf(LocalDateTime.of(today, LocalTime.MAX))
        );
    }

    private TimeWindow shiftWindow(TimeWindow window, int days) {
        return new TimeWindow(
                Timestamp.from(window.startAt().toInstant().minus(java.time.Duration.ofDays(days))),
                Timestamp.from(window.endAt().toInstant().minus(java.time.Duration.ofDays(days)))
        );
    }

    public record WorkbenchPayload(
            Long generatedAt,
            long pendingReports,
            long reviewQueue,
            long afterSalesRequests,
            long reconciliationReviewRequired,
            long totalPendingTasks,
            ProviderRuntimeSummaryPayload providerRuntime
    ) {
    }

    public record ProviderRuntimeSummaryPayload(
            int hours,
            String timezone,
            long totalProviders,
            long healthyProviders,
            long degradedProviders,
            long downProviders,
            long disabledProviders,
            long idleProviders,
            long unhealthyProviders
    ) {
    }

    public record OperationsDashboardPayload(
            String period,
            Long generatedAt,
            Long windowStartAt,
            Long windowEndAt,
            OverviewPayload overview,
            MetricPayload activationRate,
            MetricPayload retention7dRate,
            MetricPayload consultConversionRate,
            MetricPayload aiSuccessRate,
            MetricPayload communityAiCoverageRate,
            MetricPayload moderationBlockRate,
            DurationMetricPayload reportHandleMedianHours,
            MetricPayload portraitCompletenessRate,
            MetricPayload leaderboardCoverageRate
    ) {
    }

    public record OverviewPayload(
            long newStudents,
            long activatedStudents,
            long activeStudents,
            long paidConsultStudents,
            long aiCalls,
            long aiSuccessCalls,
            long newPosts,
            long aiCoveredPosts,
            long moderationEvents,
            long blockedModerationEvents,
            long closedReports,
            long totalStudents,
            long portraitCompletedStudents,
            long activeStudents7d,
            long leaderboardCoveredStudents7d
    ) {
    }

    public record MetricPayload(
            String label,
            String value,
            long numerator,
            long denominator,
            String note
    ) {
    }

    public record DurationMetricPayload(
            String label,
            String value,
            long sampleSize,
            String note
    ) {
    }

    private record TimeWindow(Timestamp startAt, Timestamp endAt) {
    }
}
