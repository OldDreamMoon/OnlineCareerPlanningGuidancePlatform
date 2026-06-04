package com.bishe.server.auth.service;

import com.bishe.server.auth.dto.AdminUserDetailResponse;
import com.bishe.server.auth.dto.AdminUserListResponse;
import com.bishe.server.auth.dto.AdminUserSummaryResponse;
import com.bishe.server.auth.model.UserAccountStatus;
import com.bishe.server.auth.model.UserRole;
import com.bishe.server.auth.repository.UserRepository;
import com.bishe.server.common.exception.ApiException;
import com.bishe.server.common.util.TextListCodec;
import com.bishe.server.mentor.service.MentorPublicListCacheService;
import com.bishe.server.mentor.service.MentorPublicDetailCacheService;
import com.bishe.server.profile.repository.StudentProfileRepository;
import com.bishe.server.profile.repository.EnterpriseProfileRepository;
import com.bishe.server.profile.service.StudentPublicProfileCacheService;
import com.bishe.server.mentor.repository.MentorRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.List;

/**
 * 管理员用户详情服务。
 */
@Service
public class AdminUserService {

    private static final EnumSet<UserAccountStatus> ADMIN_MUTABLE_STATUSES = EnumSet.of(UserAccountStatus.ACTIVE, UserAccountStatus.SUSPENDED);
    private static final List<String> PROFILE_APPROVAL_STATUSES = List.of("PENDING", "APPROVED", "REJECTED");
    private static final List<String> STUDENT_MUTABLE_TIERS = List.of("FREE", "PREMIUM");

    private final UserRepository userRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final MentorRepository mentorRepository;
    private final EnterpriseProfileRepository enterpriseProfileRepository;
    private final PasswordEncoder passwordEncoder;
    private final MentorPublicDetailCacheService mentorPublicDetailCacheService;
    private final MentorPublicListCacheService mentorPublicListCacheService;
    private final StudentPublicProfileCacheService studentPublicProfileCacheService;

    public AdminUserService(
            UserRepository userRepository,
            StudentProfileRepository studentProfileRepository,
            MentorRepository mentorRepository,
            EnterpriseProfileRepository enterpriseProfileRepository,
            PasswordEncoder passwordEncoder,
            MentorPublicDetailCacheService mentorPublicDetailCacheService,
            MentorPublicListCacheService mentorPublicListCacheService,
            StudentPublicProfileCacheService studentPublicProfileCacheService
    ) {
        this.userRepository = userRepository;
        this.studentProfileRepository = studentProfileRepository;
        this.mentorRepository = mentorRepository;
        this.enterpriseProfileRepository = enterpriseProfileRepository;
        this.passwordEncoder = passwordEncoder;
        this.mentorPublicDetailCacheService = mentorPublicDetailCacheService;
        this.mentorPublicListCacheService = mentorPublicListCacheService;
        this.studentPublicProfileCacheService = studentPublicProfileCacheService;
    }

    public AdminUserListResponse getUsers(int page, int size, String keyword, String role, String status, String approvalStatus) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 50);
        UserRole roleFilter = parseOptionalRole(role);
        UserAccountStatus statusFilter = parseOptionalStatus(status);
        String approvalStatusFilter = parseOptionalApprovalStatus(approvalStatus);
        long total = userRepository.countAdminUsers(keyword, roleFilter, statusFilter, approvalStatusFilter);
        List<AdminUserListResponse.UserItem> records = userRepository.findAdminUsers(keyword, roleFilter, statusFilter, approvalStatusFilter, safePage, safeSize)
                .stream()
                .map(item -> new AdminUserListResponse.UserItem(
                        item.userId(),
                        item.email(),
                        item.displayName(),
                        item.role().name(),
                        item.tier(),
                        item.status().name(),
                        item.approvalStatus(),
                        com.bishe.server.common.TimePayloads.toEpochMillis(item.createdAt()),
                        com.bishe.server.common.TimePayloads.toEpochMillis(item.lastLoginAt())
                ))
                .toList();
        return new AdminUserListResponse(records, total, safePage, safeSize);
    }

    public AdminUserSummaryResponse getUserSummary() {
        UserRepository.AdminUserSummaryRow summary = userRepository.summarizeAdminUsers();
        return new AdminUserSummaryResponse(
                summary.totalUsers(),
                summary.mentorUsers(),
                summary.enterpriseUsers(),
                summary.premiumUsers(),
                summary.pendingApprovalUsers(),
                summary.suspendedUsers(),
                summary.activeUsers7d(),
                summary.newUsers7d()
        );
    }

    public AdminUserDetailResponse getUserDetail(long userId) {
        UserRepository.AdminUserDetailRow user = getRequiredUser(userId);

        AdminUserDetailResponse.StudentProfileSummary studentProfile = null;
        Long communityScore7d = null;
        if (user.role() == UserRole.STUDENT) {
            StudentProfileRepository.StudentProfileRow profileRow = studentProfileRepository.findStudentProfileByUserId(userId).orElse(null);
            StudentProfileRepository.CommunityStatsRow communityStats = studentProfileRepository.countCommunityStats7d(
                    userId,
                    Instant.now().minus(7, ChronoUnit.DAYS)
            );
            studentProfile = new AdminUserDetailResponse.StudentProfileSummary(
                    profileRow == null ? null : profileRow.major(),
                    profileRow == null ? null : profileRow.grade(),
                    profileRow == null ? null : profileRow.targetPosition(),
                    TextListCodec.split(profileRow == null ? null : profileRow.skillTags()),
                    profileRow == null ? null : profileRow.selfIntro()
            );
            communityScore7d = calculateCommunityScore(communityStats);
        }

        return new AdminUserDetailResponse(
                user.userId(),
                user.email(),
                user.displayName(),
                user.role().name(),
                user.tier(),
                user.status().name(),
                user.approvalStatus(),
                com.bishe.server.common.TimePayloads.toEpochMillis(user.createdAt()),
                studentProfile,
                communityScore7d
        );
    }

    @Transactional
    public void updateStatus(long operatorUserId, long targetUserId, String nextStatus) {
        if (operatorUserId == targetUserId) {
            throw new ApiException("BIZ-1001", "cannot change current admin status", HttpStatus.BAD_REQUEST);
        }
        UserRepository.AdminUserDetailRow user = getRequiredUser(targetUserId);
        UserAccountStatus status = parseMutableStatus(nextStatus);
        if (user.status() == status) {
            return;
        }
        userRepository.updateStatus(targetUserId, status);
        if (user.role() == UserRole.MENTOR) {
            mentorPublicDetailCacheService.evictNow(targetUserId);
            mentorPublicDetailCacheService.evictAfterCommit(targetUserId);
            mentorPublicListCacheService.evictAllNow();
            mentorPublicListCacheService.evictAllAfterCommit();
        }
    }

    @Transactional
    public void updateTier(long targetUserId, String nextTier) {
        UserRepository.AdminUserDetailRow user = getRequiredUser(targetUserId);
        if (user.role() != UserRole.STUDENT) {
            throw new ApiException("BIZ-1001", "tier update only supported for student", HttpStatus.BAD_REQUEST);
        }
        String tier = parseMutableTier(nextTier);
        if (tier.equals(user.tier())) {
            return;
        }
        userRepository.updateTier(targetUserId, tier);
        studentPublicProfileCacheService.evictNow(targetUserId);
        studentPublicProfileCacheService.evictAfterCommit(targetUserId);
    }

    @Transactional
    public void resetPassword(long userId, String newPassword) {
        getRequiredUser(userId);
        userRepository.updatePasswordHash(userId, passwordEncoder.encode(newPassword));
    }

    @Transactional
    public void updateApprovalStatus(long userId, String nextApprovalStatus) {
        UserRepository.AdminUserDetailRow user = getRequiredUser(userId);
        String approvalStatus = parseRequiredApprovalStatus(nextApprovalStatus);
        switch (user.role()) {
            case MENTOR -> {
                mentorRepository.createDefaultProfileIfAbsent(userId, user.displayName(), null, null);
                if (approvalStatus.equals(user.approvalStatus())) {
                    return;
                }
                mentorRepository.updateApprovalStatus(userId, approvalStatus);
                mentorPublicDetailCacheService.evictNow(userId);
                mentorPublicDetailCacheService.evictAfterCommit(userId);
                mentorPublicListCacheService.evictAllNow();
                mentorPublicListCacheService.evictAllAfterCommit();
            }
            case ENTERPRISE -> {
                enterpriseProfileRepository.createDefaultProfileIfAbsent(userId, null, null);
                if (approvalStatus.equals(user.approvalStatus())) {
                    return;
                }
                enterpriseProfileRepository.updateApprovalStatus(userId, approvalStatus);
            }
            default -> throw new ApiException("BIZ-1001", "approval status not supported for role", HttpStatus.BAD_REQUEST);
        }
    }

    private UserRepository.AdminUserDetailRow getRequiredUser(long userId) {
        return userRepository.findAdminUserDetailById(userId)
                .orElseThrow(() -> new ApiException("BIZ-1002", "user not found", HttpStatus.NOT_FOUND));
    }

    private UserRole parseOptionalRole(String rawRole) {
        if (rawRole == null || rawRole.isBlank()) {
            return null;
        }
        try {
            return UserRole.parse(rawRole);
        } catch (IllegalArgumentException ex) {
            throw new ApiException("BIZ-1001", "role invalid", HttpStatus.BAD_REQUEST);
        }
    }

    private UserAccountStatus parseOptionalStatus(String rawStatus) {
        if (rawStatus == null || rawStatus.isBlank()) {
            return null;
        }
        try {
            return UserAccountStatus.parse(rawStatus);
        } catch (IllegalArgumentException ex) {
            throw new ApiException("BIZ-1001", "status invalid", HttpStatus.BAD_REQUEST);
        }
    }

    private String parseOptionalApprovalStatus(String rawApprovalStatus) {
        if (rawApprovalStatus == null || rawApprovalStatus.isBlank()) {
            return null;
        }
        return parseRequiredApprovalStatus(rawApprovalStatus);
    }

    private String parseRequiredApprovalStatus(String rawApprovalStatus) {
        String normalized = rawApprovalStatus == null ? "" : rawApprovalStatus.trim().toUpperCase();
        if (!PROFILE_APPROVAL_STATUSES.contains(normalized)) {
            throw new ApiException("BIZ-1001", "approval status invalid", HttpStatus.BAD_REQUEST);
        }
        return normalized;
    }

    private UserAccountStatus parseMutableStatus(String nextStatus) {
        try {
            UserAccountStatus status = UserAccountStatus.parse(nextStatus);
            if (!ADMIN_MUTABLE_STATUSES.contains(status)) {
                throw new ApiException("BIZ-1001", "status invalid", HttpStatus.BAD_REQUEST);
            }
            return status;
        } catch (IllegalArgumentException ex) {
            throw new ApiException("BIZ-1001", "status invalid", HttpStatus.BAD_REQUEST);
        }
    }

    private String parseMutableTier(String rawTier) {
        String normalized = rawTier == null ? "" : rawTier.trim().toUpperCase();
        if (!STUDENT_MUTABLE_TIERS.contains(normalized)) {
            throw new ApiException("BIZ-1001", "tier invalid", HttpStatus.BAD_REQUEST);
        }
        return normalized;
    }

    private long calculateCommunityScore(StudentProfileRepository.CommunityStatsRow communityStats) {
        return communityStats.postCount() * 5L + communityStats.commentCount() * 2L + communityStats.likesReceivedCount();
    }
}
