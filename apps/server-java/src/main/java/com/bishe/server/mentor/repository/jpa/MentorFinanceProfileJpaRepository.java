package com.bishe.server.mentor.repository.jpa;

import com.bishe.server.mentor.repository.jpa.entity.MentorFinanceProfileEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * 导师财务概览画像 JPA 仓储。
 */
public interface MentorFinanceProfileJpaRepository extends JpaRepository<MentorFinanceProfileEntity, Long> {

    Optional<MentorFinanceProfileEntity> findByUserId(long userId);
}
