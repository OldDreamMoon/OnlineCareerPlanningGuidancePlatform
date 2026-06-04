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
        name = "mentor_recommendation_snapshots",
        indexes = {
                @Index(name = "idx_mentor_recommendation_snapshots_hash", columnList = "content_hash")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_mentor_recommendation_snapshots_mentor", columnNames = "mentor_user_id")
        }
)
public class MentorRecommendationSnapshotEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "mentor_user_id", nullable = false)
    private Long mentorUserId;

    @Column(name = "content_text", nullable = false, columnDefinition = "TEXT")
    private String contentText;

    @Column(name = "expertise_tags_json", columnDefinition = "TEXT")
    private String expertiseTagsJson;

    @Column(name = "service_scenes_json", columnDefinition = "TEXT")
    private String serviceScenesJson;

    @Column(name = "quality_score", nullable = false)
    private Integer qualityScore;

    @Column(name = "price_fen", nullable = false)
    private Integer priceFen;

    @Column(name = "is_available", nullable = false)
    private boolean available;

    @JdbcTypeCode(Types.CHAR)
    @Column(name = "content_hash", nullable = false, length = 64)
    private String contentHash;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected MentorRecommendationSnapshotEntity() {
    }

    public static MentorRecommendationSnapshotEntity create(MentorRecommendationRepository.MentorRecommendationSnapshotCommand command) {
        MentorRecommendationSnapshotEntity entity = new MentorRecommendationSnapshotEntity();
        entity.mentorUserId = command.mentorUserId();
        entity.apply(command);
        return entity;
    }

    public void apply(MentorRecommendationRepository.MentorRecommendationSnapshotCommand command) {
        this.contentText = command.contentText();
        this.expertiseTagsJson = command.expertiseTagsJson();
        this.serviceScenesJson = command.serviceScenesJson();
        this.qualityScore = command.qualityScore();
        this.priceFen = command.priceFen();
        this.available = command.available();
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
