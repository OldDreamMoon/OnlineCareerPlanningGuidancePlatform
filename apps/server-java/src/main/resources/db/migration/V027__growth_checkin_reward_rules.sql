CREATE TABLE IF NOT EXISTS growth_checkin_reward_rules (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    reward_code VARCHAR(50) NOT NULL,
    streak_days INT NOT NULL,
    bonus_points INT NOT NULL,
    reward_title VARCHAR(100) NOT NULL,
    reward_description VARCHAR(255),
    is_enabled TINYINT(1) NOT NULL DEFAULT 1,
    sort_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_growth_checkin_reward_rules_code UNIQUE (reward_code),
    CONSTRAINT uq_growth_checkin_reward_rules_streak UNIQUE (streak_days)
);

INSERT INTO growth_checkin_reward_rules(
    reward_code,
    streak_days,
    bonus_points,
    reward_title,
    reward_description,
    is_enabled,
    sort_order,
    created_at,
    updated_at
)
SELECT 'CHECKIN_STREAK_3', 3, 6, '三日连签奖励', '连续签到 3 天可额外获得 6 积分。', 1, 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM growth_checkin_reward_rules WHERE reward_code = 'CHECKIN_STREAK_3');

INSERT INTO growth_checkin_reward_rules(
    reward_code,
    streak_days,
    bonus_points,
    reward_title,
    reward_description,
    is_enabled,
    sort_order,
    created_at,
    updated_at
)
SELECT 'CHECKIN_STREAK_7', 7, 14, '七日连签奖励', '连续签到 7 天可额外获得 14 积分。', 1, 20, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM growth_checkin_reward_rules WHERE reward_code = 'CHECKIN_STREAK_7');

INSERT INTO growth_checkin_reward_rules(
    reward_code,
    streak_days,
    bonus_points,
    reward_title,
    reward_description,
    is_enabled,
    sort_order,
    created_at,
    updated_at
)
SELECT 'CHECKIN_STREAK_14', 14, 30, '十四日连签奖励', '连续签到 14 天可额外获得 30 积分。', 1, 30, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM growth_checkin_reward_rules WHERE reward_code = 'CHECKIN_STREAK_14');
