package com.bishe.server.profile.repository.jpa.entity;

import com.bishe.server.auth.repository.jpa.entity.UserAccountEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * 社区帖子实体。
 */
@Entity
@Table(name = "posts")
public class PostEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, insertable = false, updatable = false)
    private UserAccountEntity author;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "tags", length = 255)
    private String tags;

    @Column(name = "scenario_code", nullable = false, length = 60)
    private String scenarioCode;

    @Column(name = "resolved_status", nullable = false, length = 20)
    private String resolvedStatus;

    @Column(name = "moderation_status", nullable = false, length = 20)
    private String moderationStatus;

    @Column(name = "risk_level", nullable = false, length = 20)
    private String riskLevel;

    @Column(name = "last_moderation_event_id")
    private Long lastModerationEventId;

    @Column(name = "is_deleted", nullable = false)
    private boolean deleted;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected PostEntity() {
    }

    public static PostEntity create(
            long userId,
            String title,
            String scenarioCode,
            String content,
            String tags,
            String resolvedStatus,
            String moderationStatus,
            String riskLevel,
            long lastModerationEventId
    ) {
        PostEntity entity = new PostEntity();
        entity.userId = userId;
        entity.title = title;
        entity.scenarioCode = scenarioCode;
        entity.content = content;
        entity.tags = tags;
        entity.resolvedStatus = resolvedStatus;
        entity.moderationStatus = moderationStatus;
        entity.riskLevel = riskLevel;
        entity.lastModerationEventId = lastModerationEventId > 0 ? lastModerationEventId : null;
        entity.deleted = false;
        return entity;
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

    public void updateResolvedStatus(String resolvedStatus) {
        this.resolvedStatus = resolvedStatus;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public UserAccountEntity getAuthor() {
        return author;
    }

    public String getTitle() {
        return title;
    }

    public String getContent() {
        return content;
    }

    public String getTags() {
        return tags;
    }

    public String getScenarioCode() {
        return scenarioCode;
    }

    public String getResolvedStatus() {
        return resolvedStatus;
    }

    public String getModerationStatus() {
        return moderationStatus;
    }

    public String getRiskLevel() {
        return riskLevel;
    }

    public Long getLastModerationEventId() {
        return lastModerationEventId;
    }

    public boolean isDeleted() {
        return deleted;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
