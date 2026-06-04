package com.bishe.server.certification.repository;

import com.bishe.server.auth.SystemUserPolicy;
import com.bishe.server.auth.model.UserRole;
import com.bishe.server.auth.repository.jpa.EnterpriseApprovalProfileJpaRepository;
import com.bishe.server.auth.repository.jpa.MentorApprovalProfileJpaRepository;
import com.bishe.server.auth.repository.jpa.UserAccountJpaRepository;
import com.bishe.server.auth.repository.jpa.entity.UserAccountEntity;
import com.bishe.server.certification.repository.jpa.CertificationSubmissionAssetJpaRepository;
import com.bishe.server.certification.repository.jpa.CertificationSubmissionJpaRepository;
import com.bishe.server.certification.repository.jpa.entity.CertificationSubmissionAssetEntity;
import com.bishe.server.certification.repository.jpa.entity.CertificationSubmissionEntity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 认证资料提交与附件仓储。
 */
@Repository
public class CertificationRepository {

    private static final String ASSET_ACTIVE = "ACTIVE";
    private static final String SYSTEM_EMAIL_PATTERN = "%@system.local";
    private static final List<UserRole> REVIEW_ROLES = List.of(UserRole.MENTOR, UserRole.ENTERPRISE);

    private final CertificationSubmissionJpaRepository certificationSubmissionJpaRepository;
    private final CertificationSubmissionAssetJpaRepository certificationSubmissionAssetJpaRepository;
    private final UserAccountJpaRepository userAccountJpaRepository;
    private final MentorApprovalProfileJpaRepository mentorApprovalProfileJpaRepository;
    private final EnterpriseApprovalProfileJpaRepository enterpriseApprovalProfileJpaRepository;
    private final EntityManager entityManager;

    public CertificationRepository(
            CertificationSubmissionJpaRepository certificationSubmissionJpaRepository,
            CertificationSubmissionAssetJpaRepository certificationSubmissionAssetJpaRepository,
            UserAccountJpaRepository userAccountJpaRepository,
            MentorApprovalProfileJpaRepository mentorApprovalProfileJpaRepository,
            EnterpriseApprovalProfileJpaRepository enterpriseApprovalProfileJpaRepository,
            EntityManager entityManager
    ) {
        this.certificationSubmissionJpaRepository = certificationSubmissionJpaRepository;
        this.certificationSubmissionAssetJpaRepository = certificationSubmissionAssetJpaRepository;
        this.userAccountJpaRepository = userAccountJpaRepository;
        this.mentorApprovalProfileJpaRepository = mentorApprovalProfileJpaRepository;
        this.enterpriseApprovalProfileJpaRepository = enterpriseApprovalProfileJpaRepository;
        this.entityManager = entityManager;
    }

    public Optional<SubmissionRow> findCurrentSubmission(long userId) {
        return certificationSubmissionJpaRepository.findFirstByUserIdAndCurrentTrueOrderBySubmittedAtDescIdDesc(userId)
                .map(this::toSubmissionRow);
    }

    public Optional<SubmissionRow> findSubmissionById(long submissionId) {
        return certificationSubmissionJpaRepository.findById(submissionId)
                .map(this::toSubmissionRow);
    }

    public List<SubmissionRow> findSubmissionsByUserId(long userId) {
        return certificationSubmissionJpaRepository.findByUserIdOrderBySubmittedAtDescIdDesc(userId).stream()
                .map(this::toSubmissionRow)
                .toList();
    }

    public List<AssetRow> findAssetsBySubmissionIds(Collection<Long> submissionIds) {
        if (submissionIds == null || submissionIds.isEmpty()) {
            return List.of();
        }
        return certificationSubmissionAssetJpaRepository.findBySubmissionIdInOrderByCreatedAtDescIdDesc(submissionIds).stream()
                .map(this::toAssetRow)
                .toList();
    }

    public List<AssetRow> findActiveAssetsBySubmissionId(long submissionId) {
        return certificationSubmissionAssetJpaRepository
                .findBySubmissionIdAndLifecycleStatusOrderByCreatedAtDescIdDesc(submissionId, ASSET_ACTIVE)
                .stream()
                .map(this::toAssetRow)
                .toList();
    }

    public ReviewSubjectRow findReviewSubject(long userId) {
        Optional<UserAccountEntity> user = userAccountJpaRepository.findByIdAndDeletedFalse(userId);
        if (user.isEmpty() || SystemUserPolicy.isSystemUserEmail(user.get().getEmail())) {
            return null;
        }
        String approvalStatus = switch (user.get().getRole()) {
            case MENTOR -> mentorApprovalProfileJpaRepository.findApprovalStatusByUserId(userId)
                    .orElse(null);
            case ENTERPRISE -> enterpriseApprovalProfileJpaRepository.findApprovalStatusByUserId(userId)
                    .orElse(null);
            default -> null;
        };
        return new ReviewSubjectRow(
                user.get().getId(),
                user.get().getEmail(),
                user.get().getDisplayName(),
                user.get().getRole().name(),
                approvalStatus
        );
    }

    public Optional<AssetAccessRow> findAssetAccessRow(long assetId) {
        Optional<CertificationSubmissionAssetEntity> asset = certificationSubmissionAssetJpaRepository.findById(assetId);
        if (asset.isEmpty()) {
            return Optional.empty();
        }
        return certificationSubmissionJpaRepository.findById(asset.get().getSubmissionId())
                .map(submission -> new AssetAccessRow(
                        asset.get().getId(),
                        submission.getId(),
                        submission.getUserId(),
                        submission.getUserRole(),
                        asset.get().getStorageBucket(),
                        asset.get().getObjectKey(),
                        asset.get().getOriginalFilename(),
                        asset.get().getContentType(),
                        asset.get().getSizeBytes(),
                        asset.get().getLifecycleStatus()
                ));
    }

    public List<ReviewListRow> findCurrentReviewItems(String keyword, String role, String status, int page, int size) {
        List<ReviewListBaseRow> baseRows = queryReviewListBaseRows(keyword, role, status, page, size);
        if (baseRows.isEmpty()) {
            return List.of();
        }
        Map<Long, SubmissionAssetSummaryRow> assetSummaryBySubmissionId = findActiveAssetSummary(baseRows.stream()
                .map(ReviewListBaseRow::submissionId)
                .toList());
        return baseRows.stream()
                .map(row -> {
                    SubmissionAssetSummaryRow summary = assetSummaryBySubmissionId.get(row.submissionId());
                    return new ReviewListRow(
                            row.userId(),
                            row.email(),
                            row.displayName(),
                            row.role(),
                            row.approvalStatus(),
                            row.submissionId(),
                            row.submissionStatus(),
                            row.realName(),
                            row.companyName(),
                            row.jobTitle(),
                            summary == null ? 0 : summary.activeAssetCount(),
                            summary == null ? null : summary.primaryAssetName(),
                            row.submittedAt(),
                            row.reviewedAt()
                    );
                })
                .toList();
    }

    public long countCurrentReviewItems(String keyword, String role, String status) {
        StringBuilder jpql = new StringBuilder("""
                select count(submission.id)
                  from CertificationSubmissionEntity submission
                  join UserAccountEntity user on user.id = submission.userId
                 where submission.current = true
                   and user.deleted = false
                   and user.email not like :systemEmailPattern
                   and user.role in :reviewRoles
                """);
        ReviewQueryParameters parameters = appendReviewFilters(jpql, keyword, role, status);
        TypedQuery<Long> query = entityManager.createQuery(jpql.toString(), Long.class);
        bindReviewQueryParameters(query, parameters);
        Long total = query.getSingleResult();
        return total == null ? 0L : total;
    }

    @Transactional
    public long createSubmission(
            long userId,
            String role,
            String realName,
            String companyName,
            String jobTitle,
            String status,
            Long previousSubmissionId,
            Instant submittedAt
    ) {
        CertificationSubmissionEntity entity = CertificationSubmissionEntity.create(
                userId,
                role,
                realName,
                companyName,
                jobTitle,
                status,
                previousSubmissionId,
                submittedAt
        );
        return certificationSubmissionJpaRepository.saveAndFlush(entity).getId();
    }

    @Transactional
    public long createAsset(
            long submissionId,
            String bucket,
            String objectKey,
            String originalFilename,
            String contentType,
            long sizeBytes
    ) {
        CertificationSubmissionAssetEntity entity = CertificationSubmissionAssetEntity.create(
                submissionId,
                bucket,
                objectKey,
                originalFilename,
                contentType,
                sizeBytes
        );
        return certificationSubmissionAssetJpaRepository.saveAndFlush(entity).getId();
    }

    @Transactional
    public void markCurrentSubmissionReplaced(long userId) {
        certificationSubmissionJpaRepository.markCurrentSubmissionReplaced(userId, Instant.now());
    }

    @Transactional
    public void markAssetsLifecycle(Collection<Long> assetIds, String lifecycleStatus, String deleteReason, Instant deletedAt) {
        if (assetIds == null || assetIds.isEmpty()) {
            return;
        }
        certificationSubmissionAssetJpaRepository.updateLifecycle(
                assetIds,
                lifecycleStatus,
                deleteReason,
                deletedAt,
                Instant.now()
        );
    }

    @Transactional
    public void updateSubmissionReview(long submissionId, String status, String reviewNote, Long reviewedBy, Instant reviewedAt) {
        certificationSubmissionJpaRepository.updateSubmissionReview(
                submissionId,
                status,
                reviewNote,
                reviewedBy,
                reviewedAt,
                Instant.now()
        );
    }

    private List<ReviewListBaseRow> queryReviewListBaseRows(String keyword, String role, String status, int page, int size) {
        StringBuilder jpql = new StringBuilder("""
                select submission.id,
                       user.id,
                       user.email,
                       user.displayName,
                       user.role,
                       case
                           when user.role = :mentorRole then mentorProfile.approvalStatus
                           when user.role = :enterpriseRole then enterpriseProfile.approvalStatus
                           else null
                       end,
                       submission.status,
                       submission.realName,
                       submission.companyName,
                       submission.jobTitle,
                       submission.submittedAt,
                       submission.reviewedAt
                  from CertificationSubmissionEntity submission
                  join UserAccountEntity user on user.id = submission.userId
             left join MentorApprovalProfileEntity mentorProfile on mentorProfile.userId = user.id
             left join EnterpriseApprovalProfileEntity enterpriseProfile on enterpriseProfile.userId = user.id
                 where submission.current = true
                   and user.deleted = false
                   and user.email not like :systemEmailPattern
                   and user.role in :reviewRoles
                """);
        ReviewQueryParameters parameters = appendReviewFilters(jpql, keyword, role, status);
        jpql.append(" order by submission.submittedAt desc, submission.id desc");

        TypedQuery<Object[]> query = entityManager.createQuery(jpql.toString(), Object[].class);
        bindReviewQueryParameters(query, parameters);
        query.setFirstResult(Math.max(page - 1, 0) * size);
        query.setMaxResults(size);
        return query.getResultList().stream()
                .map(this::toReviewListBaseRow)
                .toList();
    }

    private ReviewQueryParameters appendReviewFilters(StringBuilder jpql, String keyword, String role, String status) {
        UserRole roleFilter = null;
        if (role != null && !role.isBlank()) {
            roleFilter = UserRole.valueOf(role.trim().toUpperCase());
            jpql.append(" and user.role = :roleFilter");
        }

        String statusFilter = null;
        if (status != null && !status.isBlank()) {
            statusFilter = status.trim().toUpperCase();
            jpql.append(" and submission.status = :statusFilter");
        }

        String normalizedKeyword = null;
        String rawKeyword = null;
        if (keyword != null && !keyword.isBlank()) {
            normalizedKeyword = "%" + keyword.trim().toLowerCase() + "%";
            rawKeyword = "%" + keyword.trim() + "%";
            jpql.append("""
                     and (
                         lower(user.email) like :keyword
                      or lower(user.displayName) like :keyword
                      or lower(submission.realName) like :keyword
                      or lower(coalesce(submission.companyName, '')) like :keyword
                      or lower(coalesce(submission.jobTitle, '')) like :keyword
                      or str(user.id) like :rawKeyword
                     )
                    """);
        }

        return new ReviewQueryParameters(roleFilter, statusFilter, normalizedKeyword, rawKeyword);
    }

    private void bindReviewQueryParameters(TypedQuery<?> query, ReviewQueryParameters parameters) {
        if (hasParameter(query, "mentorRole")) {
            query.setParameter("mentorRole", UserRole.MENTOR);
        }
        if (hasParameter(query, "enterpriseRole")) {
            query.setParameter("enterpriseRole", UserRole.ENTERPRISE);
        }
        if (hasParameter(query, "systemEmailPattern")) {
            query.setParameter("systemEmailPattern", SYSTEM_EMAIL_PATTERN);
        }
        if (hasParameter(query, "reviewRoles")) {
            query.setParameter("reviewRoles", REVIEW_ROLES);
        }
        if (parameters.roleFilter() != null && hasParameter(query, "roleFilter")) {
            query.setParameter("roleFilter", parameters.roleFilter());
        }
        if (parameters.statusFilter() != null && hasParameter(query, "statusFilter")) {
            query.setParameter("statusFilter", parameters.statusFilter());
        }
        if (parameters.keyword() != null && hasParameter(query, "keyword")) {
            query.setParameter("keyword", parameters.keyword());
        }
        if (parameters.rawKeyword() != null && hasParameter(query, "rawKeyword")) {
            query.setParameter("rawKeyword", parameters.rawKeyword());
        }
    }

    private boolean hasParameter(TypedQuery<?> query, String parameterName) {
        return query.getParameters().stream()
                .anyMatch(parameter -> parameterName.equals(parameter.getName()));
    }

    private Map<Long, SubmissionAssetSummaryRow> findActiveAssetSummary(List<Long> submissionIds) {
        if (submissionIds.isEmpty()) {
            return Map.of();
        }
        List<Object[]> rows = entityManager.createQuery("""
                select asset.submissionId,
                       count(asset.id),
                       min(asset.originalFilename)
                  from CertificationSubmissionAssetEntity asset
                 where asset.submissionId in :submissionIds
                   and asset.lifecycleStatus = :lifecycleStatus
              group by asset.submissionId
                """, Object[].class)
                .setParameter("submissionIds", submissionIds)
                .setParameter("lifecycleStatus", ASSET_ACTIVE)
                .getResultList();
        Map<Long, SubmissionAssetSummaryRow> result = new LinkedHashMap<>();
        for (Object[] row : rows) {
            Long submissionId = (Long) row[0];
            long activeAssetCount = ((Number) row[1]).longValue();
            String primaryAssetName = (String) row[2];
            result.put(submissionId, new SubmissionAssetSummaryRow(Math.toIntExact(activeAssetCount), primaryAssetName));
        }
        return result;
    }

    private SubmissionRow toSubmissionRow(CertificationSubmissionEntity entity) {
        return new SubmissionRow(
                entity.getId(),
                entity.getUserId(),
                entity.getUserRole(),
                entity.getRealName(),
                entity.getCompanyName(),
                entity.getJobTitle(),
                entity.getStatus(),
                entity.getReviewNote(),
                entity.getReviewedBy(),
                entity.getPreviousSubmissionId(),
                entity.isCurrent(),
                entity.getSubmittedAt(),
                entity.getReviewedAt()
        );
    }

    private AssetRow toAssetRow(CertificationSubmissionAssetEntity entity) {
        return new AssetRow(
                entity.getId(),
                entity.getSubmissionId(),
                entity.getStorageBucket(),
                entity.getObjectKey(),
                entity.getOriginalFilename(),
                entity.getContentType(),
                entity.getSizeBytes(),
                entity.getLifecycleStatus(),
                entity.getDeleteReason(),
                entity.getCreatedAt(),
                entity.getDeletedAt()
        );
    }

    private ReviewListBaseRow toReviewListBaseRow(Object[] row) {
        UserRole role = (UserRole) row[4];
        return new ReviewListBaseRow(
                ((Number) row[1]).longValue(),
                (String) row[2],
                (String) row[3],
                role.name(),
                (String) row[5],
                ((Number) row[0]).longValue(),
                (String) row[6],
                (String) row[7],
                (String) row[8],
                (String) row[9],
                (Instant) row[10],
                (Instant) row[11]
        );
    }

    private record ReviewQueryParameters(
            UserRole roleFilter,
            String statusFilter,
            String keyword,
            String rawKeyword
    ) {
    }

    private record ReviewListBaseRow(
            long userId,
            String email,
            String displayName,
            String role,
            String approvalStatus,
            long submissionId,
            String submissionStatus,
            String realName,
            String companyName,
            String jobTitle,
            Instant submittedAt,
            Instant reviewedAt
    ) {
    }

    private record SubmissionAssetSummaryRow(
            int activeAssetCount,
            String primaryAssetName
    ) {
    }

    public record SubmissionRow(
            long submissionId,
            long userId,
            String role,
            String realName,
            String companyName,
            String jobTitle,
            String status,
            String reviewNote,
            Long reviewedBy,
            Long previousSubmissionId,
            boolean current,
            Instant submittedAt,
            Instant reviewedAt
    ) {
    }

    public record AssetRow(
            long assetId,
            long submissionId,
            String bucket,
            String objectKey,
            String originalFilename,
            String contentType,
            long sizeBytes,
            String lifecycleStatus,
            String deleteReason,
            Instant uploadedAt,
            Instant deletedAt
    ) {
    }

    public record ReviewSubjectRow(
            long userId,
            String email,
            String displayName,
            String role,
            String approvalStatus
    ) {
    }

    public record ReviewListRow(
            long userId,
            String email,
            String displayName,
            String role,
            String approvalStatus,
            long submissionId,
            String submissionStatus,
            String realName,
            String companyName,
            String jobTitle,
            int activeAssetCount,
            String primaryAssetName,
            Instant submittedAt,
            Instant reviewedAt
    ) {
    }

    public record AssetAccessRow(
            long assetId,
            long submissionId,
            long userId,
            String userRole,
            String bucket,
            String objectKey,
            String originalFilename,
            String contentType,
            long sizeBytes,
            String lifecycleStatus
    ) {
    }
}
