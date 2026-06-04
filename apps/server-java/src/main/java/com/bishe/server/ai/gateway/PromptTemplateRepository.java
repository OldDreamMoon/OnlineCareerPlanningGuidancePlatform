package com.bishe.server.ai.gateway;

import com.bishe.server.ai.gateway.jpa.PromptTemplateJpaRepository;
import com.bishe.server.ai.gateway.jpa.entity.PromptTemplateEntity;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * AI 提示词模板仓储。
 */
@Repository
public class PromptTemplateRepository {

    private static final String ACTIVE_STATUS = "ACTIVE";
    private static final String INACTIVE_STATUS = "INACTIVE";

    private final PromptTemplateJpaRepository promptTemplateJpaRepository;

    public PromptTemplateRepository(PromptTemplateJpaRepository promptTemplateJpaRepository) {
        this.promptTemplateJpaRepository = promptTemplateJpaRepository;
    }

    public List<PromptTemplateRow> findAllPromptTemplates() {
        return promptTemplateJpaRepository.findAllByOrderByTaskTypeAscTemplateNameAscVersionNoDescIdDesc().stream()
                .map(this::toRow)
                .toList();
    }

    public Optional<PromptTemplateRow> findPromptTemplateById(long id) {
        return promptTemplateJpaRepository.findById(id).map(this::toRow);
    }

    public Optional<PromptTemplateRow> findActivePromptTemplate(String taskType, String templateName) {
        return promptTemplateJpaRepository
                .findFirstByTaskTypeAndTemplateNameAndStatusOrderByVersionNoDescIdDesc(taskType, templateName, ACTIVE_STATUS)
                .map(this::toRow);
    }

    public long insertPromptTemplate(
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
        PromptTemplateEntity entity = PromptTemplateEntity.create(
                taskType,
                templateName,
                versionNo,
                status,
                templateFormat,
                content,
                description,
                variablesJson,
                bundleJson
        );
        return promptTemplateJpaRepository.saveAndFlush(entity).getId();
    }

    public boolean updatePromptTemplate(
            long id,
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
        Optional<PromptTemplateEntity> entityOptional = promptTemplateJpaRepository.findById(id);
        if (entityOptional.isEmpty()) {
            return false;
        }
        PromptTemplateEntity entity = entityOptional.get();
        entity.setTaskType(taskType);
        entity.setTemplateName(templateName);
        entity.setVersionNo(versionNo);
        entity.setStatus(status);
        entity.setTemplateFormat(templateFormat);
        entity.setContent(content);
        entity.setDescription(description);
        entity.setVariablesJson(variablesJson);
        entity.setBundleJson(bundleJson);
        promptTemplateJpaRepository.saveAndFlush(entity);
        return true;
    }

    public void deactivateOtherPromptTemplates(String taskType, String templateName, long excludeId) {
        List<PromptTemplateEntity> activeTemplates = promptTemplateJpaRepository
                .findAllByTaskTypeAndTemplateNameAndStatusOrderByVersionNoDescIdDesc(taskType, templateName, ACTIVE_STATUS);
        boolean changed = false;
        for (PromptTemplateEntity entity : activeTemplates) {
            if (entity.getId() != null && entity.getId() != excludeId) {
                entity.setStatus(INACTIVE_STATUS);
                changed = true;
            }
        }
        if (changed) {
            promptTemplateJpaRepository.saveAllAndFlush(activeTemplates);
        }
    }

    private PromptTemplateRow toRow(PromptTemplateEntity entity) {
        return new PromptTemplateRow(
                entity.getId(),
                entity.getTaskType(),
                entity.getTemplateName(),
                entity.getVersionNo(),
                entity.getStatus(),
                entity.getTemplateFormat(),
                entity.getContent(),
                entity.getDescription(),
                entity.getVariablesJson(),
                entity.getBundleJson(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    public record PromptTemplateRow(
            long id,
            String taskType,
            String templateName,
            int versionNo,
            String status,
            String templateFormat,
            String content,
            String description,
            String variablesJson,
            String bundleJson,
            Instant createdAt,
            Instant updatedAt
    ) {
    }
}
