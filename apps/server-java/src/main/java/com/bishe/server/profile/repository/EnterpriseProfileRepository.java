package com.bishe.server.profile.repository;

import com.bishe.server.auth.model.UserAccountStatus;
import com.bishe.server.auth.model.UserRole;
import com.bishe.server.auth.repository.jpa.EnterpriseApprovalProfileJpaRepository;
import com.bishe.server.auth.repository.jpa.UserAccountJpaRepository;
import com.bishe.server.auth.repository.jpa.entity.UserAccountEntity;
import com.bishe.server.common.util.TextListCodec;
import com.bishe.server.profile.repository.jpa.EnterpriseProfileJpaRepository;
import com.bishe.server.profile.repository.jpa.entity.EnterpriseProfileEntity;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

/**
 * 企业资料仓储，当前仅承载最小认证状态与占位资料。
 */
@Repository
public class EnterpriseProfileRepository {

    private static final String DEFAULT_INDUSTRY = "待补充";
    private static final String DEFAULT_COMPANY_SIZE = "待补充";
    private static final String DEFAULT_HIRING_TAGS = "校招,实习";

    private final EnterpriseApprovalProfileJpaRepository enterpriseApprovalProfileJpaRepository;
    private final UserAccountJpaRepository userAccountJpaRepository;
    private final EnterpriseProfileJpaRepository enterpriseProfileJpaRepository;

    public EnterpriseProfileRepository(
            EnterpriseApprovalProfileJpaRepository enterpriseApprovalProfileJpaRepository,
            UserAccountJpaRepository userAccountJpaRepository,
            EnterpriseProfileJpaRepository enterpriseProfileJpaRepository
    ) {
        this.enterpriseApprovalProfileJpaRepository = enterpriseApprovalProfileJpaRepository;
        this.userAccountJpaRepository = userAccountJpaRepository;
        this.enterpriseProfileJpaRepository = enterpriseProfileJpaRepository;
    }

    public void createDefaultProfileIfAbsent(long userId, String companyName, String contactTitle) {
        createDefaultProfileIfAbsent(userId, companyName, contactTitle, "PENDING");
    }

    public void createDefaultProfileIfAbsent(long userId, String companyName, String contactTitle, String approvalStatus) {
        EnterpriseProfileEntity existingProfile = enterpriseProfileJpaRepository.findByUserId(userId).orElse(null);
        if (existingProfile != null) {
            return;
        }

        EnterpriseProfileEntity profile = EnterpriseProfileEntity.create(userId);
        profile.setCompanyName(TextListCodec.normalizeText(companyName));
        profile.setIndustry(DEFAULT_INDUSTRY);
        profile.setCompanySize(DEFAULT_COMPANY_SIZE);
        profile.setHiringTags(DEFAULT_HIRING_TAGS);
        profile.setContactTitle(TextListCodec.normalizeText(contactTitle));
        profile.setApprovalStatus(approvalStatus);
        enterpriseProfileJpaRepository.save(profile);
    }

    public Optional<String> findApprovalStatusByUserId(long userId) {
        if (findActiveEnterpriseUser(userId).isEmpty()) {
            return Optional.empty();
        }
        return enterpriseApprovalProfileJpaRepository.findApprovalStatusByUserId(userId);
    }

    public Optional<EnterpriseOwnProfileRow> findOwnProfile(long userId) {
        Optional<UserAccountEntity> user = findActiveEnterpriseUser(userId);
        if (user.isEmpty()) {
            return Optional.empty();
        }
        return enterpriseProfileJpaRepository.findByUserId(userId)
                .map(profile -> toOwnProfileRow(user.get(), profile));
    }

    public void updateOwnProfile(
            long userId,
            String companyName,
            String contactTitle,
            String industry,
            String companySize,
            String hiringTags,
            String bio,
            String externalLinks,
            String preferences
    ) {
        enterpriseProfileJpaRepository.findByUserId(userId).ifPresent(profile -> {
            profile.setCompanyName(companyName);
            profile.setContactTitle(contactTitle);
            profile.setIndustry(industry);
            profile.setCompanySize(companySize);
            profile.setHiringTags(hiringTags);
            profile.setBio(bio);
            profile.setExternalLinks(externalLinks);
            profile.setPreferences(preferences);
            enterpriseProfileJpaRepository.save(profile);
        });
    }

    public void updateOwnProfile(long userId, String companyName, String contactTitle) {
        enterpriseProfileJpaRepository.findByUserId(userId).ifPresent(profile -> {
            profile.setCompanyName(companyName);
            profile.setContactTitle(contactTitle);
            enterpriseProfileJpaRepository.save(profile);
        });
    }

    public void updateApprovalStatus(long userId, String approvalStatus) {
        enterpriseProfileJpaRepository.findByUserId(userId).ifPresent(profile -> {
            profile.setApprovalStatus(approvalStatus);
            enterpriseProfileJpaRepository.save(profile);
        });
    }

    public Optional<LogoAssetRow> findLogoAssetByUserId(long userId) {
        return enterpriseProfileJpaRepository.findByUserId(userId)
                .map(this::toLogoAssetRow);
    }

    public Optional<LogoAssetRow> findPublicLogoAssetByUserId(long userId) {
        if (findActiveEnterpriseUser(userId).isEmpty()) {
            return Optional.empty();
        }
        return enterpriseProfileJpaRepository.findByUserId(userId)
                .map(this::toLogoAssetRow);
    }

    public void saveOrUpdateLogo(long userId, String bucket, String objectKey, String contentType, Instant updatedAt) {
        EnterpriseProfileEntity profile = enterpriseProfileJpaRepository.findByUserId(userId)
                .orElseGet(() -> createLogoProfile(userId));
        profile.setLogoBucket(bucket);
        profile.setLogoObjectKey(objectKey);
        profile.setLogoContentType(contentType);
        profile.setLogoUpdatedAt(updatedAt);
        enterpriseProfileJpaRepository.save(profile);
    }

    private Optional<UserAccountEntity> findActiveEnterpriseUser(long userId) {
        return userAccountJpaRepository.findByIdAndRoleAndDeletedFalse(userId, UserRole.ENTERPRISE)
                .filter(user -> user.getStatus() == UserAccountStatus.ACTIVE);
    }

    private EnterpriseProfileEntity createLogoProfile(long userId) {
        EnterpriseProfileEntity profile = EnterpriseProfileEntity.create(userId);
        profile.setIndustry(DEFAULT_INDUSTRY);
        profile.setCompanySize(DEFAULT_COMPANY_SIZE);
        profile.setHiringTags(DEFAULT_HIRING_TAGS);
        profile.setApprovalStatus("PENDING");
        return profile;
    }

    private EnterpriseOwnProfileRow toOwnProfileRow(UserAccountEntity user, EnterpriseProfileEntity profile) {
        return new EnterpriseOwnProfileRow(
                user.getId(),
                user.getDisplayName(),
                user.getRealName(),
                profile.getCompanyName(),
                profile.getContactTitle(),
                profile.getIndustry(),
                profile.getCompanySize(),
                profile.getHiringTags(),
                profile.getBio(),
                profile.getExternalLinks(),
                profile.getPreferences(),
                profile.getLogoObjectKey(),
                profile.getLogoContentType(),
                profile.getLogoUpdatedAt(),
                profile.getApprovalStatus()
        );
    }

    private LogoAssetRow toLogoAssetRow(EnterpriseProfileEntity profile) {
        return new LogoAssetRow(
                profile.getLogoBucket(),
                profile.getLogoObjectKey(),
                profile.getLogoContentType(),
                profile.getLogoUpdatedAt()
        );
    }

    public record EnterpriseOwnProfileRow(
            long userId,
            String displayName,
            String realName,
            String companyName,
            String contactTitle,
            String industry,
            String companySize,
            String hiringTags,
            String bio,
            String externalLinks,
            String preferences,
            String logoObjectKey,
            String logoContentType,
            Instant logoUpdatedAt,
            String approvalStatus
    ) {
    }

    public record LogoAssetRow(
            String bucket,
            String objectKey,
            String contentType,
            Instant updatedAt
    ) {
    }
}
