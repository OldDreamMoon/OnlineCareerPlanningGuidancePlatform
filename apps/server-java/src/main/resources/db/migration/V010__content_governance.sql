CREATE TABLE IF NOT EXISTS audit_logs (
  id               BIGINT AUTO_INCREMENT PRIMARY KEY,
  trace_id         VARCHAR(64)  NOT NULL,
  operator_user_id BIGINT       NOT NULL,
  action_type      VARCHAR(50)  NOT NULL,
  target_type      VARCHAR(30)  NULL,
  target_id        VARCHAR(100) NULL,
  detail_json      LONGTEXT     NULL,
  created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_audit_logs_trace (trace_id),
  INDEX idx_audit_logs_action_time (action_type, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS sensitive_terms (
  id           BIGINT AUTO_INCREMENT PRIMARY KEY,
  term         VARCHAR(200) NOT NULL,
  risk_level   VARCHAR(20)  NOT NULL,
  action       VARCHAR(20)  NOT NULL,
  source_scope VARCHAR(50)  NOT NULL,
  is_whitelist TINYINT(1)   NOT NULL DEFAULT 0,
  enabled      TINYINT(1)   NOT NULL DEFAULT 1,
  created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_sensitive_terms_term_scope_white (term, source_scope, is_whitelist),
  INDEX idx_sensitive_terms_scope_enabled (source_scope, enabled, is_whitelist)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS moderation_policies (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  policy_key  VARCHAR(100) NOT NULL,
  policy_value VARCHAR(500) NOT NULL,
  description VARCHAR(500) NULL,
  updated_by  BIGINT       NULL,
  updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_moderation_policies_key (policy_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS content_moderation_events (
  id               BIGINT AUTO_INCREMENT PRIMARY KEY,
  trace_id         VARCHAR(64)  NOT NULL,
  source_type      VARCHAR(30)  NOT NULL,
  target_type      VARCHAR(30)  NOT NULL,
  target_id        VARCHAR(100) NULL,
  risk_level       VARCHAR(20)  NOT NULL,
  action           VARCHAR(20)  NOT NULL,
  reason_code      VARCHAR(100) NOT NULL,
  masked_text      LONGTEXT     NULL,
  operator_user_id BIGINT       NOT NULL DEFAULT 0,
  created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_moderation_trace (trace_id),
  INDEX idx_moderation_target (target_type, target_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS content_reports (
  id               BIGINT AUTO_INCREMENT PRIMARY KEY,
  reporter_user_id BIGINT       NOT NULL,
  target_type      VARCHAR(20)  NOT NULL,
  target_id        VARCHAR(100) NOT NULL,
  reason_code      VARCHAR(50)  NOT NULL,
  detail           VARCHAR(1000) NULL,
  status           VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
  latest_action    VARCHAR(50)  NOT NULL DEFAULT 'NONE',
  created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  closed_at        DATETIME     NULL,
  UNIQUE KEY uk_content_reports_dedupe (reporter_user_id, target_type, target_id, reason_code),
  INDEX idx_content_reports_reporter (reporter_user_id, created_at),
  INDEX idx_content_reports_target (target_type, target_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS content_report_actions (
  id               BIGINT AUTO_INCREMENT PRIMARY KEY,
  report_id        BIGINT        NOT NULL,
  operator_user_id BIGINT        NOT NULL,
  decision         VARCHAR(20)   NOT NULL,
  action           VARCHAR(50)   NOT NULL,
  comment          VARCHAR(1000) NULL,
  created_at       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_content_report_actions_report (report_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO moderation_policies(policy_key, policy_value, description, updated_by, updated_at)
SELECT 'ai_input_enabled', 'true', 'AI 输入审查开关', 0, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM moderation_policies WHERE policy_key = 'ai_input_enabled');

INSERT INTO moderation_policies(policy_key, policy_value, description, updated_by, updated_at)
SELECT 'ai_output_enabled', 'true', 'AI 输出审查开关', 0, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM moderation_policies WHERE policy_key = 'ai_output_enabled');

INSERT INTO moderation_policies(policy_key, policy_value, description, updated_by, updated_at)
SELECT 'community_strict_review_enabled', 'true', '社区内容审查开关', 0, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM moderation_policies WHERE policy_key = 'community_strict_review_enabled');

INSERT INTO moderation_policies(policy_key, policy_value, description, updated_by, updated_at)
SELECT 'auto_hide_report_threshold', '3', '举报自动隐藏阈值', 0, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM moderation_policies WHERE policy_key = 'auto_hide_report_threshold');
