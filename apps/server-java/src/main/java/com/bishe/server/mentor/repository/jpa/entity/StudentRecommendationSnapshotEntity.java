package com.bishe.server.mentor.repository.jpa.entity;

import com.bishe.server.mentor.repository.MentorRecommendationRepository;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.JdbcTypeCode;

import java.sql.Types;
import java.time.Instant;

@Entity
@Table(
        name = "student_recommendation_snapshots",
        indexes = {
                @Index(name = "idx_student_recommendation_snapshots_hash", columnList = "content_hash")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_student_recommendation_snapshots_student", columnNames = "student_user_id")
        }
)
public class StudentRecommendationSnapshotEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "student_user_id", nullable = false)
    private Long studentUserId;

    @Column(name = "content_text", nullable = false, columnDefinition = "TEXT")
    private String contentText;

    @Column(name = "target_position", length = 100)
    private String targetPosition;

    @Column(name = "skill_tags_json", columnDefinition = "TEXT")
    private String skillTagsJson;

    @Column(name = "portrait_tags_json", columnDefinition = "TEXT")
    private String portraitTagsJson;

    @Column(name = "latest_resume_record_id")
    private Long latestResumeRecordId;

    @Column(name = "latest_resume_target_role", length = 100)
    private String latestResumeTargetRole;

    @Column(name = "latest_resume_summary", columnDefinition = "TEXT")
    private String latestResumeSummary;

    @Column(name = "latest_resume_suggestions_json", columnDefinition = "TEXT")
    private String latestResumeSuggestionsJson;

    @Column(name = "latest_interview_session_id", length = 64)
    private String latestInterviewSessionId;

    @Column(name = "latest_interview_target_role", length = 100)
    private String latestInterviewTargetRole;

    @Column(name = "latest_interview_weaknesses_json", columnDefinition = "TEXT")
    private String latestInterviewWeaknessesJson;

    @Column(name = "latest_interview_suggestions_json", columnDefinition = "TEXT")
    private String latestInterviewSuggestionsJson;

    @Column(name = "signal_flags_json", columnDefinition = "TEXT")
    private String signalFlagsJson;

    @JdbcTypeCode(Types.CHAR)
    @Column(name = "content_hash", nullable = false, length = 64)
    private String contentHash;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected StudentRecommendationSnapshotEntity() {
    }

    public static StudentRecommendationSnapshotEntity create(MentorRecommendationRepository.StudentRecommendationSnapshotCommand command) {
        StudentRecommendationSnapshotEntity entity = new StudentRecommendationSnapshotEntity();
        entity.studentUserId = command.studentUserId();
        entity.apply(command);
        return entity;
    }

    public void apply(MentorRecommendationRepository.StudentRecommendationSnapshotCommand command) {
        this.contentText = command.contentText();
        this.targetPosition = command.targetPosition();
        this.skillTagsJson = command.skillTagsJson();
        this.portraitTagsJson = command.portraitTagsJson();
        this.latestResumeRecordId = command.latestResumeRecordId();
        this.latestResumeTargetRole = command.latestResumeTargetRole();
        this.latestResumeSummary = command.latestResumeSummary();
        this.latestResumeSuggestionsJson = command.latestResumeSuggestionsJson();
        this.latestInterviewSessionId = command.latestInterviewSessionId();
        this.latestInterviewTargetRole = command.latestInterviewTargetRole();
        this.latestInterviewWeaknessesJson = command.latestInterviewWeaknessesJson();
        this.latestInterviewSuggestionsJson = command.latestInterviewSuggestionsJson();
        this.signalFlagsJson = command.signalFlagsJson();
        this.contentHash = command.contentHash();
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
