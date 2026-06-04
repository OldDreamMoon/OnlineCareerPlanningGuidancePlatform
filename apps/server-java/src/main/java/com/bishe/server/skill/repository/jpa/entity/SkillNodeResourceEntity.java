package com.bishe.server.skill.repository.jpa.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * 技能节点资源实体。
 */
@Entity
@Table(name = "skill_node_resources")
public class SkillNodeResourceEntity {

    @Id
    @Column(name = "resource_code", nullable = false, length = 100)
    private String resourceCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "node_code", nullable = false)
    private SkillNodeEntity node;

    @Column(name = "resource_type", nullable = false, length = 30)
    private String resourceType;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "source_label", nullable = false, length = 100)
    private String sourceLabel;

    @Column(name = "duration_label", nullable = false, length = 50)
    private String durationLabel;

    @Column(name = "link_url", nullable = false, length = 500)
    private String linkUrl;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected SkillNodeResourceEntity() {
    }

    public static SkillNodeResourceEntity create(
            String resourceCode,
            SkillNodeEntity node,
            String resourceType,
            String title,
            String sourceLabel,
            String durationLabel,
            String linkUrl,
            int sortOrder
    ) {
        SkillNodeResourceEntity entity = new SkillNodeResourceEntity();
        entity.setResourceCode(resourceCode);
        entity.setNode(node);
        entity.setResourceType(resourceType);
        entity.setTitle(title);
        entity.setSourceLabel(sourceLabel);
        entity.setDurationLabel(durationLabel);
        entity.setLinkUrl(linkUrl);
        entity.setSortOrder(sortOrder);
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

    public String getResourceCode() {
        return resourceCode;
    }

    public void setResourceCode(String resourceCode) {
        this.resourceCode = resourceCode;
    }

    public SkillNodeEntity getNode() {
        return node;
    }

    public void setNode(SkillNodeEntity node) {
        this.node = node;
    }

    public String getResourceType() {
        return resourceType;
    }

    public void setResourceType(String resourceType) {
        this.resourceType = resourceType;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getSourceLabel() {
        return sourceLabel;
    }

    public void setSourceLabel(String sourceLabel) {
        this.sourceLabel = sourceLabel;
    }

    public String getDurationLabel() {
        return durationLabel;
    }

    public void setDurationLabel(String durationLabel) {
        this.durationLabel = durationLabel;
    }

    public String getLinkUrl() {
        return linkUrl;
    }

    public void setLinkUrl(String linkUrl) {
        this.linkUrl = linkUrl;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
