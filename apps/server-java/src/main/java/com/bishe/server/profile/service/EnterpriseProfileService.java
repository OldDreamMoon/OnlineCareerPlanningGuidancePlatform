package com.bishe.server.profile.service;

import com.bishe.server.bounty.repository.BountyRepository;
import com.bishe.server.bounty.service.BountyTaskDetailCacheService;
import com.bishe.server.bounty.service.BountyTaskListCacheService;
import com.bishe.server.auth.repository.UserRepository;
import com.bishe.server.common.TimePayloads;
import com.bishe.server.common.exception.ApiException;
import com.bishe.server.common.util.TextListCodec;
import com.bishe.server.profile.dto.EnterpriseProfileLogoUploadResponse;
import com.bishe.server.profile.dto.EnterpriseProfileResponse;
import com.bishe.server.profile.dto.EnterpriseProfileUpdateRequest;
import com.bishe.server.profile.repository.EnterpriseProfileRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.List;

/**
 * 企业认证资料服务。
 */
@Service
public class EnterpriseProfileService {

    private final EnterpriseProfileRepository enterpriseProfileRepository;
    private final UserRepository userRepository;
    private final EnterpriseLogoStorageService enterpriseLogoStorageService;
    private final BountyRepository bountyRepository;
    private final BountyTaskListCacheService bountyTaskListCacheService;
    private final BountyTaskDetailCacheService bountyTaskDetailCacheService;

    public EnterpriseProfileService(
            EnterpriseProfileRepository enterpriseProfileRepository,
            UserRepository userRepository,
            EnterpriseLogoStorageService enterpriseLogoStorageService,
            BountyRepository bountyRepository,
            BountyTaskListCacheService bountyTaskListCacheService,
            BountyTaskDetailCacheService bountyTaskDetailCacheService
    ) {
        this.enterpriseProfileRepository = enterpriseProfileRepository;
        this.userRepository = userRepository;
        this.enterpriseLogoStorageService = enterpriseLogoStorageService;
        this.bountyRepository = bountyRepository;
        this.bountyTaskListCacheService = bountyTaskListCacheService;
        this.bountyTaskDetailCacheService = bountyTaskDetailCacheService;
    }

    public EnterpriseProfileResponse getMyProfile(long userId) {
        // 企业注册早期可能只创建账号，资料页读取时补齐默认 profile。
        ensureDefaultProfile(userId);
        EnterpriseProfileRepository.EnterpriseOwnProfileRow profile = enterpriseProfileRepository.findOwnProfile(userId)
                .orElseThrow(() -> new ApiException("BIZ-1002", "enterprise profile not found", HttpStatus.NOT_FOUND));
        return toResponse(profile);
    }

    @Transactional
    public EnterpriseProfileResponse updateMyProfile(long userId, EnterpriseProfileUpdateRequest request) {
        ensureDefaultProfile(userId);

        String normalizedRealName = TextListCodec.normalizeText(request.realName());
        if (normalizedRealName != null) {
            // 联系人真实姓名仍存在账号表，企业 profile 保存时同步更新。
            userRepository.updateRealName(userId, normalizedRealName);
        }

        EnterpriseProfileRepository.EnterpriseOwnProfileRow currentProfile = enterpriseProfileRepository.findOwnProfile(userId)
                .orElseThrow(() -> new ApiException("BIZ-1002", "enterprise profile not found", HttpStatus.NOT_FOUND));

        String normalizedCompanyName = TextListCodec.normalizeText(request.companyName());
        String normalizedJobTitle = TextListCodec.normalizeText(request.jobTitle());
        String normalizedIndustry = TextListCodec.normalizeText(request.industry());
        String normalizedCompanySize = TextListCodec.normalizeText(request.companySize());
        String normalizedHiringTags = request.hiringTags() == null
                ? currentProfile.hiringTags()
                : TextListCodec.join(request.hiringTags());
        String normalizedBio = request.bio() == null
                ? currentProfile.bio()
                : TextListCodec.normalizeText(request.bio());
        String normalizedExternalLinks = request.externalLinks() == null
                ? currentProfile.externalLinks()
                : TextListCodec.normalizeText(request.externalLinks());
        String normalizedPreferences = request.preferences() == null
                ? currentProfile.preferences()
                : TextListCodec.normalizeText(request.preferences());

        enterpriseProfileRepository.updateOwnProfile(
                userId,
                normalizedCompanyName == null ? currentProfile.companyName() : normalizedCompanyName,
                normalizedJobTitle == null ? currentProfile.contactTitle() : normalizedJobTitle,
                normalizedIndustry == null ? currentProfile.industry() : normalizedIndustry,
                normalizedCompanySize == null ? currentProfile.companySize() : normalizedCompanySize,
                normalizedHiringTags,
                normalizedBio,
                normalizedExternalLinks,
                normalizedPreferences
        );
        // 企业资料变更后返回重读结果，确保默认值和数据库裁剪后的字段一致。
        return getMyProfile(userId);
    }

    @Transactional
    public EnterpriseProfileLogoUploadResponse uploadMyLogo(long userId, MultipartFile file) {
        ensureDefaultProfile(userId);
        EnterpriseProfileRepository.LogoAssetRow previousLogo = enterpriseProfileRepository.findLogoAssetByUserId(userId).orElse(null);
        EnterpriseLogoStorageService.StoredLogo storedLogo = enterpriseLogoStorageService.upload(userId, file);
        Instant uploadedAt = Instant.parse(storedLogo.uploadedAt());
        enterpriseProfileRepository.saveOrUpdateLogo(
                userId,
                storedLogo.bucket(),
                storedLogo.objectKey(),
                storedLogo.contentType(),
                uploadedAt
        );
        // Logo 会出现在企业任务列表/详情，上传后需要清任务公共缓存。
        evictEnterpriseLogoDependentCaches(userId);

        if (previousLogo != null && hasText(previousLogo.bucket()) && hasText(previousLogo.objectKey())) {
            boolean logoChanged = !storedLogo.bucket().equals(previousLogo.bucket())
                    || !storedLogo.objectKey().equals(previousLogo.objectKey());
            if (logoChanged) {
                // 新 Logo 已经落库，旧对象只做尽力清理。
                enterpriseLogoStorageService.deleteQuietly(previousLogo.bucket(), previousLogo.objectKey());
            }
        }

        return new EnterpriseProfileLogoUploadResponse(
                true,
                true,
                storedLogo.contentType(),
                storedLogo.sizeBytes(),
                TimePayloads.toEpochMillis(uploadedAt),
                buildPublicLogoUrl(userId, uploadedAt)
        );
    }

    public EnterpriseLogoStorageService.StoredLogoContent getMyLogoContent(long userId) {
        ensureDefaultProfile(userId);
        EnterpriseProfileRepository.LogoAssetRow logoAsset = enterpriseProfileRepository.findLogoAssetByUserId(userId)
                .filter(asset -> hasText(asset.bucket()) && hasText(asset.objectKey()))
                .orElseThrow(() -> new ApiException("BIZ-1002", "logo not found", HttpStatus.NOT_FOUND));
        return enterpriseLogoStorageService.read(logoAsset.bucket(), logoAsset.objectKey(), logoAsset.contentType());
    }

    public EnterpriseLogoStorageService.StoredLogoContent getPublicLogoContent(long enterpriseUserId) {
        EnterpriseProfileRepository.LogoAssetRow logoAsset = enterpriseProfileRepository.findPublicLogoAssetByUserId(enterpriseUserId)
                .filter(asset -> hasText(asset.bucket()) && hasText(asset.objectKey()))
                .orElseThrow(() -> new ApiException("BIZ-1002", "logo not found", HttpStatus.NOT_FOUND));
        return enterpriseLogoStorageService.read(logoAsset.bucket(), logoAsset.objectKey(), logoAsset.contentType());
    }

    private void ensureDefaultProfile(long userId) {
        enterpriseProfileRepository.createDefaultProfileIfAbsent(userId, null, null);
    }

    private void evictEnterpriseLogoDependentCaches(long enterpriseUserId) {
        // 列表缓存全量清理，详情缓存只清当前企业发布过的任务。
        bountyTaskListCacheService.evictAllNow();
        bountyTaskListCacheService.evictAllAfterCommit();

        List<Long> taskIds = bountyRepository.findTaskIdsByEnterpriseUserId(enterpriseUserId);
        for (Long taskId : taskIds) {
            if (taskId == null || taskId <= 0) {
                continue;
            }
            bountyTaskDetailCacheService.evictNow(taskId);
            bountyTaskDetailCacheService.evictAfterCommit(taskId);
        }
    }

    private EnterpriseProfileResponse toResponse(EnterpriseProfileRepository.EnterpriseOwnProfileRow profile) {
        boolean logoConfigured = hasText(profile.logoObjectKey());
        return new EnterpriseProfileResponse(
                profile.userId(),
                profile.displayName(),
                profile.realName(),
                profile.companyName(),
                profile.contactTitle(),
                profile.industry(),
                profile.companySize(),
                TextListCodec.split(profile.hiringTags()),
                profile.bio(),
                profile.externalLinks(),
                profile.preferences(),
                logoConfigured ? buildPublicLogoUrl(profile.userId(), profile.logoUpdatedAt()) : null,
                logoConfigured,
                profile.logoContentType(),
                TimePayloads.toEpochMillis(profile.logoUpdatedAt()),
                profile.approvalStatus()
        );
    }

    private String buildPublicLogoUrl(long enterpriseUserId, Instant updatedAt) {
        return EnterpriseLogoUrlSupport.buildPublicLogoUrl(enterpriseUserId, updatedAt);
    }

    private boolean hasText(String value) {
        return StringUtils.hasText(value);
    }
}
