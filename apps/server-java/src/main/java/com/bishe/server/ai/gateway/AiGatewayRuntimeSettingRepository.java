package com.bishe.server.ai.gateway;

import com.bishe.server.ai.gateway.jpa.AiGatewayRuntimeSettingJpaRepository;
import com.bishe.server.ai.gateway.jpa.entity.AiGatewayRuntimeSettingEntity;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

/**
 * AI 网关运行时设置仓储。
 */
@Repository
public class AiGatewayRuntimeSettingRepository {

    private final AiGatewayRuntimeSettingJpaRepository aiGatewayRuntimeSettingJpaRepository;

    public AiGatewayRuntimeSettingRepository(AiGatewayRuntimeSettingJpaRepository aiGatewayRuntimeSettingJpaRepository) {
        this.aiGatewayRuntimeSettingJpaRepository = aiGatewayRuntimeSettingJpaRepository;
    }

    public List<RuntimeSettingRow> findAll() {
        return aiGatewayRuntimeSettingJpaRepository.findAllByOrderBySettingKeyAsc().stream()
                .map(this::toRow)
                .toList();
    }

    public void upsert(String settingKey, String settingValue, String description, Long updatedBy) {
        AiGatewayRuntimeSettingEntity entity = aiGatewayRuntimeSettingJpaRepository.findById(settingKey)
                .orElseGet(() -> AiGatewayRuntimeSettingEntity.create(settingKey));
        entity.setSettingValue(settingValue);
        entity.setDescription(description);
        entity.setUpdatedBy(updatedBy);
        aiGatewayRuntimeSettingJpaRepository.saveAndFlush(entity);
    }

    private RuntimeSettingRow toRow(AiGatewayRuntimeSettingEntity entity) {
        return new RuntimeSettingRow(
                entity.getSettingKey(),
                entity.getSettingValue(),
                entity.getDescription(),
                entity.getUpdatedBy(),
                entity.getUpdatedAt()
        );
    }

    public record RuntimeSettingRow(
            String settingKey,
            String settingValue,
            String description,
            Long updatedBy,
            Instant updatedAt
    ) {
    }
}
