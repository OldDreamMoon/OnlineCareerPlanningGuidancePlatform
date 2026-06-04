package com.bishe.server.mentor.service;

import com.bishe.server.common.exception.ApiException;
import com.bishe.server.common.util.TextListCodec;
import com.bishe.server.mentor.dto.MentorFinanceOverviewResponse;
import com.bishe.server.mentor.dto.MentorWithdrawalCreateRequest;
import com.bishe.server.mentor.dto.MentorWithdrawalListResponse;
import com.bishe.server.mentor.dto.MentorWithdrawalRecordResponse;
import com.bishe.server.mentor.dto.MentorWithdrawalStatusUpdateRequest;
import com.bishe.server.mentor.repository.MentorFinanceRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 导师财务中心服务：提供服务端持久化的提现演示流转能力。
 */
@Service
public class MentorFinanceService {

    private static final Set<String> ALLOWED_STATUSES = Set.of("PENDING", "PROCESSING", "COMPLETED", "REJECTED", "CANCELED");
    private static final Set<String> ALLOWED_BILL_STATUS_FILTERS = Set.of("ALL", "PAID", "COMPLETED", "REFUNDED");
    private static final Set<String> ALLOWED_BILL_RANGE_FILTERS = Set.of("ALL", "30D", "90D");
    private static final Set<String> ALLOWED_TREND_RANGE_FILTERS = Set.of("7D", "30D", "MTD");
    private static final ZoneId DISPLAY_ZONE_ID = ZoneId.of("Asia/Shanghai");
    private static final Map<String, Set<String>> ALLOWED_TRANSITIONS = Map.of(
            "PENDING", Set.of("PROCESSING", "CANCELED"),
            "PROCESSING", Set.of("COMPLETED", "REJECTED")
    );

    private final MentorFinanceRepository mentorFinanceRepository;

    public MentorFinanceService(MentorFinanceRepository mentorFinanceRepository) {
        this.mentorFinanceRepository = mentorFinanceRepository;
    }

    public MentorWithdrawalListResponse listWithdrawals(long mentorUserId) {
        return new MentorWithdrawalListResponse(
                mentorFinanceRepository.findWithdrawalsByMentorUserId(mentorUserId).stream()
                        .map(this::toRecordResponse)
                        .toList()
        );
    }

    public MentorFinanceOverviewResponse getOverview(
            long mentorUserId,
            int page,
            int size,
            String keyword,
            String statusFilter,
            String rangeFilter,
            String trendRange
    ) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 50);
        // 财务筛选统一归一化，非法值直接拒绝，避免后台口径漂移。
        String normalizedStatusFilter = normalizeFinanceFilter(statusFilter, ALLOWED_BILL_STATUS_FILTERS, "ALL");
        String normalizedRangeFilter = normalizeFinanceFilter(rangeFilter, ALLOWED_BILL_RANGE_FILTERS, "ALL");
        String normalizedTrendRange = normalizeFinanceFilter(trendRange, ALLOWED_TREND_RANGE_FILTERS, "30D");
        String normalizedKeyword = TextListCodec.normalizeText(keyword);

        MentorFinanceRepository.FinanceOverviewMetricsRow metricsRow = mentorFinanceRepository.findOverviewMetrics(mentorUserId)
                .orElseThrow(() -> new ApiException("BIZ-1002", "mentor finance profile not found", HttpStatus.NOT_FOUND));
        int pendingWithdrawalFen = mentorFinanceRepository.sumWithdrawalAmountFen(mentorUserId, List.of("PENDING", "PROCESSING"));
        int completedWithdrawalFen = mentorFinanceRepository.sumWithdrawalAmountFen(mentorUserId, List.of("COMPLETED"));
        // 可提现额度只认已完成收入，扣掉处理中和已完成提现。
        int availableWithdrawalFen = Math.max(metricsRow.completedIncomeFen() - pendingWithdrawalFen - completedWithdrawalFen, 0);

        MentorFinanceRepository.FinanceBillQuery billQuery = new MentorFinanceRepository.FinanceBillQuery(
                normalizedKeyword,
                normalizedStatusFilter,
                normalizedRangeFilter,
                safePage,
                safeSize
        );
        long total = mentorFinanceRepository.countFinanceBills(mentorUserId, billQuery);
        List<MentorFinanceOverviewResponse.BillItem> billItems = mentorFinanceRepository.findFinanceBills(mentorUserId, billQuery).stream()
                .map(item -> new MentorFinanceOverviewResponse.BillItem(
                        item.orderNo(),
                        item.counterpartUserId(),
                        item.counterpartDisplayName(),
                        item.amountFen(),
                        item.status(),
                        item.questionText(),
                        item.paymentMode(),
                        toIso(item.appointmentStartAt()),
                        toIso(item.appointmentEndAt()),
                        toIso(item.createdAt()),
                        toIso(item.paidAt()),
                        toIso(item.closedAt()),
                        null
                ))
                .toList();

        TrendWindow trendWindow = resolveTrendWindow(normalizedTrendRange);
        Map<LocalDate, MentorFinanceRepository.FinanceTrendPointRow> trendPointMap = mentorFinanceRepository.findTrendPoints(
                        mentorUserId,
                        trendWindow.startInclusive(),
                        trendWindow.endExclusive()
                ).stream()
                .collect(java.util.stream.Collectors.toMap(
                        MentorFinanceRepository.FinanceTrendPointRow::bucketDate,
                        item -> item
                ));
        // 趋势图补齐空日期，前端不再手动填缺口。
        List<MentorFinanceOverviewResponse.TrendPoint> trendPoints = trendWindow.dates().stream()
                .map(date -> {
                    MentorFinanceRepository.FinanceTrendPointRow point = trendPointMap.get(date);
                    return new MentorFinanceOverviewResponse.TrendPoint(
                            date.toString(),
                            date.getMonthValue() + "/" + date.getDayOfMonth(),
                            point == null ? 0 : point.paidFen(),
                            point == null ? 0 : point.refundedFen()
                    );
                })
                .toList();

        return new MentorFinanceOverviewResponse(
                new MentorFinanceOverviewResponse.Metrics(
                        metricsRow.totalRevenueFen(),
                        metricsRow.totalRevenueOrderCount(),
                        metricsRow.completedIncomeFen(),
                        metricsRow.closedCount(),
                        availableWithdrawalFen,
                        pendingWithdrawalFen,
                        completedWithdrawalFen,
                        metricsRow.refundedAmountFen(),
                        metricsRow.refundedOrderCount(),
                        metricsRow.answeredCount(),
                        normalizeRating(metricsRow.avgRating())
                ),
                trendPoints,
                new MentorFinanceOverviewResponse.BillPage(
                        billItems,
                        total,
                        safePage,
                        safeSize
                )
        );
    }

    @Transactional
    public MentorWithdrawalRecordResponse createWithdrawal(long mentorUserId, MentorWithdrawalCreateRequest request) {
        int amountFen = request.amountFen() == null ? 0 : request.amountFen();
        if (amountFen <= 0) {
            throw new ApiException("BIZ-1001", "withdrawal amount invalid", HttpStatus.BAD_REQUEST);
        }

        int availableWithdrawalFen = resolveAvailableWithdrawalFen(mentorUserId);
        if (amountFen > availableWithdrawalFen) {
            throw new ApiException("BIZ-1001", "withdrawal amount exceeds available quota", HttpStatus.BAD_REQUEST);
        }

        // 创建提现只进入 PENDING，后续演示流转必须走状态机。
        long withdrawalId = mentorFinanceRepository.createWithdrawal(mentorUserId, amountFen, normalizeNote(request.note()));
        MentorFinanceRepository.WithdrawalRow created = mentorFinanceRepository.findWithdrawalById(mentorUserId, withdrawalId)
                .orElseThrow(() -> new IllegalStateException("created withdrawal not found"));
        return toRecordResponse(created);
    }

    @Transactional
    public MentorWithdrawalRecordResponse updateWithdrawalStatus(long mentorUserId, long withdrawalId, MentorWithdrawalStatusUpdateRequest request) {
        String nextStatus = normalizeStatus(request.status());
        MentorFinanceRepository.WithdrawalRow current = mentorFinanceRepository.findWithdrawalById(mentorUserId, withdrawalId)
                .orElseThrow(() -> new ApiException("BIZ-1002", "withdrawal not found", HttpStatus.NOT_FOUND));

        Set<String> allowedNextStatuses = ALLOWED_TRANSITIONS.getOrDefault(current.status(), Set.of());
        if (!allowedNextStatuses.contains(nextStatus)) {
            // 只允许 PENDING->PROCESSING/CANCELED 和 PROCESSING->COMPLETED/REJECTED。
            throw new ApiException("BIZ-1001", "withdrawal status transition invalid", HttpStatus.BAD_REQUEST);
        }

        String nextNote = normalizeNote(request.note());
        if (nextNote == null) {
            nextNote = current.note();
        }

        mentorFinanceRepository.updateWithdrawalStatus(mentorUserId, withdrawalId, nextStatus, nextNote);
        MentorFinanceRepository.WithdrawalRow updated = mentorFinanceRepository.findWithdrawalById(mentorUserId, withdrawalId)
                .orElseThrow(() -> new IllegalStateException("updated withdrawal not found"));
        return toRecordResponse(updated);
    }

    private int resolveAvailableWithdrawalFen(long mentorUserId) {
        int completedIncomeFen = mentorFinanceRepository.sumCompletedIncomeFen(mentorUserId);
        int pendingWithdrawalFen = mentorFinanceRepository.sumWithdrawalAmountFen(mentorUserId, List.of("PENDING", "PROCESSING"));
        int completedWithdrawalFen = mentorFinanceRepository.sumWithdrawalAmountFen(mentorUserId, List.of("COMPLETED"));
        return Math.max(completedIncomeFen - pendingWithdrawalFen - completedWithdrawalFen, 0);
    }

    private String normalizeStatus(String rawStatus) {
        String normalized = TextListCodec.normalizeText(rawStatus);
        if (normalized == null) {
            throw new ApiException("BIZ-1001", "withdrawal status invalid", HttpStatus.BAD_REQUEST);
        }
        String upperCased = normalized.toUpperCase();
        if (!ALLOWED_STATUSES.contains(upperCased)) {
            throw new ApiException("BIZ-1001", "withdrawal status invalid", HttpStatus.BAD_REQUEST);
        }
        return upperCased;
    }

    private String normalizeFinanceFilter(String rawValue, Set<String> allowedValues, String defaultValue) {
        String normalized = TextListCodec.normalizeText(rawValue);
        if (normalized == null) {
            return defaultValue;
        }
        String upperCased = normalized.toUpperCase();
        if (!allowedValues.contains(upperCased)) {
            throw new ApiException("BIZ-1001", "finance filter invalid", HttpStatus.BAD_REQUEST);
        }
        return upperCased;
    }

    private String normalizeNote(String rawNote) {
        String normalized = TextListCodec.normalizeText(rawNote);
        if (normalized == null) {
            return null;
        }
        return normalized.length() > 500 ? normalized.substring(0, 500) : normalized;
    }

    private MentorWithdrawalRecordResponse toRecordResponse(MentorFinanceRepository.WithdrawalRow row) {
        return new MentorWithdrawalRecordResponse(
                row.id(),
                row.amountFen(),
                row.status(),
                toIso(row.createdAt()),
                toIso(row.updatedAt()),
                row.note()
        );
    }

    private BigDecimal normalizeRating(BigDecimal rating) {
        return rating == null ? BigDecimal.ZERO.setScale(1, RoundingMode.HALF_UP) : rating.setScale(1, RoundingMode.HALF_UP);
    }

    private TrendWindow resolveTrendWindow(String trendRange) {
        LocalDate today = LocalDate.now(DISPLAY_ZONE_ID);
        if ("7D".equals(trendRange)) {
            LocalDate startDate = today.minusDays(6);
            return buildTrendWindow(startDate, today);
        }
        if ("MTD".equals(trendRange)) {
            LocalDate startDate = today.withDayOfMonth(1);
            return buildTrendWindow(startDate, today);
        }
        LocalDate startDate = today.minusDays(29);
        return buildTrendWindow(startDate, today);
    }

    private TrendWindow buildTrendWindow(LocalDate startDate, LocalDate endDateInclusive) {
        List<LocalDate> dates = startDate.datesUntil(endDateInclusive.plusDays(1)).toList();
        return new TrendWindow(
                startDate.atStartOfDay(DISPLAY_ZONE_ID).toInstant(),
                endDateInclusive.plusDays(1).atStartOfDay(DISPLAY_ZONE_ID).toInstant(),
                dates
        );
    }

    private Long toIso(Instant instant) {
        return com.bishe.server.common.TimePayloads.toEpochMillis(instant);
    }

    private record TrendWindow(
            Instant startInclusive,
            Instant endExclusive,
            List<LocalDate> dates
    ) {
    }
}
