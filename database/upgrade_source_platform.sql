USE video_analysis;

ALTER TABLE video
  ADD COLUMN IF NOT EXISTS source_platform VARCHAR(32) NOT NULL DEFAULT 'seed' AFTER author,
  ADD COLUMN IF NOT EXISTS source_url VARCHAR(1000) NULL AFTER source_platform,
  ADD COLUMN IF NOT EXISTS import_type VARCHAR(32) NOT NULL DEFAULT 'seed' AFTER publish_time,
  ADD COLUMN IF NOT EXISTS source_file VARCHAR(260) NULL AFTER import_type,
  ADD COLUMN IF NOT EXISTS import_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP AFTER source_file,
  ADD COLUMN IF NOT EXISTS data_quality_score DECIMAL(5,2) NOT NULL DEFAULT 0.00 AFTER import_time,
  ADD COLUMN IF NOT EXISTS import_job_id BIGINT NULL AFTER data_quality_score,
  ADD COLUMN IF NOT EXISTS dedupe_key VARCHAR(128) NULL AFTER id;

CREATE TABLE IF NOT EXISTS import_job (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
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
  INDEX idx_import_job_started_at (started_at)
);

UPDATE video
SET source_platform = CASE
    WHEN id >= 9000 THEN 'mock'
    ELSE 'seed'
END
WHERE source_platform IS NULL OR TRIM(source_platform) = '';

UPDATE video
SET import_type = CASE
    WHEN COALESCE(source_platform, '') IN ('seed', 'mock') THEN COALESCE(source_platform, 'seed')
    ELSE 'import'
END
WHERE import_type IS NULL OR TRIM(import_type) = '';

UPDATE video
SET import_time = COALESCE(import_time, publish_time, NOW());

UPDATE video
SET data_quality_score = ROUND(
      (CASE WHEN title IS NOT NULL AND TRIM(title) <> '' THEN 22 ELSE 0 END)
    + (CASE WHEN author IS NOT NULL AND TRIM(author) <> '' THEN 14 ELSE 0 END)
    + (CASE WHEN source_platform IS NOT NULL AND TRIM(source_platform) <> '' THEN 10 ELSE 0 END)
    + (CASE WHEN play_count > 0 THEN 20 ELSE 0 END)
    + (CASE WHEN like_count >= 0 THEN 10 ELSE 0 END)
    + (CASE WHEN comment_count >= 0 THEN 10 ELSE 0 END)
    + (CASE WHEN publish_time IS NOT NULL THEN 9 ELSE 0 END)
    + (CASE WHEN source_url IS NOT NULL AND TRIM(source_url) <> '' THEN 5 ELSE 0 END),
    2
)
WHERE data_quality_score IS NULL OR data_quality_score <= 0;

UPDATE video
SET dedupe_key = SHA1(CONCAT_WS('|',
    LOWER(COALESCE(source_platform, 'unknown')),
    LOWER(TRIM(COALESCE(source_url, ''))),
    LOWER(TRIM(COALESCE(title, ''))),
    LOWER(TRIM(COALESCE(author, '')))
))
WHERE dedupe_key IS NULL OR TRIM(dedupe_key) = '';

DELETE v1 FROM video v1
JOIN video v2 ON v1.dedupe_key = v2.dedupe_key AND v1.id > v2.id
WHERE v1.dedupe_key IS NOT NULL AND TRIM(v1.dedupe_key) <> '';

CREATE UNIQUE INDEX uk_video_dedupe ON video(dedupe_key);
CREATE INDEX idx_video_import_time ON video(import_time);
