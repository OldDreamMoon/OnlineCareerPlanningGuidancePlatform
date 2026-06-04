package com.bishe.server.mentor.repository.jpa.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 管理员导师经营台只读画像实体。
 */
@Entity
@Table(name = "mentor_profiles")
public class AdminMentorProfileEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "company_name", length = 200)
    private String companyName;

    @Column(name = "job_title", length = 100)
    private String jobTitle;

    @Column(name = "show_real_name", nullable = false)
    private boolean showRealName;

    @Column(name = "avatar_url", length = 255)
    private String avatarUrl;

    @Column(name = "avatar_object_key", length = 255)
    private String avatarObjectKey;

    @Column(name = "avatar_updated_at")
    private Instant avatarUpdatedAt;

    @Column(name = "approval_status", nullable = false, length = 20)
    private String approvalStatus;

    @Column(name = "is_available", nullable = false)
    private boolean available;

    @Column(name = "price_fen", nullable = false)
    private Integer priceFen;

    @Column(name = "expertise_tags", length = 512)
    private String expertiseTags;

    @Column(name = "service_scenes", length = 512)
    private String serviceScenes;

    @Column(name = "avg_rating", nullable = false)
    private BigDecimal avgRating;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected AdminMentorProfileEntity() {
    }

    public Long getUserId() {
        return userId;
    }

    public String getCompanyName() {
        return companyName;
    }

    public String getJobTitle() {
        return jobTitle;
    }

    public boolean isShowRealName() {
        return showRealName;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public String getAvatarObjectKey() {
        return avatarObjectKey;
    }

    public Instant getAvatarUpdatedAt() {
        return avatarUpdatedAt;
    }

    public String getApprovalStatus() {
        return approvalStatus;
    }

    public boolean isAvailable() {
        return available;
    }

    public Integer getPriceFen() {
        return priceFen;
    }

    public String getExpertiseTags() {
        return expertiseTags;
    }

    public String getServiceScenes() {
        return serviceScenes;
    }

    public BigDecimal getAvgRating() {
        return avgRating;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
