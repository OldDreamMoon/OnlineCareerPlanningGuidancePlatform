package com.bishe.server.ai.gateway.jpa;

import com.bishe.server.ai.gateway.jpa.entity.PromptTemplateEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * AI 提示词模板 JPA 仓储。
 */
public interface PromptTemplateJpaRepository extends JpaRepository<PromptTemplateEntity, Long> {

    List<PromptTemplateEntity> findAllByOrderByTaskTypeAscTemplateNameAscVersionNoDescIdDesc();

    Optional<PromptTemplateEntity> findFirstByTaskTypeAndTemplateNameAndStatusOrderByVersionNoDescIdDesc(
            String taskType,
            String templateName,
            String status
    );

    List<PromptTemplateEntity> findAllByTaskTypeAndTemplateNameAndStatusOrderByVersionNoDescIdDesc(
            String taskType,
            String templateName,
            String status
    );
}
