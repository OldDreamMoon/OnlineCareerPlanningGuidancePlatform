package com.bishe.server.auth.repository;

import com.bishe.server.auth.SystemUserPolicy;
import com.bishe.server.auth.model.AppUser;
import com.bishe.server.auth.model.UserAccountStatus;
import com.bishe.server.auth.model.UserRole;
import com.bishe.server.auth.repository.jpa.EnterpriseApprovalProfileJpaRepository;
import com.bishe.server.auth.repository.jpa.MentorApprovalProfileJpaRepository;
import com.bishe.server.auth.repository.jpa.UserAccountJpaRepository;
import com.bishe.server.auth.repository.jpa.entity.UserAccountEntity;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * 用户仓储，当前版本使用 Spring Data JPA 承接持久化访问。
 */
@Repository
public class UserRepository {

    private static final Sort ADMIN_USER_SORT = Sort.by(
            Sort.Order.desc("createdAt"),
            Sort.Order.desc("id")
    );

    private final UserAccountJpaRepository userAccountJpaRepository;
    private final MentorApprovalProfileJpaRepository mentorApprovalProfileJpaRepository;
    private final EnterpriseApprovalProfileJpaRepository enterpriseApprovalProfileJpaRepository;

    public UserRepository(
            UserAccountJpaRepository userAccountJpaRepository,
            MentorApprovalProfileJpaRepository mentorApprovalProfileJpaRepository,
            EnterpriseApprovalProfileJpaRepository enterpriseApprovalProfileJpaRepository
    ) {
        this.userAccountJpaRepository = userAccountJpaRepository;
        this.mentorApprovalProfileJpaRepository = mentorApprovalProfileJpaRepository;
        this.enterpriseApprovalProfileJpaRepository = enterpriseApprovalProfileJpaRepository;
    }

    public Optional<AppUser> findByEmail(String email) {
        return userAccountJpaRepository.findByEmailAndDeletedFalse(email).map(this::toAppUser);
    }

    public Optional<AppUser> findById(Long id) {
        return userAccountJpaRepository.findByIdAndDeletedFalse(id).map(this::toAppUser);
    }

    public List<Long> findActiveUserIds() {
        return userAccountJpaRepository.findIdsByStatusAndDeletedFalseOrderByIdAsc(UserAccountStatus.ACTIVE);
    }

    public List<Long> findActiveUserIdsByRoles(List<UserRole> roles) {
        if (roles == null || roles.isEmpty()) {
            return findActiveUserIds();
        }
        return userAccountJpaRepository.findIdsByStatusAndRoleInAndDeletedFalseOrderByIdAsc(UserAccountStatus.ACTIVE, roles);
    }

    public Optional<AdminUserDetailRow> findAdminUserDetailById(long userId) {
        return userAccountJpaRepository.findByIdAndDeletedFalse(userId)
                .filter(user -> !SystemUserPolicy.isSystemUserEmail(user.getEmail()))
                .map(user -> new AdminUserDetailRow(
                        user.getId(),
                        user.getEmail(),
                        user.getRole(),
                        user.getTier(),
                        user.getStatus(),
                        resolveApprovalStatus(user.getId(), user.getRole()),
                        user.getDisplayName(),
                        user.getCreatedAt()
                ));
    }

    public List<AdminUserListRow> findAdminUsers(String keyword, UserRole role, UserAccountStatus status, String approvalStatus, int page, int size) {
        Specification<UserAccountEntity> specification = buildAdminUserSpecification(keyword, role, status, approvalStatus);
        return userAccountJpaRepository.findAll(
                        specification,
                        PageRequest.of(Math.max(page - 1, 0), size, ADMIN_USER_SORT)
                ).stream()
                .map(user -> new AdminUserListRow(
                        user.getId(),
                        user.getEmail(),
                        user.getDisplayName(),
                        user.getRole(),
                        user.getTier(),
                        user.getStatus(),
                        resolveApprovalStatus(user.getId(), user.getRole()),
                        user.getCreatedAt(),
                        user.getLastLoginAt()
                ))
                .toList();
    }

    public long countAdminUsers(String keyword, UserRole role, UserAccountStatus status, String approvalStatus) {
        return userAccountJpaRepository.count(buildAdminUserSpecification(keyword, role, status, approvalStatus));
    }

    public AdminUserSummaryRow summarizeAdminUsers() {
        Specification<UserAccountEntity> visibleUsers = adminVisibleSpecification();
        Instant threshold = Instant.now().minus(7, ChronoUnit.DAYS);
        long pendingApprovalUsers = countVisibleUsersByIds(mergeIds(
                mentorApprovalProfileJpaRepository.findUserIdsByApprovalStatus("PENDING"),
                enterpriseApprovalProfileJpaRepository.findUserIdsByApprovalStatus("PENDING")
        ));

        return new AdminUserSummaryRow(
                userAccountJpaRepository.count(visibleUsers),
                userAccountJpaRepository.count(visibleUsers.and(hasRole(UserRole.MENTOR))),
                userAccountJpaRepository.count(visibleUsers.and(hasRole(UserRole.ENTERPRISE))),
                userAccountJpaRepository.count(visibleUsers.and(hasTier("PREMIUM"))),
                pendingApprovalUsers,
                userAccountJpaRepository.count(visibleUsers.and(hasStatus(UserAccountStatus.SUSPENDED))),
                userAccountJpaRepository.count(visibleUsers.and(lastLoginAtAfter(threshold))),
                userAccountJpaRepository.count(visibleUsers.and(createdAtAfter(threshold)))
        );
    }

    public long save(String email, String passwordHash, UserRole role, String displayName, String realName, String tier, UserAccountStatus status) {
        UserAccountEntity saved = userAccountJpaRepository.save(
                UserAccountEntity.create(email, passwordHash, role, tier, status, displayName, realName)
        );
        if (saved.getId() == null) {
            throw new IllegalStateException("failed to resolve generated user id");
        }
        return saved.getId();
    }

    public void updateStatus(long userId, UserAccountStatus status) {
        userAccountJpaRepository.findByIdAndDeletedFalse(userId).ifPresent(user -> {
            user.setStatus(status);
            userAccountJpaRepository.save(user);
        });
    }

    public void updateTier(long userId, String tier) {
        userAccountJpaRepository.findByIdAndDeletedFalse(userId).ifPresent(user -> {
            user.setTier(tier);
            userAccountJpaRepository.save(user);
        });
    }

    public void updatePasswordHash(long userId, String passwordHash) {
        userAccountJpaRepository.findByIdAndDeletedFalse(userId).ifPresent(user -> {
            user.setPasswordHash(passwordHash);
            userAccountJpaRepository.save(user);
        });
    }

    public void updateEmail(long userId, String email) {
        userAccountJpaRepository.findByIdAndDeletedFalse(userId).ifPresent(user -> {
            user.setEmail(email);
            userAccountJpaRepository.save(user);
        });
    }

    public void updateDisplayName(long userId, String displayName) {
        userAccountJpaRepository.findByIdAndDeletedFalse(userId).ifPresent(user -> {
            user.setDisplayName(displayName);
            userAccountJpaRepository.save(user);
        });
    }

    public void updateRealName(long userId, String realName) {
        userAccountJpaRepository.findByIdAndDeletedFalse(userId).ifPresent(user -> {
            user.setRealName(realName);
            userAccountJpaRepository.save(user);
        });
    }

    public void updateLastLoginAt(long userId, Instant loginTime) {
        userAccountJpaRepository.findByIdAndDeletedFalse(userId).ifPresent(user -> {
            user.setLastLoginAt(loginTime);
            userAccountJpaRepository.save(user);
        });
    }

    private AppUser toAppUser(UserAccountEntity entity) {
        return new AppUser(
                entity.getId(),
                entity.getEmail(),
                entity.getPasswordHash(),
                entity.getRole(),
                entity.getTier(),
                entity.getStatus(),
                entity.getDisplayName(),
                entity.getCreatedAt()
        );
    }

    private String resolveApprovalStatus(long userId, UserRole role) {
        if (role == UserRole.MENTOR) {
            return mentorApprovalProfileJpaRepository.findApprovalStatusByUserId(userId)
                    .orElse(null);
        }
        if (role == UserRole.ENTERPRISE) {
            return enterpriseApprovalProfileJpaRepository.findApprovalStatusByUserId(userId)
                    .orElse(null);
        }
        return null;
    }

    private Specification<UserAccountEntity> buildAdminUserSpecification(
            String keyword,
            UserRole role,
            UserAccountStatus status,
            String approvalStatus
    ) {
        Specification<UserAccountEntity> specification = adminVisibleSpecification();
        if (role != null) {
            specification = specification.and(hasRole(role));
        }
        if (status != null) {
            specification = specification.and(hasStatus(status));
        }
        if (keyword != null && !keyword.isBlank()) {
            specification = specification.and(keywordLike(keyword));
        }
        if (approvalStatus == null || approvalStatus.isBlank()) {
            return specification;
        }
        Set<Long> approvalUserIds = resolveApprovalUserIds(role, approvalStatus);
        if (approvalUserIds.isEmpty()) {
            return specification.and(alwaysFalse());
        }
        return specification.and(hasIds(approvalUserIds));
    }

    private Specification<UserAccountEntity> adminVisibleSpecification() {
        return notDeleted().and(notSystemUser());
    }

    private Specification<UserAccountEntity> notDeleted() {
        return (root, query, criteriaBuilder) -> criteriaBuilder.isFalse(root.get("deleted"));
    }

    private Specification<UserAccountEntity> notSystemUser() {
        return (root, query, criteriaBuilder) ->
                criteriaBuilder.notLike(criteriaBuilder.lower(root.get("email")), "%@system.local");
    }

    private Specification<UserAccountEntity> hasRole(UserRole role) {
        return (root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get("role"), role);
    }

    private Specification<UserAccountEntity> hasStatus(UserAccountStatus status) {
        return (root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get("status"), status);
    }

    private Specification<UserAccountEntity> hasTier(String tier) {
        return (root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get("tier"), tier);
    }

    private Specification<UserAccountEntity> lastLoginAtAfter(Instant threshold) {
        return (root, query, criteriaBuilder) -> criteriaBuilder.greaterThanOrEqualTo(root.get("lastLoginAt"), threshold);
    }

    private Specification<UserAccountEntity> createdAtAfter(Instant threshold) {
        return (root, query, criteriaBuilder) -> criteriaBuilder.greaterThanOrEqualTo(root.get("createdAt"), threshold);
    }

    private Specification<UserAccountEntity> keywordLike(String keyword) {
        String normalizedKeyword = "%" + keyword.trim().toLowerCase(Locale.ROOT) + "%";
        String rawKeyword = "%" + keyword.trim() + "%";
        return (root, query, criteriaBuilder) -> criteriaBuilder.or(
                criteriaBuilder.like(criteriaBuilder.lower(root.get("email")), normalizedKeyword),
                criteriaBuilder.like(criteriaBuilder.lower(root.get("displayName")), normalizedKeyword),
                criteriaBuilder.like(root.get("id").as(String.class), rawKeyword)
        );
    }

    private Specification<UserAccountEntity> hasIds(Collection<Long> ids) {
        return (root, query, criteriaBuilder) -> root.get("id").in(ids);
    }

    private Specification<UserAccountEntity> alwaysFalse() {
        return (root, query, criteriaBuilder) -> criteriaBuilder.disjunction();
    }

    private Set<Long> resolveApprovalUserIds(UserRole role, String approvalStatus) {
        Set<Long> userIds = new LinkedHashSet<>();
        if (role == null || role == UserRole.MENTOR) {
            userIds.addAll(mentorApprovalProfileJpaRepository.findUserIdsByApprovalStatus(approvalStatus));
        }
        if (role == null || role == UserRole.ENTERPRISE) {
            userIds.addAll(enterpriseApprovalProfileJpaRepository.findUserIdsByApprovalStatus(approvalStatus));
        }
        return userIds;
    }

    private Set<Long> mergeIds(List<Long> first, List<Long> second) {
        Set<Long> ids = new LinkedHashSet<>(first);
        ids.addAll(second);
        return ids;
    }

    private long countVisibleUsersByIds(Set<Long> userIds) {
        if (userIds.isEmpty()) {
            return 0L;
        }
        return userAccountJpaRepository.count(adminVisibleSpecification().and(hasIds(userIds)));
    }

    public record AdminUserDetailRow(
            long userId,
            String email,
            UserRole role,
            String tier,
            UserAccountStatus status,
            String approvalStatus,
            String displayName,
            Instant createdAt
    ) {
    }

    public record AdminUserListRow(
            long userId,
            String email,
            String displayName,
            UserRole role,
            String tier,
            UserAccountStatus status,
            String approvalStatus,
            Instant createdAt,
            Instant lastLoginAt
    ) {
    }

    public record AdminUserSummaryRow(
            long totalUsers,
            long mentorUsers,
            long enterpriseUsers,
            long premiumUsers,
            long pendingApprovalUsers,
            long suspendedUsers,
            long activeUsers7d,
            long newUsers7d
    ) {
    }
}
