package com.bishe.server.mentor.repository;

import com.bishe.server.auth.repository.jpa.UserAccountJpaRepository;
import com.bishe.server.auth.repository.jpa.entity.UserAccountEntity;
import com.bishe.server.consult.repository.jpa.ConsultOrderJpaRepository;
import com.bishe.server.consult.repository.jpa.entity.ConsultOrderEntity;
import com.bishe.server.consult.repository.jpa.PaymentRecordJpaRepository;
import com.bishe.server.consult.repository.jpa.entity.PaymentRecordEntity;
import com.bishe.server.mentor.repository.jpa.MentorFinanceProfileJpaRepository;
import com.bishe.server.mentor.repository.jpa.MentorWithdrawalRequestJpaRepository;
import com.bishe.server.mentor.repository.jpa.entity.MentorFinanceProfileEntity;
import com.bishe.server.mentor.repository.jpa.entity.MentorWithdrawalRequestEntity;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 导师财务中心仓储，负责提现演示记录与相关额度口径查询。
 */
@Repository
public class MentorFinanceRepository {

    private static final ZoneId DISPLAY_ZONE_ID = ZoneId.of("Asia/Shanghai");

    private final MentorWithdrawalRequestJpaRepository mentorWithdrawalRequestJpaRepository;
    private final MentorFinanceProfileJpaRepository mentorFinanceProfileJpaRepository;
    private final ConsultOrderJpaRepository consultOrderJpaRepository;
    private final PaymentRecordJpaRepository paymentRecordJpaRepository;
    private final UserAccountJpaRepository userAccountJpaRepository;

    public MentorFinanceRepository(
            MentorWithdrawalRequestJpaRepository mentorWithdrawalRequestJpaRepository,
            MentorFinanceProfileJpaRepository mentorFinanceProfileJpaRepository,
            ConsultOrderJpaRepository consultOrderJpaRepository,
            PaymentRecordJpaRepository paymentRecordJpaRepository,
            UserAccountJpaRepository userAccountJpaRepository
    ) {
        this.mentorWithdrawalRequestJpaRepository = mentorWithdrawalRequestJpaRepository;
        this.mentorFinanceProfileJpaRepository = mentorFinanceProfileJpaRepository;
        this.consultOrderJpaRepository = consultOrderJpaRepository;
        this.paymentRecordJpaRepository = paymentRecordJpaRepository;
        this.userAccountJpaRepository = userAccountJpaRepository;
    }

    public List<WithdrawalRow> findWithdrawalsByMentorUserId(long mentorUserId) {
        return mentorWithdrawalRequestJpaRepository.findByMentorUserIdOrderByCreatedAtDescIdDesc(mentorUserId)
                .stream()
                .map(this::toWithdrawalRow)
                .toList();
    }

    public Optional<WithdrawalRow> findWithdrawalById(long mentorUserId, long withdrawalId) {
        return mentorWithdrawalRequestJpaRepository.findByMentorUserIdAndId(mentorUserId, withdrawalId)
                .map(this::toWithdrawalRow);
    }

    public long createWithdrawal(long mentorUserId, int amountFen, String note) {
        MentorWithdrawalRequestEntity entity = MentorWithdrawalRequestEntity.create(mentorUserId, amountFen, note);
        mentorWithdrawalRequestJpaRepository.saveAndFlush(entity);
        if (entity.getId() == null) {
            throw new IllegalStateException("failed to create mentor withdrawal");
        }
        return entity.getId();
    }

    public boolean updateWithdrawalStatus(long mentorUserId, long withdrawalId, String status, String note) {
        Optional<MentorWithdrawalRequestEntity> entityOptional = mentorWithdrawalRequestJpaRepository.findByMentorUserIdAndId(mentorUserId, withdrawalId);
        if (entityOptional.isEmpty()) {
            return false;
        }
        MentorWithdrawalRequestEntity entity = entityOptional.get();
        entity.updateStatus(status, note);
        mentorWithdrawalRequestJpaRepository.saveAndFlush(entity);
        return true;
    }

    public int sumCompletedIncomeFen(long mentorUserId) {
        return intValue(consultOrderJpaRepository.sumClosedAmountFenByMentorUserId(mentorUserId));
    }

    public Optional<FinanceOverviewMetricsRow> findOverviewMetrics(long mentorUserId) {
        Optional<MentorFinanceProfileEntity> profileOptional = mentorFinanceProfileJpaRepository.findByUserId(mentorUserId);
        if (profileOptional.isEmpty()) {
            return Optional.empty();
        }
        ConsultOrderJpaRepository.MentorFinanceAggregateProjection aggregate = consultOrderJpaRepository.summarizeMentorFinance(mentorUserId);
        MentorFinanceProfileEntity profile = profileOptional.get();
        return Optional.of(new FinanceOverviewMetricsRow(
                intValue(aggregate == null ? null : aggregate.getTotalRevenueFen()),
                longValue(aggregate == null ? null : aggregate.getTotalRevenueOrderCount()),
                intValue(aggregate == null ? null : aggregate.getCompletedIncomeFen()),
                longValue(aggregate == null ? null : aggregate.getClosedCount()),
                intValue(aggregate == null ? null : aggregate.getRefundedAmountFen()),
                longValue(aggregate == null ? null : aggregate.getRefundedOrderCount()),
                longValue(aggregate == null ? null : aggregate.getAnsweredCount()),
                profile.getAvgRating()
        ));
    }

    public List<FinanceTrendPointRow> findTrendPoints(long mentorUserId, Instant startInclusive, Instant endExclusive) {
        Map<LocalDate, TrendTotals> totalsMap = new LinkedHashMap<>();
        for (ConsultOrderEntity entity : consultOrderJpaRepository.findFinanceTrendOrders(mentorUserId, startInclusive, endExclusive)) {
            if ("REFUNDED".equals(entity.getStatus())) {
                Instant refundAt = entity.getClosedAt() != null ? entity.getClosedAt() : entity.getPaidAt();
                if (refundAt == null) {
                    continue;
                }
                LocalDate bucketDate = toBucketDate(refundAt);
                TrendTotals current = totalsMap.getOrDefault(bucketDate, TrendTotals.ZERO);
                totalsMap.put(bucketDate, current.addRefunded(entity.getAmountFen()));
                continue;
            }
            Instant paidAt = entity.getPaidAt();
            if (paidAt == null) {
                continue;
            }
            LocalDate bucketDate = toBucketDate(paidAt);
            TrendTotals current = totalsMap.getOrDefault(bucketDate, TrendTotals.ZERO);
            totalsMap.put(bucketDate, current.addPaid(entity.getAmountFen()));
        }
        return totalsMap.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> new FinanceTrendPointRow(
                        entry.getKey(),
                        entry.getValue().paidFen(),
                        entry.getValue().refundedFen()
                ))
                .toList();
    }

    public long countFinanceBills(long mentorUserId, FinanceBillQuery query) {
        return consultOrderJpaRepository.count(buildFinanceBillSpecification(mentorUserId, query));
    }

    public List<FinanceBillRow> findFinanceBills(long mentorUserId, FinanceBillQuery query) {
        int safePage = Math.max(query.page(), 1);
        int safeSize = Math.max(query.size(), 1);
        PageRequest pageRequest = PageRequest.of(
                safePage - 1,
                safeSize,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))
        );
        List<ConsultOrderEntity> orders = consultOrderJpaRepository.findAll(buildFinanceBillSpecification(mentorUserId, query), pageRequest)
                .getContent();
        Map<Long, UserAccountEntity> counterpartUserMap = loadUserAccountMap(
                orders.stream()
                        .map(ConsultOrderEntity::getStudentUserId)
                        .filter(Objects::nonNull)
                        .distinct()
                        .toList()
        );
        Map<String, String> latestPaymentModeMap = loadLatestPaymentModeMap(
                orders.stream()
                        .map(ConsultOrderEntity::getOrderNo)
                        .filter(Objects::nonNull)
                        .distinct()
                        .toList()
        );
        return orders.stream()
                .map(order -> {
                    UserAccountEntity counterpart = counterpartUserMap.get(order.getStudentUserId());
                    return new FinanceBillRow(
                            order.getOrderNo(),
                            longValue(order.getStudentUserId()),
                            counterpart == null ? null : counterpart.getDisplayName(),
                            intValue(order.getAmountFen()),
                            order.getStatus(),
                            order.getQuestionText(),
                            latestPaymentModeMap.get(order.getOrderNo()),
                            order.getAppointmentStartAt(),
                            order.getAppointmentEndAt(),
                            order.getCreatedAt(),
                            order.getPaidAt(),
                            order.getClosedAt()
                    );
                })
                .toList();
    }

    public int sumWithdrawalAmountFen(long mentorUserId, List<String> statuses) {
        if (statuses == null || statuses.isEmpty()) {
            return 0;
        }
        return intValue(mentorWithdrawalRequestJpaRepository.sumAmountFenByMentorUserIdAndStatusIn(mentorUserId, statuses));
    }

    private Specification<ConsultOrderEntity> buildFinanceBillSpecification(long mentorUserId, FinanceBillQuery query) {
        String normalizedKeyword = normalizeKeyword(query.keyword());
        Set<Long> matchedStudentUserIds = normalizedKeyword == null
                ? Set.of()
                : new LinkedHashSet<>(userAccountJpaRepository.findIdsByDisplayNameContainingIgnoreCaseAndDeletedFalse(normalizedKeyword));
        Instant rangeStart = resolveRangeStart(query.rangeFilter());
        String normalizedStatusFilter = normalizeStatusFilter(query.statusFilter());
        return (root, criteriaQuery, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(criteriaBuilder.equal(root.get("mentorUserId"), mentorUserId));
            appendKeywordPredicate(root, criteriaBuilder, predicates, normalizedKeyword, matchedStudentUserIds);
            appendStatusPredicate(root, criteriaBuilder, predicates, normalizedStatusFilter);
            if (rangeStart != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(resolveEffectiveTime(root, criteriaBuilder), rangeStart));
            }
            return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
        };
    }

    private void appendKeywordPredicate(
            Root<ConsultOrderEntity> root,
            CriteriaBuilder criteriaBuilder,
            List<Predicate> predicates,
            String normalizedKeyword,
            Set<Long> matchedStudentUserIds
    ) {
        if (normalizedKeyword == null) {
            return;
        }
        String pattern = "%" + normalizedKeyword + "%";
        List<Predicate> keywordPredicates = new ArrayList<>();
        keywordPredicates.add(criteriaBuilder.like(criteriaBuilder.lower(root.get("orderNo")), pattern));
        keywordPredicates.add(criteriaBuilder.like(criteriaBuilder.lower(criteriaBuilder.coalesce(root.get("questionText"), "")), pattern));
        if (!matchedStudentUserIds.isEmpty()) {
            keywordPredicates.add(root.get("studentUserId").in(matchedStudentUserIds));
        }
        predicates.add(criteriaBuilder.or(keywordPredicates.toArray(Predicate[]::new)));
    }

    private void appendStatusPredicate(
            Root<ConsultOrderEntity> root,
            CriteriaBuilder criteriaBuilder,
            List<Predicate> predicates,
            String normalizedStatusFilter
    ) {
        if (normalizedStatusFilter == null || "ALL".equals(normalizedStatusFilter)) {
            return;
        }
        if ("PAID".equals(normalizedStatusFilter)) {
            predicates.add(root.get("status").in(List.of("PAID", "ANSWERED")));
            return;
        }
        if ("COMPLETED".equals(normalizedStatusFilter)) {
            predicates.add(criteriaBuilder.equal(root.get("status"), "CLOSED"));
            return;
        }
        if ("REFUNDED".equals(normalizedStatusFilter)) {
            predicates.add(criteriaBuilder.equal(root.get("status"), "REFUNDED"));
        }
    }

    private Expression<Instant> resolveEffectiveTime(Root<ConsultOrderEntity> root, CriteriaBuilder criteriaBuilder) {
        return criteriaBuilder.<Instant>coalesce()
                .value(root.get("paidAt"))
                .value(root.get("createdAt"));
    }

    private Instant resolveRangeStart(String rangeFilter) {
        if ("30D".equals(rangeFilter)) {
            return Instant.now().minus(30, ChronoUnit.DAYS);
        }
        if ("90D".equals(rangeFilter)) {
            return Instant.now().minus(90, ChronoUnit.DAYS);
        }
        return null;
    }

    private String normalizeKeyword(String keyword) {
        if (keyword == null) {
            return null;
        }
        String normalized = keyword.trim().toLowerCase();
        return normalized.isEmpty() ? null : normalized;
    }

    private String normalizeStatusFilter(String statusFilter) {
        if (statusFilter == null || statusFilter.isBlank()) {
            return "ALL";
        }
        return statusFilter.trim().toUpperCase();
    }

    private Map<Long, UserAccountEntity> loadUserAccountMap(Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        return userAccountJpaRepository.findAllById(userIds).stream()
                .filter(entity -> entity.getId() != null)
                .collect(Collectors.toMap(
                        UserAccountEntity::getId,
                        entity -> entity,
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
    }

    private Map<String, String> loadLatestPaymentModeMap(Collection<String> orderNos) {
        if (orderNos == null || orderNos.isEmpty()) {
            return Map.of();
        }
        Map<String, String> result = new LinkedHashMap<>();
        for (PaymentRecordEntity entity : paymentRecordJpaRepository.findByOrderNoInOrderByOrderNoAscIdDesc(orderNos)) {
            if (entity.getOrderNo() == null) {
                continue;
            }
            result.putIfAbsent(entity.getOrderNo(), entity.getMode());
        }
        return result;
    }

    private WithdrawalRow toWithdrawalRow(MentorWithdrawalRequestEntity entity) {
        return new WithdrawalRow(
                longValue(entity.getId()),
                longValue(entity.getMentorUserId()),
                intValue(entity.getAmountFen()),
                entity.getStatus(),
                entity.getNote(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private LocalDate toBucketDate(Instant instant) {
        return instant.atZone(DISPLAY_ZONE_ID).toLocalDate();
    }

    private int intValue(Number value) {
        return value == null ? 0 : value.intValue();
    }

    private long longValue(Number value) {
        return value == null ? 0L : value.longValue();
    }

    private record TrendTotals(int paidFen, int refundedFen) {

        private static final TrendTotals ZERO = new TrendTotals(0, 0);

        private TrendTotals addPaid(Integer amountFen) {
            return new TrendTotals(paidFen + (amountFen == null ? 0 : amountFen), refundedFen);
        }

        private TrendTotals addRefunded(Integer amountFen) {
            return new TrendTotals(paidFen, refundedFen + (amountFen == null ? 0 : amountFen));
        }
    }

    /**
     * 提现演示记录行。
     */
    public record WithdrawalRow(
            long id,
            long mentorUserId,
            int amountFen,
            String status,
            String note,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    /**
     * 财务概览指标行。
     */
    public record FinanceOverviewMetricsRow(
            int totalRevenueFen,
            long totalRevenueOrderCount,
            int completedIncomeFen,
            long closedCount,
            int refundedAmountFen,
            long refundedOrderCount,
            long answeredCount,
            BigDecimal avgRating
    ) {
    }

    /**
     * 财务趋势聚合点。
     */
    public record FinanceTrendPointRow(
            LocalDate bucketDate,
            int paidFen,
            int refundedFen
    ) {
    }

    /**
     * 财务账单筛选查询。
     */
    public record FinanceBillQuery(
            String keyword,
            String statusFilter,
            String rangeFilter,
            int page,
            int size
    ) {
    }

    /**
     * 财务账单行。
     */
    public record FinanceBillRow(
            String orderNo,
            long counterpartUserId,
            String counterpartDisplayName,
            int amountFen,
            String status,
            String questionText,
            String paymentMode,
            Instant appointmentStartAt,
            Instant appointmentEndAt,
            Instant createdAt,
            Instant paidAt,
            Instant closedAt
    ) {
    }
}
