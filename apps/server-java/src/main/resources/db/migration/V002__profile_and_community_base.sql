-- T-BIZ-002 第一阶段：用户画像与社区基础表。
-- 说明：本次只落最小持久化层，为后续 profile/community API 提供稳定数据基础。

-- 学生画像扩展表（含冷启动字段）
CREATE TABLE IF NOT EXISTS student_profiles (
  id              BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id         BIGINT       NOT NULL,
  major           VARCHAR(100) NULL,
  grade           VARCHAR(20)  NULL,
  target_position VARCHAR(100) NULL,
  skill_tags      VARCHAR(512) NULL COMMENT '逗号分隔',
  self_intro      TEXT         NULL,
  created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_student_profile_user (user_id),
  INDEX idx_student_profile_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 学生动态画像快照表
CREATE TABLE IF NOT EXISTS student_portrait_snapshots (
  id              BIGINT AUTO_INCREMENT PRIMARY KEY,
  student_user_id BIGINT       NOT NULL,
  portrait_tags   JSON         NOT NULL,
  evidence        JSON         NOT NULL,
  updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_portrait_student (student_user_id),
  INDEX idx_portrait_updated_at (updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 社区帖子主表
CREATE TABLE IF NOT EXISTS posts (
  id                       BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id                  BIGINT       NOT NULL,
  title                    VARCHAR(200) NOT NULL,
  content                  TEXT         NOT NULL,
  tags                     VARCHAR(255) NULL COMMENT '逗号分隔',
  moderation_status        VARCHAR(20)  NOT NULL DEFAULT 'PASS' COMMENT 'PASS|REVIEW|BLOCK',
  risk_level               VARCHAR(20)  NOT NULL DEFAULT 'LOW' COMMENT 'LOW|MEDIUM|HIGH|CRITICAL',
  last_moderation_event_id BIGINT       NULL,
  is_deleted               TINYINT(1)   NOT NULL DEFAULT 0,
  created_at               DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at               DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_posts_user_created (user_id, created_at),
  INDEX idx_posts_moderation_created (moderation_status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 社区评论表（支持 AI 首答标记）
CREATE TABLE IF NOT EXISTS comments (
  id                       BIGINT AUTO_INCREMENT PRIMARY KEY,
  post_id                  BIGINT      NOT NULL,
  user_id                  BIGINT      NOT NULL,
  content                  TEXT        NOT NULL,
  is_ai                    TINYINT(1)  NOT NULL DEFAULT 0,
  moderation_status        VARCHAR(20) NOT NULL DEFAULT 'PASS' COMMENT 'PASS|REVIEW|BLOCK',
  risk_level               VARCHAR(20) NOT NULL DEFAULT 'LOW' COMMENT 'LOW|MEDIUM|HIGH|CRITICAL',
  last_moderation_event_id BIGINT      NULL,
  is_deleted               TINYINT(1)  NOT NULL DEFAULT 0,
  created_at               DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at               DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_comments_post_created (post_id, created_at),
  INDEX idx_comments_user_created (user_id, created_at),
  INDEX idx_comments_moderation_created (moderation_status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 帖子点赞关系表
CREATE TABLE IF NOT EXISTS post_likes (
  id         BIGINT AUTO_INCREMENT PRIMARY KEY,
  post_id    BIGINT    NOT NULL,
  user_id    BIGINT    NOT NULL,
  created_at DATETIME  NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_post_likes_post_user (post_id, user_id),
  INDEX idx_post_likes_user_created (user_id, created_at),
  INDEX idx_post_likes_post_created (post_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
