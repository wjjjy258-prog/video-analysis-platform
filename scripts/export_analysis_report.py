from __future__ import annotations

import argparse
import os
import csv
from datetime import datetime
from pathlib import Path

import mysql.connector


def table_exists(cursor, table_name: str) -> bool:
    cursor.execute(
        """
        SELECT COUNT(*) AS cnt
        FROM information_schema.TABLES
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = %s
        """,
        (table_name,),
    )
    row = cursor.fetchone() or {"cnt": 0}
    return int(row.get("cnt", 0)) > 0


def column_exists(cursor, table_name: str, column_name: str) -> bool:
    cursor.execute(
        """
        SELECT COUNT(*) AS cnt
        FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = %s AND COLUMN_NAME = %s
        """,
        (table_name, column_name),
    )
    row = cursor.fetchone() or {"cnt": 0}
    return int(row.get("cnt", 0)) > 0


def ensure_output_dir(output_dir: str | None) -> Path:
    if output_dir:
        path = Path(output_dir).expanduser().resolve()
    else:
        path = (Path(__file__).resolve().parent.parent / "analysis" / "reports").resolve()
    path.mkdir(parents=True, exist_ok=True)
    return path


def fetch_all(conn: mysql.connector.MySQLConnection) -> dict:
    cursor = conn.cursor(dictionary=True)
    try:
        has_import_job = table_exists(cursor, "import_job")
        cursor.execute(
            """
            SELECT
                (SELECT COUNT(*) FROM video) AS video_count,
                (SELECT COUNT(*) FROM `user`) AS user_count,
                (SELECT COUNT(*) FROM user_behavior) AS behavior_count,
                (SELECT COALESCE(SUM(play_count), 0) FROM video) AS total_play_count
            """
        )
        overview = cursor.fetchone() or {}
        if has_import_job:
            cursor.execute("SELECT COUNT(*) AS import_job_count FROM import_job")
            overview["import_job_count"] = (cursor.fetchone() or {}).get("import_job_count", 0)
        else:
            overview["import_job_count"] = 0

        cursor.execute(
            """
            SELECT COALESCE(source_platform, 'unknown') AS source_platform,
                   COUNT(*) AS video_count,
                   COALESCE(SUM(play_count), 0) AS total_play,
                   COALESCE(SUM(like_count), 0) AS total_like,
                   COALESCE(SUM(comment_count), 0) AS total_comment,
                   ROUND(CASE WHEN COUNT(*) = 0 THEN 0 ELSE COALESCE(SUM(play_count), 0) / COUNT(*) END, 2) AS avg_play_per_video,
                   ROUND(CASE WHEN COALESCE(SUM(play_count), 0) = 0 THEN 0
                              ELSE (COALESCE(SUM(like_count), 0) + COALESCE(SUM(comment_count), 0)) * 1000 / COALESCE(SUM(play_count), 0)
                         END, 2) AS engagement_per_thousand_play
            FROM video
            GROUP BY COALESCE(source_platform, 'unknown')
            ORDER BY engagement_per_thousand_play DESC, total_play DESC
            """
        )
        platform = cursor.fetchall()

        cursor.execute(
            """
            SELECT category,
                   COUNT(*) AS video_count,
                   COALESCE(SUM(play_count), 0) AS total_play,
                   COALESCE(SUM(like_count), 0) AS total_like,
                   COALESCE(SUM(comment_count), 0) AS total_comment,
                   ROUND(CASE WHEN COALESCE(SUM(play_count), 0) = 0 THEN 0
                              ELSE (COALESCE(SUM(like_count), 0) + COALESCE(SUM(comment_count), 0)) / COALESCE(SUM(play_count), 0)
                         END, 6) AS engagement_rate
            FROM video
            GROUP BY category
            ORDER BY total_play DESC
            """
        )
        category = cursor.fetchall()

        has_import_type = column_exists(cursor, "video", "import_type")
        has_source_file = column_exists(cursor, "video", "source_file")
        has_quality = column_exists(cursor, "video", "data_quality_score")
        has_import_time = column_exists(cursor, "video", "import_time")

        if has_import_type and has_source_file and has_quality and has_import_time:
            cursor.execute(
                """
                SELECT id, title, source_platform, import_type, source_file, data_quality_score, import_time
                FROM video
                ORDER BY import_time DESC, id DESC
                LIMIT 100
                """
            )
            source_trace = cursor.fetchall()
        else:
            cursor.execute(
                """
                SELECT id,
                       title,
                       COALESCE(source_platform, 'unknown') AS source_platform,
                       '' AS import_type,
                       '' AS source_file,
                       0 AS data_quality_score,
                       publish_time AS import_time
                FROM video
                ORDER BY publish_time DESC, id DESC
                LIMIT 100
                """
            )
            source_trace = cursor.fetchall()
    finally:
        cursor.close()

    return {
        "overview": overview,
        "platform": platform,
        "category": category,
        "source_trace": source_trace,
    }


def write_csv(path: Path, rows: list[dict]) -> None:
    if not rows:
        path.write_text("", encoding="utf-8")
        return
    with path.open("w", encoding="utf-8-sig", newline="") as f:
        writer = csv.DictWriter(f, fieldnames=list(rows[0].keys()))
        writer.writeheader()
        writer.writerows(rows)


def write_overview_csv(path: Path, row: dict) -> None:
    with path.open("w", encoding="utf-8-sig", newline="") as f:
        writer = csv.DictWriter(f, fieldnames=list(row.keys()))
        writer.writeheader()
        writer.writerow(row)


def write_summary_md(path: Path, data: dict) -> None:
    overview = data["overview"]
    platform = data["platform"]
    lines = [
        "# 骞冲彴鍒嗘瀽瀵煎嚭鎶ュ憡",
        "",
        f"- 鐢熸垚鏃堕棿: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}",
        f"- 瑙嗛鎬绘暟: {overview.get('video_count', 0)}",
        f"- 鐢ㄦ埛鎬绘暟: {overview.get('user_count', 0)}",
        f"- 琛屼负鏃ュ織鏁? {overview.get('behavior_count', 0)}",
        f"- 瀵煎叆浠诲姟鏁? {overview.get('import_job_count', 0)}",
        f"- 绱鎾斁閲? {overview.get('total_play_count', 0)}",
        "",
        "## 骞冲彴鏁堢巼鎺掑悕锛堟瘡鍗冩挱鏀句簰鍔級",
    ]
    for row in platform:
        lines.append(
            f"- {row.get('source_platform')}: {row.get('engagement_per_thousand_play')} "
            f"(瑙嗛鏁?{row.get('video_count')}, 鎬绘挱鏀?{row.get('total_play')})"
        )
    lines.append("")
    lines.append("鎶ュ憡鍖呭惈 4 涓?CSV 鏂囦欢锛屽彲鐩存帴鐢ㄤ簬璁烘枃鍥捐〃鎴栫瓟杈╂潗鏂欍€?)
    path.write_text("\n".join(lines) + "\n", encoding="utf-8")


def main() -> None:
    parser = argparse.ArgumentParser(description="瀵煎嚭骞冲彴鍒嗘瀽鎶ュ憡涓?CSV + Markdown")
    parser.add_argument("--mysql-host", default="localhost")
    parser.add_argument("--mysql-port", type=int, default=3306)
    parser.add_argument("--mysql-user", default="root")
    parser.add_argument("--mysql-password", default=os.getenv("MYSQL_PASSWORD", ""))
    parser.add_argument("--mysql-db", default="video_analysis")
    parser.add_argument("--output-dir", default="", help="杈撳嚭鐩綍锛岄粯璁?analysis/reports")
    args = parser.parse_args()

    output_dir = ensure_output_dir(args.output_dir or None)
    ts = datetime.now().strftime("%Y%m%d_%H%M%S")

    conn = mysql.connector.connect(
        host=args.mysql_host,
        port=args.mysql_port,
        user=args.mysql_user,
        password=args.mysql_password,
        database=args.mysql_db,
        autocommit=True,
    )
    try:
        data = fetch_all(conn)
    finally:
        conn.close()

    overview_csv = output_dir / f"overview_{ts}.csv"
    platform_csv = output_dir / f"platform_benchmark_{ts}.csv"
    category_csv = output_dir / f"category_engagement_{ts}.csv"
    trace_csv = output_dir / f"source_trace_{ts}.csv"
    summary_md = output_dir / f"analysis_report_{ts}.md"

    write_overview_csv(overview_csv, data["overview"])
    write_csv(platform_csv, data["platform"])
    write_csv(category_csv, data["category"])
    write_csv(trace_csv, data["source_trace"])
    write_summary_md(summary_md, data)

    (output_dir / "latest_overview.csv").write_bytes(overview_csv.read_bytes())
    (output_dir / "latest_platform_benchmark.csv").write_bytes(platform_csv.read_bytes())
    (output_dir / "latest_category_engagement.csv").write_bytes(category_csv.read_bytes())
    (output_dir / "latest_source_trace.csv").write_bytes(trace_csv.read_bytes())
    (output_dir / "latest_analysis_report.md").write_bytes(summary_md.read_bytes())

    print(f"[EXPORT] {summary_md}")
    print(f"[EXPORT] {platform_csv}")
    print(f"[EXPORT] {category_csv}")
    print(f"[EXPORT] {trace_csv}")


if __name__ == "__main__":
    main()


