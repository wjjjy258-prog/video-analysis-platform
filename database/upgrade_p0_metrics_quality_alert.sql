-- P0 migration: metric dictionary + import quality/lineage + alert center
-- Apply on database: video_analysis
-- Compatible target: MySQL 8.x

USE video_analysis;

-- 1) 指标口径中心
CREATE TABLE IF NOT EXISTS metric_definition (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  metric_code VARCHAR(64) NOT NULL,
  metric_name VARCHAR(100) NOT NULL,
  formula_text VARCHAR(500) NOT NULL,
  unit VARCHAR(32) DEFAULT NULL,
  description VARCHAR(1000) DEFAULT NULL,
  platform_scope VARCHAR(255) NOT NULL DEFAULT 'all',
  version VARCHAR(32) NOT NULL DEFAULT 'v1',
  enabled TINYINT(1) NOT NULL DEFAULT 1,
  created_by VARCHAR(64) NOT NULL DEFAULT 'system',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_metric_code_version (metric_code, version),
  INDEX idx_metric_enabled (enabled),
  INDEX idx_metric_scope (platform_scope)
);

INSERT INTO metric_definition
  (metric_code, metric_name, formula_text, unit, description, platform_scope, version, enabled, created_by)
VALUES
  ('play_count', '播放量', 'sum(video.play_count)', '次', '统计周期内视频播放总次数。', 'all', 'v1', 1, 'system'),
  ('like_count', '点赞量', 'sum(video.like_count)', '次', '统计周期内视频点赞总次数。', 'all', 'v1', 1, 'system'),
  ('comment_count', '评论量', 'sum(video.comment_count)', '条', '统计周期内视频评论总数。', 'all', 'v1', 1, 'system'),
  ('interaction_rate', '互动率', '(点赞量 + 评论量 + 分享量) / max(播放量, 1)', '%', '反映播放到互动的转化效率。', 'all', 'v1', 1, 'system'),
  ('avg_play_per_video', '单条平均播放', '播放量 / max(视频总数, 1)', '次/条', '平均每条视频播放表现。', 'all', 'v1', 1, 'system'),
  ('quality_score', '数据质量分', 'avg(video.data_quality_score)', '分', '导入记录质量均值，用于评估数据可信度。', 'all', 'v1', 1, 'system')
ON DUPLICATE KEY UPDATE
  metric_name = VALUES(metric_name),
  formula_text = VALUES(formula_text),
  unit = VALUES(unit),
  description = VALUES(description),
  platform_scope = VALUES(platform_scope),
  enabled = VALUES(enabled),
  updated_at = CURRENT_TIMESTAMP;

-- 2) import_job 增强（质量审计字段）
ALTER TABLE import_job
  ADD COLUMN IF NOT EXISTS failed_count INT NOT NULL DEFAULT 0 AFTER success_count,
  ADD COLUMN IF NOT EXISTS duration_ms BIGINT DEFAULT NULL AFTER finished_at,
  ADD COLUMN IF NOT EXISTS error_summary VARCHAR(1000) DEFAULT NULL AFTER notes,
  ADD COLUMN IF NOT EXISTS updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP AFTER created_at;

-- 3) 导入错误明细（行级）
CREATE TABLE IF NOT EXISTS import_job_error (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_user_id BIGINT NOT NULL DEFAULT 1,
  job_id BIGINT NOT NULL,
  line_no INT DEFAULT NULL,
  raw_excerpt TEXT,
  error_code VARCHAR(64) NOT NULL,
  error_message VARCHAR(500) NOT NULL,
  suggestion VARCHAR(500) DEFAULT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_import_error_job (job_id),
  INDEX idx_import_error_tenant_job (tenant_user_id, job_id),
  CONSTRAINT fk_import_error_job
    FOREIGN KEY (job_id) REFERENCES import_job(id)
    ON DELETE CASCADE
);

-- 4) 数据血缘（可追溯来源）
CREATE TABLE IF NOT EXISTS data_lineage (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_user_id BIGINT NOT NULL DEFAULT 1,
  data_type VARCHAR(32) NOT NULL,
  data_id BIGINT NOT NULL,
  import_job_id BIGINT DEFAULT NULL,
  source_type VARCHAR(32) NOT NULL DEFAULT 'file_import',
  source_platform VARCHAR(32) NOT NULL DEFAULT 'unknown',
  source_ref VARCHAR(1000) DEFAULT NULL,
  source_file VARCHAR(260) DEFAULT NULL,
  parser_version VARCHAR(32) NOT NULL DEFAULT 'v1',
  normalize_version VARCHAR(32) NOT NULL DEFAULT 'v1',
  quality_score DECIMAL(5,2) NOT NULL DEFAULT 0.00,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_lineage_tenant_data (tenant_user_id, data_type, data_id),
  INDEX idx_lineage_job (import_job_id),
  INDEX idx_lineage_tenant_platform (tenant_user_id, source_platform),
  CONSTRAINT fk_lineage_import_job
    FOREIGN KEY (import_job_id) REFERENCES import_job(id)
    ON DELETE SET NULL
);

-- 5) 预警规则
CREATE TABLE IF NOT EXISTS alert_rule (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_user_id BIGINT NOT NULL DEFAULT 1,
  rule_name VARCHAR(120) NOT NULL,
  metric_code VARCHAR(64) NOT NULL,
  dimension_json VARCHAR(1000) DEFAULT NULL,
  operator VARCHAR(8) NOT NULL,
  threshold DECIMAL(18,6) NOT NULL,
  window_size INT NOT NULL DEFAULT 1,
  compare_type VARCHAR(32) NOT NULL DEFAULT 'day_over_day',
  cooldown_minutes INT NOT NULL DEFAULT 60,
  severity VARCHAR(16) NOT NULL DEFAULT 'MEDIUM',
  enabled TINYINT(1) NOT NULL DEFAULT 1,
  last_triggered_at DATETIME DEFAULT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_alert_rule_tenant_enabled (tenant_user_id, enabled),
  INDEX idx_alert_rule_tenant_metric (tenant_user_id, metric_code),
  UNIQUE KEY uk_alert_rule_tenant_name (tenant_user_id, rule_name)
);

-- 6) 预警事件
CREATE TABLE IF NOT EXISTS alert_event (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_user_id BIGINT NOT NULL DEFAULT 1,
  rule_id BIGINT NOT NULL,
  metric_code VARCHAR(64) NOT NULL,
  event_time DATETIME NOT NULL,
  current_value DECIMAL(18,6) DEFAULT NULL,
  baseline_value DECIMAL(18,6) DEFAULT NULL,
  change_ratio DECIMAL(10,4) DEFAULT NULL,
  severity VARCHAR(16) NOT NULL DEFAULT 'MEDIUM',
  message VARCHAR(500) NOT NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'NEW',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  read_at DATETIME DEFAULT NULL,
  INDEX idx_alert_event_tenant_status_time (tenant_user_id, status, event_time),
  INDEX idx_alert_event_rule (rule_id),
  CONSTRAINT fk_alert_event_rule
    FOREIGN KEY (rule_id) REFERENCES alert_rule(id)
    ON DELETE CASCADE
);

-- Optional seed rule for smoke test
INSERT INTO alert_rule
  (tenant_user_id, rule_name, metric_code, dimension_json, operator, threshold, window_size, compare_type, cooldown_minutes, severity, enabled)
VALUES
  (1, '日播放环比异常上涨', 'play_count', '{"platform":"all"}', '>', 0.50, 1, 'day_over_day', 120, 'HIGH', 0)
ON DUPLICATE KEY UPDATE
  metric_code = VALUES(metric_code),
  dimension_json = VALUES(dimension_json),
  operator = VALUES(operator),
  threshold = VALUES(threshold),
  window_size = VALUES(window_size),
  compare_type = VALUES(compare_type),
  cooldown_minutes = VALUES(cooldown_minutes),
  severity = VALUES(severity),
  updated_at = CURRENT_TIMESTAMP;

