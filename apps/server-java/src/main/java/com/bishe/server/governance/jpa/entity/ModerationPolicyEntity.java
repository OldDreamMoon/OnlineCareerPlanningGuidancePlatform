package com.bishe.server.governance.jpa.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "moderation_policies")
public class ModerationPolicyEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "policy_key", nullable = false, length = 100)
    private String policyKey;

    @Column(name = "policy_value", nullable = false, length = 500)
    private String policyValue;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "updated_by")
    private Long updatedBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ModerationPolicyEntity() {
    }

    public static ModerationPolicyEntity create(
            String policyKey,
            String policyValue,
            String description,
            long updatedBy
    ) {
        ModerationPolicyEntity entity = new ModerationPolicyEntity();
        entity.policyKey = policyKey;
        entity.policyValue = policyValue;
        entity.description = description;
        entity.updatedBy = updatedBy;
        return entity;
    }

    @PrePersist
    void onCreate() {
        if (updatedAt == null) {
            updatedAt = Instant.now();
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public void update(String policyValue, String description, long updatedBy) {
        this.policyValue = policyValue;
        this.description = description;
        this.updatedBy = updatedBy;
    }

    public Long getId() {
        return id;
    }

    public String getPolicyKey() {
        return policyKey;
    }

    public String getPolicyValue() {
        return policyValue;
    }
}
