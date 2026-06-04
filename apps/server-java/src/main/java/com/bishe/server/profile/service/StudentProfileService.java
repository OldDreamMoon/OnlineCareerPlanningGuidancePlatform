package com.bishe.server.profile.service;

import com.bishe.server.auth.model.UserRole;
import com.bishe.server.auth.repository.UserRepository;
import com.bishe.server.common.TimePayloads;
import com.bishe.server.common.exception.ApiException;
import com.bishe.server.common.util.TextListCodec;
import com.bishe.server.profile.dto.StudentProfileAvatarUploadResponse;
import com.bishe.server.profile.dto.StudentPublicProfileResponse;
import com.bishe.server.profile.dto.StudentProfilePrivacyUpdateRequest;
import com.bishe.server.profile.dto.StudentProfilePrivacyUpdateResponse;
import com.bishe.server.profile.dto.StudentProfileResponse;
import com.bishe.server.profile.dto.StudentProfileSocialLinkItem;
import com.bishe.server.profile.dto.StudentProfileUpdateRequest;
import com.bishe.server.profile.dto.StudentProfileUpdateResponse;
import com.bishe.server.profile.repository.StudentProfileRepository;
import com.bishe.server.security.UserPrincipal;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 学生画像服务：组合冷启动资料、隐私矩阵与动态画像快照。
 */
@Service
public class StudentProfileService {

    private static final TypeReference<List<StudentProfileResponse.PortraitTagItem>> PORTRAIT_TAG_LIST_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<List<StudentProfileSocialLinkItem>> SOCIAL_LINK_LIST_TYPE = new TypeReference<>() {
    };
    private static final Set<String> ALLOWED_SOCIAL_PLATFORMS = Set.of(
            "GITHUB",
            "PORTFOLIO",
            "GITEE",
            "JUEJIN",
            "CSDN",
            "ZHIHU",
            "BILIBILI",
            "XIAOHONGSHU",
            "WEIBO"
    );
    private static final int MAX_SOCIAL_LINKS = 8;

    private final StudentProfileRepository studentProfileRepository;
    private final UserRepository userRepository;
    private final StudentPortraitRefreshService studentPortraitRefreshService;
    private final StudentPortraitSnapshotCacheService studentPortraitSnapshotCacheService;
    private final StudentPublicProfileCacheService studentPublicProfileCacheService;
    private final StudentAvatarStorageService studentAvatarStorageService;
    private final ObjectMapper objectMapper;

    public StudentProfileService(
            StudentProfileRepository studentProfileRepository,
            UserRepository userRepository,
            StudentPortraitRefreshService studentPortraitRefreshService,
            StudentPortraitSnapshotCacheService studentPortraitSnapshotCacheService,
            StudentPublicProfileCacheService studentPublicProfileCacheService,
            StudentAvatarStorageService studentAvatarStorageService,
            ObjectMapper objectMapper
    ) {
        this.studentProfileRepository = studentProfileRepository;
        this.userRepository = userRepository;
        this.studentPortraitRefreshService = studentPortraitRefreshService;
        this.studentPortraitSnapshotCacheService = studentPortraitSnapshotCacheService;
        this.studentPublicProfileCacheService = studentPublicProfileCacheService;
        this.studentAvatarStorageService = studentAvatarStorageService;
        this.objectMapper = objectMapper;
    }

    public StudentProfileResponse getMyProfile(long userId) {
        StudentProfileRepository.StudentProfileRow profileRow = requireStudentProfile(userId);
        StudentProfileRepository.PortraitSnapshotRow snapshotRow = loadMyPortraitSnapshot(userId);
        StudentProfileResponse.PortraitPayload portraitPayload = buildPortraitPayload(snapshotRow);
        StudentProfileResponse.PortraitEvidencePayload evidencePayload = portraitPayload == null
                ? buildEvidencePayload((JsonNode) null)
                : portraitPayload.evidence();
        Long updatedAt = snapshotRow == null ? null : TimePayloads.toEpochMillis(snapshotRow.updatedAt());
        long communityScore7d = calculateCommunityScore(evidencePayload);
        List<String> skillTags = TextListCodec.split(profileRow.skillTags());
        List<StudentProfileSocialLinkItem> socialLinks = loadSocialLinks(profileRow);
        StudentProfileResponse.PrivacySettingsPayload privacySettings = loadPrivacySettings(userId);

        // 我的资料返回完整字段，隐私矩阵只在公开空间读取时生效。
        return new StudentProfileResponse(
                profileRow.userId(),
                profileRow.displayName(),
                profileRow.email(),
                profileRow.tier(),
                calculateCompletionRate(profileRow, skillTags, socialLinks),
                profileRow.realName(),
                profileRow.jobStatus(),
                profileRow.schoolName(),
                profileRow.major(),
                profileRow.grade(),
                profileRow.gpa(),
                profileRow.targetPosition(),
                profileRow.honors(),
                findSocialLinkValue(socialLinks, "GITHUB"),
                findSocialLinkValue(socialLinks, "PORTFOLIO"),
                socialLinks,
                profileRow.phone(),
                profileRow.wechat(),
                skillTags,
                profileRow.selfIntro(),
                buildAvatarPayload(profileRow),
                privacySettings,
                portraitPayload == null
                        ? new StudentProfileResponse.PortraitPayload(
                        List.of(),
                        List.of(),
                        List.of(),
                        null,
                        null,
                        null,
                        null,
                        List.of(),
                        null,
                        evidencePayload,
                        updatedAt
                )
                        : portraitPayload,
                communityScore7d
        );
    }

    public StudentPublicProfileResponse getPublicProfile(long targetUserId, UserPrincipal viewer) {
        StudentPublicProfileCacheService.StudentPublicProfileSlice slice = studentPublicProfileCacheService.getPublicProfile(
                targetUserId,
                () -> loadPublicProfileSlice(targetUserId)
        );
        if (slice == null) {
            throw new ApiException("BIZ-1002", "student not found", HttpStatus.NOT_FOUND);
        }
        StudentProfileResponse.PrivacySettingsPayload privacySettings = slice.privacy();
        ViewerAudience viewerAudience = resolveViewerAudience(targetUserId, slice.schoolNameKey(), viewer);

        // 公开空间先读公共切片缓存，再按访问者身份即时套隐私矩阵。
        return new StudentPublicProfileResponse(
                slice.userId(),
                slice.displayName(),
                slice.tier(),
                maskText(slice.realName(), privacySettings.realName(), viewerAudience),
                maskText(slice.jobStatus(), privacySettings.jobStatus(), viewerAudience),
                maskText(slice.schoolName(), privacySettings.eduInfo(), viewerAudience),
                maskText(slice.major(), privacySettings.eduInfo(), viewerAudience),
                maskText(slice.grade(), privacySettings.eduInfo(), viewerAudience),
                maskText(slice.gpa(), privacySettings.academic(), viewerAudience),
                maskText(slice.targetPosition(), privacySettings.targetPos(), viewerAudience),
                maskText(slice.honors(), privacySettings.academic(), viewerAudience),
                maskList(slice.skillTags(), privacySettings.skills(), viewerAudience),
                maskText(slice.selfIntro(), privacySettings.intro(), viewerAudience),
                slice.avatar(),
                maskSocialLinks(slice.socialLinks(), privacySettings.social(), viewerAudience),
                privacySettings,
                maskPortrait(slice.portrait(), privacySettings.portrait(), viewerAudience),
                slice.communityScore7d()
        );
    }

    public StudentProfileUpdateResponse updateMyProfile(long userId, StudentProfileUpdateRequest request) {
        StudentProfileRepository.StudentProfileRow currentProfile = requireStudentProfile(userId);

        String normalizedDisplayName = TextListCodec.normalizeText(request.displayName());
        String normalizedRealName = TextListCodec.normalizeText(request.realName());
        String normalizedSchoolName = TextListCodec.normalizeText(request.schoolName());
        List<StudentProfileSocialLinkItem> socialLinks = normalizeSocialLinksForWrite(request.socialLinks(), request.github(), request.portfolio());
        if (request.displayName() != null && normalizedDisplayName != null && !normalizedDisplayName.equals(currentProfile.displayName())) {
            // displayName 同时是登录态展示名，资料保存时需要同步账号表。
            userRepository.updateDisplayName(userId, normalizedDisplayName);
        }
        userRepository.updateRealName(userId, normalizedRealName);

        studentProfileRepository.saveOrUpdate(
                userId,
                TextListCodec.normalizeText(request.jobStatus()),
                normalizedSchoolName,
                normalizeSchoolNameKey(normalizedSchoolName),
                TextListCodec.normalizeText(request.major()),
                TextListCodec.normalizeText(request.grade()),
                TextListCodec.normalizeText(request.gpa()),
                TextListCodec.normalizeText(request.targetPosition()),
                normalizeMultilineText(request.honors()),
                findSocialLinkValue(socialLinks, "GITHUB"),
                findSocialLinkValue(socialLinks, "PORTFOLIO"),
                serializeSocialLinks(socialLinks),
                TextListCodec.normalizeText(request.phone()),
                TextListCodec.normalizeText(request.wechat()),
                TextListCodec.join(request.skillTags()),
                TextListCodec.normalizeText(request.selfIntro())
        );
        // 资料变化会影响公开空间、画像事实层和推荐信号，提交前后都清缓存。
        studentPublicProfileCacheService.evictNow(userId);
        studentPublicProfileCacheService.evictAfterCommit(userId);
        studentPortraitRefreshService.refreshNow(userId);
        return new StudentProfileUpdateResponse(true, true);
    }

    public StudentProfilePrivacyUpdateResponse updateMyPrivacy(long userId, StudentProfilePrivacyUpdateRequest request) {
        requireStudentProfile(userId);
        StudentProfileResponse.PrivacySettingsPayload privacy = normalizePrivacySettings(request.privacy());
        try {
            studentProfileRepository.saveOrUpdatePrivacySettings(userId, objectMapper.writeValueAsString(privacy));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("failed to serialize privacy settings", ex);
        }
        // 隐私变更只影响公开读取，不强制刷新画像内容。
        studentPublicProfileCacheService.evictNow(userId);
        studentPublicProfileCacheService.evictAfterCommit(userId);
        return new StudentProfilePrivacyUpdateResponse(true);
    }

    public StudentProfileAvatarUploadResponse uploadMyAvatar(long userId, MultipartFile file) {
        requireStudentProfile(userId);
        StudentProfileRepository.AvatarAssetRow previousAvatar = studentProfileRepository.findAvatarAssetByUserId(userId).orElse(null);
        StudentAvatarStorageService.StoredAvatar storedAvatar = studentAvatarStorageService.upload(userId, file);
        Instant uploadedAt = Instant.parse(storedAvatar.uploadedAt());
        studentProfileRepository.saveOrUpdateAvatar(
                userId,
                storedAvatar.bucket(),
                storedAvatar.objectKey(),
                storedAvatar.contentType(),
                uploadedAt
        );
        // 头像 URL 由更新时间做版本戳，公开空间缓存必须立即失效。
        studentPublicProfileCacheService.evictNow(userId);
        studentPublicProfileCacheService.evictAfterCommit(userId);

        if (previousAvatar != null && hasText(previousAvatar.bucket()) && hasText(previousAvatar.objectKey())) {
            boolean avatarChanged = !storedAvatar.bucket().equals(previousAvatar.bucket())
                    || !storedAvatar.objectKey().equals(previousAvatar.objectKey());
            if (avatarChanged) {
                // 新对象已经写入 DB 后再尽力删除旧对象，失败不回滚主流程。
                studentAvatarStorageService.deleteQuietly(previousAvatar.bucket(), previousAvatar.objectKey());
            }
        }

        return new StudentProfileAvatarUploadResponse(
                true,
                storedAvatar.contentType(),
                storedAvatar.sizeBytes(),
                TimePayloads.toEpochMillis(uploadedAt)
        );
    }

    public StudentAvatarStorageService.StoredAvatarContent getMyAvatarContent(long userId) {
        requireStudentProfile(userId);
        StudentProfileRepository.AvatarAssetRow avatarAsset = studentProfileRepository.findAvatarAssetByUserId(userId)
                .filter(asset -> hasText(asset.bucket()) && hasText(asset.objectKey()))
                .orElseThrow(() -> new ApiException("BIZ-1002", "avatar not found", HttpStatus.NOT_FOUND));
        return studentAvatarStorageService.read(avatarAsset.bucket(), avatarAsset.objectKey());
    }

    public StudentAvatarStorageService.StoredAvatarContent getPublicAvatarContent(long targetUserId) {
        requireStudentProfile(targetUserId);
        StudentProfileRepository.AvatarAssetRow avatarAsset = studentProfileRepository.findAvatarAssetByUserId(targetUserId)
                .filter(asset -> hasText(asset.bucket()) && hasText(asset.objectKey()))
                .orElseThrow(() -> new ApiException("BIZ-1002", "avatar not found", HttpStatus.NOT_FOUND));
        return studentAvatarStorageService.read(avatarAsset.bucket(), avatarAsset.objectKey());
    }

    private StudentProfileRepository.StudentProfileRow requireStudentProfile(long userId) {
        return studentProfileRepository.findStudentProfileByUserId(userId)
                .orElseThrow(() -> new ApiException("BIZ-1002", "student not found", HttpStatus.NOT_FOUND));
    }

    private StudentProfileRepository.PortraitSnapshotRow loadMyPortraitSnapshot(long userId) {
        StudentProfileRepository.PortraitSnapshotRow snapshotRow = studentPortraitSnapshotCacheService.getSnapshot(
                userId,
                () -> studentProfileRepository.findPortraitSnapshot(userId).orElse(null)
        );
        if (snapshotRow != null) {
            return snapshotRow;
        }
        // 首次打开资料页时允许同步生成一次画像快照，避免前端长期显示空画像。
        studentPortraitRefreshService.refreshNow(userId);
        return studentPortraitSnapshotCacheService.getSnapshot(
                userId,
                () -> studentProfileRepository.findPortraitSnapshot(userId).orElse(null)
        );
    }

    private StudentPublicProfileCacheService.StudentPublicProfileSlice loadPublicProfileSlice(long targetUserId) {
        StudentProfileRepository.StudentProfileRow profileRow = requireStudentProfile(targetUserId);
        StudentProfileRepository.PortraitSnapshotRow snapshotRow = studentProfileRepository.findPortraitSnapshot(targetUserId).orElse(null);
        if (snapshotRow == null) {
            // 公开空间兜底只走模板画像，避免访客读取触发 LLM 成本。
            studentPortraitRefreshService.refreshNowWithoutLlm(targetUserId);
            snapshotRow = studentProfileRepository.findPortraitSnapshot(targetUserId).orElse(null);
        }
        StudentProfileResponse.PortraitPayload portraitPayload = buildPortraitPayload(snapshotRow);
        StudentProfileResponse.PortraitEvidencePayload evidencePayload = portraitPayload == null
                ? buildEvidencePayload((JsonNode) null)
                : portraitPayload.evidence();
        List<String> skillTags = TextListCodec.split(profileRow.skillTags());
        List<StudentProfileSocialLinkItem> socialLinks = loadSocialLinks(profileRow);
        StudentProfileResponse.PrivacySettingsPayload privacySettings = loadPrivacySettings(targetUserId);

        return new StudentPublicProfileCacheService.StudentPublicProfileSlice(
                profileRow.userId(),
                profileRow.displayName(),
                profileRow.tier(),
                profileRow.realName(),
                profileRow.jobStatus(),
                profileRow.schoolName(),
                profileRow.schoolNameKey(),
                profileRow.major(),
                profileRow.grade(),
                profileRow.gpa(),
                profileRow.targetPosition(),
                profileRow.honors(),
                skillTags,
                profileRow.selfIntro(),
                buildAvatarPayload(profileRow),
                socialLinks,
                privacySettings,
                portraitPayload,
                calculateCommunityScore(evidencePayload)
        );
    }

    private ViewerAudience resolveViewerAudience(
            long targetUserId,
            String targetSchoolNameKey,
            UserPrincipal viewer
    ) {
        if (viewer == null || !hasText(viewer.getRole())) {
            return new ViewerAudience(UserRole.ADMIN, false, true);
        }

        UserRole viewerRole;
        try {
            viewerRole = UserRole.parse(viewer.getRole());
        } catch (IllegalArgumentException ex) {
            return new ViewerAudience(UserRole.ADMIN, false, true);
        }

        if (viewerRole != UserRole.STUDENT) {
            return new ViewerAudience(viewerRole, false, false);
        }

        Long viewerUserId = viewer.getUserId();
        boolean sameSchool = viewerUserId != null && viewerUserId == targetUserId;
        if (!sameSchool && viewerUserId != null) {
            // 学生可见性区分“同校学生”和“平台学生”，依赖归一化后的 schoolNameKey。
            StudentProfileRepository.StudentProfileRow viewerProfile = studentProfileRepository.findStudentProfileByUserId(viewerUserId).orElse(null);
            sameSchool = viewerProfile != null
                    && hasText(viewerProfile.schoolNameKey())
                    && hasText(targetSchoolNameKey)
                    && viewerProfile.schoolNameKey().equals(targetSchoolNameKey);
        }
        return new ViewerAudience(UserRole.STUDENT, sameSchool, false);
    }

    private StudentProfileResponse.PrivacySettingsPayload loadPrivacySettings(long userId) {
        return studentProfileRepository.findPrivacySettingsJson(userId)
                .map(this::parsePrivacySettings)
                .orElseGet(this::defaultPrivacySettings);
    }

    private StudentProfileResponse.AvatarPayload buildAvatarPayload(StudentProfileRepository.StudentProfileRow profileRow) {
        return new StudentProfileResponse.AvatarPayload(
                hasText(profileRow.avatarObjectKey()),
                profileRow.avatarContentType(),
                TimePayloads.toEpochMillis(profileRow.avatarUpdatedAt())
        );
    }

    private String maskText(
            String value,
            StudentProfileResponse.VisibilityItem visibility,
            ViewerAudience viewerAudience
    ) {
        return isVisibleToViewer(visibility, viewerAudience) ? TextListCodec.normalizeText(value) : null;
    }

    private List<String> maskList(
            List<String> values,
            StudentProfileResponse.VisibilityItem visibility,
            ViewerAudience viewerAudience
    ) {
        if (!isVisibleToViewer(visibility, viewerAudience) || values == null || values.isEmpty()) {
            return List.of();
        }
        return List.copyOf(values);
    }

    private List<StudentProfileSocialLinkItem> maskSocialLinks(
            List<StudentProfileSocialLinkItem> socialLinks,
            StudentProfileResponse.VisibilityItem visibility,
            ViewerAudience viewerAudience
    ) {
        if (!isVisibleToViewer(visibility, viewerAudience) || socialLinks == null || socialLinks.isEmpty()) {
            return List.of();
        }
        return List.copyOf(socialLinks);
    }

    private StudentProfileResponse.PortraitPayload maskPortrait(
            StudentProfileResponse.PortraitPayload portrait,
            StudentProfileResponse.VisibilityItem visibility,
            ViewerAudience viewerAudience
    ) {
        if (!isVisibleToViewer(visibility, viewerAudience) || portrait == null) {
            return null;
        }
        // 画像可见时整体返回，避免把标签、建议和证据拆成不同隐私口径。
        return new StudentProfileResponse.PortraitPayload(
                portrait.tags() == null ? List.of() : List.copyOf(portrait.tags()),
                portrait.strengthTags() == null ? List.of() : List.copyOf(portrait.strengthTags()),
                portrait.riskTags() == null ? List.of() : List.copyOf(portrait.riskTags()),
                portrait.signalLevel(),
                portrait.freshnessLevel(),
                portrait.headline(),
                portrait.summary(),
                portrait.nextActions() == null ? List.of() : List.copyOf(portrait.nextActions()),
                portrait.summaryVersion(),
                portrait.evidence(),
                portrait.updatedAt()
        );
    }

    private boolean isVisibleToViewer(
            StudentProfileResponse.VisibilityItem visibility,
            ViewerAudience viewerAudience
    ) {
        if (visibility == null) {
            return false;
        }
        if (viewerAudience.guest()) {
            return visibility.guest();
        }

        return switch (viewerAudience.role()) {
            case STUDENT -> viewerAudience.sameSchool()
                    ? visibility.student() || visibility.platformStudent()
                    : visibility.platformStudent();
            case MENTOR -> visibility.mentor();
            case ENTERPRISE -> visibility.enterprise();
            default -> visibility.guest();
        };
    }

    private StudentProfileResponse.PrivacySettingsPayload parsePrivacySettings(String json) {
        if (json == null || json.isBlank()) {
            return defaultPrivacySettings();
        }

        try {
            JsonNode root = objectMapper.readTree(json);
            StudentProfileResponse.PrivacySettingsPayload defaults = defaultPrivacySettings();
            return new StudentProfileResponse.PrivacySettingsPayload(
                    normalizeVisibilityItem(root.path("realName"), defaults.realName()),
                    normalizeVisibilityItem(root.path("jobStatus"), defaults.jobStatus()),
                    normalizeVisibilityItem(root.path("eduInfo"), defaults.eduInfo()),
                    normalizeVisibilityItem(root.path("targetPos"), defaults.targetPos()),
                    normalizeVisibilityItem(root.path("academic"), defaults.academic()),
                    normalizeVisibilityItem(root.path("skills"), defaults.skills()),
                    normalizeVisibilityItem(root.path("intro"), defaults.intro()),
                    normalizeVisibilityItem(root.path("social"), defaults.social()),
                    normalizeVisibilityItem(root.path("email"), defaults.email()),
                    normalizeVisibilityItem(root.path("phone"), defaults.phone()),
                    normalizeVisibilityItem(root.path("wechat"), defaults.wechat()),
                    normalizeVisibilityItem(root.path("portrait"), defaults.portrait())
            );
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("failed to parse privacy settings json", ex);
        }
    }

    private List<StudentProfileResponse.PortraitTagItem> parsePortraitTags(String portraitTagsJson) {
        if (portraitTagsJson == null || portraitTagsJson.isBlank()) {
            return List.of();
        }

        try {
            return objectMapper.readValue(portraitTagsJson, PORTRAIT_TAG_LIST_TYPE);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("failed to parse portrait tags json", ex);
        }
    }

    private List<StudentProfileSocialLinkItem> loadSocialLinks(StudentProfileRepository.StudentProfileRow profileRow) {
        List<StudentProfileSocialLinkItem> storedLinks = parseSocialLinks(profileRow.socialLinksJson());
        if (!storedLinks.isEmpty()) {
            return storedLinks;
        }
        // 旧版 github/portfolio 字段仍向新 socialLinks 结构兼容。
        return buildLegacySocialLinks(profileRow.github(), profileRow.portfolio());
    }

    private List<StudentProfileSocialLinkItem> parseSocialLinks(String socialLinksJson) {
        if (socialLinksJson == null || socialLinksJson.isBlank()) {
            return List.of();
        }

        try {
            return sanitizeSocialLinks(objectMapper.readValue(socialLinksJson, SOCIAL_LINK_LIST_TYPE), false);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("failed to parse social links json", ex);
        }
    }

    private StudentProfileResponse.PortraitPayload buildPortraitPayload(StudentProfileRepository.PortraitSnapshotRow snapshotRow) {
        if (snapshotRow == null) {
            return null;
        }
        // evidenceJson 是画像解释层，前端画像卡和社区贡献分都从这里读取。
        JsonNode evidenceNode = readJsonSafely(snapshotRow.evidenceJson());
        return new StudentProfileResponse.PortraitPayload(
                parsePortraitTags(snapshotRow.portraitTagsJson()),
                readStringList(evidenceNode.path("strengthTags")),
                readStringList(evidenceNode.path("riskTags")),
                readText(evidenceNode, "signalLevel"),
                readText(evidenceNode, "freshnessLevel"),
                readText(evidenceNode, "headline"),
                readText(evidenceNode, "summary"),
                readStringList(evidenceNode.path("nextActions")),
                readText(evidenceNode, "summaryVersion"),
                buildEvidencePayload(evidenceNode),
                TimePayloads.toEpochMillis(snapshotRow.updatedAt())
        );
    }

    private StudentProfileResponse.PortraitEvidencePayload buildEvidencePayload(JsonNode evidenceNode) {
        return new StudentProfileResponse.PortraitEvidencePayload(
                evidenceNode.path("masteredSkills").asInt(0),
                evidenceNode.path("learningSkills").asInt(0),
                evidenceNode.path("interviewMessages7d").asInt(0),
                evidenceNode.path("posts7d").asInt(0),
                evidenceNode.path("comments7d").asInt(0),
                evidenceNode.path("likesReceived7d").asInt(0)
        );
    }

    private JsonNode readJsonSafely(String json) {
        if (json == null || json.isBlank()) {
            return objectMapper.createObjectNode();
        }

        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("failed to parse portrait evidence json", ex);
        }
    }

    private List<String> readStringList(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        node.forEach(item -> {
            String value = TextListCodec.normalizeText(item.asText(null));
            if (value != null) {
                values.add(value);
            }
        });
        return List.copyOf(values);
    }

    private String readText(JsonNode node, String fieldName) {
        if (node == null || fieldName == null || fieldName.isBlank()) {
            return null;
        }
        return TextListCodec.normalizeText(node.path(fieldName).asText(null));
    }

    private long calculateCommunityScore(StudentProfileResponse.PortraitEvidencePayload evidencePayload) {
        return evidencePayload.posts7d() * 5L + evidencePayload.comments7d() * 2L + evidencePayload.likesReceived7d();
    }

    private int calculateCompletionRate(
            StudentProfileRepository.StudentProfileRow row,
            List<String> skillTags,
            List<StudentProfileSocialLinkItem> socialLinks
    ) {
        int socialLinkCount = Math.min(socialLinks.size(), 2);
        // 完整度只统计冷启动关键字段，动态画像和隐私设置不参与分母。
        List<Boolean> checklist = List.of(
                hasText(row.realName()),
                hasText(row.jobStatus()),
                hasText(row.schoolName()),
                hasText(row.grade()),
                hasText(row.major()),
                hasText(row.gpa()),
                hasText(row.targetPosition()),
                hasText(row.honors()),
                socialLinkCount >= 1,
                socialLinkCount >= 2,
                hasText(row.phone()),
                hasText(row.wechat()),
                !skillTags.isEmpty(),
                hasText(row.selfIntro())
        );
        long completed = checklist.stream().filter(Boolean::booleanValue).count();
        return (int) Math.round(completed * 100.0 / checklist.size());
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String normalizeSchoolNameKey(String schoolName) {
        if (!hasText(schoolName)) {
            return null;
        }

        String collapsed = schoolName
                .replace('\u3000', ' ')
                .trim()
                .replaceAll("\\s+", " ");
        return collapsed.isEmpty() ? null : collapsed.toLowerCase(Locale.ROOT);
    }

    private String normalizeMultilineText(String value) {
        if (value == null) {
            return null;
        }

        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private List<StudentProfileSocialLinkItem> normalizeSocialLinksForWrite(
            List<StudentProfileSocialLinkItem> socialLinks,
            String legacyGithub,
            String legacyPortfolio
    ) {
        List<StudentProfileSocialLinkItem> candidates = new ArrayList<>();
        if (socialLinks != null) {
            candidates.addAll(socialLinks);
        }
        if (candidates.isEmpty()) {
            candidates.addAll(buildLegacySocialLinks(legacyGithub, legacyPortfolio));
        }
        return sanitizeSocialLinks(candidates, true);
    }

    private List<StudentProfileSocialLinkItem> buildLegacySocialLinks(String legacyGithub, String legacyPortfolio) {
        List<StudentProfileSocialLinkItem> socialLinks = new ArrayList<>();
        String normalizedGithub = TextListCodec.normalizeText(legacyGithub);
        String normalizedPortfolio = TextListCodec.normalizeText(legacyPortfolio);
        if (hasText(normalizedGithub)) {
            socialLinks.add(new StudentProfileSocialLinkItem("GITHUB", normalizedGithub));
        }
        if (hasText(normalizedPortfolio)) {
            socialLinks.add(new StudentProfileSocialLinkItem("PORTFOLIO", normalizedPortfolio));
        }
        return socialLinks;
    }

    private List<StudentProfileSocialLinkItem> sanitizeSocialLinks(
            List<StudentProfileSocialLinkItem> socialLinks,
            boolean strict
    ) {
        if (socialLinks == null || socialLinks.isEmpty()) {
            return List.of();
        }

        LinkedHashMap<String, StudentProfileSocialLinkItem> normalized = new LinkedHashMap<>();
        for (StudentProfileSocialLinkItem socialLink : socialLinks) {
            if (socialLink == null) {
                if (strict) {
                    throw new ApiException("REQ-1001", "social link item is required", HttpStatus.BAD_REQUEST);
                }
                continue;
            }

            String platform = normalizeSocialPlatform(socialLink.platform());
            String value = TextListCodec.normalizeText(socialLink.value());
            if (!hasText(platform) || !hasText(value)) {
                if (strict) {
                    throw new ApiException("REQ-1001", "social link platform and value are required", HttpStatus.BAD_REQUEST);
                }
                continue;
            }
            if (!ALLOWED_SOCIAL_PLATFORMS.contains(platform)) {
                if (strict) {
                    throw new ApiException("REQ-1001", "unsupported social link platform", HttpStatus.BAD_REQUEST);
                }
                continue;
            }

            // LinkedHashMap 保留用户输入顺序，同平台只保留第一条，避免公开页重复展示。
            normalized.putIfAbsent(platform, new StudentProfileSocialLinkItem(platform, value));
        }

        if (strict && normalized.size() > MAX_SOCIAL_LINKS) {
            throw new ApiException("REQ-1001", "too many social links", HttpStatus.BAD_REQUEST);
        }
        return List.copyOf(normalized.values());
    }

    private String normalizeSocialPlatform(String platform) {
        if (!hasText(platform)) {
            return null;
        }
        return platform.trim().toUpperCase(Locale.ROOT);
    }

    private String findSocialLinkValue(List<StudentProfileSocialLinkItem> socialLinks, String platform) {
        return socialLinks.stream()
                .filter(item -> platform.equals(item.platform()))
                .map(StudentProfileSocialLinkItem::value)
                .findFirst()
                .orElse(null);
    }

    private String serializeSocialLinks(List<StudentProfileSocialLinkItem> socialLinks) {
        if (socialLinks == null || socialLinks.isEmpty()) {
            return null;
        }

        try {
            return objectMapper.writeValueAsString(socialLinks);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("failed to serialize social links", ex);
        }
    }

    private StudentProfileResponse.PrivacySettingsPayload defaultPrivacySettings() {
        // 默认策略偏展示成长信息，联系方式默认不向访客、学生和导师开放。
        return new StudentProfileResponse.PrivacySettingsPayload(
                visibility(false, true, true, true, true),
                visibility(true, true, true, true, true),
                visibility(true, true, true, true, true),
                visibility(true, true, true, true, true),
                visibility(false, true, true, true, true),
                visibility(true, true, true, true, true),
                visibility(true, true, true, true, true),
                visibility(true, true, true, true, true),
                visibility(false, false, false, true, true),
                visibility(false, false, false, true, true),
                visibility(false, false, false, true, true),
                visibility(false, true, true, true, true)
        );
    }

    private StudentProfileResponse.PrivacySettingsPayload normalizePrivacySettings(StudentProfileResponse.PrivacySettingsPayload privacy) {
        StudentProfileResponse.PrivacySettingsPayload defaults = defaultPrivacySettings();
        if (privacy == null) {
            return defaults;
        }

        return new StudentProfileResponse.PrivacySettingsPayload(
                normalizeVisibilityItem(privacy.realName(), defaults.realName()),
                normalizeVisibilityItem(privacy.jobStatus(), defaults.jobStatus()),
                normalizeVisibilityItem(privacy.eduInfo(), defaults.eduInfo()),
                normalizeVisibilityItem(privacy.targetPos(), defaults.targetPos()),
                normalizeVisibilityItem(privacy.academic(), defaults.academic()),
                normalizeVisibilityItem(privacy.skills(), defaults.skills()),
                normalizeVisibilityItem(privacy.intro(), defaults.intro()),
                normalizeVisibilityItem(privacy.social(), defaults.social()),
                normalizeVisibilityItem(privacy.email(), defaults.email()),
                normalizeVisibilityItem(privacy.phone(), defaults.phone()),
                normalizeVisibilityItem(privacy.wechat(), defaults.wechat()),
                normalizeVisibilityItem(privacy.portrait(), defaults.portrait())
        );
    }

    private StudentProfileResponse.VisibilityItem normalizeVisibilityItem(
            StudentProfileResponse.VisibilityItem item,
            StudentProfileResponse.VisibilityItem fallback
    ) {
        if (item == null) {
            return fallback;
        }
        return new StudentProfileResponse.VisibilityItem(
                item.guest(),
                item.student(),
                item.platformStudent(),
                item.mentor(),
                item.enterprise()
        );
    }

    private StudentProfileResponse.VisibilityItem normalizeVisibilityItem(
            JsonNode node,
            StudentProfileResponse.VisibilityItem fallback
    ) {
        boolean studentVisible = readBoolean(node, "student", fallback.student());
        return new StudentProfileResponse.VisibilityItem(
                readBoolean(node, "guest", fallback.guest()),
                studentVisible,
                readBoolean(node, "platformStudent", studentVisible),
                readBoolean(node, "mentor", fallback.mentor()),
                readBoolean(node, "enterprise", fallback.enterprise())
        );
    }

    private boolean readBoolean(JsonNode node, String fieldName, boolean fallback) {
        if (node == null || node.isMissingNode()) {
            return fallback;
        }
        JsonNode valueNode = node.get(fieldName);
        if (valueNode == null || valueNode.isNull()) {
            return fallback;
        }
        return valueNode.asBoolean();
    }

    private StudentProfileResponse.VisibilityItem visibility(
            boolean guest,
            boolean student,
            boolean platformStudent,
            boolean mentor,
            boolean enterprise
    ) {
        return new StudentProfileResponse.VisibilityItem(guest, student, platformStudent, mentor, enterprise);
    }

    private record ViewerAudience(
            UserRole role,
            boolean sameSchool,
            boolean guest
    ) {
    }
}
