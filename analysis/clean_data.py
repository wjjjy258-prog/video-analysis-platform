from __future__ import annotations

import argparse

from db_util import execute_sql


def main() -> None:
    execute_sql("DROP TABLE IF EXISTS video_clean")
    execute_sql("DROP TABLE IF EXISTS user_behavior_clean")

    video_sql = """
        CREATE TABLE video_clean AS
        SELECT DISTINCT
            id,
            LEFT(COALESCE(NULLIF(TRIM(title), ''), '未知标题'), 255) AS title,
            LEFT(COALESCE(NULLIF(TRIM(author), ''), '未知作者'), 100) AS author,
            LEFT(COALESCE(NULLIF(TRIM(category), ''), '其他'), 60) AS category,
            GREATEST(COALESCE(play_count, 0), 0) AS play_count,
            GREATEST(COALESCE(like_count, 0), 0) AS like_count,
            GREATEST(COALESCE(comment_count, 0), 0) AS comment_count,
            COALESCE(publish_time, NOW()) AS publish_time
        FROM video
        WHERE id IS NOT NULL
    """

    behavior_sql = """
        CREATE TABLE user_behavior_clean AS
        SELECT DISTINCT
            id,
            user_id,
            video_id,
            CASE
                WHEN LOWER(action) IN ('play', 'like', 'comment') THEN LOWER(action)
                ELSE 'play'
            END AS action,
            COALESCE(time, NOW()) AS time
        FROM user_behavior
        WHERE user_id IS NOT NULL AND user_id > 0
          AND video_id IS NOT NULL AND video_id > 0
    """

    execute_sql(video_sql)
    execute_sql(behavior_sql)
    print("清洗完成：已生成 video_clean 和 user_behavior_clean。")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="数据清洗脚本")
    parser.parse_args()
    main()
