package com.bishe.server.skill.repository.jpa;

import com.bishe.server.auth.model.UserRole;
import com.bishe.server.skill.repository.jpa.entity.UserAccountRefEntity;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 技能域只读用户引用仓储。
 */
public interface UserAccountRefJpaRepository extends JpaRepository<UserAccountRefEntity, Long> {

    boolean existsByIdAndRoleAndDeletedFalse(Long id, UserRole role);
}
