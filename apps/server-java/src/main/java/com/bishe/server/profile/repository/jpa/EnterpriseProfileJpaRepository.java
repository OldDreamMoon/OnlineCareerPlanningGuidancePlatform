package com.bishe.server.profile.repository.jpa;

import com.bishe.server.profile.repository.jpa.entity.EnterpriseProfileEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 企业资料 JPA 仓储。
 */
public interface EnterpriseProfileJpaRepository extends JpaRepository<EnterpriseProfileEntity, Long> {

    Optional<EnterpriseProfileEntity> findByUserId(long userId);

    List<EnterpriseProfileEntity> findAllByUserIdIn(Collection<Long> userIds);
}
