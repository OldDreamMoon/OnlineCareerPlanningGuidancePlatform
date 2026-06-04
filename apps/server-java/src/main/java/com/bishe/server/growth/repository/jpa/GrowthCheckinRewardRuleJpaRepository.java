package com.bishe.server.growth.repository.jpa;

import com.bishe.server.growth.repository.jpa.entity.GrowthCheckinRewardRuleEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 签到奖励规则 JPA 仓储。
 */
public interface GrowthCheckinRewardRuleJpaRepository extends JpaRepository<GrowthCheckinRewardRuleEntity, Long> {

    List<GrowthCheckinRewardRuleEntity> findByEnabledTrueOrderByStreakDaysAscSortOrderAscIdAsc();
}
