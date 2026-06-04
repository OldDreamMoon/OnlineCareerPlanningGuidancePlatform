package com.bishe.server.skill.service;

/**
 * 技能节点学习状态。
 */
public enum SkillProgressStatus {
    NOT_STARTED,
    LEARNING,
    MASTERED;

    public static SkillProgressStatus from(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            throw new IllegalArgumentException("targetStatus is required");
        }
        return SkillProgressStatus.valueOf(rawValue.trim().toUpperCase());
    }
}
