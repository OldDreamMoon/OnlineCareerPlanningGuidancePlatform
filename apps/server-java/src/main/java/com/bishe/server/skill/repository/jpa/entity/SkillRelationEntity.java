package com.bishe.server.skill.repository.jpa.entity;

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
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

/**
 * 技能节点关系实体。
 */
@Entity
@Table(
        name = "skill_relations",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_skill_relations_unique",
                        columnNames = {"source_node_code", "target_node_code", "relation_type"}
                )
        }
)
public class SkillRelationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_node_code", nullable = false)
    private SkillNodeEntity sourceNode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_node_code", nullable = false)
    private SkillNodeEntity targetNode;

    @Column(name = "relation_type", nullable = false, length = 30)
    private String relationType;

    @Column(name = "label", nullable = false, length = 100)
    private String label;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected SkillRelationEntity() {
    }

    public static SkillRelationEntity create(
            SkillNodeEntity sourceNode,
            SkillNodeEntity targetNode,
            String relationType,
            String label,
            int sortOrder
    ) {
        SkillRelationEntity entity = new SkillRelationEntity();
        entity.setSourceNode(sourceNode);
        entity.setTargetNode(targetNode);
        entity.setRelationType(relationType);
        entity.setLabel(label);
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

    public Long getId() {
        return id;
    }

    public SkillNodeEntity getSourceNode() {
        return sourceNode;
    }

    public void setSourceNode(SkillNodeEntity sourceNode) {
        this.sourceNode = sourceNode;
    }

    public SkillNodeEntity getTargetNode() {
        return targetNode;
    }

    public void setTargetNode(SkillNodeEntity targetNode) {
        this.targetNode = targetNode;
    }

    public String getRelationType() {
        return relationType;
    }

    public void setRelationType(String relationType) {
        this.relationType = relationType;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }
}
