package com.bishe.server.skill.repository.jpa.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 技能树节点实体。
 */
@Entity
@Table(name = "skills")
public class SkillNodeEntity {

    @Id
    @Column(name = "node_code", nullable = false, length = 100)
    private String nodeCode;

    @Column(name = "label", nullable = false, length = 100)
    private String label;

    @Column(name = "description", length = 500)
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_code")
    private SkillNodeEntity parent;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "parent", fetch = FetchType.LAZY)
    private Set<SkillNodeEntity> children = new LinkedHashSet<>();

    @OneToMany(mappedBy = "node", fetch = FetchType.LAZY)
    private Set<SkillNodeResourceEntity> resources = new LinkedHashSet<>();

    @OneToMany(mappedBy = "sourceNode", fetch = FetchType.LAZY)
    private Set<SkillRelationEntity> outboundRelations = new LinkedHashSet<>();

    @OneToMany(mappedBy = "targetNode", fetch = FetchType.LAZY)
    private Set<SkillRelationEntity> inboundRelations = new LinkedHashSet<>();

    protected SkillNodeEntity() {
    }

    public static SkillNodeEntity create(
            String nodeCode,
            String label,
            String description,
            SkillNodeEntity parent,
            int sortOrder
    ) {
        SkillNodeEntity entity = new SkillNodeEntity();
        entity.setNodeCode(nodeCode);
        entity.setLabel(label);
        entity.setDescription(description);
        entity.setParent(parent);
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

    public String getNodeCode() {
        return nodeCode;
    }

    public void setNodeCode(String nodeCode) {
        this.nodeCode = nodeCode;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public SkillNodeEntity getParent() {
        return parent;
    }

    public void setParent(SkillNodeEntity parent) {
        this.parent = parent;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
