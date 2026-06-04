package com.bishe.server.ai.gateway.jpa;

import com.bishe.server.ai.gateway.jpa.entity.AiProviderModelEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * AI 提供商模型配置 JPA 仓储。
 */
public interface AiProviderModelJpaRepository extends JpaRepository<AiProviderModelEntity, Long> {

    List<AiProviderModelEntity> findAllByOrderByProviderConfigIdAscEnabledDescModelCodeAscIdAsc();

    List<AiProviderModelEntity> findByProviderConfigIdOrderByEnabledDescModelCodeAscIdAsc(long providerConfigId);

    List<AiProviderModelEntity> findByProviderConfigIdInOrderByProviderConfigIdAscEnabledDescModelCodeAscIdAsc(List<Long> providerConfigIds);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from AiProviderModelEntity entity where entity.providerConfigId = :providerConfigId")
    void deleteAllByProviderConfigId(@Param("providerConfigId") long providerConfigId);
}
