package com.bishe.server.mentor.repository;

import com.bishe.server.auth.model.UserRole;
import com.bishe.server.auth.repository.jpa.UserAccountJpaRepository;
import com.bishe.server.auth.repository.jpa.entity.UserAccountEntity;
import com.bishe.server.consult.repository.jpa.ConsultAfterSalesRequestJpaRepository;
import com.bishe.server.consult.repository.jpa.ConsultOrderJpaRepository;
import com.bishe.server.consult.repository.jpa.entity.ConsultAfterSalesRequestEntity;
import com.bishe.server.consult.repository.jpa.entity.ConsultOrderEntity;
import com.bishe.server.mentor.repository.jpa.AdminMentorProfileJpaRepository;
import com.bishe.server.mentor.repository.jpa.MentorServicePackageJpaRepository;
import com.bishe.server.mentor.repository.jpa.MentorWithdrawalRequestJpaRepository;
import com.bishe.server.mentor.repository.jpa.entity.AdminMentorProfileEntity;
import com.bishe.server.mentor.repository.jpa.entity.MentorServicePackageEntity;
import com.bishe.server.mentor.repository.jpa.entity.MentorWithdrawalRequestEntity;
import com.bishe.server.mentor.schedule.repository.jpa.MentorScheduleSlotJpaRepository;
import com.bishe.server.mentor.schedule.repository.jpa.entity.MentorScheduleSlotEntity;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 管理员导师经营治理仓储。
 */
@Repository
public class AdminMentorOpsRepository {

    private static final List<String> OPEN_WITHDRAWAL_STATUSES = List.of("PENDING", "PROCESSING");

    private final UserAccountJpaRepository userAccountJpaRepository;
    private final AdminMentorProfileJpaRepository adminMentorProfileJpaRepository;
    private final MentorServicePackageJpaRepository mentorServicePackageJpaRepository;
    private final MentorScheduleSlotJpaRepository mentorScheduleSlotJpaRepository;
    private final ConsultOrderJpaRepository consultOrderJpaRepository;
    private final ConsultAfterSalesRequestJpaRepository consultAfterSalesRequestJpaRepository;
    private final MentorWithdrawalRequestJpaRepository mentorWithdrawalRequestJpaRepository;

    public AdminMentorOpsRepository(
            UserAccountJpaRepository userAccountJpaRepository,
            AdminMentorProfileJpaRepository adminMentorProfileJpaRepository,
            MentorServicePackageJpaRepository mentorServicePackageJpaRepository,
            MentorScheduleSlotJpaRepository mentorScheduleSlotJpaRepository,
            ConsultOrderJpaRepository consultOrderJpaRepository,
            ConsultAfterSalesRequestJpaRepository consultAfterSalesRequestJpaRepository,
            MentorWithdrawalRequestJpaRepository mentorWithdrawalRequestJpaRepository
    ) {
        this.userAccountJpaRepository = userAccountJpaRepository;
        this.adminMentorProfileJpaRepository = adminMentorProfileJpaRepository;
        this.mentorServicePackageJpaRepository = mentorServicePackageJpaRepository;
        this.mentorScheduleSlotJpaRepository = mentorScheduleSlotJpaRepository;
        this.consultOrderJpaRepository = consultOrderJpaRepository;
        this.consultAfterSalesRequestJpaRepository = consultAfterSalesRequestJpaRepository;
        this.mentorWithdrawalRequestJpaRepository = mentorWithdrawalRequestJpaRepository;
    }

    public List<AdminMentorOpsRow> findMentorOps(
            String keyword,
            String approvalStatus,
            String withdrawalStatus,
            int mentorReplyTimeoutHours
    ) {
        List<Long> mentorUserIds = userAccountJpaRepository.findIdsByRoleAndDeletedFalseOrderByIdAsc(UserRole.MENTOR);
        if (mentorUserIds.isEmpty()) {
            return List.of();
        }

        Map<Long, UserAccountEntity> mentorUserMap = loadMentorUserMap(mentorUserIds);
        if (mentorUserMap.isEmpty()) {
            return List.of();
        }

        List<Long> orderedMentorUserIds = mentorUserMap.keySet().stream()
                .sorted()
                .toList();
        Map<Long, AdminMentorProfileEntity> mentorProfileMap = loadMentorProfileMap(orderedMentorUserIds);
        List<Long> mentorIdsWithProfile = orderedMentorUserIds.stream()
                .filter(mentorProfileMap::containsKey)
                .toList();
        if (mentorIdsWithProfile.isEmpty()) {
            return List.of();
        }

        Map<Long, PackageSummary> packageSummaryMap = buildPackageSummaryMap(mentorIdsWithProfile);
        Map<Long, ScheduleSummary> scheduleSummaryMap = buildScheduleSummaryMap(mentorIdsWithProfile);
        Map<Long, OrderSummary> orderSummaryMap = buildOrderSummaryMap(mentorIdsWithProfile, mentorReplyTimeoutHours);
        Map<Long, WithdrawalSummary> withdrawalSummaryMap = buildWithdrawalSummaryMap(mentorIdsWithProfile);

        String normalizedKeyword = normalizeKeyword(keyword);
        String normalizedApprovalStatus = normalizeStatus(approvalStatus);
        String normalizedWithdrawalStatus = normalizeStatus(withdrawalStatus);

        return mentorIdsWithProfile.stream()
                .filter(mentorUserId -> matchesFilters(
                        mentorUserMap.get(mentorUserId),
                        mentorProfileMap.get(mentorUserId),
                        packageSummaryMap.get(mentorUserId),
                        withdrawalSummaryMap.get(mentorUserId),
                        normalizedKeyword,
                        normalizedApprovalStatus,
                        normalizedWithdrawalStatus
                ))
                .map(mentorUserId -> toAdminMentorOpsRow(
                        mentorUserMap.get(mentorUserId),
                        mentorProfileMap.get(mentorUserId),
                        packageSummaryMap.get(mentorUserId),
                        scheduleSummaryMap.get(mentorUserId),
                        orderSummaryMap.get(mentorUserId),
                        withdrawalSummaryMap.get(mentorUserId)
                ))
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(
                                AdminMentorOpsRow::profileUpdatedAt,
                                Comparator.nullsLast(Comparator.reverseOrder())
                        )
                        .thenComparing(AdminMentorOpsRow::mentorUserId, Comparator.reverseOrder()))
                .toList();
    }

    public Optional<AdminWithdrawalRow> findWithdrawalById(long withdrawalId) {
        return mentorWithdrawalRequestJpaRepository.findById(withdrawalId)
                .map(this::toAdminWithdrawalRow);
    }

    public boolean updateWithdrawalStatus(long withdrawalId, String status, String note) {
        Optional<MentorWithdrawalRequestEntity> entityOptional = mentorWithdrawalRequestJpaRepository.findById(withdrawalId);
        if (entityOptional.isEmpty()) {
            return false;
        }
        MentorWithdrawalRequestEntity entity = entityOptional.get();
        entity.updateStatus(status, note);
        mentorWithdrawalRequestJpaRepository.saveAndFlush(entity);
        return true;
    }

    private Map<Long, UserAccountEntity> loadMentorUserMap(Collection<Long> mentorUserIds) {
        if (mentorUserIds == null || mentorUserIds.isEmpty()) {
            return Map.of();
        }
        return userAccountJpaRepository.findAllById(mentorUserIds).stream()
                .filter(entity -> entity.getId() != null)
                .filter(entity -> entity.getRole() == UserRole.MENTOR)
                .filter(entity -> !entity.isDeleted())
                .collect(Collectors.toMap(
                        UserAccountEntity::getId,
                        entity -> entity,
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
    }

    private Map<Long, AdminMentorProfileEntity> loadMentorProfileMap(Collection<Long> mentorUserIds) {
        if (mentorUserIds == null || mentorUserIds.isEmpty()) {
            return Map.of();
        }
        return adminMentorProfileJpaRepository.findAllByUserIdIn(mentorUserIds).stream()
                .filter(entity -> entity.getUserId() != null)
                .collect(Collectors.toMap(
                        AdminMentorProfileEntity::getUserId,
                        entity -> entity,
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
    }

    private Map<Long, PackageSummary> buildPackageSummaryMap(Collection<Long> mentorUserIds) {
        if (mentorUserIds == null || mentorUserIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, PackageAccumulator> accumulatorMap = new LinkedHashMap<>();
        for (MentorServicePackageEntity entity
                : mentorServicePackageJpaRepository.findByMentorUserIdInOrderByMentorUserIdAscSortNoAscIdAsc(mentorUserIds)) {
            if (entity.getMentorUserId() == null) {
                continue;
            }
            PackageAccumulator accumulator = accumulatorMap.computeIfAbsent(entity.getMentorUserId(), ignored -> new PackageAccumulator());
            accumulator.totalPackageCount++;
            if (!entity.isEnabled()) {
                continue;
            }
            accumulator.enabledPackageCount++;
            if ("APPOINTMENT".equals(entity.getDeliveryMode())) {
                accumulator.enabledAppointmentPackageCount++;
            }
            if (entity.getPriceFen() != null) {
                accumulator.startingPriceFen = accumulator.startingPriceFen == null
                        ? entity.getPriceFen()
                        : Math.min(accumulator.startingPriceFen, entity.getPriceFen());
            }
            appendPipeValue(accumulator.enabledPackageNames, entity.getPackageName());
            appendPipeValue(accumulator.enabledSceneLabels, entity.getSceneLabel());
        }
        return accumulatorMap.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> entry.getValue().toSummary(),
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
    }

    private Map<Long, ScheduleSummary> buildScheduleSummaryMap(Collection<Long> mentorUserIds) {
        if (mentorUserIds == null || mentorUserIds.isEmpty()) {
            return Map.of();
        }
        Instant now = Instant.now();
        Instant weekEnd = now.plusSeconds(7L * 24 * 60 * 60);
        Instant threeDayEnd = now.plusSeconds(3L * 24 * 60 * 60);
        Map<Long, ScheduleAccumulator> accumulatorMap = new LinkedHashMap<>();
        for (MentorScheduleSlotEntity entity
                : mentorScheduleSlotJpaRepository.findByMentorUserIdInAndStartAtGreaterThanEqualOrderByMentorUserIdAscStartAtAscIdAsc(
                mentorUserIds,
                now
        )) {
            if (entity.getMentorUserId() == null || entity.getStartAt() == null) {
                continue;
            }
            ScheduleAccumulator accumulator = accumulatorMap.computeIfAbsent(entity.getMentorUserId(), ignored -> new ScheduleAccumulator());
            if ("AVAILABLE".equals(entity.getStatus())) {
                if (entity.getStartAt().isBefore(weekEnd)) {
                    accumulator.weekAvailableSlotCount++;
                }
                if (entity.getStartAt().isBefore(threeDayEnd)) {
                    accumulator.nextThreeDayAvailableSlotCount++;
                }
                if (accumulator.nextAvailableAt == null) {
                    accumulator.nextAvailableAt = entity.getStartAt();
                }
                continue;
            }
            if ("BOOKED".equals(entity.getStatus())) {
                accumulator.upcomingBookedSlotCount++;
                if (accumulator.nextBookedAt == null) {
                    accumulator.nextBookedAt = entity.getStartAt();
                }
            }
        }
        return accumulatorMap.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> entry.getValue().toSummary(),
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
    }

    private Map<Long, OrderSummary> buildOrderSummaryMap(Collection<Long> mentorUserIds, int mentorReplyTimeoutHours) {
        if (mentorUserIds == null || mentorUserIds.isEmpty()) {
            return Map.of();
        }
        List<ConsultOrderEntity> orders = consultOrderJpaRepository.findByMentorUserIdInOrderByMentorUserIdAscIdAsc(mentorUserIds);
        if (orders.isEmpty()) {
            return Map.of();
        }

        Map<String, AfterSalesOrderSummary> afterSalesSummaryMap = buildAfterSalesSummaryMap(
                orders.stream()
                        .map(ConsultOrderEntity::getOrderNo)
                        .filter(Objects::nonNull)
                        .distinct()
                        .toList()
        );

        Instant now = Instant.now();
        Instant expiringDeadline = now.plusSeconds(6L * 60 * 60);
        Map<Long, OrderAccumulator> accumulatorMap = new LinkedHashMap<>();
        for (ConsultOrderEntity entity : orders) {
            if (entity.getMentorUserId() == null) {
                continue;
            }
            OrderAccumulator accumulator = accumulatorMap.computeIfAbsent(entity.getMentorUserId(), ignored -> new OrderAccumulator());
            accumulator.totalOrderCount++;
            if ("PAID".equals(entity.getStatus())) {
                accumulator.paidOrderCount++;
                if (entity.getPaidAt() != null) {
                    Instant replyDeadline = entity.getPaidAt().plusSeconds(Math.max(mentorReplyTimeoutHours, 0) * 3600L);
                    if (!replyDeadline.isAfter(now)) {
                        accumulator.overdueReplyOrderCount++;
                    } else if (!replyDeadline.isAfter(expiringDeadline)) {
                        accumulator.expiringReplyOrderCount++;
                    }
                }
            } else if ("ANSWERED".equals(entity.getStatus())) {
                accumulator.answeredOrderCount++;
            } else if ("CLOSED".equals(entity.getStatus())) {
                accumulator.closedOrderCount++;
            } else if ("REFUNDED".equals(entity.getStatus())) {
                accumulator.refundedOrderCount++;
            }

            AfterSalesOrderSummary afterSalesSummary = afterSalesSummaryMap.getOrDefault(
                    entity.getOrderNo(),
                    AfterSalesOrderSummary.EMPTY
            );
            if (afterSalesSummary.pendingAfterSalesCount() > 0) {
                accumulator.pendingAfterSalesCount++;
            }
            if ("REFUNDED".equals(entity.getStatus()) || afterSalesSummary.afterSalesCount() > 0) {
                accumulator.afterSalesImpactCount++;
            }
            Instant activityAt = entity.getPaidAt() != null ? entity.getPaidAt() : entity.getCreatedAt();
            if (activityAt != null && (accumulator.latestOrderActivityAt == null || activityAt.isAfter(accumulator.latestOrderActivityAt))) {
                accumulator.latestOrderActivityAt = activityAt;
            }
        }

        return accumulatorMap.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> entry.getValue().toSummary(),
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
    }

    private Map<String, AfterSalesOrderSummary> buildAfterSalesSummaryMap(Collection<String> orderNos) {
        if (orderNos == null || orderNos.isEmpty()) {
            return Map.of();
        }
        Map<String, AfterSalesAccumulator> accumulatorMap = new LinkedHashMap<>();
        for (ConsultAfterSalesRequestEntity entity
                : consultAfterSalesRequestJpaRepository.findByOrderNoInOrderByOrderNoAscCreatedAtAscIdAsc(orderNos)) {
            if (entity.getOrderNo() == null) {
                continue;
            }
            AfterSalesAccumulator accumulator = accumulatorMap.computeIfAbsent(entity.getOrderNo(), ignored -> new AfterSalesAccumulator());
            accumulator.afterSalesCount++;
            if ("PENDING".equals(entity.getStatus())) {
                accumulator.pendingAfterSalesCount++;
            }
        }
        return accumulatorMap.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> entry.getValue().toSummary(),
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
    }

    private Map<Long, WithdrawalSummary> buildWithdrawalSummaryMap(Collection<Long> mentorUserIds) {
        if (mentorUserIds == null || mentorUserIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, WithdrawalAccumulator> accumulatorMap = new LinkedHashMap<>();
        for (MentorWithdrawalRequestEntity entity
                : mentorWithdrawalRequestJpaRepository.findByMentorUserIdInOrderByMentorUserIdAscIdDesc(mentorUserIds)) {
            if (entity.getMentorUserId() == null) {
                continue;
            }
            WithdrawalAccumulator accumulator = accumulatorMap.computeIfAbsent(entity.getMentorUserId(), ignored -> new WithdrawalAccumulator());
            if (accumulator.latestWithdrawal == null) {
                accumulator.latestWithdrawal = entity;
            }
            if ("PENDING".equals(entity.getStatus())) {
                accumulator.pendingWithdrawalCount++;
            } else if ("PROCESSING".equals(entity.getStatus())) {
                accumulator.processingWithdrawalCount++;
            } else if ("COMPLETED".equals(entity.getStatus())) {
                accumulator.completedWithdrawalCount++;
            } else if ("REJECTED".equals(entity.getStatus())) {
                accumulator.rejectedWithdrawalCount++;
            }
            if (OPEN_WITHDRAWAL_STATUSES.contains(entity.getStatus()) && entity.getAmountFen() != null) {
                accumulator.openWithdrawalAmountFen += entity.getAmountFen();
            }
        }
        return accumulatorMap.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> entry.getValue().toSummary(),
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
    }

    private AdminMentorOpsRow toAdminMentorOpsRow(
            UserAccountEntity userAccount,
            AdminMentorProfileEntity profile,
            PackageSummary packageSummary,
            ScheduleSummary scheduleSummary,
            OrderSummary orderSummary,
            WithdrawalSummary withdrawalSummary
    ) {
        if (userAccount == null || profile == null || userAccount.getId() == null) {
            return null;
        }
        PackageSummary safePackageSummary = packageSummary == null ? PackageSummary.EMPTY : packageSummary;
        ScheduleSummary safeScheduleSummary = scheduleSummary == null ? ScheduleSummary.EMPTY : scheduleSummary;
        OrderSummary safeOrderSummary = orderSummary == null ? OrderSummary.EMPTY : orderSummary;
        WithdrawalSummary safeWithdrawalSummary = withdrawalSummary == null ? WithdrawalSummary.EMPTY : withdrawalSummary;
        MentorWithdrawalRequestEntity latestWithdrawal = safeWithdrawalSummary.latestWithdrawal();

        return new AdminMentorOpsRow(
                userAccount.getId(),
                userAccount.getDisplayName(),
                profile.isShowRealName() ? userAccount.getRealName() : null,
                profile.isShowRealName(),
                profile.getCompanyName(),
                profile.getJobTitle(),
                profile.getAvatarUrl(),
                profile.getAvatarObjectKey(),
                profile.getAvatarUpdatedAt(),
                profile.getApprovalStatus(),
                profile.isAvailable(),
                safePackageSummary.totalPackageCount(),
                safePackageSummary.enabledPackageCount(),
                safePackageSummary.enabledAppointmentPackageCount(),
                safePackageSummary.startingPriceFen() == null ? safeInt(profile.getPriceFen()) : safePackageSummary.startingPriceFen(),
                safePackageSummary.enabledPackageNames(),
                safePackageSummary.enabledSceneLabels(),
                defaultString(profile.getExpertiseTags()),
                defaultString(profile.getServiceScenes()),
                safeScheduleSummary.weekAvailableSlotCount(),
                safeScheduleSummary.nextThreeDayAvailableSlotCount(),
                safeScheduleSummary.upcomingBookedSlotCount(),
                safeScheduleSummary.nextAvailableAt(),
                safeScheduleSummary.nextBookedAt(),
                safeOrderSummary.totalOrderCount(),
                safeOrderSummary.paidOrderCount(),
                safeOrderSummary.answeredOrderCount(),
                safeOrderSummary.closedOrderCount(),
                safeOrderSummary.refundedOrderCount(),
                safeOrderSummary.overdueReplyOrderCount(),
                safeOrderSummary.expiringReplyOrderCount(),
                safeOrderSummary.pendingAfterSalesCount(),
                safeOrderSummary.afterSalesImpactCount(),
                safeOrderSummary.latestOrderActivityAt(),
                safeWithdrawalSummary.pendingWithdrawalCount(),
                safeWithdrawalSummary.processingWithdrawalCount(),
                safeWithdrawalSummary.completedWithdrawalCount(),
                safeWithdrawalSummary.rejectedWithdrawalCount(),
                safeWithdrawalSummary.openWithdrawalAmountFen(),
                latestWithdrawal == null ? null : latestWithdrawal.getId(),
                latestWithdrawal == null ? null : latestWithdrawal.getAmountFen(),
                latestWithdrawal == null ? null : latestWithdrawal.getStatus(),
                latestWithdrawal == null ? null : latestWithdrawal.getNote(),
                latestWithdrawal == null ? null : latestWithdrawal.getCreatedAt(),
                latestWithdrawal == null ? null : latestWithdrawal.getUpdatedAt(),
                profile.getAvgRating(),
                profile.getUpdatedAt()
        );
    }

    private AdminWithdrawalRow toAdminWithdrawalRow(MentorWithdrawalRequestEntity entity) {
        return new AdminWithdrawalRow(
                entity.getId() == null ? 0L : entity.getId(),
                entity.getMentorUserId() == null ? 0L : entity.getMentorUserId(),
                safeInt(entity.getAmountFen()),
                entity.getStatus(),
                entity.getNote(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private boolean matchesFilters(
            UserAccountEntity userAccount,
            AdminMentorProfileEntity profile,
            PackageSummary packageSummary,
            WithdrawalSummary withdrawalSummary,
            String normalizedKeyword,
            String normalizedApprovalStatus,
            String normalizedWithdrawalStatus
    ) {
        if (userAccount == null || profile == null) {
            return false;
        }
        if (normalizedApprovalStatus != null && !normalizedApprovalStatus.equals(profile.getApprovalStatus())) {
            return false;
        }
        MentorWithdrawalRequestEntity latestWithdrawal = withdrawalSummary == null ? null : withdrawalSummary.latestWithdrawal();
        if (normalizedWithdrawalStatus != null
                && (latestWithdrawal == null || !normalizedWithdrawalStatus.equals(latestWithdrawal.getStatus()))) {
            return false;
        }
        return matchesKeyword(userAccount, profile, packageSummary, normalizedKeyword);
    }

    private boolean matchesKeyword(
            UserAccountEntity userAccount,
            AdminMentorProfileEntity profile,
            PackageSummary packageSummary,
            String normalizedKeyword
    ) {
        if (normalizedKeyword == null) {
            return true;
        }
        PackageSummary safePackageSummary = packageSummary == null ? PackageSummary.EMPTY : packageSummary;
        return contains(userAccount.getDisplayName(), normalizedKeyword)
                || contains(userAccount.getRealName(), normalizedKeyword)
                || contains(profile.getCompanyName(), normalizedKeyword)
                || contains(profile.getJobTitle(), normalizedKeyword)
                || contains(safePackageSummary.enabledPackageNames(), normalizedKeyword)
                || contains(safePackageSummary.enabledSceneLabels(), normalizedKeyword)
                || contains(profile.getExpertiseTags(), normalizedKeyword)
                || contains(profile.getServiceScenes(), normalizedKeyword);
    }

    private boolean contains(String value, String keyword) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(keyword);
    }

    private String normalizeKeyword(String keyword) {
        if (keyword == null) {
            return null;
        }
        String normalized = keyword.trim().toLowerCase(Locale.ROOT);
        return normalized.isEmpty() ? null : normalized;
    }

    private String normalizeStatus(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private void appendPipeValue(List<String> values, String value) {
        String normalized = normalizeText(value);
        if (normalized != null) {
            values.add(normalized);
        }
    }

    private String normalizeText(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private String defaultString(String value) {
        return value == null ? "" : value;
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }

    public record AdminMentorOpsRow(
            long mentorUserId,
            String displayName,
            String realName,
            boolean showRealName,
            String companyName,
            String jobTitle,
            String avatarUrl,
            String avatarObjectKey,
            Instant avatarUpdatedAt,
            String approvalStatus,
            boolean available,
            int totalPackageCount,
            int enabledPackageCount,
            int enabledAppointmentPackageCount,
            int startingPriceFen,
            String enabledPackageNames,
            String enabledSceneLabels,
            String expertiseTags,
            String serviceScenes,
            int weekAvailableSlotCount,
            int nextThreeDayAvailableSlotCount,
            int upcomingBookedSlotCount,
            Instant nextAvailableAt,
            Instant nextBookedAt,
            long totalOrderCount,
            long paidOrderCount,
            long answeredOrderCount,
            long closedOrderCount,
            long refundedOrderCount,
            long overdueReplyOrderCount,
            long expiringReplyOrderCount,
            long pendingAfterSalesCount,
            long afterSalesImpactCount,
            Instant latestOrderActivityAt,
            int pendingWithdrawalCount,
            int processingWithdrawalCount,
            int completedWithdrawalCount,
            int rejectedWithdrawalCount,
            int openWithdrawalAmountFen,
            Long latestWithdrawalId,
            Integer latestWithdrawalAmountFen,
            String latestWithdrawalStatus,
            String latestWithdrawalNote,
            Instant latestWithdrawalCreatedAt,
            Instant latestWithdrawalUpdatedAt,
            BigDecimal avgRating,
            Instant profileUpdatedAt
    ) {
    }

    public record AdminWithdrawalRow(
            long id,
            long mentorUserId,
            int amountFen,
            String status,
            String note,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    private record PackageSummary(
            int totalPackageCount,
            int enabledPackageCount,
            int enabledAppointmentPackageCount,
            Integer startingPriceFen,
            String enabledPackageNames,
            String enabledSceneLabels
    ) {
        private static final PackageSummary EMPTY = new PackageSummary(0, 0, 0, null, null, null);
    }

    private record ScheduleSummary(
            int weekAvailableSlotCount,
            int nextThreeDayAvailableSlotCount,
            int upcomingBookedSlotCount,
            Instant nextAvailableAt,
            Instant nextBookedAt
    ) {
        private static final ScheduleSummary EMPTY = new ScheduleSummary(0, 0, 0, null, null);
    }

    private record OrderSummary(
            long totalOrderCount,
            long paidOrderCount,
            long answeredOrderCount,
            long closedOrderCount,
            long refundedOrderCount,
            long overdueReplyOrderCount,
            long expiringReplyOrderCount,
            long pendingAfterSalesCount,
            long afterSalesImpactCount,
            Instant latestOrderActivityAt
    ) {
        private static final OrderSummary EMPTY = new OrderSummary(0, 0, 0, 0, 0, 0, 0, 0, 0, null);
    }

    private record AfterSalesOrderSummary(long afterSalesCount, long pendingAfterSalesCount) {
        private static final AfterSalesOrderSummary EMPTY = new AfterSalesOrderSummary(0, 0);
    }

    private record WithdrawalSummary(
            int pendingWithdrawalCount,
            int processingWithdrawalCount,
            int completedWithdrawalCount,
            int rejectedWithdrawalCount,
            int openWithdrawalAmountFen,
            MentorWithdrawalRequestEntity latestWithdrawal
    ) {
        private static final WithdrawalSummary EMPTY = new WithdrawalSummary(0, 0, 0, 0, 0, null);
    }

    private static final class PackageAccumulator {

        private int totalPackageCount;
        private int enabledPackageCount;
        private int enabledAppointmentPackageCount;
        private Integer startingPriceFen;
        private final List<String> enabledPackageNames = new java.util.ArrayList<>();
        private final List<String> enabledSceneLabels = new java.util.ArrayList<>();

        private PackageSummary toSummary() {
            return new PackageSummary(
                    totalPackageCount,
                    enabledPackageCount,
                    enabledAppointmentPackageCount,
                    startingPriceFen,
                    enabledPackageNames.isEmpty() ? null : String.join("||", enabledPackageNames),
                    enabledSceneLabels.isEmpty() ? null : String.join("||", enabledSceneLabels)
            );
        }
    }

    private static final class ScheduleAccumulator {

        private int weekAvailableSlotCount;
        private int nextThreeDayAvailableSlotCount;
        private int upcomingBookedSlotCount;
        private Instant nextAvailableAt;
        private Instant nextBookedAt;

        private ScheduleSummary toSummary() {
            return new ScheduleSummary(
                    weekAvailableSlotCount,
                    nextThreeDayAvailableSlotCount,
                    upcomingBookedSlotCount,
                    nextAvailableAt,
                    nextBookedAt
            );
        }
    }

    private static final class OrderAccumulator {

        private long totalOrderCount;
        private long paidOrderCount;
        private long answeredOrderCount;
        private long closedOrderCount;
        private long refundedOrderCount;
        private long overdueReplyOrderCount;
        private long expiringReplyOrderCount;
        private long pendingAfterSalesCount;
        private long afterSalesImpactCount;
        private Instant latestOrderActivityAt;

        private OrderSummary toSummary() {
            return new OrderSummary(
                    totalOrderCount,
                    paidOrderCount,
                    answeredOrderCount,
                    closedOrderCount,
                    refundedOrderCount,
                    overdueReplyOrderCount,
                    expiringReplyOrderCount,
                    pendingAfterSalesCount,
                    afterSalesImpactCount,
                    latestOrderActivityAt
            );
        }
    }

    private static final class AfterSalesAccumulator {

        private long afterSalesCount;
        private long pendingAfterSalesCount;

        private AfterSalesOrderSummary toSummary() {
            return new AfterSalesOrderSummary(afterSalesCount, pendingAfterSalesCount);
        }
    }

    private static final class WithdrawalAccumulator {

        private int pendingWithdrawalCount;
        private int processingWithdrawalCount;
        private int completedWithdrawalCount;
        private int rejectedWithdrawalCount;
        private int openWithdrawalAmountFen;
        private MentorWithdrawalRequestEntity latestWithdrawal;

        private WithdrawalSummary toSummary() {
            return new WithdrawalSummary(
                    pendingWithdrawalCount,
                    processingWithdrawalCount,
                    completedWithdrawalCount,
                    rejectedWithdrawalCount,
                    openWithdrawalAmountFen,
                    latestWithdrawal
            );
        }
    }
}
