package com.bishe.server.ai.gateway.jpa;

import com.bishe.server.ai.gateway.jpa.entity.AiProviderConfigEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * AI 提供商配置 JPA 仓储。
 */
public interface AiProviderConfigJpaRepository extends JpaRepository<AiProviderConfigEntity, Long> {

    List<AiProviderConfigEntity> findAllByOrderByEnabledDescProviderCodeAscIdAsc();

    Optional<AiProviderConfigEntity> findByProviderCode(String providerCode);
}
