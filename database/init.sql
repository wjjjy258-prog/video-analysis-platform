CREATE DATABASE IF NOT EXISTS video_analysis
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

USE video_analysis;

CREATE TABLE IF NOT EXISTS app_user (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  username VARCHAR(64) NOT NULL,
  password_hash VARCHAR(128) NOT NULL,
  password_salt VARCHAR(64) NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_app_user_username (username)
);

CREATE TABLE IF NOT EXISTS app_session (
  token VARCHAR(96) PRIMARY KEY,
  user_id BIGINT NOT NULL,
  created_at DATETIME NOT NULL,
  last_active_at DATETIME NOT NULL,
  expires_at DATETIME NOT NULL,
  is_revoked TINYINT(1) NOT NULL DEFAULT 0,
  revoked_at DATETIME DEFAULT NULL,
  INDEX idx_app_session_user (user_id),
  INDEX idx_app_session_expire (expires_at)
);

CREATE TABLE IF NOT EXISTS video (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_user_id BIGINT NOT NULL DEFAULT 1,
  dedupe_key VARCHAR(128) DEFAULT NULL,
  platform_video_id VARCHAR(128) DEFAULT NULL,
  title VARCHAR(255) NOT NULL,
  author VARCHAR(100) NOT NULL,
  source_platform VARCHAR(32) NOT NULL DEFAULT 'unknown',
  source_url VARCHAR(1000) DEFAULT NULL,
  category VARCHAR(60) NOT NULL,
  play_count BIGINT NOT NULL DEFAULT 0,
  like_count BIGINT NOT NULL DEFAULT 0,
  comment_count BIGINT NOT NULL DEFAULT 0,
  share_count BIGINT NOT NULL DEFAULT 0,
  favorite_count BIGINT NOT NULL DEFAULT 0,
  duration_sec BIGINT NOT NULL DEFAULT 0,
  publish_time DATETIME NOT NULL,
  import_type VARCHAR(32) NOT NULL DEFAULT 'import',
  source_file VARCHAR(260) DEFAULT NULL,
  import_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  data_quality_score DECIMAL(5,2) NOT NULL DEFAULT 0.00,
  quality_level VARCHAR(16) NOT NULL DEFAULT 'GOOD',
  ai_confidence DECIMAL(5,4) NOT NULL DEFAULT 0.0000,
  tags_json TEXT DEFAULT NULL,
  extra_json TEXT DEFAULT NULL,
  import_job_id BIGINT DEFAULT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_video_tenant_dedupe (tenant_user_id, dedupe_key),
  INDEX idx_video_import_time (import_time),
  INDEX idx_video_tenant_platform (tenant_user_id, source_platform),
  INDEX idx_video_tenant_platform_video_id (tenant_user_id, source_platform, platform_video_id),
  INDEX idx_video_tenant_import_time (tenant_user_id, import_time),
  INDEX idx_video_tenant_category (tenant_user_id, category),
  INDEX idx_video_tenant_play_like (tenant_user_id, play_count, like_count),
  INDEX idx_video_tenant_quality (tenant_user_id, quality_level, data_quality_score)
);

CREATE TABLE IF NOT EXISTS `user` (
  user_id BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_user_id BIGINT NOT NULL DEFAULT 1,
  user_name VARCHAR(100) NOT NULL,
  fans BIGINT NOT NULL DEFAULT 0,
  `follow` BIGINT NOT NULL DEFAULT 0,
  `level` INT NOT NULL DEFAULT 1,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_user_tenant (tenant_user_id)
);

CREATE TABLE IF NOT EXISTS comment (
  comment_id BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_user_id BIGINT NOT NULL DEFAULT 1,
  video_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  content VARCHAR(1000) NOT NULL,
  like_count BIGINT NOT NULL DEFAULT 0,
  time DATETIME NOT NULL,
  INDEX idx_comment_video (video_id),
  INDEX idx_comment_user (user_id),
  INDEX idx_comment_tenant (tenant_user_id),
  INDEX idx_comment_tenant_video (tenant_user_id, video_id)
);

CREATE TABLE IF NOT EXISTS user_behavior (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_user_id BIGINT NOT NULL DEFAULT 1,
  user_id BIGINT NOT NULL,
  video_id BIGINT NOT NULL,
  action VARCHAR(32) NOT NULL,
  time DATETIME NOT NULL,
  INDEX idx_behavior_user (user_id),
  INDEX idx_behavior_video (video_id),
  INDEX idx_behavior_time (time),
  INDEX idx_behavior_tenant_time (tenant_user_id, time),
  INDEX idx_behavior_tenant_video (tenant_user_id, video_id),
  INDEX idx_behavior_tenant_user_action_time (tenant_user_id, user_id, action, time),
  INDEX idx_behavior_tenant_user_video (tenant_user_id, user_id, video_id)
);

CREATE TABLE IF NOT EXISTS video_statistics (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_user_id BIGINT NOT NULL DEFAULT 1,
  video_id BIGINT NOT NULL,
  stat_date DATE NOT NULL,
  daily_play BIGINT NOT NULL DEFAULT 0,
  daily_like BIGINT NOT NULL DEFAULT 0,
  daily_comment BIGINT NOT NULL DEFAULT 0,
  UNIQUE KEY uk_video_date_tenant (tenant_user_id, video_id, stat_date),
  INDEX idx_stat_date (stat_date),
  INDEX idx_stat_tenant_date (tenant_user_id, stat_date)
);

CREATE TABLE IF NOT EXISTS user_interest_result (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_user_id BIGINT NOT NULL DEFAULT 1,
  user_id BIGINT NOT NULL,
  cluster_id INT NOT NULL,
  cluster_label VARCHAR(64) NOT NULL,
  favorite_category VARCHAR(64) NOT NULL,
  updated_at DATETIME NOT NULL,
  UNIQUE KEY uk_user_interest_tenant (tenant_user_id, user_id)
);

CREATE TABLE IF NOT EXISTS import_job (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_user_id BIGINT NOT NULL DEFAULT 1,
  import_type VARCHAR(32) NOT NULL,
  source_platform VARCHAR(32) DEFAULT NULL,
  source_file VARCHAR(260) DEFAULT NULL,
  source_count INT NOT NULL DEFAULT 0,
  success_count INT NOT NULL DEFAULT 0,
  started_at DATETIME NOT NULL,
  finished_at DATETIME DEFAULT NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'RUNNING',
  notes VARCHAR(500) DEFAULT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_import_job_started_at (started_at),
  INDEX idx_import_job_tenant (tenant_user_id)
);

CREATE TABLE IF NOT EXISTS import_reject_record (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_user_id BIGINT NOT NULL DEFAULT 1,
  import_job_id BIGINT DEFAULT NULL,
  source_platform VARCHAR(32) NOT NULL DEFAULT 'unknown',
  source_file VARCHAR(260) DEFAULT NULL,
  reject_reason VARCHAR(300) NOT NULL,
  suggest_fix VARCHAR(300) DEFAULT NULL,
  raw_excerpt TEXT,
  quality_score DECIMAL(5,2) NOT NULL DEFAULT 0.00,
  ai_used TINYINT(1) NOT NULL DEFAULT 0,
  ai_confidence DECIMAL(5,4) NOT NULL DEFAULT 0.0000,
  import_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_reject_tenant_time (tenant_user_id, import_time),
  INDEX idx_reject_tenant_job (tenant_user_id, import_job_id)
);

CREATE TABLE IF NOT EXISTS tenant_overview_cache (
  tenant_user_id BIGINT NOT NULL,
  source_platform VARCHAR(32) NOT NULL DEFAULT '__all__',
  video_count BIGINT NOT NULL DEFAULT 0,
  user_count BIGINT NOT NULL DEFAULT 0,
  comment_count BIGINT NOT NULL DEFAULT 0,
  behavior_count BIGINT NOT NULL DEFAULT 0,
  total_play_count BIGINT NOT NULL DEFAULT 0,
  source_platform_count INT NOT NULL DEFAULT 0,
  refreshed_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (tenant_user_id, source_platform),
  INDEX idx_tenant_overview_refreshed (refreshed_at)
);

INSERT INTO app_user (id, username, password_hash, password_salt, created_at, updated_at)
VALUES (1, 'demo', '81a30b2d68b8c5b25f10389a8ef153491b6b9d4e4e30fac1fd859c05d9c7f0c0', 'demo_salt_2026', NOW(), NOW())
ON DUPLICATE KEY UPDATE
  username = VALUES(username),
  password_hash = VALUES(password_hash),
  password_salt = VALUES(password_salt),
  updated_at = NOW();
