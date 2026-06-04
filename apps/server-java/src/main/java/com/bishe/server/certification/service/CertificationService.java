package com.bishe.server.certification.service;

import com.bishe.server.auth.SystemUserPolicy;
import com.bishe.server.auth.dto.RegisterWithCertificationResponse;
import com.bishe.server.auth.email.EmailVerificationService;
import com.bishe.server.auth.model.AppUser;
import com.bishe.server.auth.model.UserAccountStatus;
import com.bishe.server.auth.model.UserRole;
import com.bishe.server.auth.repository.UserRepository;
import com.bishe.server.auth.service.AuthException;
import com.bishe.server.certification.CertificationStorageService;
import com.bishe.server.certification.dto.CertificationAssetResponse;
import com.bishe.server.certification.dto.CertificationOwnViewResponse;
import com.bishe.server.certification.dto.CertificationReviewDetailResponse;
import com.bishe.server.certification.dto.CertificationReviewListResponse;
import com.bishe.server.certification.dto.CertificationSubmissionResponse;
import com.bishe.server.certification.repository.CertificationRepository;
import com.bishe.server.common.exception.ApiException;
import com.bishe.server.common.util.TextListCodec;
import com.bishe.server.demo.DemoModeProperties;
import com.bishe.server.mentor.repository.MentorRepository;
import com.bishe.server.mentor.service.MentorPublicListCacheService;
import com.bishe.server.mentor.service.MentorPublicDetailCacheService;
import com.bishe.server.notification.model.NotificationCategory;
import com.bishe.server.notification.model.NotificationPriority;
import com.bishe.server.notification.service.NotificationService;
import com.bishe.server.notification.service.PlatformNotificationPublishService;
import com.bishe.server.profile.repository.EnterpriseProfileRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 正式认证资料提交、生命周期与管理员审核服务。
 */
@Service
public class CertificationService {

    private static final String DEFAULT_TIER = "FREE";
    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_APPROVED = "APPROVED";
    private static final String STATUS_REJECTED = "REJECTED";
    private static final String ASSET_ACTIVE = "ACTIVE";
    private static final String ASSET_REPLACED = "REPLACED";
    private static final String REPLACED_BY_NEW_SUBMISSION = "REPLACED_BY_NEW_SUBMISSION";
    private static final EnumSet<UserRole> CERTIFICATION_ROLES = EnumSet.of(UserRole.MENTOR, UserRole.ENTERPRISE);
    private static final Set<String> REVIEWABLE_STATUSES = Set.of(STATUS_PENDING, STATUS_APPROVED, STATUS_REJECTED);
    private static final Pattern PASSWORD_PATTERN = Pattern.compile("^(?=.*[A-Za-z])(?=.*\\d).{8,64}$");

    private final CertificationRepository certificationRepository;
    private final CertificationStorageService certificationStorageService;
    private final UserRepository userRepository;
    private final MentorRepository mentorRepository;
    private final EnterpriseProfileRepository enterpriseProfileRepository;
    private final PasswordEncoder passwordEncoder;
    private final TransactionTemplate transactionTemplate;
    private final EmailVerificationService emailVerificationService;
    private final DemoModeProperties demoModeProperties;
    private final NotificationService notificationService;
    private final MentorPublicDetailCacheService mentorPublicDetailCacheService;
    private final MentorPublicListCacheService mentorPublicListCacheService;
    private final CertificationOwnViewCacheService certificationOwnViewCacheService;
    private final CertificationReviewListCacheService certificationReviewListCacheService;
    private final CertificationReviewDetailCacheService certificationReviewDetailCacheService;

    public CertificationService(
            CertificationRepository certificationRepository,
            CertificationStorageService certificationStorageService,
            UserRepository userRepository,
            MentorRepository mentorRepository,
            EnterpriseProfileRepository enterpriseProfileRepository,
            PasswordEncoder passwordEncoder,
            TransactionTemplate transactionTemplate,
            EmailVerificationService emailVerificationService,
            DemoModeProperties demoModeProperties,
            NotificationService notificationService,
            MentorPublicDetailCacheService mentorPublicDetailCacheService,
            MentorPublicListCacheService mentorPublicListCacheService,
            CertificationOwnViewCacheService certificationOwnViewCacheService,
            CertificationReviewListCacheService certificationReviewListCacheService,
            CertificationReviewDetailCacheService certificationReviewDetailCacheService
    ) {
        this.certificationRepository = certificationRepository;
        this.certificationStorageService = certificationStorageService;
        this.userRepository = userRepository;
        this.mentorRepository = mentorRepository;
        this.enterpriseProfileRepository = enterpriseProfileRepository;
        this.passwordEncoder = passwordEncoder;
        this.transactionTemplate = transactionTemplate;
        this.emailVerificationService = emailVerificationService;
        this.demoModeProperties = demoModeProperties;
        this.notificationService = notificationService;
        this.mentorPublicDetailCacheService = mentorPublicDetailCacheService;
        this.mentorPublicListCacheService = mentorPublicListCacheService;
        this.certificationOwnViewCacheService = certificationOwnViewCacheService;
        this.certificationReviewListCacheService = certificationReviewListCacheService;
        this.certificationReviewDetailCacheService = certificationReviewDetailCacheService;
    }

    public RegisterWithCertificationResponse registerWithCertification(
            String role,
            String email,
            String password,
            String displayName,
            String realName,
            String companyName,
            String jobTitle,
            MultipartFile file,
            String emailVerificationToken
    ) {
        UserRole registerRole = requireCertificationRole(role);
        String normalizedEmail = normalizeEmail(email);
        SystemUserPolicy.assertPublicEmailAllowed(normalizedEmail);
        emailVerificationService.assertEmailVerificationProof(normalizedEmail, emailVerificationToken);
        validatePassword(password);
        String normalizedDisplayName = requireText(displayName, "displayName is required", 100);
        CertificationPayload payload = buildPayload(normalizedDisplayName, realName, companyName, jobTitle);
        boolean demoBypassEnabled = demoModeProperties.isCertificationBypassEnabled();
        CertificationStorageService.StoredObject uploadedObject = demoBypassEnabled
                ? null
                : certificationStorageService.upload(registerRole.name(), normalizedEmail, "register", file);
        String initialStatus = demoBypassEnabled ? STATUS_APPROVED : STATUS_PENDING;

        try {
            CertificationSubmissionResponse currentSubmission = transactionTemplate.execute(status -> {
                if (userRepository.findByEmail(normalizedEmail).isPresent()) {
                    throw AuthException.emailExists();
                }

                long userId = userRepository.save(
                        normalizedEmail,
                        passwordEncoder.encode(password),
                        registerRole,
                        normalizedDisplayName,
                        payload.realName(),
                        DEFAULT_TIER,
                        UserAccountStatus.ACTIVE
                );
                applyCertificationIdentity(userId, registerRole, normalizedDisplayName, payload, initialStatus);
                long submissionId = certificationRepository.createSubmission(
                        userId,
                        registerRole.name(),
                        payload.realName(),
                        payload.companyName(),
                        payload.jobTitle(),
                        initialStatus,
                        null,
                        Instant.now()
                );
                if (uploadedObject != null) {
                    certificationRepository.createAsset(
                            submissionId,
                            uploadedObject.bucket(),
                            uploadedObject.objectKey(),
                            uploadedObject.originalFilename(),
                            uploadedObject.contentType(),
                            uploadedObject.sizeBytes()
                    );
                }
                if (demoBypassEnabled) {
                    certificationRepository.updateSubmissionReview(submissionId, STATUS_APPROVED, "DEMO_MODE_AUTO_APPROVED", null, Instant.now());
                }
                return getSubmissionResponse(userId, submissionId);
            });

            return new RegisterWithCertificationResponse(
                    currentSubmission.userId(),
                    registerRole.name(),
                    initialStatus,
                    currentSubmission
            );
        } catch (RuntimeException ex) {
            if (uploadedObject != null) {
                certificationStorageService.deleteQuietly(uploadedObject.bucket(), uploadedObject.objectKey());
            }
            throw ex;
        } finally {
            evictReviewListCache();
        }
    }

    public CertificationOwnViewResponse getOwnView(long userId, String role) {
        UserRole certificationRole = requireCertificationRole(role);
        AppUser user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException("BIZ-1002", "user not found", HttpStatus.NOT_FOUND));
        ensureCertificationProfileExists(userId, user.displayName(), certificationRole);
        return certificationOwnViewCacheService.getOwnView(
                userId,
                () -> buildOwnView(userId, certificationRole.name())
        );
    }

    public CertificationSubmissionResponse submitOwnCertification(
            long userId,
            String role,
            String realName,
            String companyName,
            String jobTitle,
            MultipartFile file
    ) {
        UserRole certificationRole = requireCertificationRole(role);
        AppUser user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException("BIZ-1002", "user not found", HttpStatus.NOT_FOUND));
        ensureCertificationProfileExists(userId, user.displayName(), certificationRole);
        CertificationPayload payload = buildPayload(user.displayName(), realName, companyName, jobTitle);
        Optional<MentorRepository.MentorOwnProfileRow> mentorProfile = certificationRole == UserRole.MENTOR
                ? mentorRepository.findOwnProfile(userId)
                : Optional.empty();
        String currentRealName = mentorProfile
                .map(MentorRepository.MentorOwnProfileRow::realName)
                .map(TextListCodec::normalizeText)
                .orElse(null);
        String currentCompanyName = mentorProfile
                .map(MentorRepository.MentorOwnProfileRow::companyName)
                .map(TextListCodec::normalizeText)
                .orElse(null);
        if (certificationRole == UserRole.MENTOR
                && currentRealName != null
                && !Objects.equals(currentRealName, payload.realName())) {
            throw new ApiException("BIZ-1001", "realName immutable", HttpStatus.BAD_REQUEST);
        }
        if (certificationRole == UserRole.MENTOR
                && currentCompanyName != null
                && !Objects.equals(currentCompanyName, payload.companyName())) {
            throw new ApiException("BIZ-1001", "companyName immutable", HttpStatus.BAD_REQUEST);
        }
        CertificationStorageService.StoredObject uploadedObject = certificationStorageService.upload(certificationRole.name(), String.valueOf(userId), "resubmit", file);
        Optional<CertificationRepository.SubmissionRow> previousSubmission = certificationRepository.findCurrentSubmission(userId);
        List<CertificationRepository.AssetRow> previousAssets = previousSubmission
                .map(submissionRow -> certificationRepository.findActiveAssetsBySubmissionId(submissionRow.submissionId()))
                .orElse(List.of());

        try {
            Long submissionId = transactionTemplate.execute(status -> {
                applyCertificationIdentity(userId, certificationRole, user.displayName(), payload, STATUS_PENDING);
                previousSubmission.ifPresent(submission -> certificationRepository.markCurrentSubmissionReplaced(userId));
                long createdSubmissionId = certificationRepository.createSubmission(
                        userId,
                        certificationRole.name(),
                        payload.realName(),
                        payload.companyName(),
                        payload.jobTitle(),
                        STATUS_PENDING,
                        previousSubmission.map(CertificationRepository.SubmissionRow::submissionId).orElse(null),
                        Instant.now()
                );
                certificationRepository.createAsset(
                        createdSubmissionId,
                        uploadedObject.bucket(),
                        uploadedObject.objectKey(),
                        uploadedObject.originalFilename(),
                        uploadedObject.contentType(),
                        uploadedObject.sizeBytes()
                );
                return createdSubmissionId;
            });

            cleanupHistoricalAssets(previousAssets);
            evictOwnViewCache(userId);
            evictReviewListCache();
            evictReviewDetailCache(userId);
            return getSubmissionResponse(userId, submissionId);
        } catch (RuntimeException ex) {
            certificationStorageService.deleteQuietly(uploadedObject.bucket(), uploadedObject.objectKey());
            throw ex;
        }
    }

    public CertificationReviewListResponse getReviewList(int page, int size, String keyword, String role, String status) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 50);
        String roleFilter = normalizeOptionalRoleFilter(role);
        String statusFilter = normalizeOptionalReviewStatus(status);
        return certificationReviewListCacheService.getReviewList(
                keyword,
                roleFilter,
                statusFilter,
                safePage,
                safeSize,
                () -> loadReviewList(keyword, roleFilter, statusFilter, safePage, safeSize)
        );
    }

    public CertificationReviewDetailResponse getReviewDetail(long userId) {
        return certificationReviewDetailCacheService.getReviewDetail(
                userId,
                () -> buildReviewDetail(userId)
        );
    }

    public CertificationReviewDetailResponse reviewCurrentSubmission(long operatorUserId, long userId, String approvalStatus, String reviewNote) {
        CertificationRepository.ReviewSubjectRow subject = requireReviewSubject(userId);
        String normalizedApprovalStatus = normalizeRequiredReviewStatus(approvalStatus);
        String normalizedReviewNote = TextListCodec.normalizeText(reviewNote);
        CertificationRepository.SubmissionRow currentSubmission = certificationRepository.findCurrentSubmission(userId)
                .orElseThrow(() -> new ApiException("BIZ-1002", "certification submission not found", HttpStatus.NOT_FOUND));

        transactionTemplate.executeWithoutResult(status -> {
            updateRoleApprovalStatus(userId, UserRole.parse(subject.role()), normalizedApprovalStatus);
            certificationRepository.updateSubmissionReview(
                    currentSubmission.submissionId(),
                    normalizedApprovalStatus,
                    normalizedReviewNote,
                    STATUS_PENDING.equals(normalizedApprovalStatus) ? null : operatorUserId,
                    STATUS_PENDING.equals(normalizedApprovalStatus) ? null : Instant.now()
            );
        });
        evictOwnViewCache(userId);
        evictReviewListCache();
        evictReviewDetailCache(userId);

        publishCertificationReviewNotification(subject, currentSubmission.submissionId(), normalizedApprovalStatus, normalizedReviewNote);

        return getReviewDetail(userId);
    }

    private void publishCertificationReviewNotification(
            CertificationRepository.ReviewSubjectRow subject,
            long submissionId,
            String approvalStatus,
            String reviewNote
    ) {
        String eventType;
        String content;
        if (STATUS_APPROVED.equals(approvalStatus)) {
            eventType = "CERTIFICATION_APPROVED";
            content = "你的认证审核已通过，相关身份已在平台内生效。";
        } else if (STATUS_REJECTED.equals(approvalStatus) && reviewNote != null && !reviewNote.isBlank()) {
            eventType = "CERTIFICATION_RESUBMIT_REQUIRED";
            content = "你的认证资料需要重新补充后再提交，请查看审核备注。";
        } else if (STATUS_REJECTED.equals(approvalStatus)) {
            eventType = "CERTIFICATION_REJECTED";
            content = "你的认证审核未通过，请查看当前状态与后续处理建议。";
        } else {
            return;
        }

        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("submissionId", submissionId);
        payload.put("role", subject.role());
        payload.put("approvalStatus", approvalStatus);
        payload.put("reviewNote", reviewNote);

        notificationService.publish(new PlatformNotificationPublishService.NotificationPublishCommand(
                eventType,
                NotificationCategory.CERTIFICATION,
                "CERTIFICATION",
                String.valueOf(submissionId),
                null,
                List.of(subject.userId()),
                NotificationPriority.HIGH,
                null,
                content,
                null,
                String.valueOf(submissionId),
                null,
                payload,
                "cert-review:" + submissionId + ":" + approvalStatus,
                Instant.now()
        ));
    }

    public AssetContentResponse readAsset(long operatorUserId, String operatorRole, long assetId) {
        CertificationRepository.AssetAccessRow asset = certificationRepository.findAssetAccessRow(assetId)
                .orElseThrow(() -> new ApiException("BIZ-1002", "certification asset not found", HttpStatus.NOT_FOUND));
        if (!"ADMIN".equals(operatorRole) && asset.userId() != operatorUserId) {
            throw AuthException.permissionDenied();
        }
        if (!ASSET_ACTIVE.equals(asset.lifecycleStatus()) && !ASSET_REPLACED.equals(asset.lifecycleStatus())) {
            throw new ApiException("BIZ-1002", "certification asset not found", HttpStatus.NOT_FOUND);
        }

        CertificationStorageService.StoredContent storedContent = certificationStorageService.read(asset.bucket(), asset.objectKey());
        MediaType mediaType = StringUtils.hasText(asset.contentType()) ? MediaType.parseMediaType(asset.contentType()) : MediaType.APPLICATION_OCTET_STREAM;
        return new AssetContentResponse(
                asset.originalFilename(),
                mediaType,
                storedContent.bytes()
        );
    }

    private CertificationOwnViewResponse buildOwnView(long userId, String role) {
        String approvalStatus = readApprovalStatus(userId, UserRole.parse(role));
        List<CertificationSubmissionResponse> submissions = buildSubmissionResponses(certificationRepository.findSubmissionsByUserId(userId));
        CertificationSubmissionResponse currentSubmission = submissions.stream().filter(CertificationSubmissionResponse::current).findFirst().orElse(null);
        return new CertificationOwnViewResponse(userId, role, approvalStatus, currentSubmission, submissions);
    }

    private CertificationReviewListResponse loadReviewList(String keyword, String roleFilter, String statusFilter, int page, int size) {
        long total = certificationRepository.countCurrentReviewItems(keyword, roleFilter, statusFilter);
        List<CertificationReviewListResponse.ReviewItem> records = certificationRepository.findCurrentReviewItems(keyword, roleFilter, statusFilter, page, size)
                .stream()
                .map(item -> new CertificationReviewListResponse.ReviewItem(
                        item.userId(),
                        item.email(),
                        item.displayName(),
                        item.role(),
                        item.approvalStatus(),
                        item.submissionId(),
                        item.submissionStatus(),
                        item.realName(),
                        item.companyName(),
                        item.jobTitle(),
                        item.activeAssetCount(),
                        item.primaryAssetName(),
                        toIso(item.submittedAt()),
                        toIso(item.reviewedAt())
                ))
                .toList();
        return new CertificationReviewListResponse(records, total, page, size);
    }

    private CertificationReviewDetailResponse buildReviewDetail(long userId) {
        CertificationRepository.ReviewSubjectRow subject = requireReviewSubject(userId);
        List<CertificationSubmissionResponse> submissions = buildSubmissionResponses(certificationRepository.findSubmissionsByUserId(userId));
        CertificationSubmissionResponse currentSubmission = submissions.stream().filter(CertificationSubmissionResponse::current).findFirst().orElse(null);
        if (currentSubmission == null) {
            throw new ApiException("BIZ-1002", "certification submission not found", HttpStatus.NOT_FOUND);
        }
        return new CertificationReviewDetailResponse(
                subject.userId(),
                subject.email(),
                subject.displayName(),
                subject.role(),
                subject.approvalStatus(),
                currentSubmission,
                submissions
        );
    }

    private void evictOwnViewCache(long userId) {
        certificationOwnViewCacheService.evictNow(userId);
        certificationOwnViewCacheService.evictAfterCommit(userId);
    }

    private void evictReviewListCache() {
        certificationReviewListCacheService.evictAllNow();
        certificationReviewListCacheService.evictAllAfterCommit();
    }

    private void evictReviewDetailCache(long userId) {
        certificationReviewDetailCacheService.evictNow(userId);
        certificationReviewDetailCacheService.evictAfterCommit(userId);
    }

    private CertificationSubmissionResponse getSubmissionResponse(long userId, long submissionId) {
        List<CertificationSubmissionResponse> submissions = buildSubmissionResponses(certificationRepository.findSubmissionsByUserId(userId));
        return submissions.stream()
                .filter(item -> item.submissionId() == submissionId)
                .findFirst()
                .orElseThrow(() -> new ApiException("BIZ-1002", "certification submission not found", HttpStatus.NOT_FOUND));
    }

    private List<CertificationSubmissionResponse> buildSubmissionResponses(List<CertificationRepository.SubmissionRow> submissions) {
        if (submissions.isEmpty()) {
            return List.of();
        }

        List<Long> submissionIds = submissions.stream().map(CertificationRepository.SubmissionRow::submissionId).toList();
        Map<Long, List<CertificationAssetResponse>> assetsBySubmissionId = new LinkedHashMap<>();
        for (CertificationRepository.AssetRow assetRow : certificationRepository.findAssetsBySubmissionIds(submissionIds)) {
            assetsBySubmissionId.computeIfAbsent(assetRow.submissionId(), ignored -> new ArrayList<>())
                    .add(new CertificationAssetResponse(
                            assetRow.assetId(),
                            assetRow.bucket(),
                            assetRow.objectKey(),
                            assetRow.originalFilename(),
                            assetRow.contentType(),
                            assetRow.sizeBytes(),
                            assetRow.lifecycleStatus(),
                            assetRow.deleteReason(),
                            toIso(assetRow.uploadedAt()),
                            toIso(assetRow.deletedAt())
                    ));
        }

        return submissions.stream()
                .map(submission -> new CertificationSubmissionResponse(
                        submission.submissionId(),
                        submission.userId(),
                        submission.role(),
                        submission.realName(),
                        submission.companyName(),
                        submission.jobTitle(),
                        submission.status(),
                        submission.current(),
                        submission.reviewNote(),
                        submission.previousSubmissionId(),
                        toIso(submission.submittedAt()),
                        toIso(submission.reviewedAt()),
                        assetsBySubmissionId.getOrDefault(submission.submissionId(), List.of())
                ))
                .toList();
    }

    private void cleanupHistoricalAssets(List<CertificationRepository.AssetRow> previousAssets) {
        if (previousAssets.isEmpty()) {
            return;
        }

        List<Long> replacedAssetIds = previousAssets.stream()
                .map(CertificationRepository.AssetRow::assetId)
                .toList();
        certificationRepository.markAssetsLifecycle(replacedAssetIds, ASSET_REPLACED, REPLACED_BY_NEW_SUBMISSION, Instant.now());
    }

    private void applyCertificationIdentity(long userId, UserRole role, String displayName, CertificationPayload payload, String approvalStatus) {
        userRepository.updateRealName(userId, payload.realName());
        switch (role) {
            case MENTOR -> {
                mentorRepository.createDefaultProfileIfAbsent(userId, displayName, payload.companyName(), payload.jobTitle(), approvalStatus);
                mentorRepository.updateCertificationIdentity(userId, payload.companyName(), payload.jobTitle());
                mentorRepository.updateApprovalStatus(userId, approvalStatus);
                mentorPublicDetailCacheService.evictNow(userId);
                mentorPublicDetailCacheService.evictAfterCommit(userId);
                mentorPublicListCacheService.evictAllNow();
                mentorPublicListCacheService.evictAllAfterCommit();
            }
            case ENTERPRISE -> {
                enterpriseProfileRepository.createDefaultProfileIfAbsent(userId, payload.companyName(), payload.jobTitle(), approvalStatus);
                enterpriseProfileRepository.updateOwnProfile(userId, payload.companyName(), payload.jobTitle());
                enterpriseProfileRepository.updateApprovalStatus(userId, approvalStatus);
            }
            default -> throw new ApiException("BIZ-1001", "role not supported", HttpStatus.BAD_REQUEST);
        }
    }

    private void ensureCertificationProfileExists(long userId, String displayName, UserRole role) {
        switch (role) {
            case MENTOR -> mentorRepository.createDefaultProfileIfAbsent(userId, displayName, null, null, STATUS_PENDING);
            case ENTERPRISE -> enterpriseProfileRepository.createDefaultProfileIfAbsent(userId, null, null, STATUS_PENDING);
            default -> throw new ApiException("BIZ-1001", "role not supported", HttpStatus.BAD_REQUEST);
        }
    }

    private String readApprovalStatus(long userId, UserRole role) {
        return switch (role) {
            case MENTOR -> mentorRepository.findOwnProfile(userId)
                    .map(MentorRepository.MentorOwnProfileRow::approvalStatus)
                    .orElse(STATUS_PENDING);
            case ENTERPRISE -> enterpriseProfileRepository.findOwnProfile(userId)
                    .map(EnterpriseProfileRepository.EnterpriseOwnProfileRow::approvalStatus)
                    .orElse(STATUS_PENDING);
            default -> throw new ApiException("BIZ-1001", "role not supported", HttpStatus.BAD_REQUEST);
        };
    }

    private void updateRoleApprovalStatus(long userId, UserRole role, String approvalStatus) {
        switch (role) {
            case MENTOR -> {
                mentorRepository.findOwnProfile(userId)
                        .orElseThrow(() -> new ApiException("BIZ-1002", "mentor profile not found", HttpStatus.NOT_FOUND));
                mentorRepository.updateApprovalStatus(userId, approvalStatus);
                mentorPublicDetailCacheService.evictNow(userId);
                mentorPublicDetailCacheService.evictAfterCommit(userId);
                mentorPublicListCacheService.evictAllNow();
                mentorPublicListCacheService.evictAllAfterCommit();
            }
            case ENTERPRISE -> {
                enterpriseProfileRepository.findOwnProfile(userId)
                        .orElseThrow(() -> new ApiException("BIZ-1002", "enterprise profile not found", HttpStatus.NOT_FOUND));
                enterpriseProfileRepository.updateApprovalStatus(userId, approvalStatus);
            }
            default -> throw new ApiException("BIZ-1001", "role not supported", HttpStatus.BAD_REQUEST);
        }
    }

    private CertificationRepository.ReviewSubjectRow requireReviewSubject(long userId) {
        CertificationRepository.ReviewSubjectRow subject = certificationRepository.findReviewSubject(userId);
        if (subject == null) {
            throw new ApiException("BIZ-1002", "user not found", HttpStatus.NOT_FOUND);
        }
        if (!"MENTOR".equals(subject.role()) && !"ENTERPRISE".equals(subject.role())) {
            throw new ApiException("BIZ-1001", "approval status not supported for role", HttpStatus.BAD_REQUEST);
        }
        return subject;
    }

    private UserRole requireCertificationRole(String rawRole) {
        try {
            UserRole role = UserRole.parse(rawRole);
            if (!CERTIFICATION_ROLES.contains(role) || !UserRole.canSelfRegister(role)) {
                throw AuthException.roleNotAllowedForRegister();
            }
            return role;
        } catch (IllegalArgumentException ex) {
            throw AuthException.roleNotAllowedForRegister();
        }
    }

    private CertificationPayload buildPayload(String displayName, String realName, String companyName, String jobTitle) {
        String normalizedCompanyName = requireText(companyName, "companyName is required", 200);
        String normalizedJobTitle = requireText(jobTitle, "jobTitle is required", 100);
        String normalizedRealName = TextListCodec.normalizeText(realName);
        if (normalizedRealName == null) {
            normalizedRealName = displayName;
        }
        if (normalizedRealName.length() > 100) {
            throw new ApiException("BIZ-1001", "realName too long", HttpStatus.BAD_REQUEST);
        }
        return new CertificationPayload(normalizedRealName, normalizedCompanyName, normalizedJobTitle);
    }

    private String normalizeEmail(String email) {
        String normalized = email == null ? "" : email.trim().toLowerCase();
        if (!StringUtils.hasText(normalized) || !normalized.contains("@")) {
            throw new ApiException("BIZ-1001", "email format is invalid", HttpStatus.BAD_REQUEST);
        }
        return normalized;
    }

    private void validatePassword(String password) {
        if (password == null || !PASSWORD_PATTERN.matcher(password).matches()) {
            throw new ApiException("BIZ-1001", "password length must be between 8 and 64 and include letters and numbers", HttpStatus.BAD_REQUEST);
        }
    }

    private String requireText(String rawValue, String message, int maxLength) {
        String normalized = TextListCodec.normalizeText(rawValue);
        if (normalized == null) {
            throw new ApiException("BIZ-1001", message, HttpStatus.BAD_REQUEST);
        }
        if (normalized.length() > maxLength) {
            throw new ApiException("BIZ-1001", message.replace("is required", "too long"), HttpStatus.BAD_REQUEST);
        }
        return normalized;
    }

    private String normalizeOptionalRoleFilter(String role) {
        if (!StringUtils.hasText(role)) {
            return null;
        }
        UserRole parsedRole = requireCertificationRole(role);
        return parsedRole.name();
    }

    private String normalizeOptionalReviewStatus(String status) {
        if (!StringUtils.hasText(status)) {
            return null;
        }
        return normalizeRequiredReviewStatus(status);
    }

    private String normalizeRequiredReviewStatus(String status) {
        String normalized = status == null ? "" : status.trim().toUpperCase();
        if (!REVIEWABLE_STATUSES.contains(normalized)) {
            throw new ApiException("BIZ-1001", "approval status invalid", HttpStatus.BAD_REQUEST);
        }
        return normalized;
    }

    private Long toIso(Instant instant) {
        return com.bishe.server.common.TimePayloads.toEpochMillis(instant);
    }

    private record CertificationPayload(
            String realName,
            String companyName,
            String jobTitle
    ) {
    }

    public record AssetContentResponse(
            String filename,
            MediaType mediaType,
            byte[] bytes
    ) {
    }
}
