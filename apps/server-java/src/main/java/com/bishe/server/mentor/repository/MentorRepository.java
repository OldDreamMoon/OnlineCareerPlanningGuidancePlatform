package com.bishe.server.mentor.repository;

import com.bishe.server.auth.model.UserAccountStatus;
import com.bishe.server.auth.model.UserRole;
import com.bishe.server.auth.repository.jpa.UserAccountJpaRepository;
import com.bishe.server.auth.repository.jpa.entity.UserAccountEntity;
import com.bishe.server.common.util.TextListCodec;
import com.bishe.server.consult.repository.jpa.ConsultOrderJpaRepository;
import com.bishe.server.consult.repository.jpa.entity.ConsultOrderEntity;
import com.bishe.server.mentor.repository.jpa.MentorProfileJpaRepository;
import com.bishe.server.mentor.repository.jpa.entity.MentorProfileEntity;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.math.RoundingMode;
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
 * 导师资料仓储，负责导师广场、详情与资料持久化。
 */
@Repository
public class MentorRepository {

    private static final String DEFAULT_EXPERTISE_TAGS = "职业规划,简历诊断";
    private static final String DEFAULT_SERVICE_SCENES = "简历诊断,项目表达,模拟面试复盘";
    private static final String DEFAULT_APPROVAL_STATUS = "PENDING";
    private static final int DEFAULT_PRICE_FEN = 5000;
    private static final BigDecimal ZERO_RATING = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

    private final UserAccountJpaRepository userAccountJpaRepository;
    private final MentorProfileJpaRepository mentorProfileJpaRepository;
    private final ConsultOrderJpaRepository consultOrderJpaRepository;

    public MentorRepository(
            UserAccountJpaRepository userAccountJpaRepository,
            MentorProfileJpaRepository mentorProfileJpaRepository,
            ConsultOrderJpaRepository consultOrderJpaRepository
    ) {
        this.userAccountJpaRepository = userAccountJpaRepository;
        this.mentorProfileJpaRepository = mentorProfileJpaRepository;
        this.consultOrderJpaRepository = consultOrderJpaRepository;
    }

    public void createDefaultProfileIfAbsent(long userId, String displayName, String companyName, String jobTitle) {
        createDefaultProfileIfAbsent(userId, displayName, companyName, jobTitle, DEFAULT_APPROVAL_STATUS);
    }

    public void createDefaultProfileIfAbsent(long userId, String displayName, String companyName, String jobTitle, String approvalStatus) {
        MentorProfileEntity existingProfile = mentorProfileJpaRepository.findByUserId(userId).orElse(null);
        if (existingProfile != null) {
            existingProfile.touch();
            mentorProfileJpaRepository.save(existingProfile);
            return;
        }

        MentorProfileEntity profile = MentorProfileEntity.create(userId);
        profile.setCompanyName(TextListCodec.normalizeText(companyName));
        profile.setJobTitle(TextListCodec.normalizeText(jobTitle));
        profile.setShowRealName(false);
        profile.setExpertiseTags(DEFAULT_EXPERTISE_TAGS);
        profile.setServiceScenes(DEFAULT_SERVICE_SCENES);
        profile.setBio(buildDefaultBio(displayName));
        profile.setPriceFen(DEFAULT_PRICE_FEN);
        profile.setAvailable(true);
        profile.setApprovalStatus(approvalStatus);
        profile.setTotalOrders(0);
        profile.setAvgRating(ZERO_RATING);
        mentorProfileJpaRepository.save(profile);
    }

    public List<MentorListRow> findMentors(
            String keyword,
            String expertise,
            String scene,
            Integer minPrice,
            Integer maxPrice,
            Boolean available,
            List<Long> mentorUserIds,
            int page,
            int size
    ) {
        List<MentorListRow> allRows = findPublicMentorRows(keyword, expertise, scene, minPrice, maxPrice, available, mentorUserIds);
        int safePage = Math.max(page, 1);
        int safeSize = Math.max(size, 1);
        int fromIndex = Math.min((safePage - 1) * safeSize, allRows.size());
        int toIndex = Math.min(fromIndex + safeSize, allRows.size());
        return List.copyOf(allRows.subList(fromIndex, toIndex));
    }

    public List<MentorListRow> findMentorCandidates(
            String keyword,
            String expertise,
            String scene,
            Integer minPrice,
            Integer maxPrice,
            Boolean available,
            List<Long> mentorUserIds,
            int limit
    ) {
        int safeLimit = Math.max(1, Math.min(limit, 200));
        List<MentorListRow> allRows = findPublicMentorRows(keyword, expertise, scene, minPrice, maxPrice, available, mentorUserIds);
        return List.copyOf(allRows.subList(0, Math.min(safeLimit, allRows.size())));
    }

    public long countMentors(
            String keyword,
            String expertise,
            String scene,
            Integer minPrice,
            Integer maxPrice,
            Boolean available,
            List<Long> mentorUserIds
    ) {
        return findPublicMentorRows(keyword, expertise, scene, minPrice, maxPrice, available, mentorUserIds).size();
    }

    public Optional<MentorDetailRow> findMentorDetail(long mentorUserId) {
        Optional<UserAccountEntity> user = findActiveMentorUser(mentorUserId);
        if (user.isEmpty() || isSystemAccount(user.get())) {
            return Optional.empty();
        }
        return mentorProfileJpaRepository.findByUserId(mentorUserId)
                .filter(profile -> "APPROVED".equals(profile.getApprovalStatus()))
                .map(profile -> toMentorDetailRow(user.get(), profile));
    }

    public List<MentorRecentReviewRow> findRecentReviews(long mentorUserId, int limit) {
        int safeLimit = Math.max(0, Math.min(limit, 10));
        if (safeLimit == 0) {
            return List.of();
        }
        List<ConsultOrderEntity> reviews = consultOrderJpaRepository.findByMentorUserIdAndReviewRatingIsNotNullOrderByReviewCreatedAtDescIdDesc(
                mentorUserId,
                PageRequest.of(0, safeLimit)
        );
        if (reviews.isEmpty()) {
            return List.of();
        }
        Map<Long, UserAccountEntity> studentUserMap = userAccountJpaRepository.findAllById(
                        reviews.stream()
                                .map(ConsultOrderEntity::getStudentUserId)
                                .filter(Objects::nonNull)
                                .distinct()
                                .toList()
                )
                .stream()
                .filter(entity -> entity.getId() != null)
                .collect(Collectors.toMap(
                        UserAccountEntity::getId,
                        entity -> entity,
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
        return reviews.stream()
                .map(review -> new MentorRecentReviewRow(
                        review.getOrderNo(),
                        studentUserMap.containsKey(review.getStudentUserId())
                                ? studentUserMap.get(review.getStudentUserId()).getDisplayName()
                                : null,
                        review.getReviewRating() == null ? 0 : review.getReviewRating(),
                        TextListCodec.normalizeText(review.getReviewComment()),
                        review.getReviewCreatedAt()
                ))
                .toList();
    }

    public Optional<MentorOwnProfileRow> findOwnProfile(long mentorUserId) {
        Optional<UserAccountEntity> user = findActiveMentorUser(mentorUserId);
        if (user.isEmpty()) {
            return Optional.empty();
        }
        return mentorProfileJpaRepository.findByUserId(mentorUserId)
                .map(profile -> toOwnProfileRow(user.get(), profile));
    }

    public void updateOwnProfile(
            long mentorUserId,
            String companyName,
            String jobTitle,
            boolean showRealName,
            String avatarUrl,
            String expertiseTags,
            String serviceScenes,
            String bio,
            String suitableFor,
            String notSuitableFor,
            String prepMaterials,
            String replyRhythm,
            int priceFen,
            boolean available
    ) {
        mentorProfileJpaRepository.findByUserId(mentorUserId).ifPresent(profile -> {
            profile.setCompanyName(companyName);
            profile.setJobTitle(jobTitle);
            profile.setShowRealName(showRealName);
            profile.setAvatarUrl(avatarUrl);
            profile.setExpertiseTags(expertiseTags);
            profile.setServiceScenes(serviceScenes);
            profile.setBio(bio);
            profile.setSuitableFor(suitableFor);
            profile.setNotSuitableFor(notSuitableFor);
            profile.setPrepMaterials(prepMaterials);
            profile.setReplyRhythm(replyRhythm);
            profile.setPriceFen(priceFen);
            profile.setAvailable(available);
            mentorProfileJpaRepository.save(profile);
        });
    }

    public Optional<AvatarAssetRow> findAvatarAssetByUserId(long mentorUserId) {
        return mentorProfileJpaRepository.findByUserId(mentorUserId)
                .map(profile -> new AvatarAssetRow(
                        profile.getAvatarBucket(),
                        profile.getAvatarObjectKey(),
                        profile.getAvatarContentType()
                ));
    }

    public Optional<PublicAvatarSourceRow> findPublicAvatarSourceByUserId(long mentorUserId) {
        return mentorProfileJpaRepository.findByUserId(mentorUserId)
                .map(profile -> new PublicAvatarSourceRow(
                        TextListCodec.normalizeText(profile.getAvatarUrl()),
                        profile.getAvatarBucket(),
                        profile.getAvatarObjectKey(),
                        profile.getAvatarContentType()
                ));
    }

    public void saveOrUpdateAvatar(long mentorUserId, String bucket, String objectKey, String contentType, Instant updatedAt) {
        MentorProfileEntity profile = mentorProfileJpaRepository.findByUserId(mentorUserId)
                .orElseGet(() -> createAvatarProfile(mentorUserId));
        profile.setAvatarBucket(bucket);
        profile.setAvatarObjectKey(objectKey);
        profile.setAvatarContentType(contentType);
        profile.setAvatarUpdatedAt(updatedAt);
        mentorProfileJpaRepository.save(profile);
    }

    public void updateCertificationIdentity(long mentorUserId, String companyName, String jobTitle) {
        mentorProfileJpaRepository.findByUserId(mentorUserId).ifPresent(profile -> {
            profile.setCompanyName(companyName);
            profile.setJobTitle(jobTitle);
            mentorProfileJpaRepository.save(profile);
        });
    }

    public void updateApprovalStatus(long mentorUserId, String approvalStatus) {
        mentorProfileJpaRepository.findByUserId(mentorUserId).ifPresent(profile -> {
            profile.setApprovalStatus(approvalStatus);
            mentorProfileJpaRepository.save(profile);
        });
    }

    public void incrementTotalOrders(long mentorUserId) {
        mentorProfileJpaRepository.findByUserId(mentorUserId).ifPresent(profile -> {
            int currentTotalOrders = profile.getTotalOrders() == null ? 0 : profile.getTotalOrders();
            profile.setTotalOrders(currentTotalOrders + 1);
            mentorProfileJpaRepository.save(profile);
        });
    }

    public void decrementTotalOrders(long mentorUserId) {
        mentorProfileJpaRepository.findByUserId(mentorUserId).ifPresent(profile -> {
            int currentTotalOrders = profile.getTotalOrders() == null ? 0 : profile.getTotalOrders();
            profile.setTotalOrders(Math.max(currentTotalOrders - 1, 0));
            mentorProfileJpaRepository.save(profile);
        });
    }

    public void refreshAverageRating(long mentorUserId) {
        mentorProfileJpaRepository.findByUserId(mentorUserId).ifPresent(profile -> {
            Double averageRatingValue = consultOrderJpaRepository.findAverageReviewRatingValueByMentorUserId(mentorUserId);
            BigDecimal normalizedAverage = averageRatingValue == null
                    ? ZERO_RATING
                    : BigDecimal.valueOf(averageRatingValue).setScale(2, RoundingMode.HALF_UP);
            profile.setAvgRating(normalizedAverage);
            mentorProfileJpaRepository.save(profile);
        });
    }

    private List<MentorListRow> findPublicMentorRows(
            String keyword,
            String expertise,
            String scene,
            Integer minPrice,
            Integer maxPrice,
            Boolean available,
            List<Long> mentorUserIds
    ) {
        Map<Long, UserAccountEntity> mentorUserMap = loadMentorUserMap(mentorUserIds);
        if (mentorUserMap.isEmpty()) {
            return List.of();
        }
        Map<Long, MentorProfileEntity> mentorProfileMap = loadMentorProfileMap(mentorUserMap.keySet());
        String normalizedKeyword = normalizeText(keyword);
        String normalizedExpertise = normalizeText(expertise);
        String normalizedScene = normalizeScene(scene);
        return mentorUserMap.values().stream()
                .map(user -> toPublicMentorListRow(user, mentorProfileMap.get(user.getId())))
                .filter(Objects::nonNull)
                .filter(row -> matchesMentorFilters(row, normalizedKeyword, normalizedExpertise, normalizedScene, minPrice, maxPrice, available))
                .sorted(Comparator
                        .comparing(MentorListRow::avgRating, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(MentorListRow::totalOrders, Comparator.reverseOrder())
                        .thenComparing(MentorListRow::userId, Comparator.reverseOrder()))
                .toList();
    }

    private MentorListRow toPublicMentorListRow(UserAccountEntity user, MentorProfileEntity profile) {
        if (!isPublicMentor(user, profile)) {
            return null;
        }
        return toMentorListRow(user, profile);
    }

    private boolean isPublicMentor(UserAccountEntity user, MentorProfileEntity profile) {
        return user != null
                && profile != null
                && user.getId() != null
                && user.getRole() == UserRole.MENTOR
                && user.getStatus() == UserAccountStatus.ACTIVE
                && !user.isDeleted()
                && !isSystemAccount(user)
                && "APPROVED".equals(profile.getApprovalStatus());
    }

    private boolean matchesMentorFilters(
            MentorListRow row,
            String normalizedKeyword,
            String normalizedExpertise,
            String normalizedScene,
            Integer minPrice,
            Integer maxPrice,
            Boolean available
    ) {
        if (normalizedKeyword != null
                && !containsIgnoreCase(row.displayName(), normalizedKeyword)
                && !containsIgnoreCase(row.companyName(), normalizedKeyword)
                && !containsIgnoreCase(row.jobTitle(), normalizedKeyword)
                && !containsIgnoreCase(row.bio(), normalizedKeyword)
                && !containsAnyIgnoreCase(row.expertiseTags(), normalizedKeyword)
                && !containsAnyIgnoreCase(row.serviceScenes(), normalizedKeyword)) {
            return false;
        }
        if (normalizedExpertise != null && !containsAnyIgnoreCase(row.expertiseTags(), normalizedExpertise)) {
            return false;
        }
        if (normalizedScene != null && !containsAnyIgnoreCase(row.serviceScenes(), normalizedScene)) {
            return false;
        }
        if (minPrice != null && row.priceFen() < minPrice) {
            return false;
        }
        if (maxPrice != null && row.priceFen() > maxPrice) {
            return false;
        }
        return available == null || row.available() == available;
    }

    private Optional<UserAccountEntity> findActiveMentorUser(long mentorUserId) {
        return userAccountJpaRepository.findByIdAndRoleAndDeletedFalse(mentorUserId, UserRole.MENTOR)
                .filter(user -> user.getStatus() == UserAccountStatus.ACTIVE);
    }

    private Optional<UserAccountEntity> findMentorUser(long mentorUserId) {
        return userAccountJpaRepository.findByIdAndRoleAndDeletedFalse(mentorUserId, UserRole.MENTOR);
    }

    private Map<Long, UserAccountEntity> loadMentorUserMap(List<Long> mentorUserIds) {
        List<Long> scopedMentorUserIds = mentorUserIds == null
                ? userAccountJpaRepository.findIdsByRoleAndDeletedFalseOrderByIdAsc(UserRole.MENTOR)
                : mentorUserIds.stream()
                        .filter(Objects::nonNull)
                        .distinct()
                        .toList();
        if (scopedMentorUserIds.isEmpty()) {
            return Map.of();
        }
        return userAccountJpaRepository.findAllById(scopedMentorUserIds).stream()
                .filter(entity -> entity.getId() != null)
                .filter(entity -> entity.getRole() == UserRole.MENTOR)
                .filter(entity -> !entity.isDeleted())
                .filter(entity -> entity.getStatus() == UserAccountStatus.ACTIVE)
                .sorted(Comparator.comparing(UserAccountEntity::getId))
                .collect(Collectors.toMap(
                        UserAccountEntity::getId,
                        entity -> entity,
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
    }

    private Map<Long, MentorProfileEntity> loadMentorProfileMap(Collection<Long> mentorUserIds) {
        if (mentorUserIds == null || mentorUserIds.isEmpty()) {
            return Map.of();
        }
        return mentorProfileJpaRepository.findAllByUserIdIn(mentorUserIds).stream()
                .filter(entity -> entity.getUserId() != null)
                .collect(Collectors.toMap(
                        MentorProfileEntity::getUserId,
                        entity -> entity,
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
    }

    private MentorProfileEntity createAvatarProfile(long mentorUserId) {
        MentorProfileEntity profile = MentorProfileEntity.create(mentorUserId);
        String displayName = findMentorUser(mentorUserId)
                .map(UserAccountEntity::getDisplayName)
                .orElse("导师");
        profile.setExpertiseTags(DEFAULT_EXPERTISE_TAGS);
        profile.setServiceScenes(DEFAULT_SERVICE_SCENES);
        profile.setBio(buildDefaultBio(displayName));
        profile.setPriceFen(DEFAULT_PRICE_FEN);
        profile.setAvailable(true);
        profile.setApprovalStatus(DEFAULT_APPROVAL_STATUS);
        profile.setTotalOrders(0);
        profile.setAvgRating(ZERO_RATING);
        return profile;
    }

    private MentorListRow toMentorListRow(UserAccountEntity user, MentorProfileEntity profile) {
        return new MentorListRow(
                user.getId(),
                user.getDisplayName(),
                profile.isShowRealName() ? TextListCodec.normalizeText(user.getRealName()) : null,
                profile.isShowRealName(),
                TextListCodec.normalizeText(profile.getCompanyName()),
                TextListCodec.normalizeText(profile.getJobTitle()),
                TextListCodec.normalizeText(profile.getAvatarUrl()),
                TextListCodec.normalizeText(profile.getAvatarObjectKey()),
                profile.getAvatarUpdatedAt(),
                TextListCodec.split(profile.getExpertiseTags()),
                TextListCodec.split(profile.getServiceScenes()),
                TextListCodec.normalizeText(profile.getBio()),
                profile.getPriceFen() == null ? DEFAULT_PRICE_FEN : profile.getPriceFen(),
                profile.getAvgRating() == null ? ZERO_RATING : profile.getAvgRating(),
                profile.getTotalOrders() == null ? 0 : profile.getTotalOrders(),
                profile.isAvailable(),
                false
        );
    }

    private MentorDetailRow toMentorDetailRow(UserAccountEntity user, MentorProfileEntity profile) {
        return new MentorDetailRow(
                user.getId(),
                user.getDisplayName(),
                profile.isShowRealName() ? TextListCodec.normalizeText(user.getRealName()) : null,
                profile.isShowRealName(),
                TextListCodec.normalizeText(profile.getCompanyName()),
                TextListCodec.normalizeText(profile.getJobTitle()),
                TextListCodec.normalizeText(profile.getAvatarUrl()),
                TextListCodec.normalizeText(profile.getAvatarObjectKey()),
                profile.getAvatarUpdatedAt(),
                TextListCodec.split(profile.getExpertiseTags()),
                TextListCodec.split(profile.getServiceScenes()),
                TextListCodec.normalizeText(profile.getBio()),
                TextListCodec.normalizeText(profile.getSuitableFor()),
                TextListCodec.normalizeText(profile.getNotSuitableFor()),
                TextListCodec.normalizeText(profile.getPrepMaterials()),
                TextListCodec.normalizeText(profile.getReplyRhythm()),
                profile.getPriceFen() == null ? DEFAULT_PRICE_FEN : profile.getPriceFen(),
                profile.getAvgRating() == null ? ZERO_RATING : profile.getAvgRating(),
                profile.getTotalOrders() == null ? 0 : profile.getTotalOrders(),
                profile.isAvailable(),
                false
        );
    }

    private MentorOwnProfileRow toOwnProfileRow(UserAccountEntity user, MentorProfileEntity profile) {
        return new MentorOwnProfileRow(
                user.getId(),
                user.getDisplayName(),
                user.getRealName(),
                profile.isShowRealName(),
                TextListCodec.normalizeText(profile.getCompanyName()),
                TextListCodec.normalizeText(profile.getJobTitle()),
                TextListCodec.normalizeText(profile.getAvatarUrl()),
                TextListCodec.normalizeText(profile.getAvatarObjectKey()),
                TextListCodec.normalizeText(profile.getAvatarContentType()),
                profile.getAvatarUpdatedAt(),
                TextListCodec.split(profile.getExpertiseTags()),
                TextListCodec.split(profile.getServiceScenes()),
                TextListCodec.normalizeText(profile.getBio()),
                TextListCodec.normalizeText(profile.getSuitableFor()),
                TextListCodec.normalizeText(profile.getNotSuitableFor()),
                TextListCodec.normalizeText(profile.getPrepMaterials()),
                TextListCodec.normalizeText(profile.getReplyRhythm()),
                profile.getPriceFen() == null ? DEFAULT_PRICE_FEN : profile.getPriceFen(),
                profile.getAvgRating() == null ? ZERO_RATING : profile.getAvgRating(),
                profile.getTotalOrders() == null ? 0 : profile.getTotalOrders(),
                profile.isAvailable(),
                profile.getApprovalStatus()
        );
    }

    private boolean containsAnyIgnoreCase(List<String> values, String keyword) {
        return values != null && values.stream().anyMatch(value -> containsIgnoreCase(value, keyword));
    }

    private boolean containsIgnoreCase(String source, String keyword) {
        String normalizedSource = normalizeText(source);
        if (normalizedSource == null || keyword == null) {
            return false;
        }
        return normalizedSource.toLowerCase(Locale.ROOT).contains(keyword);
    }

    private String normalizeScene(String scene) {
        String normalizedScene = normalizeText(scene);
        if ("不限".equals(normalizedScene)) {
            return null;
        }
        return normalizedScene;
    }

    private String normalizeText(String value) {
        String normalizedValue = TextListCodec.normalizeText(value);
        return normalizedValue == null ? null : normalizedValue.toLowerCase(Locale.ROOT);
    }

    private String buildDefaultBio(String displayName) {
        String normalizedDisplayName = TextListCodec.normalizeText(displayName);
        return "导师 " + (normalizedDisplayName == null ? "待补充" : normalizedDisplayName) + " 还没有补充完整简介，建议尽快完善个人介绍与服务说明。";
    }

    private boolean isSystemAccount(UserAccountEntity user) {
        String email = user.getEmail();
        return email != null && email.toLowerCase(Locale.ROOT).endsWith("@system.local");
    }

    /**
     * 导师列表行。
     */
    public record MentorListRow(
            long userId,
            String displayName,
            String realName,
            boolean showRealName,
            String companyName,
            String jobTitle,
            String avatarUrl,
            String avatarObjectKey,
            Instant avatarUpdatedAt,
            List<String> expertiseTags,
            List<String> serviceScenes,
            String bio,
            int priceFen,
            BigDecimal avgRating,
            int totalOrders,
            boolean available,
            boolean favorited
    ) {
    }

    /**
     * 导师详情行。
     */
    public record MentorDetailRow(
            long userId,
            String displayName,
            String realName,
            boolean showRealName,
            String companyName,
            String jobTitle,
            String avatarUrl,
            String avatarObjectKey,
            Instant avatarUpdatedAt,
            List<String> expertiseTags,
            List<String> serviceScenes,
            String bio,
            String suitableFor,
            String notSuitableFor,
            String prepMaterials,
            String replyRhythm,
            int priceFen,
            BigDecimal avgRating,
            int totalOrders,
            boolean available,
            boolean favorited
    ) {
    }

    /**
     * 导师本人资料行。
     */
    public record MentorOwnProfileRow(
            long userId,
            String displayName,
            String realName,
            boolean showRealName,
            String companyName,
            String jobTitle,
            String avatarUrl,
            String avatarObjectKey,
            String avatarContentType,
            Instant avatarUpdatedAt,
            List<String> expertiseTags,
            List<String> serviceScenes,
            String bio,
            String suitableFor,
            String notSuitableFor,
            String prepMaterials,
            String replyRhythm,
            int priceFen,
            BigDecimal avgRating,
            int totalOrders,
            boolean available,
            String approvalStatus
    ) {
    }

    /**
     * 导师头像资产行。
     */
    public record AvatarAssetRow(
            String bucket,
            String objectKey,
            String contentType
    ) {
    }

    /**
     * 导师公开头像来源行。
     */
    public record PublicAvatarSourceRow(
            String avatarUrl,
            String bucket,
            String objectKey,
            String contentType
    ) {
    }

    /**
     * 导师近期评价行。
     */
    public record MentorRecentReviewRow(
            String orderNo,
            String studentDisplayName,
            int rating,
            String comment,
            Instant createdAt
    ) {
    }
}
