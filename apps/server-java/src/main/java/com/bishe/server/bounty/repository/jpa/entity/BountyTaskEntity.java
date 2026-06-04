package com.bishe.server.bounty.repository.jpa.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
        name = "bounty_tasks",
        indexes = {
                @Index(name = "idx_bounty_tasks_enterprise_time", columnList = "enterprise_user_id, created_at"),
                @Index(name = "idx_bounty_tasks_status_time", columnList = "status, created_at")
        }
)
public class BountyTaskEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "enterprise_user_id", nullable = false)
    private Long enterpriseUserId;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "description", nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(name = "reward_description", nullable = false, length = 255)
    private String rewardDescription;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "accepted_submission_id")
    private Long acceptedSubmissionId;

    @Column(name = "deadline_at")
    private Instant deadlineAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "task", fetch = FetchType.LAZY)
    private List<BountySubmissionEntity> submissions = new ArrayList<>();

    protected BountyTaskEntity() {
    }

    public static BountyTaskEntity create(
            long enterpriseUserId,
            String title,
            String description,
            String rewardDescription,
            Instant deadlineAt
    ) {
        BountyTaskEntity entity = new BountyTaskEntity();
        entity.enterpriseUserId = enterpriseUserId;
        entity.title = title;
        entity.description = description;
        entity.rewardDescription = rewardDescription;
        entity.status = "OPEN";
        entity.deadlineAt = deadlineAt;
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

    public void updateTask(String title, String description, String rewardDescription, Instant deadlineAt) {
        this.title = title;
        this.description = description;
        this.rewardDescription = rewardDescription;
        this.deadlineAt = deadlineAt;
        this.updatedAt = Instant.now();
    }

    public void acceptSubmission(long acceptedSubmissionId) {
        Instant now = Instant.now();
        this.acceptedSubmissionId = acceptedSubmissionId;
        this.status = "CLOSED";
        this.closedAt = now;
        this.updatedAt = now;
    }

    public void updateStatus(String status) {
        Instant now = Instant.now();
        this.status = status;
        this.closedAt = "OPEN".equals(status) ? null : now;
        this.updatedAt = now;
    }

    public Long getId() {
        return id;
    }

    public Long getEnterpriseUserId() {
        return enterpriseUserId;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public String getRewardDescription() {
        return rewardDescription;
    }

    public String getStatus() {
        return status;
    }

    public Long getAcceptedSubmissionId() {
        return acceptedSubmissionId;
    }

    public Instant getDeadlineAt() {
        return deadlineAt;
    }

    public Instant getClosedAt() {
        return closedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
