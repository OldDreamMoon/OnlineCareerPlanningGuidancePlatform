package com.bishe.server.bounty.repository;

import com.bishe.server.auth.repository.jpa.UserAccountJpaRepository;
import com.bishe.server.auth.repository.jpa.entity.UserAccountEntity;
import com.bishe.server.bounty.repository.jpa.BountyTaskAdminOpsJpaRepository;
import com.bishe.server.profile.repository.jpa.EnterpriseProfileJpaRepository;
import com.bishe.server.profile.repository.jpa.entity.EnterpriseProfileEntity;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Repository
public class AdminBountyOpsRepository {

    private final BountyTaskAdminOpsJpaRepository bountyTaskAdminOpsJpaRepository;
    private final EnterpriseProfileJpaRepository enterpriseProfileJpaRepository;
    private final UserAccountJpaRepository userAccountJpaRepository;

    public AdminBountyOpsRepository(
            BountyTaskAdminOpsJpaRepository bountyTaskAdminOpsJpaRepository,
            EnterpriseProfileJpaRepository enterpriseProfileJpaRepository,
            UserAccountJpaRepository userAccountJpaRepository
    ) {
        this.bountyTaskAdminOpsJpaRepository = bountyTaskAdminOpsJpaRepository;
        this.enterpriseProfileJpaRepository = enterpriseProfileJpaRepository;
        this.userAccountJpaRepository = userAccountJpaRepository;
    }

    public List<AdminTaskOpsRow> findTaskOps(String keyword, String status) {
        String normalizedStatus = normalizeStatus(status);
        List<AdminTaskOpsRow> rows = toRows(bountyTaskAdminOpsJpaRepository.findTaskOps(normalizedStatus));
        if (keyword == null || keyword.isBlank()) {
            return rows;
        }
        String normalizedKeyword = keyword.trim().toLowerCase(Locale.ROOT);
        return rows.stream()
                .filter(row -> containsKeyword(row, normalizedKeyword))
                .toList();
    }

    public Optional<AdminTaskOpsRow> findTaskOpsById(long taskId) {
        List<AdminTaskOpsRow> rows = toRows(bountyTaskAdminOpsJpaRepository.findTaskOpsById(taskId).stream().toList());
        return rows.stream().findFirst();
    }

    private List<AdminTaskOpsRow> toRows(List<BountyTaskAdminOpsJpaRepository.TaskOpsView> taskViews) {
        Map<Long, EnterpriseProfileEntity> enterpriseProfileMap = enterpriseProfileJpaRepository.findAllByUserIdIn(collectEnterpriseUserIds(taskViews))
                .stream()
                .collect(Collectors.toMap(EnterpriseProfileEntity::getUserId, Function.identity(), (left, right) -> right));
        Map<Long, UserAccountEntity> userAccountMap = userAccountJpaRepository.findAllById(collectEnterpriseUserIds(taskViews))
                .stream()
                .collect(Collectors.toMap(UserAccountEntity::getId, Function.identity(), (left, right) -> right));
        return taskViews.stream()
                .map(view -> toRow(view, enterpriseProfileMap.get(view.getEnterpriseUserId()), userAccountMap.get(view.getEnterpriseUserId())))
                .toList();
    }

    private Collection<Long> collectEnterpriseUserIds(List<BountyTaskAdminOpsJpaRepository.TaskOpsView> taskViews) {
        return taskViews.stream()
                .map(BountyTaskAdminOpsJpaRepository.TaskOpsView::getEnterpriseUserId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
    }

    private AdminTaskOpsRow toRow(
            BountyTaskAdminOpsJpaRepository.TaskOpsView view,
            EnterpriseProfileEntity enterpriseProfile,
            UserAccountEntity userAccount
    ) {
        String enterpriseName = resolveEnterpriseName(enterpriseProfile, userAccount);
        return new AdminTaskOpsRow(
                defaultLong(view.getTaskId()),
                defaultLong(view.getEnterpriseUserId()),
                enterpriseName,
                enterpriseProfile == null ? null : enterpriseProfile.getLogoObjectKey(),
                enterpriseProfile == null ? null : enterpriseProfile.getLogoUpdatedAt(),
                enterpriseProfile == null ? "PENDING" : enterpriseProfile.getApprovalStatus(),
                view.getTitle(),
                view.getDescription(),
                view.getRewardDescription(),
                view.getStatus(),
                view.getAcceptedSubmissionId(),
                view.getDeadlineAt(),
                view.getClosedAt(),
                view.getCreatedAt(),
                view.getUpdatedAt(),
                safeInt(view.getSubmissionCount()),
                safeInt(view.getPendingSubmissionCount()),
                safeInt(view.getAcceptedSubmissionCount()),
                safeInt(view.getRejectedSubmissionCount()),
                view.getLatestSubmissionAt()
        );
    }

    private String resolveEnterpriseName(EnterpriseProfileEntity enterpriseProfile, UserAccountEntity userAccount) {
        if (enterpriseProfile != null && enterpriseProfile.getCompanyName() != null && !enterpriseProfile.getCompanyName().isBlank()) {
            return enterpriseProfile.getCompanyName();
        }
        if (userAccount != null && userAccount.getDisplayName() != null && !userAccount.getDisplayName().isBlank()) {
            return userAccount.getDisplayName();
        }
        return "";
    }

    private boolean containsKeyword(AdminTaskOpsRow row, String normalizedKeyword) {
        return contains(row.title(), normalizedKeyword)
                || contains(row.description(), normalizedKeyword)
                || contains(row.rewardDescription(), normalizedKeyword)
                || contains(row.enterpriseName(), normalizedKeyword);
    }

    private boolean contains(String value, String keyword) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(keyword);
    }

    private String normalizeStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        return status.trim().toUpperCase(Locale.ROOT);
    }

    private long defaultLong(Long value) {
        return value == null ? 0L : value;
    }

    private int safeInt(long value) {
        return Math.toIntExact(value);
    }

    public record AdminTaskOpsRow(
            long taskId,
            long enterpriseUserId,
            String enterpriseName,
            String enterpriseLogoObjectKey,
            Instant enterpriseLogoUpdatedAt,
            String enterpriseApprovalStatus,
            String title,
            String description,
            String rewardDescription,
            String status,
            Long acceptedSubmissionId,
            Instant deadlineAt,
            Instant closedAt,
            Instant createdAt,
            Instant updatedAt,
            int submissionCount,
            int pendingSubmissionCount,
            int acceptedSubmissionCount,
            int rejectedSubmissionCount,
            Instant latestSubmissionAt
    ) {
    }
}
