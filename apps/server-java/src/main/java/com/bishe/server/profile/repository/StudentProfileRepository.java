package com.bishe.server.profile.repository;

import com.bishe.server.auth.model.UserRole;
import com.bishe.server.auth.repository.jpa.UserAccountJpaRepository;
import com.bishe.server.auth.repository.jpa.entity.UserAccountEntity;
import com.bishe.server.profile.repository.jpa.CommentJpaRepository;
import com.bishe.server.profile.repository.jpa.InterviewMessageRefJpaRepository;
import com.bishe.server.profile.repository.jpa.PostJpaRepository;
import com.bishe.server.profile.repository.jpa.PostLikeJpaRepository;
import com.bishe.server.profile.repository.jpa.StudentPortraitSnapshotJpaRepository;
import com.bishe.server.profile.repository.jpa.StudentProfileJpaRepository;
import com.bishe.server.profile.repository.jpa.StudentProfilePrivacySettingsJpaRepository;
import com.bishe.server.profile.repository.jpa.entity.StudentPortraitSnapshotEntity;
import com.bishe.server.profile.repository.jpa.entity.StudentProfileEntity;
import com.bishe.server.profile.repository.jpa.entity.StudentProfilePrivacySettingsEntity;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 学生画像仓储，负责 profile/snapshot 与社区统计的持久化访问。
 */
@Repository
public class StudentProfileRepository {

    private static final String PASS_STATUS = "PASS";

    private final UserAccountJpaRepository userAccountJpaRepository;
    private final StudentProfileJpaRepository studentProfileJpaRepository;
    private final StudentProfilePrivacySettingsJpaRepository studentProfilePrivacySettingsJpaRepository;
    private final StudentPortraitSnapshotJpaRepository studentPortraitSnapshotJpaRepository;
    private final PostJpaRepository postJpaRepository;
    private final CommentJpaRepository commentJpaRepository;
    private final PostLikeJpaRepository postLikeJpaRepository;
    private final InterviewMessageRefJpaRepository interviewMessageRefJpaRepository;

    public StudentProfileRepository(
            UserAccountJpaRepository userAccountJpaRepository,
            StudentProfileJpaRepository studentProfileJpaRepository,
            StudentProfilePrivacySettingsJpaRepository studentProfilePrivacySettingsJpaRepository,
            StudentPortraitSnapshotJpaRepository studentPortraitSnapshotJpaRepository,
            PostJpaRepository postJpaRepository,
            CommentJpaRepository commentJpaRepository,
            PostLikeJpaRepository postLikeJpaRepository,
            InterviewMessageRefJpaRepository interviewMessageRefJpaRepository
    ) {
        this.userAccountJpaRepository = userAccountJpaRepository;
        this.studentProfileJpaRepository = studentProfileJpaRepository;
        this.studentProfilePrivacySettingsJpaRepository = studentProfilePrivacySettingsJpaRepository;
        this.studentPortraitSnapshotJpaRepository = studentPortraitSnapshotJpaRepository;
        this.postJpaRepository = postJpaRepository;
        this.commentJpaRepository = commentJpaRepository;
        this.postLikeJpaRepository = postLikeJpaRepository;
        this.interviewMessageRefJpaRepository = interviewMessageRefJpaRepository;
    }

    public Optional<StudentProfileRow> findStudentProfileByUserId(long userId) {
        Optional<UserAccountEntity> user = userAccountJpaRepository.findByIdAndRoleAndDeletedFalse(userId, UserRole.STUDENT);
        if (user.isEmpty()) {
            return Optional.empty();
        }
        StudentProfileEntity profile = studentProfileJpaRepository.findByUserId(userId).orElse(null);
        return Optional.of(toProfileRow(user.get(), profile));
    }

    public List<Long> findAllStudentUserIds() {
        return userAccountJpaRepository.findIdsByRoleAndDeletedFalseOrderByIdAsc(UserRole.STUDENT);
    }

    public void saveOrUpdate(
            long userId,
            String jobStatus,
            String schoolName,
            String schoolNameKey,
            String major,
            String grade,
            String gpa,
            String targetPosition,
            String honors,
            String github,
            String portfolio,
            String socialLinksJson,
            String phone,
            String wechat,
            String skillTags,
            String selfIntro
    ) {
        StudentProfileEntity profile = studentProfileJpaRepository.findByUserId(userId)
                .orElseGet(() -> StudentProfileEntity.create(userId));
        profile.setJobStatus(jobStatus);
        profile.setSchoolName(schoolName);
        profile.setSchoolNameKey(schoolNameKey);
        profile.setMajor(major);
        profile.setGrade(grade);
        profile.setGpa(gpa);
        profile.setTargetPosition(targetPosition);
        profile.setHonors(honors);
        profile.setGithub(github);
        profile.setPortfolio(portfolio);
        profile.setSocialLinksJson(socialLinksJson);
        profile.setPhone(phone);
        profile.setWechat(wechat);
        profile.setSkillTags(skillTags);
        profile.setSelfIntro(selfIntro);
        studentProfileJpaRepository.save(profile);
    }

    public Optional<String> findPrivacySettingsJson(long userId) {
        return studentProfilePrivacySettingsJpaRepository.findByStudentUserId(userId)
                .map(StudentProfilePrivacySettingsEntity::getSettingsJson);
    }

    public void saveOrUpdatePrivacySettings(long userId, String settingsJson) {
        StudentProfilePrivacySettingsEntity settings = studentProfilePrivacySettingsJpaRepository.findByStudentUserId(userId)
                .orElseGet(() -> StudentProfilePrivacySettingsEntity.create(userId, settingsJson));
        settings.setSettingsJson(settingsJson);
        studentProfilePrivacySettingsJpaRepository.save(settings);
    }

    public Optional<PortraitSnapshotRow> findPortraitSnapshot(long userId) {
        return studentPortraitSnapshotJpaRepository.findByStudentUserId(userId)
                .map(snapshot -> new PortraitSnapshotRow(
                        snapshot.getPortraitTags(),
                        snapshot.getEvidence(),
                        snapshot.getUpdatedAt()
                ));
    }

    public Optional<AvatarAssetRow> findAvatarAssetByUserId(long userId) {
        return studentProfileJpaRepository.findByUserId(userId)
                .map(profile -> new AvatarAssetRow(
                        profile.getAvatarBucket(),
                        profile.getAvatarObjectKey(),
                        profile.getAvatarContentType()
                ));
    }

    public void saveOrUpdateAvatar(long userId, String bucket, String objectKey, String contentType, Instant updatedAt) {
        StudentProfileEntity profile = studentProfileJpaRepository.findByUserId(userId)
                .orElseGet(() -> StudentProfileEntity.create(userId));
        profile.setAvatarBucket(bucket);
        profile.setAvatarObjectKey(objectKey);
        profile.setAvatarContentType(contentType);
        profile.setAvatarUpdatedAt(updatedAt);
        studentProfileJpaRepository.save(profile);
    }

    public void savePortraitSnapshot(long userId, String portraitTagsJson, String evidenceJson) {
        StudentPortraitSnapshotEntity snapshot = studentPortraitSnapshotJpaRepository.findByStudentUserId(userId)
                .orElseGet(() -> StudentPortraitSnapshotEntity.create(userId, portraitTagsJson, evidenceJson));
        snapshot.setPortraitTags(portraitTagsJson);
        snapshot.setEvidence(evidenceJson);
        snapshot.touch();
        studentPortraitSnapshotJpaRepository.save(snapshot);
    }

    public void touchPortraitSnapshot(long userId) {
        StudentPortraitSnapshotEntity snapshot = studentPortraitSnapshotJpaRepository.findByStudentUserId(userId)
                .orElseGet(() -> StudentPortraitSnapshotEntity.create(userId, "[]", "{}"));
        snapshot.touch();
        studentPortraitSnapshotJpaRepository.save(snapshot);
    }

    public CommunityStatsRow countCommunityStats7d(long userId, Instant windowStart) {
        return new CommunityStatsRow(
                safeLongToInt(postJpaRepository.countByUserIdAndDeletedFalseAndModerationStatusAndCreatedAtGreaterThanEqual(
                        userId,
                        PASS_STATUS,
                        windowStart
                )),
                safeLongToInt(commentJpaRepository.countByUserIdAndDeletedFalseAndModerationStatusAndCreatedAtGreaterThanEqual(
                        userId,
                        PASS_STATUS,
                        windowStart
                )),
                safeLongToInt(postLikeJpaRepository.countLikesReceivedByPostOwnerSince(userId, PASS_STATUS, windowStart))
        );
    }

    public int countInterviewMessages7d(long userId, Instant windowStart) {
        return safeLongToInt(interviewMessageRefJpaRepository.countByStudentUserIdAndSenderRoleSince(userId, "USER", windowStart));
    }

    private StudentProfileRow toProfileRow(UserAccountEntity user, StudentProfileEntity profile) {
        return new StudentProfileRow(
                user.getId(),
                user.getDisplayName(),
                user.getEmail(),
                user.getTier(),
                user.getRealName(),
                profile == null ? null : profile.getJobStatus(),
                profile == null ? null : profile.getSchoolName(),
                profile == null ? null : profile.getSchoolNameKey(),
                profile == null ? null : profile.getMajor(),
                profile == null ? null : profile.getGrade(),
                profile == null ? null : profile.getGpa(),
                profile == null ? null : profile.getTargetPosition(),
                profile == null ? null : profile.getHonors(),
                profile == null ? null : profile.getGithub(),
                profile == null ? null : profile.getPortfolio(),
                profile == null ? null : profile.getSocialLinksJson(),
                profile == null ? null : profile.getPhone(),
                profile == null ? null : profile.getWechat(),
                profile == null ? null : profile.getSkillTags(),
                profile == null ? null : profile.getSelfIntro(),
                profile == null ? null : profile.getAvatarBucket(),
                profile == null ? null : profile.getAvatarObjectKey(),
                profile == null ? null : profile.getAvatarContentType(),
                profile == null ? null : profile.getAvatarUpdatedAt()
        );
    }

    private int safeLongToInt(long value) {
        return value > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) value;
    }

    /**
     * 学生资料视图。
     */
    public record StudentProfileRow(
            long userId,
            String displayName,
            String email,
            String tier,
            String realName,
            String jobStatus,
            String schoolName,
            String schoolNameKey,
            String major,
            String grade,
            String gpa,
            String targetPosition,
            String honors,
            String github,
            String portfolio,
            String socialLinksJson,
            String phone,
            String wechat,
            String skillTags,
            String selfIntro,
            String avatarBucket,
            String avatarObjectKey,
            String avatarContentType,
            Instant avatarUpdatedAt
    ) {
    }

    public record AvatarAssetRow(
            String bucket,
            String objectKey,
            String contentType
    ) {
    }

    /**
     * 动态画像快照视图。
     */
    public record PortraitSnapshotRow(
            String portraitTagsJson,
            String evidenceJson,
            Instant updatedAt
    ) {
    }

    /**
     * 社区 7 日统计视图。
     */
    public record CommunityStatsRow(
            int postCount,
            int commentCount,
            int likesReceivedCount
    ) {
    }
}
