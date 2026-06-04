CREATE TABLE IF NOT EXISTS daily_tasks (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    task_code VARCHAR(50) NOT NULL,
    title VARCHAR(100) NOT NULL,
    description VARCHAR(255) NULL,
    points INT NOT NULL,
    is_active TINYINT(1) NOT NULL DEFAULT 1,
    sort_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_daily_tasks_task_code UNIQUE (task_code)
);

CREATE TABLE IF NOT EXISTS checkins (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    student_user_id BIGINT NOT NULL,
    checkin_date DATE NOT NULL,
    streak_count INT NOT NULL,
    points_earned INT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_checkins_user_date UNIQUE (student_user_id, checkin_date),
    CONSTRAINT fk_checkins_user FOREIGN KEY (student_user_id) REFERENCES users(id)
);

CREATE TABLE IF NOT EXISTS points_ledger (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    student_user_id BIGINT NOT NULL,
    delta_points INT NOT NULL,
    reason_code VARCHAR(50) NOT NULL,
    balance_after INT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_points_ledger_user FOREIGN KEY (student_user_id) REFERENCES users(id)
);

CREATE INDEX idx_points_ledger_student_time ON points_ledger(student_user_id, created_at);

INSERT INTO daily_tasks(task_code, title, description, points, is_active, sort_order, created_at, updated_at)
SELECT 'TASK_RESUME_OPTIMIZE', '完成一次简历优化', '针对目标岗位优化一版简历内容并记录调整点。', 10, 1, 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM daily_tasks WHERE task_code = 'TASK_RESUME_OPTIMIZE');

INSERT INTO daily_tasks(task_code, title, description, points, is_active, sort_order, created_at, updated_at)
SELECT 'TASK_SKILL_PROGRESS', '点亮一个技能节点', '在技能树中完成一个学习节点更新，保持成长连续性。', 8, 1, 20, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM daily_tasks WHERE task_code = 'TASK_SKILL_PROGRESS');

INSERT INTO daily_tasks(task_code, title, description, points, is_active, sort_order, created_at, updated_at)
SELECT 'TASK_COMMUNITY_INTERACT', '参与一次社区互动', '阅读并发布一条有价值的社区互动内容。', 6, 1, 30, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM daily_tasks WHERE task_code = 'TASK_COMMUNITY_INTERACT');
