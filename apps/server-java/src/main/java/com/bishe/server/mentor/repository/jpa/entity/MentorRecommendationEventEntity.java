package com.bishe.server.mentor.repository.jpa.entity;

import com.bishe.server.mentor.repository.MentorRecommendationRepository;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

@Entity
@Table(
        name = "mentor_recommendation_events",
        indexes = {
                @Index(name = "idx_mentor_recommendation_events_run", columnList = "run_id, created_at"),
                @Index(name = "idx_mentor_recommendation_events_mentor", columnList = "mentor_user_id, created_at")
        }
)
public class MentorRecommendationEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "run_id", nullable = false)
    private Long runId;

    @Column(name = "mentor_user_id", nullable = false)
    private Long mentorUserId;

    @Column(name = "event_type", nullable = false, length = 30)
    private String eventType;

    @Column(name = "stage_rank")
    private Integer stageRank;

    @Column(name = "score", precision = 10, scale = 6)
    private BigDecimal score;

    @Column(name = "detail_json", columnDefinition = "TEXT")
    private String detailJson;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected MentorRecommendationEventEntity() {
    }

    public static MentorRecommendationEventEntity create(MentorRecommendationRepository.RecommendationEventCommand command) {
        MentorRecommendationEventEntity entity = new MentorRecommendationEventEntity();
        entity.runId = command.runId();
        entity.mentorUserId = command.mentorUserId();
        entity.eventType = command.eventType();
        entity.stageRank = command.stageRank();
        entity.score = command.score() == null ? null : BigDecimal.valueOf(command.score()).setScale(6, RoundingMode.HALF_UP);
        entity.detailJson = command.detailJson();
        return entity;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
