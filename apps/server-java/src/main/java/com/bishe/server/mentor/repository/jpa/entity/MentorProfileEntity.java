package com.bishe.server.mentor.repository.jpa.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 导师资料完整实体。
 */
@Entity
@Table(name = "mentor_profiles")
public class MentorProfileEntity {

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

    @Column(name = "avatar_bucket", length = 100)
    private String avatarBucket;

    @Column(name = "avatar_object_key", length = 255)
    private String avatarObjectKey;

    @Column(name = "avatar_content_type", length = 100)
    private String avatarContentType;

    @Column(name = "avatar_updated_at")
    private Instant avatarUpdatedAt;

    @Column(name = "expertise_tags", length = 512)
    private String expertiseTags;

    @Column(name = "service_scenes", length = 512)
    private String serviceScenes;

    @Column(name = "bio", columnDefinition = "TEXT")
    private String bio;

    @Column(name = "suitable_for", columnDefinition = "TEXT")
    private String suitableFor;

    @Column(name = "not_suitable_for", columnDefinition = "TEXT")
    private String notSuitableFor;

    @Column(name = "prep_materials", columnDefinition = "TEXT")
    private String prepMaterials;

    @Column(name = "reply_rhythm", columnDefinition = "TEXT")
    private String replyRhythm;

    @Column(name = "price_fen", nullable = false)
    private Integer priceFen;

    @Column(name = "is_available", nullable = false)
    private boolean available;

    @Column(name = "approval_status", nullable = false, length = 20)
    private String approvalStatus;

    @Column(name = "total_orders", nullable = false)
    private Integer totalOrders;

    @Column(name = "avg_rating", nullable = false)
    private BigDecimal avgRating;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected MentorProfileEntity() {
    }

    public static MentorProfileEntity create(long userId) {
        MentorProfileEntity entity = new MentorProfileEntity();
        entity.setUserId(userId);
        return entity;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (priceFen == null) {
            priceFen = 5000;
        }
        if (approvalStatus == null) {
            approvalStatus = "PENDING";
        }
        if (totalOrders == null) {
            totalOrders = 0;
        }
        if (avgRating == null) {
            avgRating = BigDecimal.ZERO.setScale(2);
        }
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

    public void touch() {
        updatedAt = Instant.now();
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getCompanyName() {
        return companyName;
    }

    public void setCompanyName(String companyName) {
        this.companyName = companyName;
    }

    public String getJobTitle() {
        return jobTitle;
    }

    public void setJobTitle(String jobTitle) {
        this.jobTitle = jobTitle;
    }

    public boolean isShowRealName() {
        return showRealName;
    }

    public void setShowRealName(boolean showRealName) {
        this.showRealName = showRealName;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }

    public String getAvatarBucket() {
        return avatarBucket;
    }

    public void setAvatarBucket(String avatarBucket) {
        this.avatarBucket = avatarBucket;
    }

    public String getAvatarObjectKey() {
        return avatarObjectKey;
    }

    public void setAvatarObjectKey(String avatarObjectKey) {
        this.avatarObjectKey = avatarObjectKey;
    }

    public String getAvatarContentType() {
        return avatarContentType;
    }

    public void setAvatarContentType(String avatarContentType) {
        this.avatarContentType = avatarContentType;
    }

    public Instant getAvatarUpdatedAt() {
        return avatarUpdatedAt;
    }

    public void setAvatarUpdatedAt(Instant avatarUpdatedAt) {
        this.avatarUpdatedAt = avatarUpdatedAt;
    }

    public String getExpertiseTags() {
        return expertiseTags;
    }

    public void setExpertiseTags(String expertiseTags) {
        this.expertiseTags = expertiseTags;
    }

    public String getServiceScenes() {
        return serviceScenes;
    }

    public void setServiceScenes(String serviceScenes) {
        this.serviceScenes = serviceScenes;
    }

    public String getBio() {
        return bio;
    }

    public void setBio(String bio) {
        this.bio = bio;
    }

    public String getSuitableFor() {
        return suitableFor;
    }

    public void setSuitableFor(String suitableFor) {
        this.suitableFor = suitableFor;
    }

    public String getNotSuitableFor() {
        return notSuitableFor;
    }

    public void setNotSuitableFor(String notSuitableFor) {
        this.notSuitableFor = notSuitableFor;
    }

    public String getPrepMaterials() {
        return prepMaterials;
    }

    public void setPrepMaterials(String prepMaterials) {
        this.prepMaterials = prepMaterials;
    }

    public String getReplyRhythm() {
        return replyRhythm;
    }

    public void setReplyRhythm(String replyRhythm) {
        this.replyRhythm = replyRhythm;
    }

    public Integer getPriceFen() {
        return priceFen;
    }

    public void setPriceFen(Integer priceFen) {
        this.priceFen = priceFen;
    }

    public boolean isAvailable() {
        return available;
    }

    public void setAvailable(boolean available) {
        this.available = available;
    }

    public String getApprovalStatus() {
        return approvalStatus;
    }

    public void setApprovalStatus(String approvalStatus) {
        this.approvalStatus = approvalStatus;
    }

    public Integer getTotalOrders() {
        return totalOrders;
    }

    public void setTotalOrders(Integer totalOrders) {
        this.totalOrders = totalOrders;
    }

    public BigDecimal getAvgRating() {
        return avgRating;
    }

    public void setAvgRating(BigDecimal avgRating) {
        this.avgRating = avgRating;
    }
}
