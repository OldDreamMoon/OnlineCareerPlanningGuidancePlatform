package com.bishe.server.ai.gateway.jpa;

import com.bishe.server.ai.gateway.jpa.entity.AiGatewayRuntimeSettingEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * AI 网关运行时设置 JPA 仓储。
 */
public interface AiGatewayRuntimeSettingJpaRepository extends JpaRepository<AiGatewayRuntimeSettingEntity, String> {

    List<AiGatewayRuntimeSettingEntity> findAllByOrderBySettingKeyAsc();
}
