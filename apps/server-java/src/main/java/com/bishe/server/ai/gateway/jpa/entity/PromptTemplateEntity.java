package com.bishe.server.ai.gateway.jpa.entity;

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
 * AI 提示词模板实体。
 */
@Entity
@Table(
        name = "prompt_templates",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_prompt_templates_version", columnNames = {"task_type", "template_name", "version_no"})
        },
        indexes = {
                @Index(name = "idx_prompt_templates_task_name_status", columnList = "task_type, template_name, status, version_no")
        }
)
public class PromptTemplateEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "task_type", nullable = false, length = 40)
    private String taskType;

    @Column(name = "template_name", nullable = false, length = 80)
    private String templateName;

    @Column(name = "version_no", nullable = false)
    private int versionNo;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "template_format", nullable = false, length = 32)
    private String templateFormat;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "description", length = 255)
    private String description;

    @Column(name = "variables_json", columnDefinition = "TEXT")
    private String variablesJson;

    @Column(name = "bundle_json", columnDefinition = "TEXT")
    private String bundleJson;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected PromptTemplateEntity() {
    }

    public static PromptTemplateEntity create(
            String taskType,
            String templateName,
            int versionNo,
            String status,
            String templateFormat,
            String content,
            String description,
            String variablesJson,
            String bundleJson
    ) {
        PromptTemplateEntity entity = new PromptTemplateEntity();
        entity.taskType = taskType;
        entity.templateName = templateName;
        entity.versionNo = versionNo;
        entity.status = status;
        entity.templateFormat = templateFormat;
        entity.content = content;
        entity.description = description;
        entity.variablesJson = variablesJson;
        entity.bundleJson = bundleJson;
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

    public String getTaskType() {
        return taskType;
    }

    public void setTaskType(String taskType) {
        this.taskType = taskType;
    }

    public String getTemplateName() {
        return templateName;
    }

    public void setTemplateName(String templateName) {
        this.templateName = templateName;
    }

    public int getVersionNo() {
        return versionNo;
    }

    public void setVersionNo(int versionNo) {
        this.versionNo = versionNo;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getTemplateFormat() {
        return templateFormat;
    }

    public void setTemplateFormat(String templateFormat) {
        this.templateFormat = templateFormat;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getVariablesJson() {
        return variablesJson;
    }

    public void setVariablesJson(String variablesJson) {
        this.variablesJson = variablesJson;
    }

    public String getBundleJson() {
        return bundleJson;
    }

    public void setBundleJson(String bundleJson) {
        this.bundleJson = bundleJson;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
