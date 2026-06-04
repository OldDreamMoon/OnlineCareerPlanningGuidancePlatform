package com.bishe.server.growth.repository.jpa.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

/**
 * 成长中心签到奖励规则实体。
 */
@Entity
@Table(
        name = "growth_checkin_reward_rules",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_growth_checkin_reward_rules_code", columnNames = {"reward_code"}),
                @UniqueConstraint(name = "uq_growth_checkin_reward_rules_streak", columnNames = {"streak_days"})
        }
)
public class GrowthCheckinRewardRuleEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "reward_code", nullable = false, length = 50)
    private String rewardCode;

    @Column(name = "streak_days", nullable = false)
    private int streakDays;

    @Column(name = "bonus_points", nullable = false)
    private int bonusPoints;

    @Column(name = "reward_title", nullable = false, length = 100)
    private String rewardTitle;

    @Column(name = "reward_description", length = 255)
    private String rewardDescription;

    @Column(name = "is_enabled", nullable = false)
    private boolean enabled;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected GrowthCheckinRewardRuleEntity() {
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public String getRewardCode() {
        return rewardCode;
    }

    public int getStreakDays() {
        return streakDays;
    }

    public int getBonusPoints() {
        return bonusPoints;
    }

    public String getRewardTitle() {
        return rewardTitle;
    }

    public String getRewardDescription() {
        return rewardDescription;
    }

    public int getSortOrder() {
        return sortOrder;
    }
}
