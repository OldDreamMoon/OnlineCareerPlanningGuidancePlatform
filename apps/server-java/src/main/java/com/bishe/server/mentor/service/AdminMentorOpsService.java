package com.bishe.server.mentor.service;

import com.bishe.server.common.TimePayloads;
import com.bishe.server.common.exception.ApiException;
import com.bishe.server.common.util.TextListCodec;
import com.bishe.server.consult.ConsultProperties;
import com.bishe.server.mentor.dto.MentorWithdrawalStatusUpdateRequest;
import com.bishe.server.mentor.repository.AdminMentorOpsRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class AdminMentorOpsService {

    private static final List<String> APPROVAL_STATUSES = List.of("PENDING", "APPROVED", "REJECTED");
    private static final List<String> RISK_LEVELS = List.of("CRITICAL", "HIGH", "MEDIUM", "LOW");
    private static final List<String> WITHDRAWAL_STATUSES = List.of("PENDING", "PROCESSING", "COMPLETED", "REJECTED", "CANCELED");
    private static final BigDecimal LOW_RATING_THRESHOLD = new BigDecimal("4.0");
    private static final Map<String, Set<String>> ADMIN_WITHDRAWAL_TRANSITIONS = Map.of(
            "PENDING", Set.of("PROCESSING", "REJECTED"),
            "PROCESSING", Set.of("COMPLETED", "REJECTED")
    );

    private final AdminMentorOpsRepository adminMentorOpsRepository;
    private final ConsultProperties consultProperties;
    private final MentorAvatarService mentorAvatarService;

    public AdminMentorOpsService(
            AdminMentorOpsRepository adminMentorOpsRepository,
            ConsultProperties consultProperties,
            MentorAvatarService mentorAvatarService
    ) {
        this.adminMentorOpsRepository = adminMentorOpsRepository;
        this.consultProperties = consultProperties;
        this.mentorAvatarService = mentorAvatarService;
    }

    public MentorOpsOverviewPayload getOverview() {
        List<MentorOpsRecord> records = buildMentorOpsRecords(
                adminMentorOpsRepository.findMentorOps(null, null, null, consultProperties.getMentorReplyTimeoutHours())
        );
        long riskyMentorCount = records.stream().filter(item -> item.highestRiskLevel() != null).count();
        long scheduleRiskMentorCount = records.stream()
                .filter(item -> hasRiskCode(item.riskSignals(), "APPOINTMENT_WITHOUT_SLOT")
                        || hasRiskCode(item.riskSignals(), "LOW_SLOT_COVERAGE")
                        || hasRiskCode(item.riskSignals(), "NO_ENABLED_PACKAGE"))
                .count();
        long fulfillmentRiskMentorCount = records.stream()
                .filter(item -> hasRiskCode(item.riskSignals(), "OVERDUE_REPLY")
                        || hasRiskCode(item.riskSignals(), "REPLY_EXPIRING")
                        || hasRiskCode(item.riskSignals(), "PENDING_AFTER_SALES")
                        || hasRiskCode(item.riskSignals(), "LOW_RATING")
                        || hasRiskCode(item.riskSignals(), "AFTER_SALES_PRESSURE"))
                .count();

        return new MentorOpsOverviewPayload(
                records.size(),
                records.stream().filter(item -> "APPROVED".equals(item.approvalStatus())).count(),
                records.stream().filter(item -> "PENDING".equals(item.approvalStatus())).count(),
                riskyMentorCount,
                scheduleRiskMentorCount,
                fulfillmentRiskMentorCount,
                records.stream().filter(item -> item.openWithdrawalCount() > 0).count(),
                records.stream().mapToLong(MentorOpsRecord::openWithdrawalAmountFen).sum()
        );
    }

    public MentorOpsListPayload listMentors(
            int page,
            int size,
            String keyword,
            String approvalStatus,
            String riskLevel,
            String withdrawalStatus
    ) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 50);
        String normalizedApprovalStatus = normalizeStatus(approvalStatus, APPROVAL_STATUSES, "approvalStatus");
        String normalizedRiskLevel = normalizeStatus(riskLevel, RISK_LEVELS, "riskLevel");
        String normalizedWithdrawalStatus = normalizeStatus(withdrawalStatus, WITHDRAWAL_STATUSES, "withdrawalStatus");

        List<MentorOpsRecord> records = buildMentorOpsRecords(
                adminMentorOpsRepository.findMentorOps(
                        keyword,
                        normalizedApprovalStatus,
                        normalizedWithdrawalStatus,
                        consultProperties.getMentorReplyTimeoutHours()
                )
        );
        if (normalizedRiskLevel != null) {
            records = records.stream()
                    .filter(item -> normalizedRiskLevel.equals(item.highestRiskLevel()))
                    .toList();
        }

        int fromIndex = Math.min((safePage - 1) * safeSize, records.size());
        int toIndex = Math.min(fromIndex + safeSize, records.size());
        List<MentorOpsRecord> pageRecords = records.subList(fromIndex, toIndex);
        return new MentorOpsListPayload(pageRecords, records.size(), safePage, safeSize);
    }

    @Transactional
    public WithdrawalManageResponse manageWithdrawal(long withdrawalId, MentorWithdrawalStatusUpdateRequest request) {
        String nextStatus = normalizeRequiredStatus(request.status(), WITHDRAWAL_STATUSES, "status");
        AdminMentorOpsRepository.AdminWithdrawalRow current = requireWithdrawal(withdrawalId);
        Set<String> allowedNextStatuses = ADMIN_WITHDRAWAL_TRANSITIONS.getOrDefault(current.status(), Set.of());
        if (!allowedNextStatuses.contains(nextStatus)) {
            throw new ApiException("BIZ-1001", "withdrawal status transition invalid", HttpStatus.BAD_REQUEST);
        }
        String nextNote = normalizeNote(request.note());
        if (nextNote == null) {
            nextNote = current.note();
        }
        adminMentorOpsRepository.updateWithdrawalStatus(withdrawalId, nextStatus, nextNote);
        AdminMentorOpsRepository.AdminWithdrawalRow updated = requireWithdrawal(withdrawalId);
        return new WithdrawalManageResponse(
                updated.id(),
                updated.mentorUserId(),
                updated.status(),
                toTimePayload(updated.updatedAt())
        );
    }

    private List<MentorOpsRecord> buildMentorOpsRecords(List<AdminMentorOpsRepository.AdminMentorOpsRow> rows) {
        return rows.stream()
                .map(this::toMentorOpsRecord)
                .sorted(buildRecordComparator())
                .toList();
    }

    private MentorOpsRecord toMentorOpsRecord(AdminMentorOpsRepository.AdminMentorOpsRow row) {
        List<RiskSignalItem> riskSignals = buildRiskSignals(row);
        String highestRiskLevel = riskSignals.stream()
                .map(RiskSignalItem::level)
                .min(Comparator.comparingInt(this::riskOrder))
                .orElse(null);

        List<String> packageNames = splitPipeList(row.enabledPackageNames());
        List<String> scenes = mergeLabels(
                splitPipeList(row.enabledSceneLabels()),
                TextListCodec.split(row.serviceScenes()),
                TextListCodec.split(row.expertiseTags())
        );

        return new MentorOpsRecord(
                row.mentorUserId(),
                row.displayName(),
                row.realName(),
                row.showRealName(),
                row.companyName(),
                row.jobTitle(),
                TextListCodec.normalizeText(row.avatarObjectKey()) == null
                        ? null
                        : mentorAvatarService.resolveAvatarUrl(
                        row.mentorUserId(),
                        row.avatarUrl(),
                        row.displayName(),
                        row.avatarObjectKey(),
                        row.avatarUpdatedAt()
                ),
                row.approvalStatus(),
                row.available(),
                row.totalPackageCount(),
                row.enabledPackageCount(),
                row.enabledAppointmentPackageCount(),
                row.startingPriceFen(),
                packageNames,
                scenes,
                normalizeRating(row.avgRating()),
                row.weekAvailableSlotCount(),
                row.nextThreeDayAvailableSlotCount(),
                row.upcomingBookedSlotCount(),
                toTimePayload(row.nextAvailableAt()),
                toTimePayload(row.nextBookedAt()),
                row.totalOrderCount(),
                row.paidOrderCount(),
                row.answeredOrderCount(),
                row.closedOrderCount(),
                row.refundedOrderCount(),
                row.overdueReplyOrderCount(),
                row.expiringReplyOrderCount(),
                row.pendingAfterSalesCount(),
                row.afterSalesImpactCount(),
                toTimePayload(row.latestOrderActivityAt()),
                row.pendingWithdrawalCount(),
                row.processingWithdrawalCount(),
                row.completedWithdrawalCount(),
                row.rejectedWithdrawalCount(),
                row.pendingWithdrawalCount() + row.processingWithdrawalCount(),
                row.openWithdrawalAmountFen(),
                row.latestWithdrawalId(),
                row.latestWithdrawalAmountFen(),
                row.latestWithdrawalStatus(),
                row.latestWithdrawalNote(),
                toTimePayload(row.latestWithdrawalCreatedAt()),
                toTimePayload(row.latestWithdrawalUpdatedAt()),
                toTimePayload(row.profileUpdatedAt()),
                highestRiskLevel,
                riskSignals
        );
    }

    private List<RiskSignalItem> buildRiskSignals(AdminMentorOpsRepository.AdminMentorOpsRow row) {
        List<RiskSignalItem> signals = new ArrayList<>();

        if (!"APPROVED".equals(row.approvalStatus())) {
            String level = "REJECTED".equals(row.approvalStatus())
                    ? "HIGH"
                    : (row.available() || row.enabledPackageCount() > 0 || row.totalOrderCount() > 0 ? "HIGH" : "MEDIUM");
            String label = "REJECTED".equals(row.approvalStatus()) ? "导师认证已驳回" : "导师认证待处理";
            String description = "当前导师认证状态不是已通过，但仍保留经营配置或历史履约数据，平台应尽快确认是否允许继续对外承接。";
            signals.add(new RiskSignalItem("APPROVAL_BLOCKED", level, label, description));
        }

        if ("APPROVED".equals(row.approvalStatus()) && row.available() && row.enabledPackageCount() == 0) {
            signals.add(new RiskSignalItem(
                    "NO_ENABLED_PACKAGE",
                    "HIGH",
                    "可接单但无启用套餐",
                    "导师已处于可接单状态，但当前没有任何启用中的服务套餐，学生侧会缺少明确成交入口。"
            ));
        }

        if ("APPROVED".equals(row.approvalStatus()) && row.available() && row.enabledAppointmentPackageCount() > 0) {
            if (row.weekAvailableSlotCount() == 0) {
                signals.add(new RiskSignalItem(
                        "APPOINTMENT_WITHOUT_SLOT",
                        "HIGH",
                        "预约套餐无可约时段",
                        "导师已经启用预约型套餐，但未来 7 天没有任何可预约时段，需尽快补排期或调整可售服务。"
                ));
            } else if (row.weekAvailableSlotCount() < 3) {
                signals.add(new RiskSignalItem(
                        "LOW_SLOT_COVERAGE",
                        "MEDIUM",
                        "预约时段覆盖偏少",
                        "未来 7 天仅剩少量可预约时段，建议提前补排期，避免首页推荐后无法承接咨询。"
                ));
            }
        }

        if (row.overdueReplyOrderCount() > 0) {
            signals.add(new RiskSignalItem(
                    "OVERDUE_REPLY",
                    "CRITICAL",
                    "已支付订单超时未答复",
                    "已有已支付订单超过导师答复时限仍未收口，平台应优先跟进履约或触发售后处理。"
            ));
        } else if (row.expiringReplyOrderCount() > 0) {
            signals.add(new RiskSignalItem(
                    "REPLY_EXPIRING",
                    "HIGH",
                    "临近答复超时",
                    "存在即将触发答复超时的已支付订单，建议平台提醒导师尽快处理。"
            ));
        }

        if (row.pendingAfterSalesCount() > 0) {
            signals.add(new RiskSignalItem(
                    "PENDING_AFTER_SALES",
                    "HIGH",
                    "存在待审核售后",
                    "导师关联订单存在待处理售后申请，平台需尽快判断是否退款或补充处置说明。"
            ));
        } else if (row.afterSalesImpactCount() >= 2 && row.totalOrderCount() >= 4) {
            signals.add(new RiskSignalItem(
                    "AFTER_SALES_PRESSURE",
                    "MEDIUM",
                    "售后影响偏高",
                    "该导师近期已有多笔订单进入退款或售后链路，建议结合评价和履约表现做专项巡检。"
            ));
        }

        if (row.pendingWithdrawalCount() > 0) {
            signals.add(new RiskSignalItem(
                    "WITHDRAWAL_PENDING",
                    "MEDIUM",
                    "存在待打款申请",
                    "导师已有新的提现申请待平台处理，需及时推进到打款中或驳回，避免财务积压。"
            ));
        } else if (row.processingWithdrawalCount() > 0) {
            signals.add(new RiskSignalItem(
                    "WITHDRAWAL_PROCESSING",
                    "LOW",
                    "提现处理中",
                    "导师提现已进入处理链路，平台仍需关注是否按预期完成打款与状态收口。"
            ));
        }

        if (row.totalOrderCount() >= 3 && row.avgRating() != null && row.avgRating().compareTo(LOW_RATING_THRESHOLD) < 0) {
            signals.add(new RiskSignalItem(
                    "LOW_RATING",
                    "MEDIUM",
                    "评分偏低",
                    "导师已有稳定履约样本，但当前评分低于 4.0，建议结合售后与服务内容做质量复盘。"
            ));
        }

        return signals.stream()
                .sorted(Comparator.comparingInt(item -> riskOrder(item.level())))
                .toList();
    }

    private Comparator<MentorOpsRecord> buildRecordComparator() {
        return Comparator.comparing((MentorOpsRecord item) -> item.highestRiskLevel() == null ? 99 : riskOrder(item.highestRiskLevel()))
                .thenComparing((MentorOpsRecord item) -> item.openWithdrawalCount() > 0 ? 0 : 1)
                .thenComparing(MentorOpsRecord::latestOrderActivityAt, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(MentorOpsRecord::profileUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(MentorOpsRecord::mentorUserId, Comparator.reverseOrder());
    }

    private AdminMentorOpsRepository.AdminWithdrawalRow requireWithdrawal(long withdrawalId) {
        return adminMentorOpsRepository.findWithdrawalById(withdrawalId)
                .orElseThrow(() -> new ApiException("BIZ-1002", "withdrawal not found", HttpStatus.NOT_FOUND));
    }

    private boolean hasRiskCode(List<RiskSignalItem> items, String code) {
        return items.stream().anyMatch(item -> code.equals(item.code()));
    }

    private int riskOrder(String level) {
        if ("CRITICAL".equals(level)) {
            return 0;
        }
        if ("HIGH".equals(level)) {
            return 1;
        }
        if ("MEDIUM".equals(level)) {
            return 2;
        }
        return 3;
    }

    private String normalizeStatus(String value, List<String> allowedValues, String fieldName) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!allowedValues.contains(normalized)) {
            throw new ApiException("BIZ-1001", fieldName + " is invalid", HttpStatus.BAD_REQUEST);
        }
        return normalized;
    }

    private String normalizeRequiredStatus(String value, List<String> allowedValues, String fieldName) {
        String normalized = normalizeStatus(value, allowedValues, fieldName);
        if (normalized == null) {
            throw new ApiException("BIZ-1001", fieldName + " is invalid", HttpStatus.BAD_REQUEST);
        }
        return normalized;
    }

    private String normalizeNote(String value) {
        String normalized = TextListCodec.normalizeText(value);
        if (normalized == null) {
            return null;
        }
        return normalized.length() > 500 ? normalized.substring(0, 500) : normalized;
    }

    private BigDecimal normalizeRating(BigDecimal value) {
        return value == null ? BigDecimal.ZERO.setScale(1, RoundingMode.HALF_UP) : value.setScale(1, RoundingMode.HALF_UP);
    }

    private Long toTimePayload(Instant value) {
        return TimePayloads.toEpochMillis(value);
    }

    private List<String> splitPipeList(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return java.util.Arrays.stream(value.split("\\|\\|"))
                .map(TextListCodec::normalizeText)
                .filter(item -> item != null && !item.isBlank())
                .distinct()
                .toList();
    }

    private List<String> mergeLabels(List<String> first, List<String> second, List<String> third) {
        java.util.LinkedHashSet<String> values = new java.util.LinkedHashSet<>();
        values.addAll(first);
        values.addAll(second);
        values.addAll(third);
        return List.copyOf(values);
    }

    public record MentorOpsOverviewPayload(
            long totalMentorCount,
            long approvedMentorCount,
            long pendingApprovalCount,
            long riskyMentorCount,
            long scheduleRiskMentorCount,
            long fulfillmentRiskMentorCount,
            long pendingWithdrawalMentorCount,
            long pendingWithdrawalAmountFen
    ) {
    }

    public record MentorOpsListPayload(
            List<MentorOpsRecord> records,
            int total,
            int page,
            int size
    ) {
    }

    public record MentorOpsRecord(
            long mentorUserId,
            String displayName,
            String realName,
            boolean showRealName,
            String companyName,
            String jobTitle,
            String avatarUrl,
            String approvalStatus,
            boolean available,
            int totalPackageCount,
            int enabledPackageCount,
            int enabledAppointmentPackageCount,
            int startingPriceFen,
            List<String> enabledPackageNames,
            List<String> serviceScenes,
            BigDecimal avgRating,
            int weekAvailableSlotCount,
            int nextThreeDayAvailableSlotCount,
            int upcomingBookedSlotCount,
            Long nextAvailableAt,
            Long nextBookedAt,
            long totalOrderCount,
            long paidOrderCount,
            long answeredOrderCount,
            long closedOrderCount,
            long refundedOrderCount,
            long overdueReplyOrderCount,
            long expiringReplyOrderCount,
            long pendingAfterSalesCount,
            long afterSalesImpactCount,
            Long latestOrderActivityAt,
            int pendingWithdrawalCount,
            int processingWithdrawalCount,
            int completedWithdrawalCount,
            int rejectedWithdrawalCount,
            int openWithdrawalCount,
            int openWithdrawalAmountFen,
            Long latestWithdrawalId,
            Integer latestWithdrawalAmountFen,
            String latestWithdrawalStatus,
            String latestWithdrawalNote,
            Long latestWithdrawalCreatedAt,
            Long latestWithdrawalUpdatedAt,
            Long profileUpdatedAt,
            String highestRiskLevel,
            List<RiskSignalItem> riskSignals
    ) {
    }

    public record RiskSignalItem(
            String code,
            String level,
            String label,
            String description
    ) {
    }

    public record WithdrawalManageResponse(
            long withdrawalId,
            long mentorUserId,
            String status,
            Long updatedAt
    ) {
    }
}
