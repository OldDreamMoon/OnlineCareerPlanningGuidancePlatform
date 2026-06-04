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
 * 社区评论实体。
 */
@Entity
@Table(name = "comments")
public class CommentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "post_id", nullable = false)
    private Long postId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, insertable = false, updatable = false)
    private UserAccountEntity author;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "is_ai", nullable = false)
    private boolean ai;

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

    protected CommentEntity() {
    }

    public static CommentEntity create(
            long postId,
            long userId,
            String content,
            boolean ai,
            String moderationStatus,
            String riskLevel,
            long lastModerationEventId
    ) {
        CommentEntity entity = new CommentEntity();
        entity.postId = postId;
        entity.userId = userId;
        entity.content = content;
        entity.ai = ai;
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

    public Long getId() {
        return id;
    }

    public Long getPostId() {
        return postId;
    }

    public Long getUserId() {
        return userId;
    }

    public UserAccountEntity getAuthor() {
        return author;
    }

    public String getContent() {
        return content;
    }

    public boolean isAi() {
        return ai;
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
