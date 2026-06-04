package com.bishe.server.mentor.repository.jpa.entity;

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

import java.time.Instant;

/**
 * 导师服务套餐实体。
 */
@Entity
@Table(
        name = "mentor_service_packages",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_mentor_service_packages_sort",
                        columnNames = {"mentor_user_id", "sort_no"}
                )
        },
        indexes = {
                @Index(name = "idx_mentor_service_packages_enabled", columnList = "mentor_user_id, enabled, sort_no")
        }
)
public class MentorServicePackageEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "mentor_user_id", nullable = false)
    private Long mentorUserId;

    @Column(name = "package_name", nullable = false, length = 40)
    private String packageName;

    @Column(name = "scene_code", nullable = false, length = 60)
    private String sceneCode;

    @Column(name = "scene_label", nullable = false, length = 30)
    private String sceneLabel;

    @Column(name = "delivery_mode", nullable = false, length = 20)
    private String deliveryMode;

    @Column(name = "duration_minutes")
    private Integer durationMinutes;

    @Column(name = "price_fen", nullable = false)
    private Integer priceFen;

    @Column(name = "description", length = 240)
    private String description;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Column(name = "sort_no", nullable = false)
    private Integer sortNo;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected MentorServicePackageEntity() {
    }

    public static MentorServicePackageEntity create(long mentorUserId) {
        MentorServicePackageEntity entity = new MentorServicePackageEntity();
        entity.setMentorUserId(mentorUserId);
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

    public Long getMentorUserId() {
        return mentorUserId;
    }

    public void setMentorUserId(Long mentorUserId) {
        this.mentorUserId = mentorUserId;
    }

    public String getPackageName() {
        return packageName;
    }

    public void setPackageName(String packageName) {
        this.packageName = packageName;
    }

    public String getSceneCode() {
        return sceneCode;
    }

    public void setSceneCode(String sceneCode) {
        this.sceneCode = sceneCode;
    }

    public String getSceneLabel() {
        return sceneLabel;
    }

    public void setSceneLabel(String sceneLabel) {
        this.sceneLabel = sceneLabel;
    }

    public String getDeliveryMode() {
        return deliveryMode;
    }

    public void setDeliveryMode(String deliveryMode) {
        this.deliveryMode = deliveryMode;
    }

    public Integer getDurationMinutes() {
        return durationMinutes;
    }

    public void setDurationMinutes(Integer durationMinutes) {
        this.durationMinutes = durationMinutes;
    }

    public Integer getPriceFen() {
        return priceFen;
    }

    public void setPriceFen(Integer priceFen) {
        this.priceFen = priceFen;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Integer getSortNo() {
        return sortNo;
    }

    public void setSortNo(Integer sortNo) {
        this.sortNo = sortNo;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
