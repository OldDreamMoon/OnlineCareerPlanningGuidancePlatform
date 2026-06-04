package com.bishe.server.profile.repository.jpa.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * 企业资料实体。
 */
@Entity
@Table(name = "enterprise_profiles")
public class EnterpriseProfileEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "company_name", length = 200)
    private String companyName;

    @Column(name = "industry", length = 100)
    private String industry;

    @Column(name = "company_size", length = 50)
    private String companySize;

    @Column(name = "hiring_tags", length = 512)
    private String hiringTags;

    @Column(name = "contact_title", length = 100)
    private String contactTitle;

    @Column(name = "bio", columnDefinition = "TEXT")
    private String bio;

    @Column(name = "external_links", columnDefinition = "TEXT")
    private String externalLinks;

    @Column(name = "preferences", columnDefinition = "TEXT")
    private String preferences;

    @Column(name = "logo_bucket", length = 100)
    private String logoBucket;

    @Column(name = "logo_object_key", length = 255)
    private String logoObjectKey;

    @Column(name = "logo_content_type", length = 100)
    private String logoContentType;

    @Column(name = "logo_updated_at")
    private Instant logoUpdatedAt;

    @Column(name = "approval_status", nullable = false, length = 20)
    private String approvalStatus;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected EnterpriseProfileEntity() {
    }

    public static EnterpriseProfileEntity create(long userId) {
        EnterpriseProfileEntity entity = new EnterpriseProfileEntity();
        entity.setUserId(userId);
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

    public String getIndustry() {
        return industry;
    }

    public void setIndustry(String industry) {
        this.industry = industry;
    }

    public String getCompanySize() {
        return companySize;
    }

    public void setCompanySize(String companySize) {
        this.companySize = companySize;
    }

    public String getHiringTags() {
        return hiringTags;
    }

    public void setHiringTags(String hiringTags) {
        this.hiringTags = hiringTags;
    }

    public String getContactTitle() {
        return contactTitle;
    }

    public void setContactTitle(String contactTitle) {
        this.contactTitle = contactTitle;
    }

    public String getBio() {
        return bio;
    }

    public void setBio(String bio) {
        this.bio = bio;
    }

    public String getExternalLinks() {
        return externalLinks;
    }

    public void setExternalLinks(String externalLinks) {
        this.externalLinks = externalLinks;
    }

    public String getPreferences() {
        return preferences;
    }

    public void setPreferences(String preferences) {
        this.preferences = preferences;
    }

    public String getLogoBucket() {
        return logoBucket;
    }

    public void setLogoBucket(String logoBucket) {
        this.logoBucket = logoBucket;
    }

    public String getLogoObjectKey() {
        return logoObjectKey;
    }

    public void setLogoObjectKey(String logoObjectKey) {
        this.logoObjectKey = logoObjectKey;
    }

    public String getLogoContentType() {
        return logoContentType;
    }

    public void setLogoContentType(String logoContentType) {
        this.logoContentType = logoContentType;
    }

    public Instant getLogoUpdatedAt() {
        return logoUpdatedAt;
    }

    public void setLogoUpdatedAt(Instant logoUpdatedAt) {
        this.logoUpdatedAt = logoUpdatedAt;
    }

    public String getApprovalStatus() {
        return approvalStatus;
    }

    public void setApprovalStatus(String approvalStatus) {
        this.approvalStatus = approvalStatus;
    }
}
