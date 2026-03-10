-- Light performance upgrade (no business logic change)
-- Target: video_analysis / MySQL 8.x

USE video_analysis;

SET @db := DATABASE();

-- 1) import_job list query optimization
SET @idx_exists := (
  SELECT COUNT(*)
  FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = @db
    AND TABLE_NAME = 'import_job'
    AND INDEX_NAME = 'idx_import_job_tenant_status_started'
);
SET @sql := IF(
  @idx_exists = 0,
  'CREATE INDEX idx_import_job_tenant_status_started ON import_job(tenant_user_id, status, started_at)',
  'SELECT ''skip idx_import_job_tenant_status_started'''
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 3) user behavior profile aggregation optimization
SET @idx_exists := (
  SELECT COUNT(*)
  FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = @db
    AND TABLE_NAME = 'user_behavior'
    AND INDEX_NAME = 'idx_behavior_tenant_user_video'
);
SET @sql := IF(
  @idx_exists = 0,
  'CREATE INDEX idx_behavior_tenant_user_video ON user_behavior(tenant_user_id, user_id, video_id)',
  'SELECT ''skip idx_behavior_tenant_user_video'''
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 4) dashboard overview cache table
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

-- 2) video import trace query optimization
SET @idx_exists := (
  SELECT COUNT(*)
  FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = @db
    AND TABLE_NAME = 'video'
    AND INDEX_NAME = 'idx_video_tenant_import_type_time'
);
SET @sql := IF(
  @idx_exists = 0,
  'CREATE INDEX idx_video_tenant_import_type_time ON video(tenant_user_id, import_type, import_time)',
  'SELECT ''skip idx_video_tenant_import_type_time'''
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
