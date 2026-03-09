from __future__ import annotations

import argparse
from datetime import date

from db_util import execute_many, execute_sql, query_all

try:
    from pyspark.sql import SparkSession
    from pyspark.sql import functions as F
except Exception:  # pragma: no cover
    SparkSession = None
    F = None


def run_sql_mode() -> int:
    sql = """
        INSERT INTO video_statistics (video_id, stat_date, daily_play, daily_like, daily_comment)
        SELECT
            video_id,
            DATE(time) AS stat_date,
            SUM(CASE WHEN action = 'play' THEN 1 ELSE 0 END) AS daily_play,
            SUM(CASE WHEN action = 'like' THEN 1 ELSE 0 END) AS daily_like,
            SUM(CASE WHEN action = 'comment' THEN 1 ELSE 0 END) AS daily_comment
        FROM user_behavior
        GROUP BY video_id, DATE(time)
        ON DUPLICATE KEY UPDATE
            daily_play = VALUES(daily_play),
            daily_like = VALUES(daily_like),
            daily_comment = VALUES(daily_comment)
    """
    return execute_sql(sql)


def run_spark_mode() -> int:
    rows = query_all("SELECT video_id, action, time FROM user_behavior")
    if not rows:
        return 0

    spark = SparkSession.builder.appName("video_statistics").master("local[*]").getOrCreate()
    try:
        df = spark.createDataFrame(rows)
        stat_df = (
            df.withColumn("stat_date", F.to_date(F.col("time")))
            .groupBy("video_id", "stat_date")
            .pivot("action", ["play", "like", "comment"])
            .count()
            .fillna(0)
            .withColumnRenamed("play", "daily_play")
            .withColumnRenamed("like", "daily_like")
            .withColumnRenamed("comment", "daily_comment")
        )
        result = stat_df.collect()
    finally:
        spark.stop()

    upsert_sql = """
        INSERT INTO video_statistics (video_id, stat_date, daily_play, daily_like, daily_comment)
        VALUES (%s, %s, %s, %s, %s)
        ON DUPLICATE KEY UPDATE
            daily_play = VALUES(daily_play),
            daily_like = VALUES(daily_like),
            daily_comment = VALUES(daily_comment)
    """
    upsert_rows = []
    for row in result:
        stat_date = row["stat_date"]
        if isinstance(stat_date, date):
            stat_date = stat_date.strftime("%Y-%m-%d")
        upsert_rows.append(
            (
                int(row["video_id"]),
                stat_date,
                int(row["daily_play"]),
                int(row["daily_like"]),
                int(row["daily_comment"]),
            )
        )
    return execute_many(upsert_sql, upsert_rows)


def main() -> None:
    parser = argparse.ArgumentParser(description="视频统计分析脚本")
    parser.add_argument("--use-spark", action="store_true", help="优先使用 Spark 统计")
    args = parser.parse_args()

    if args.use_spark:
        if SparkSession is None:
            print("未安装 pyspark，自动回退 SQL 统计模式。")
            count = run_sql_mode()
            print(f"统计完成（SQL 模式），影响行数: {count}")
            return
        count = run_spark_mode()
        print(f"统计完成（Spark 模式），写入行数: {count}")
        return

    count = run_sql_mode()
    print(f"统计完成（SQL 模式），影响行数: {count}")


if __name__ == "__main__":
    main()
