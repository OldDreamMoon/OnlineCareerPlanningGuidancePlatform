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
        name = "recommendation_embedding_vectors",
        indexes = {
                @Index(name = "idx_recommendation_embedding_vectors_hash", columnList = "content_hash")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_recommendation_embedding_vectors_entity", columnNames = "entity_type, entity_id, model_code")
        }
)
public class RecommendationEmbeddingVectorEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "entity_type", nullable = false, length = 30)
    private String entityType;

    @Column(name = "entity_id", nullable = false)
    private Long entityId;

    @Column(name = "model_code", nullable = false, length = 60)
    private String modelCode;

    @Column(name = "vector_dim", nullable = false)
    private Integer vectorDim;

    @Column(name = "vector_json", nullable = false, columnDefinition = "TEXT")
    private String vectorJson;

    @JdbcTypeCode(Types.CHAR)
    @Column(name = "content_hash", nullable = false, length = 64)
    private String contentHash;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected RecommendationEmbeddingVectorEntity() {
    }

    public static RecommendationEmbeddingVectorEntity create(MentorRecommendationRepository.EmbeddingVectorCommand command) {
        RecommendationEmbeddingVectorEntity entity = new RecommendationEmbeddingVectorEntity();
        entity.entityType = command.entityType();
        entity.entityId = command.entityId();
        entity.modelCode = command.modelCode();
        entity.apply(command);
        return entity;
    }

    public void apply(MentorRecommendationRepository.EmbeddingVectorCommand command) {
        this.vectorDim = command.vectorDim();
        this.vectorJson = command.vectorJson();
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

    public String getEntityType() {
        return entityType;
    }

    public Long getEntityId() {
        return entityId;
    }

    public String getModelCode() {
        return modelCode;
    }

    public Integer getVectorDim() {
        return vectorDim;
    }

    public String getVectorJson() {
        return vectorJson;
    }

    public String getContentHash() {
        return contentHash;
    }
}
