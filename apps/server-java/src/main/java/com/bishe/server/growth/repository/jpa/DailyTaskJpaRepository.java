package com.bishe.server.growth.repository.jpa;

import com.bishe.server.growth.repository.jpa.entity.DailyTaskEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 每日任务 JPA 仓储。
 */
public interface DailyTaskJpaRepository extends JpaRepository<DailyTaskEntity, Long> {

    List<DailyTaskEntity> findByActiveTrueOrderBySortOrderAscIdAsc();

    Optional<DailyTaskEntity> findByIdAndActiveTrue(long id);
}
