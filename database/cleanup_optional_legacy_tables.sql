-- Optional cleanup for legacy raw tables
-- Safe strategy: rename to legacy_* instead of DROP
-- Run manually only when you confirm those tables are unused by your workflow.

USE video_analysis;
SET @db := DATABASE();

-- bilibili_video_raw -> legacy_bilibili_video_raw
SET @tbl_exists := (
  SELECT COUNT(*)
  FROM information_schema.TABLES
  WHERE TABLE_SCHEMA = @db
    AND TABLE_NAME = 'bilibili_video_raw'
);
SET @legacy_exists := (
  SELECT COUNT(*)
  FROM information_schema.TABLES
  WHERE TABLE_SCHEMA = @db
    AND TABLE_NAME = 'legacy_bilibili_video_raw'
);
SET @sql := IF(
  @tbl_exists = 1 AND @legacy_exists = 0,
  'RENAME TABLE bilibili_video_raw TO legacy_bilibili_video_raw',
  'SELECT ''skip bilibili_video_raw'''
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- douyin_live_raw -> legacy_douyin_live_raw
SET @tbl_exists := (
  SELECT COUNT(*)
  FROM information_schema.TABLES
  WHERE TABLE_SCHEMA = @db
    AND TABLE_NAME = 'douyin_live_raw'
);
SET @legacy_exists := (
  SELECT COUNT(*)
  FROM information_schema.TABLES
  WHERE TABLE_SCHEMA = @db
    AND TABLE_NAME = 'legacy_douyin_live_raw'
);
SET @sql := IF(
  @tbl_exists = 1 AND @legacy_exists = 0,
  'RENAME TABLE douyin_live_raw TO legacy_douyin_live_raw',
  'SELECT ''skip douyin_live_raw'''
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

