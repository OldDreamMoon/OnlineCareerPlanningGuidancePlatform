package com.bishe.server.ai.interview.jpa.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * 文本面试消息实体。
 */
@Entity
@Table(
        name = "interview_messages",
        indexes = {
                @Index(name = "idx_interview_messages_session_time", columnList = "session_pk, created_at")
        }
)
public class InterviewMessageEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "session_pk", nullable = false)
    private Long sessionPk;

    @Column(name = "sender_role", nullable = false, length = 20)
    private String senderRole;

    @Column(name = "message_text", nullable = false, columnDefinition = "TEXT")
    private String messageText;

    @Column(name = "coach_feedback", columnDefinition = "TEXT")
    private String coachFeedback;

    @Column(name = "score_hint")
    private Integer scoreHint;

    @Column(name = "audio_object_key", length = 255)
    private String audioObjectKey;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected InterviewMessageEntity() {
    }

    public static InterviewMessageEntity create(
            long sessionPk,
            String senderRole,
            String messageText,
            String coachFeedback,
            Integer scoreHint,
            String audioObjectKey
    ) {
        InterviewMessageEntity entity = new InterviewMessageEntity();
        entity.sessionPk = sessionPk;
        entity.senderRole = senderRole;
        entity.messageText = messageText;
        entity.coachFeedback = coachFeedback;
        entity.scoreHint = scoreHint;
        entity.audioObjectKey = audioObjectKey;
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

    public Long getSessionPk() {
        return sessionPk;
    }

    public String getSenderRole() {
        return senderRole;
    }

    public String getMessageText() {
        return messageText;
    }

    public String getCoachFeedback() {
        return coachFeedback;
    }

    public Integer getScoreHint() {
        return scoreHint;
    }

    public String getAudioObjectKey() {
        return audioObjectKey;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
