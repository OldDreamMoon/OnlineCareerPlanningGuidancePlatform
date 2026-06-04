package com.bishe.server.bounty.repository.jpa.entity;

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
        name = "bounty_submission_events",
        indexes = {
                @Index(name = "idx_bounty_submission_events_submission_time", columnList = "submission_id, created_at, id"),
                @Index(name = "idx_bounty_submission_events_task_time", columnList = "task_id, created_at, id")
        }
)
public class BountySubmissionEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "submission_id", nullable = false)
    private Long submissionId;

    @Column(name = "task_id", nullable = false)
    private Long taskId;

    @Column(name = "actor_user_id")
    private Long actorUserId;

    @Column(name = "event_type", nullable = false, length = 40)
    private String eventType;

    @Column(name = "comment_text", length = 500)
    private String commentText;

    @Column(name = "contact_intent", length = 80)
    private String contactIntent;

    @Column(name = "reject_template", length = 160)
    private String rejectTemplate;

    @Column(name = "note", columnDefinition = "TEXT")
    private String note;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected BountySubmissionEventEntity() {
    }

    public static BountySubmissionEventEntity create(
            long submissionId,
            long taskId,
            Long actorUserId,
            String eventType,
            String commentText,
            String contactIntent,
            String rejectTemplate,
            String note,
            Instant createdAt
    ) {
        BountySubmissionEventEntity entity = new BountySubmissionEventEntity();
        entity.submissionId = submissionId;
        entity.taskId = taskId;
        entity.actorUserId = actorUserId;
        entity.eventType = eventType;
        entity.commentText = commentText;
        entity.contactIntent = contactIntent;
        entity.rejectTemplate = rejectTemplate;
        entity.note = note;
        entity.createdAt = createdAt;
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

    public Long getSubmissionId() {
        return submissionId;
    }

    public Long getTaskId() {
        return taskId;
    }

    public Long getActorUserId() {
        return actorUserId;
    }

    public String getEventType() {
        return eventType;
    }

    public String getCommentText() {
        return commentText;
    }

    public String getContactIntent() {
        return contactIntent;
    }

    public String getRejectTemplate() {
        return rejectTemplate;
    }

    public String getNote() {
        return note;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
