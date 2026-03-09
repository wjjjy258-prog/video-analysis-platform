from __future__ import annotations

import argparse
import os
import csv
import hashlib
import random
from collections import defaultdict
from dataclasses import dataclass
from datetime import datetime, timedelta
from pathlib import Path
from typing import Any

import mysql.connector


@dataclass
class RowRecord:
    dataset_type: str
    source_file: str
    row_no: int
    company_id: str
    date_time: datetime
    uv_value: float
    comment_cnt: int
    incr_fans_cnt: float
    leave_ucnt: float
    fans_club_ucnt: float
    pay_order_gmv: float
    online_user_cnt: int
    watch_ucnt: float
    pay_cnt: float
    pay_ucnt: float
    natural_flow: str
    pay_flow: str


def stable_int(text: str, namespace: str) -> int:
    digest = hashlib.sha1(f"{namespace}:{text}".encode("utf-8")).hexdigest()
    value = int(digest[:15], 16)
    return value if value > 0 else 1


def sha1_hex(text: str) -> str:
    return hashlib.sha1(text.encode("utf-8")).hexdigest()


def parse_float(v: Any) -> float:
    if v is None:
        return 0.0
    txt = str(v).strip().replace(",", "").replace("锛?, "")
    if txt == "":
        return 0.0
    try:
        return float(txt)
    except Exception:
        return 0.0


def parse_int(v: Any) -> int:
    return int(parse_float(v))


def parse_dt(v: Any) -> datetime:
    txt = str(v or "").strip()
    if not txt:
        return datetime.now()
    for fmt in ("%Y-%m-%d %H:%M:%S", "%Y/%m/%d %H:%M:%S", "%Y-%m-%d %H:%M", "%Y/%m/%d %H:%M"):
        try:
            return datetime.strptime(txt, fmt)
        except Exception:
            pass
    try:
        return datetime.fromisoformat(txt.replace("Z", "+00:00"))
    except Exception:
        return datetime.now()


def locate_file(path_arg: str | None, fallback_name: str, search_root: Path) -> Path:
    if path_arg:
        p = Path(path_arg).expanduser().resolve()
        if not p.exists():
            raise FileNotFoundError(f"File not found: {p}")
        return p
    matches = list(search_root.rglob(fallback_name))
    if not matches:
        raise FileNotFoundError(f"Cannot find {fallback_name} under {search_root}")
    return matches[0]


def load_rows(path: Path, dataset_type: str, company_fallback: str) -> list[RowRecord]:
    rows: list[RowRecord] = []
    with path.open("r", encoding="utf-8-sig", newline="") as f:
        reader = csv.DictReader(f)
        for i, r in enumerate(reader, start=1):
            company_id = str(r.get("company_id") or company_fallback).strip() or company_fallback
            rows.append(
                RowRecord(
                    dataset_type=dataset_type,
                    source_file=path.name,
                    row_no=i,
                    company_id=company_id,
                    date_time=parse_dt(r.get("date_time")),
                    uv_value=parse_float(r.get("UV_VALUE")),
                    comment_cnt=parse_int(r.get("COMMENT_CNT")),
                    incr_fans_cnt=parse_float(r.get("INCR_FANS_CNT")),
                    leave_ucnt=parse_float(r.get("LEAVE_UCNT")),
                    fans_club_ucnt=parse_float(r.get("FANS_CLUB_UCNT")),
                    pay_order_gmv=parse_float(r.get("PAY_ORDER_GMV")),
                    online_user_cnt=parse_int(r.get("ONLINE_USER_CNT")),
                    watch_ucnt=parse_float(r.get("WATCH_UCNT")),
                    pay_cnt=parse_float(r.get("PAY_CNT")),
                    pay_ucnt=parse_float(r.get("PAY_UCNT")),
                    natural_flow=str(r.get("natural_flow_trend_index") or ""),
                    pay_flow=str(r.get("pay_flow_trend_index") or ""),
                )
            )
    return rows


def ensure_import_job_table(cursor) -> None:
    cursor.execute(
        """
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
        )
        """
    )


def ensure_raw_table(cursor) -> None:
    cursor.execute(
        """
        CREATE TABLE IF NOT EXISTS douyin_live_raw (
          id BIGINT PRIMARY KEY AUTO_INCREMENT,
          source_file VARCHAR(260) NOT NULL,
          dataset_type VARCHAR(32) NOT NULL,
          row_no INT NOT NULL,
          company_id VARCHAR(64) NOT NULL,
          date_time DATETIME NULL,
          uv_value DOUBLE NOT NULL DEFAULT 0,
          comment_cnt BIGINT NOT NULL DEFAULT 0,
          incr_fans_cnt DOUBLE NOT NULL DEFAULT 0,
          leave_ucnt DOUBLE NOT NULL DEFAULT 0,
          fans_club_ucnt DOUBLE NOT NULL DEFAULT 0,
          pay_order_gmv DOUBLE NOT NULL DEFAULT 0,
          online_user_cnt BIGINT NOT NULL DEFAULT 0,
          watch_ucnt DOUBLE NOT NULL DEFAULT 0,
          pay_cnt DOUBLE NOT NULL DEFAULT 0,
          pay_ucnt DOUBLE NOT NULL DEFAULT 0,
          natural_flow_trend_index LONGTEXT,
          pay_flow_trend_index LONGTEXT,
          created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
          UNIQUE KEY uk_douyin_live_raw (source_file, dataset_type, row_no),
          INDEX idx_douyin_live_raw_company (company_id),
          INDEX idx_douyin_live_raw_time (date_time)
        )
        """
    )


def get_columns(cursor, table: str) -> set[str]:
    cursor.execute(
        """
        SELECT COLUMN_NAME FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = %s
        """,
        (table,),
    )
    return {row[0] for row in cursor.fetchall()}


def upsert_video_rows(cursor, rows: list[dict[str, Any]]) -> int:
    if not rows:
        return 0
    video_cols = get_columns(cursor, "video")
    preferred = [
        "id",
        "dedupe_key",
        "title",
        "author",
        "source_platform",
        "source_url",
        "category",
        "play_count",
        "like_count",
        "comment_count",
        "publish_time",
        "import_type",
        "source_file",
        "import_time",
        "data_quality_score",
        "import_job_id",
    ]
    cols = [c for c in preferred if c in video_cols]
    placeholders = ",".join(["%s"] * len(cols))

    updates: list[str] = []
    if "title" in cols:
        updates.append("title=VALUES(title)")
    if "author" in cols:
        updates.append("author=VALUES(author)")
    if "source_platform" in cols:
        updates.append("source_platform=VALUES(source_platform)")
    if "source_url" in cols:
        updates.append("source_url=VALUES(source_url)")
    if "category" in cols:
        updates.append("category=VALUES(category)")
    if "play_count" in cols:
        updates.append("play_count=GREATEST(play_count, VALUES(play_count))")
    if "like_count" in cols:
        updates.append("like_count=GREATEST(like_count, VALUES(like_count))")
    if "comment_count" in cols:
        updates.append("comment_count=GREATEST(comment_count, VALUES(comment_count))")
    if "publish_time" in cols:
        updates.append("publish_time=LEAST(publish_time, VALUES(publish_time))")
    if "import_type" in cols:
        updates.append("import_type=VALUES(import_type)")
    if "source_file" in cols:
        updates.append("source_file=VALUES(source_file)")
    if "import_time" in cols:
        updates.append("import_time=VALUES(import_time)")
    if "data_quality_score" in cols:
        updates.append("data_quality_score=GREATEST(data_quality_score, VALUES(data_quality_score))")
    if "import_job_id" in cols:
        updates.append("import_job_id=VALUES(import_job_id)")

    sql = f"INSERT INTO video ({','.join(cols)}) VALUES ({placeholders})"
    if updates:
        sql += " ON DUPLICATE KEY UPDATE " + ",".join(updates)

    params = [[r.get(c) for c in cols] for r in rows]
    cursor.executemany(sql, params)
    return len(rows)


def upsert_user_rows(cursor, rows: list[dict[str, Any]]) -> int:
    if not rows:
        return 0
    cols = get_columns(cursor, "user")
    preferred = ["user_id", "user_name", "fans", "follow", "level"]
    use_cols = [c for c in preferred if c in cols]
    if not use_cols:
        return 0
    escaped_cols = [f"`{c}`" for c in use_cols]
    updates: list[str] = []
    if "user_name" in use_cols:
        updates.append("`user_name`=VALUES(`user_name`)")
    if "fans" in use_cols:
        updates.append("`fans`=GREATEST(`fans`, VALUES(`fans`))")
    if "follow" in use_cols:
        updates.append("`follow`=GREATEST(`follow`, VALUES(`follow`))")
    if "level" in use_cols:
        updates.append("`level`=GREATEST(`level`, VALUES(`level`))")

    sql = f"INSERT INTO `user` ({','.join(escaped_cols)}) VALUES ({','.join(['%s'] * len(use_cols))})"
    if updates:
        sql += " ON DUPLICATE KEY UPDATE " + ",".join(updates)
    params = [[r.get(c) for c in use_cols] for r in rows]
    cursor.executemany(sql, params)
    return len(rows)


def insert_behaviors(cursor, rows: list[dict[str, Any]]) -> int:
    if not rows:
        return 0
    sql = "INSERT INTO user_behavior (user_id,video_id,action,time) VALUES (%s,%s,%s,%s)"
    params = [(r["user_id"], r["video_id"], r["action"], r["time"]) for r in rows]
    cursor.executemany(sql, params)
    return len(rows)


def upsert_video_stats(cursor, rows: list[dict[str, Any]]) -> int:
    if not rows:
        return 0
    sql = (
        "INSERT INTO video_statistics (video_id,stat_date,daily_play,daily_like,daily_comment) "
        "VALUES (%s,%s,%s,%s,%s) "
        "ON DUPLICATE KEY UPDATE "
        "daily_play=GREATEST(daily_play, VALUES(daily_play)),"
        "daily_like=GREATEST(daily_like, VALUES(daily_like)),"
        "daily_comment=GREATEST(daily_comment, VALUES(daily_comment))"
    )
    params = [(r["video_id"], r["stat_date"], r["daily_play"], r["daily_like"], r["daily_comment"]) for r in rows]
    cursor.executemany(sql, params)
    return len(rows)


def build_aggregates(rows: list[RowRecord]) -> dict[tuple[str, str], dict[str, Any]]:
    grouped: dict[tuple[str, str], dict[str, Any]] = {}
    for row in rows:
        key = (row.dataset_type, row.company_id)
        if key not in grouped:
            grouped[key] = {
                "dataset_type": row.dataset_type,
                "company_id": row.company_id,
                "source_file": row.source_file,
                "start_time": row.date_time,
                "end_time": row.date_time,
                "rows": 0,
                "sum_uv": 0.0,
                "sum_comment": 0,
                "sum_incr_fans": 0.0,
                "sum_leave": 0.0,
                "sum_fans_club": 0.0,
                "sum_gmv": 0.0,
                "max_online": 0,
                "sum_watch": 0.0,
                "sum_pay_cnt": 0.0,
                "sum_pay_ucnt": 0.0,
                "daily": defaultdict(lambda: {"watch": 0.0, "incr_fans": 0.0, "comment": 0}),
            }
        g = grouped[key]
        g["rows"] += 1
        g["start_time"] = min(g["start_time"], row.date_time)
        g["end_time"] = max(g["end_time"], row.date_time)
        g["sum_uv"] += row.uv_value
        g["sum_comment"] += row.comment_cnt
        g["sum_incr_fans"] += row.incr_fans_cnt
        g["sum_leave"] += row.leave_ucnt
        g["sum_fans_club"] += row.fans_club_ucnt
        g["sum_gmv"] += row.pay_order_gmv
        g["max_online"] = max(g["max_online"], row.online_user_cnt)
        g["sum_watch"] += row.watch_ucnt
        g["sum_pay_cnt"] += row.pay_cnt
        g["sum_pay_ucnt"] += row.pay_ucnt
        d = row.date_time.date()
        g["daily"][d]["watch"] += row.watch_ucnt
        g["daily"][d]["incr_fans"] += row.incr_fans_cnt
        g["daily"][d]["comment"] += row.comment_cnt
    return grouped


def main() -> None:
    parser = argparse.ArgumentParser(description="瀵煎叆鎶栭煶鏁版嵁鍒嗘瀽 CSV 鍒?video_analysis")
    parser.add_argument("--data-csv", default="", help="data.csv 璺緞")
    parser.add_argument("--predict-csv", default="", help="predict.csv 璺緞")
    parser.add_argument("--search-root", default=r"C:\Users\17929\Downloads\c58c0-main", help="鑷姩鎼滅储鏍圭洰褰?)
    parser.add_argument("--host", default="localhost")
    parser.add_argument("--port", type=int, default=3306)
    parser.add_argument("--user", default="root")
    parser.add_argument("--password", default=os.getenv("MYSQL_PASSWORD", ""))
    parser.add_argument("--database", default="video_analysis")
    args = parser.parse_args()

    search_root = Path(args.search_root).expanduser().resolve()
    data_csv = locate_file(args.data_csv or None, "data.csv", search_root)
    predict_csv = locate_file(args.predict_csv or None, "predict.csv", search_root)

    data_rows = load_rows(data_csv, dataset_type="historical", company_fallback="historical_default")
    predict_rows = load_rows(predict_csv, dataset_type="predict", company_fallback="predict_default")
    all_rows = data_rows + predict_rows

    conn = mysql.connector.connect(
        host=args.host,
        port=args.port,
        user=args.user,
        password=args.password,
        database=args.database,
        autocommit=False,
    )
    cursor = conn.cursor()

    now = datetime.now()
    started_at = now.strftime("%Y-%m-%d %H:%M:%S")
    import_job_id = 0

    try:
        ensure_import_job_table(cursor)
        ensure_raw_table(cursor)

        cursor.execute(
            """
            INSERT INTO import_job (import_type,source_platform,source_file,source_count,success_count,started_at,status,notes)
            VALUES (%s,%s,%s,%s,%s,%s,%s,%s)
            """,
            (
                "dataset_csv",
                "douyin",
                f"{data_csv.name};{predict_csv.name}",
                len(all_rows),
                0,
                started_at,
                "RUNNING",
                "import douyin csv dataset",
            ),
        )
        import_job_id = cursor.lastrowid or 0

        raw_sql = (
            "INSERT INTO douyin_live_raw "
            "(source_file,dataset_type,row_no,company_id,date_time,uv_value,comment_cnt,incr_fans_cnt,leave_ucnt,fans_club_ucnt,pay_order_gmv,online_user_cnt,watch_ucnt,pay_cnt,pay_ucnt,natural_flow_trend_index,pay_flow_trend_index) "
            "VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s) "
            "ON DUPLICATE KEY UPDATE "
            "date_time=VALUES(date_time),uv_value=VALUES(uv_value),comment_cnt=VALUES(comment_cnt),"
            "incr_fans_cnt=VALUES(incr_fans_cnt),leave_ucnt=VALUES(leave_ucnt),fans_club_ucnt=VALUES(fans_club_ucnt),"
            "pay_order_gmv=VALUES(pay_order_gmv),online_user_cnt=VALUES(online_user_cnt),watch_ucnt=VALUES(watch_ucnt),"
            "pay_cnt=VALUES(pay_cnt),pay_ucnt=VALUES(pay_ucnt),natural_flow_trend_index=VALUES(natural_flow_trend_index),"
            "pay_flow_trend_index=VALUES(pay_flow_trend_index)"
        )
        raw_params = [
            (
                r.source_file,
                r.dataset_type,
                r.row_no,
                r.company_id,
                r.date_time.strftime("%Y-%m-%d %H:%M:%S"),
                r.uv_value,
                r.comment_cnt,
                r.incr_fans_cnt,
                r.leave_ucnt,
                r.fans_club_ucnt,
                r.pay_order_gmv,
                r.online_user_cnt,
                r.watch_ucnt,
                r.pay_cnt,
                r.pay_ucnt,
                r.natural_flow,
                r.pay_flow,
            )
            for r in all_rows
        ]
        cursor.executemany(raw_sql, raw_params)

        grouped = build_aggregates(all_rows)
        video_rows: list[dict[str, Any]] = []
        user_rows: list[dict[str, Any]] = []
        behavior_rows: list[dict[str, Any]] = []
        stat_rows: list[dict[str, Any]] = []

        for (dataset_type, company_id), g in grouped.items():
            key = f"{dataset_type}:{company_id}"
            video_id = stable_int(key, "douyin_dataset_video")
            user_id = stable_int(key, "douyin_dataset_user")
            source_url = f"dataset://douyin/{dataset_type}/{company_id}"
            dedupe_key = sha1_hex(f"douyin|{source_url}")

            play_count = int(max(g["sum_watch"], g["sum_uv"], g["sum_comment"] * 8, g["sum_pay_ucnt"] * 10, 1))
            like_count = int(max(g["sum_incr_fans"], g["sum_pay_cnt"] * 2, 0))
            comment_count = int(max(g["sum_comment"], 0))
            publish_time = g["end_time"].strftime("%Y-%m-%d %H:%M:%S")
            quality = 86.0 if dataset_type == "historical" else 78.0
            quality = min(95.0, max(60.0, quality + min(8.0, g["rows"] / 1200.0)))

            title = f"鎶栭煶鐩存挱鏁版嵁鏍锋湰-{dataset_type}-鍟嗗{company_id}"
            author = f"鎶栭煶鍟嗗_{company_id}"

            video_rows.append(
                {
                    "id": video_id,
                    "dedupe_key": dedupe_key,
                    "title": title[:255],
                    "author": author[:100],
                    "source_platform": "douyin",
                    "source_url": source_url,
                    "category": "鐩存挱鐢靛晢",
                    "play_count": play_count,
                    "like_count": like_count,
                    "comment_count": comment_count,
                    "publish_time": publish_time,
                    "import_type": "dataset_csv",
                    "source_file": g["source_file"],
                    "import_time": now.strftime("%Y-%m-%d %H:%M:%S"),
                    "data_quality_score": round(quality, 2),
                    "import_job_id": import_job_id,
                }
            )

            level = int(min(10, max(1, 1 + int(g["sum_incr_fans"] // 5000))))
            user_rows.append(
                {
                    "user_id": user_id,
                    "user_name": author[:100],
                    "fans": int(max(g["sum_incr_fans"], 0)),
                    "follow": int(max(g["sum_fans_club"], 0)),
                    "level": level,
                }
            )

            start_time = g["start_time"]
            end_time = g["end_time"]
            if end_time <= start_time:
                end_time = start_time + timedelta(hours=2)
            total_seconds = max(1, int((end_time - start_time).total_seconds()))
            play_n = min(420, max(20, int(g["sum_watch"] / 120)))
            like_n = min(160, max(4, int((g["sum_incr_fans"] + g["sum_pay_cnt"]) / 20)))
            comment_n = min(120, max(2, int(g["sum_comment"] / 30)))

            for _ in range(play_n):
                sec = random.randint(0, total_seconds)
                behavior_rows.append(
                    {
                        "user_id": user_id,
                        "video_id": video_id,
                        "action": "play",
                        "time": (start_time + timedelta(seconds=sec)).strftime("%Y-%m-%d %H:%M:%S"),
                    }
                )
            for _ in range(like_n):
                sec = random.randint(0, total_seconds)
                behavior_rows.append(
                    {
                        "user_id": user_id,
                        "video_id": video_id,
                        "action": "like",
                        "time": (start_time + timedelta(seconds=sec)).strftime("%Y-%m-%d %H:%M:%S"),
                    }
                )
            for _ in range(comment_n):
                sec = random.randint(0, total_seconds)
                behavior_rows.append(
                    {
                        "user_id": user_id,
                        "video_id": video_id,
                        "action": "comment",
                        "time": (start_time + timedelta(seconds=sec)).strftime("%Y-%m-%d %H:%M:%S"),
                    }
                )

            for stat_date, daily in g["daily"].items():
                stat_rows.append(
                    {
                        "video_id": video_id,
                        "stat_date": stat_date.strftime("%Y-%m-%d"),
                        "daily_play": int(max(daily["watch"], 0)),
                        "daily_like": int(max(daily["incr_fans"], 0)),
                        "daily_comment": int(max(daily["comment"], 0)),
                    }
                )

        video_count = upsert_video_rows(cursor, video_rows)
        user_count = upsert_user_rows(cursor, user_rows)
        behavior_count = insert_behaviors(cursor, behavior_rows)
        stat_count = upsert_video_stats(cursor, stat_rows)

        if import_job_id:
            cursor.execute(
                """
                UPDATE import_job
                SET success_count=%s, finished_at=%s, status=%s, notes=%s
                WHERE id=%s
                """,
                (
                    video_count,
                    datetime.now().strftime("%Y-%m-%d %H:%M:%S"),
                    "SUCCESS",
                    f"raw={len(all_rows)},video={video_count},user={user_count},behavior={behavior_count},stat={stat_count}",
                    import_job_id,
                ),
            )

        conn.commit()
        print("瀵煎叆瀹屾垚锛?)
        print(f"- 鍘熷鏁版嵁鍏ュ簱(douyin_live_raw): {len(all_rows)}")
        print(f"- 鑱氬悎瑙嗛鍐欏叆(video): {video_count}")
        print(f"- 鐢ㄦ埛鍐欏叆(user): {user_count}")
        print(f"- 琛屼负鍐欏叆(user_behavior): {behavior_count}")
        print(f"- 鏃ョ粺璁″啓鍏?video_statistics): {stat_count}")
        if import_job_id:
            print(f"- import_job_id: {import_job_id}")
    except Exception:
        conn.rollback()
        raise
    finally:
        cursor.close()
        conn.close()


if __name__ == "__main__":
    main()


