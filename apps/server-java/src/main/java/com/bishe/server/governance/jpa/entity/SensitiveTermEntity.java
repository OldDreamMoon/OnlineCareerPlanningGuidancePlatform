package com.bishe.server.governance.jpa.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(
        name = "sensitive_terms",
        indexes = {
                @Index(name = "idx_sensitive_terms_scope_enabled", columnList = "source_scope, enabled, is_whitelist"),
                @Index(name = "idx_sensitive_terms_type_enabled", columnList = "term_type, enabled, is_whitelist")
        }
)
public class SensitiveTermEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "term", nullable = false, length = 200)
    private String term;

    @Column(name = "term_type", nullable = false, length = 50)
    private String termType;

    @Column(name = "risk_level", nullable = false, length = 20)
    private String riskLevel;

    @Column(name = "action", nullable = false, length = 20)
    private String action;

    @Column(name = "source_scope", nullable = false, length = 50)
    private String sourceScope;

    @Column(name = "is_whitelist", nullable = false)
    private boolean whitelist;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected SensitiveTermEntity() {
    }

    public static SensitiveTermEntity create(
            String term,
            String termType,
            String riskLevel,
            String action,
            String sourceScope,
            boolean whitelist,
            boolean enabled
    ) {
        SensitiveTermEntity entity = new SensitiveTermEntity();
        entity.term = term;
        entity.termType = termType;
        entity.riskLevel = riskLevel;
        entity.action = action;
        entity.sourceScope = sourceScope;
        entity.setWhitelist(whitelist);
        entity.setEnabled(enabled);
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

    public void update(
            String term,
            String termType,
            String riskLevel,
            String action,
            String sourceScope,
            boolean whitelist,
            boolean enabled
    ) {
        this.term = term;
        this.termType = termType;
        this.riskLevel = riskLevel;
        this.action = action;
        this.sourceScope = sourceScope;
        setWhitelist(whitelist);
        setEnabled(enabled);
    }

    public void setWhitelist(boolean whitelist) {
        this.whitelist = whitelist;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Long getId() {
        return id;
    }

    public String getTerm() {
        return term;
    }

    public String getTermType() {
        return termType;
    }

    public String getRiskLevel() {
        return riskLevel;
    }

    public String getAction() {
        return action;
    }

    public String getSourceScope() {
        return sourceScope;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public boolean isWhitelist() {
        return whitelist;
    }

    public boolean isEnabled() {
        return enabled;
    }
}
