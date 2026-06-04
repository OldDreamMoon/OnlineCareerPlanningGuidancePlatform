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

import java.time.Instant;

@Entity
@Table(
        name = "mentor_recommendation_runs",
        indexes = {
                @Index(name = "idx_mentor_recommendation_runs_student_time", columnList = "student_user_id, created_at"),
                @Index(name = "idx_mentor_recommendation_runs_created", columnList = "created_at, id")
        }
)
public class MentorRecommendationRunEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "student_user_id", nullable = false)
    private Long studentUserId;

    @Column(name = "scene", length = 60)
    private String scene;

    @Column(name = "keyword", length = 255)
    private String keyword;

    @Column(name = "expertise", length = 255)
    private String expertise;

    @Column(name = "filter_payload_json", nullable = false, columnDefinition = "TEXT")
    private String filterPayloadJson;

    @Column(name = "recall_model_code", nullable = false, length = 60)
    private String recallModelCode;

    @Column(name = "rerank_version", nullable = false, length = 60)
    private String rerankVersion;

    @Column(name = "weak_signal", nullable = false)
    private boolean weakSignal;

    @Column(name = "candidate_count", nullable = false)
    private Integer candidateCount;

    @Column(name = "recalled_count", nullable = false)
    private Integer recalledCount;

    @Column(name = "top_mentor_user_ids_json", nullable = false, columnDefinition = "TEXT")
    private String topMentorUserIdsJson;

    @Column(name = "basis_summary", columnDefinition = "TEXT")
    private String basisSummary;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected MentorRecommendationRunEntity() {
    }

    public static MentorRecommendationRunEntity create(MentorRecommendationRepository.RecommendationRunCommand command) {
        MentorRecommendationRunEntity entity = new MentorRecommendationRunEntity();
        entity.studentUserId = command.studentUserId();
        entity.scene = command.scene();
        entity.keyword = command.keyword();
        entity.expertise = command.expertise();
        entity.filterPayloadJson = command.filterPayloadJson();
        entity.recallModelCode = command.recallModelCode();
        entity.rerankVersion = command.rerankVersion();
        entity.weakSignal = command.weakSignal();
        entity.candidateCount = command.candidateCount();
        entity.recalledCount = command.recalledCount();
        entity.topMentorUserIdsJson = command.topMentorUserIdsJson();
        entity.basisSummary = command.basisSummary();
        return entity;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public Long getId() {
        return id;
    }
}
