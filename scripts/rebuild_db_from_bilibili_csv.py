from __future__ import annotations

import argparse
import os
import csv
import hashlib
import math
import random
import re
from collections import Counter, defaultdict
from dataclasses import dataclass
from datetime import date, datetime, timedelta
from pathlib import Path
from typing import Any

import mysql.connector


@dataclass
class CsvVideo:
    rank_num: int
    category: str
    title: str
    author: str
    play_count: int
    danmaku_count: int
    comment_count: int
    coin_count: int
    like_count: int
    share_count: int
    favorite_count: int
    cover_url: str
    video_url: str
    av_id: int | None
    bv_id: str | None


HEADER_ALIASES = {
    "rank_num": ["鎺掑悕", "rank", "搴忓彿"],
    "category": ["鍒嗗尯", "鍒嗙被", "category"],
    "title": ["鏍囬", "title", "瑙嗛鏍囬"],
    "author": ["浣滆€?, "author", "up涓?, "up"],
    "play_count": ["鎾斁閲?, "play_count", "view_count", "views"],
    "danmaku_count": ["寮瑰箷閲?, "danmaku_count", "danmaku"],
    "comment_count": ["璇勮閲?, "comment_count", "comments"],
    "coin_count": ["纭竵", "coin_count", "coins"],
    "like_count": ["鐐硅禐閲?, "like_count", "likes"],
    "share_count": ["鍒嗕韩閲?, "share_count", "shares"],
    "favorite_count": ["鏀惰棌閲?, "favorite_count", "favorites"],
    "cover_url": ["灏侀潰", "cover_url", "cover"],
    "video_url": ["閾炬帴", "link", "url", "video_url"],
}

AV_PATTERN = re.compile(r"/video/av(\d+)", re.IGNORECASE)
BV_PATTERN = re.compile(r"(BV[0-9A-Za-z]{10})")
NUM_PATTERN = re.compile(r"([0-9]+(?:\.[0-9]+)?)([wWkK涓囦嚎]?)")


def normalize_key(key: str) -> str:
    return (
        key.strip()
        .lower()
        .replace("_", "")
        .replace("-", "")
        .replace(" ", "")
        .replace("锛?, "")
        .replace(":", "")
    )


def detect_encoding(csv_path: Path) -> str:
    for enc in ("utf-8-sig", "utf-8", "gb18030", "gbk"):
        try:
            with csv_path.open("r", encoding=enc, newline="") as f:
                next(csv.reader(f), None)
            return enc
        except UnicodeDecodeError:
            continue
    return "utf-8-sig"


def parse_count(raw: Any) -> int:
    if raw is None:
        return 0
    text = str(raw).strip().replace(",", "").replace("锛?, "")
    if not text:
        return 0
    if text.isdigit():
        return int(text)

    m = NUM_PATTERN.search(text)
    if not m:
        return 0
    base = float(m.group(1))
    unit = m.group(2)
    if unit in ("w", "W", "涓?):
        base *= 10_000
    elif unit == "浜?:
        base *= 100_000_000
    elif unit in ("k", "K"):
        base *= 1_000
    return int(max(0, base))


def safe_text(raw: Any, fallback: str = "") -> str:
    value = fallback if raw is None else str(raw).strip()
    return value if value else fallback


def parse_ids_from_url(url: str) -> tuple[int | None, str | None]:
    if not url:
        return None, None
    av_m = AV_PATTERN.search(url)
    bv_m = BV_PATTERN.search(url)
    av_id = int(av_m.group(1)) if av_m else None
    bv_id = bv_m.group(1) if bv_m else None
    return av_id, bv_id


def stable_bigint(namespace: str, seed: str) -> int:
    digest = hashlib.sha1(f"{namespace}:{seed}".encode("utf-8")).digest()
    value = int.from_bytes(digest[:8], "big", signed=False) & 0x7FFFFFFFFFFFFFFF
    return 1 if value == 0 else value


def build_dedupe_key(platform: str, source_url: str | None, title: str, author: str) -> str:
    normalized_url = (source_url or "").strip().split("?", 1)[0].split("#", 1)[0].rstrip("/")
    if normalized_url:
        seed = f"{platform.lower()}|{normalized_url.lower()}"
    else:
        seed = f"{platform.lower()}|{title.strip().lower()}|{author.strip().lower()}"
    return hashlib.sha1(seed.encode("utf-8")).hexdigest()


def map_headers(fieldnames: list[str]) -> dict[str, str]:
    normalized = {normalize_key(h): h for h in fieldnames}
    mapping: dict[str, str] = {}
    for target, aliases in HEADER_ALIASES.items():
        for alias in aliases:
            found = normalized.get(normalize_key(alias))
            if found:
                mapping[target] = found
                break
    missing = [k for k in ("rank_num", "category", "title", "author", "video_url") if k not in mapping]
    if missing:
        raise ValueError(f"CSV 缂哄皯鍏抽敭鍒? {missing}")
    return mapping


def load_csv_rows(csv_path: Path) -> list[CsvVideo]:
    encoding = detect_encoding(csv_path)
    rows: list[CsvVideo] = []
    with csv_path.open("r", encoding=encoding, newline="") as f:
        reader = csv.DictReader(f)
        if not reader.fieldnames:
            raise ValueError("CSV 涓虹┖鎴栫己灏戣〃澶?)
        header_map = map_headers(reader.fieldnames)
        for idx, row in enumerate(reader, start=1):
            rank_num = parse_count(row.get(header_map["rank_num"])) or idx
            category = safe_text(row.get(header_map["category"]), "鏈垎绫?)
            title = safe_text(row.get(header_map["title"]))
            author = safe_text(row.get(header_map["author"]), "unknown_author")
            if not title:
                continue

            video_url = safe_text(row.get(header_map["video_url"]))
            av_id, bv_id = parse_ids_from_url(video_url)

            rows.append(
                CsvVideo(
                    rank_num=rank_num,
                    category=category,
                    title=title,
                    author=author,
                    play_count=parse_count(row.get(header_map.get("play_count", ""))),
                    danmaku_count=parse_count(row.get(header_map.get("danmaku_count", ""))),
                    comment_count=parse_count(row.get(header_map.get("comment_count", ""))),
                    coin_count=parse_count(row.get(header_map.get("coin_count", ""))),
                    like_count=parse_count(row.get(header_map.get("like_count", ""))),
                    share_count=parse_count(row.get(header_map.get("share_count", ""))),
                    favorite_count=parse_count(row.get(header_map.get("favorite_count", ""))),
                    cover_url=safe_text(row.get(header_map.get("cover_url", ""))),
                    video_url=video_url,
                    av_id=av_id,
                    bv_id=bv_id,
                )
            )
    if not rows:
        raise ValueError("CSV 鏈В鏋愬嚭鏈夋晥鏁版嵁琛?)
    return rows


def chunked(iterable: list[tuple[Any, ...]], size: int) -> list[list[tuple[Any, ...]]]:
    return [iterable[i : i + size] for i in range(0, len(iterable), size)]


def rebuild_schema(cursor: mysql.connector.cursor.MySQLCursor) -> None:
    cursor.execute("DROP DATABASE IF EXISTS video_analysis")
    cursor.execute(
        """
        CREATE DATABASE video_analysis
        DEFAULT CHARACTER SET utf8mb4
        COLLATE utf8mb4_unicode_ci
        """
    )
    cursor.execute("USE video_analysis")

    cursor.execute(
        """
        CREATE TABLE bilibili_video_raw (
            id BIGINT PRIMARY KEY AUTO_INCREMENT,
            rank_num INT NOT NULL,
            category VARCHAR(100) NOT NULL,
            title VARCHAR(500) NOT NULL,
            author VARCHAR(200) NOT NULL,
            play_count BIGINT NOT NULL DEFAULT 0,
            danmaku_count BIGINT NOT NULL DEFAULT 0,
            comment_count BIGINT NOT NULL DEFAULT 0,
            coin_count BIGINT NOT NULL DEFAULT 0,
            like_count BIGINT NOT NULL DEFAULT 0,
            share_count BIGINT NOT NULL DEFAULT 0,
            favorite_count BIGINT NOT NULL DEFAULT 0,
            cover_url VARCHAR(1000),
            video_url VARCHAR(1000) NOT NULL,
            av_id BIGINT NULL,
            bv_id VARCHAR(32) NULL,
            source_file VARCHAR(260) NOT NULL,
            imported_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
            INDEX idx_raw_video_url (video_url(255)),
            INDEX idx_raw_category (category),
            INDEX idx_raw_author (author),
            INDEX idx_raw_play (play_count)
        )
        """
    )

    cursor.execute(
        """
        CREATE TABLE video (
            id BIGINT PRIMARY KEY,
            dedupe_key VARCHAR(128) DEFAULT NULL,
            title VARCHAR(255) NOT NULL,
            author VARCHAR(100) NOT NULL,
            source_platform VARCHAR(32) NOT NULL DEFAULT 'bilibili',
            source_url VARCHAR(1000) DEFAULT NULL,
            category VARCHAR(60) NOT NULL,
            play_count BIGINT NOT NULL DEFAULT 0,
            like_count BIGINT NOT NULL DEFAULT 0,
            comment_count BIGINT NOT NULL DEFAULT 0,
            publish_time DATETIME NOT NULL,
            import_type VARCHAR(32) NOT NULL DEFAULT 'csv_rebuild',
            source_file VARCHAR(260) DEFAULT NULL,
            import_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
            data_quality_score DECIMAL(5,2) NOT NULL DEFAULT 0.00,
            import_job_id BIGINT DEFAULT NULL,
            created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
            UNIQUE KEY uk_video_dedupe (dedupe_key),
            INDEX idx_video_category (category),
            INDEX idx_video_play (play_count),
            INDEX idx_video_platform (source_platform),
            INDEX idx_video_import_time (import_time)
        )
        """
    )

    cursor.execute(
        """
        CREATE TABLE `user` (
            user_id BIGINT PRIMARY KEY,
            user_name VARCHAR(100) NOT NULL,
            fans BIGINT NOT NULL DEFAULT 0,
            `follow` BIGINT NOT NULL DEFAULT 0,
            `level` INT NOT NULL DEFAULT 1,
            created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
        )
        """
    )

    cursor.execute(
        """
        CREATE TABLE comment (
            comment_id BIGINT PRIMARY KEY AUTO_INCREMENT,
            video_id BIGINT NOT NULL,
            user_id BIGINT NOT NULL,
            content VARCHAR(1000) NOT NULL,
            like_count BIGINT NOT NULL DEFAULT 0,
            time DATETIME NOT NULL,
            INDEX idx_comment_video (video_id),
            INDEX idx_comment_user (user_id)
        )
        """
    )

    cursor.execute(
        """
        CREATE TABLE user_behavior (
            id BIGINT PRIMARY KEY AUTO_INCREMENT,
            user_id BIGINT NOT NULL,
            video_id BIGINT NOT NULL,
            action VARCHAR(32) NOT NULL,
            time DATETIME NOT NULL,
            INDEX idx_behavior_user (user_id),
            INDEX idx_behavior_video (video_id),
            INDEX idx_behavior_time (time)
        )
        """
    )

    cursor.execute(
        """
        CREATE TABLE video_statistics (
            id BIGINT PRIMARY KEY AUTO_INCREMENT,
            video_id BIGINT NOT NULL,
            stat_date DATE NOT NULL,
            daily_play BIGINT NOT NULL DEFAULT 0,
            daily_like BIGINT NOT NULL DEFAULT 0,
            daily_comment BIGINT NOT NULL DEFAULT 0,
            UNIQUE KEY uk_video_date (video_id, stat_date),
            INDEX idx_stat_date (stat_date)
        )
        """
    )

    cursor.execute(
        """
        CREATE TABLE user_interest_result (
            id BIGINT PRIMARY KEY AUTO_INCREMENT,
            user_id BIGINT NOT NULL UNIQUE,
            cluster_id INT NOT NULL,
            cluster_label VARCHAR(64) NOT NULL,
            favorite_category VARCHAR(64) NOT NULL,
            updated_at DATETIME NOT NULL
        )
        """
    )

    cursor.execute(
        """
        CREATE TABLE import_job (
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


def import_data(
    cursor: mysql.connector.cursor.MySQLCursor,
    rows: list[CsvVideo],
    csv_file_name: str,
) -> dict[str, int]:
    now = datetime.now()
    rng = random.Random(20260307)

    cursor.execute(
        "INSERT INTO import_job (import_type,source_platform,source_file,source_count,success_count,started_at,status,notes) "
        "VALUES (%s,%s,%s,%s,%s,%s,%s,%s)",
        (
            "csv_rebuild",
            "bilibili",
            csv_file_name,
            len(rows),
            0,
            now.strftime("%Y-%m-%d %H:%M:%S"),
            "RUNNING",
            "rebuild_db_from_bilibili_csv.py",
        ),
    )
    import_job_id = int(cursor.lastrowid)

    raw_rows = [
        (
            r.rank_num,
            r.category[:100],
            r.title[:500],
            r.author[:200],
            r.play_count,
            r.danmaku_count,
            r.comment_count,
            r.coin_count,
            r.like_count,
            r.share_count,
            r.favorite_count,
            r.cover_url[:1000],
            r.video_url[:1000],
            r.av_id,
            r.bv_id,
            csv_file_name,
        )
        for r in rows
    ]
    raw_sql = (
        "INSERT INTO bilibili_video_raw "
        "(rank_num,category,title,author,play_count,danmaku_count,comment_count,coin_count,like_count,share_count,"
        "favorite_count,cover_url,video_url,av_id,bv_id,source_file) "
        "VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s)"
    )
    for part in chunked(raw_rows, 1000):
        cursor.executemany(raw_sql, part)

    used_video_ids: set[int] = set()
    author_to_user_id: dict[str, int] = {}
    author_stat: dict[str, dict[str, Any]] = defaultdict(lambda: {"play": 0, "like": 0, "comment": 0, "cat": Counter()})

    video_rows: list[tuple[Any, ...]] = []
    video_publish_times: dict[int, datetime] = {}
    video_to_author: dict[int, str] = {}

    for idx, r in enumerate(rows, start=1):
        video_id = r.av_id if r.av_id else stable_bigint("video", f"{r.video_url}|{r.title}|{r.author}")
        while video_id in used_video_ids:
            video_id = stable_bigint("video-dup", f"{video_id}|{idx}")
        used_video_ids.add(video_id)

        author_key = r.author.strip() or "unknown_author"
        user_id = author_to_user_id.setdefault(author_key, stable_bigint("user", author_key))

        publish_time = now - timedelta(minutes=max(1, r.rank_num))
        video_publish_times[video_id] = publish_time
        video_to_author[video_id] = author_key

        source_url = r.video_url[:1000] if r.video_url else None
        dedupe_key = build_dedupe_key("bilibili", source_url, r.title, author_key)
        quality_score = 100.0 if source_url else 92.0

        video_rows.append(
            (
                video_id,
                dedupe_key,
                r.title[:255],
                author_key[:100],
                "bilibili",
                source_url,
                r.category[:60] if r.category else "鏈垎绫?,
                r.play_count,
                r.like_count,
                r.comment_count,
                publish_time.strftime("%Y-%m-%d %H:%M:%S"),
                "csv_rebuild",
                csv_file_name,
                now.strftime("%Y-%m-%d %H:%M:%S"),
                quality_score,
                import_job_id,
            )
        )

        author_stat[author_key]["play"] += r.play_count
        author_stat[author_key]["like"] += r.like_count
        author_stat[author_key]["comment"] += r.comment_count
        author_stat[author_key]["cat"][r.category[:64] if r.category else "鏈垎绫?] += 1

    video_sql = (
        "INSERT INTO video "
        "(id,dedupe_key,title,author,source_platform,source_url,category,play_count,like_count,comment_count,publish_time,"
        "import_type,source_file,import_time,data_quality_score,import_job_id) "
        "VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s)"
    )
    for part in chunked(video_rows, 1000):
        cursor.executemany(video_sql, part)

    user_rows: list[tuple[Any, ...]] = []
    for author, st in author_stat.items():
        user_id = author_to_user_id[author]
        fans = max(1, int(st["play"] / 1200 + st["like"] / 30))
        follow = max(1, fans // 9)
        level = min(10, max(1, int(math.log10(fans + 10)) + 1))
        user_rows.append((user_id, author[:100], fans, follow, level))

    user_sql = "INSERT INTO `user` (user_id,user_name,fans,`follow`,`level`) VALUES (%s,%s,%s,%s,%s)"
    for part in chunked(user_rows, 1000):
        cursor.executemany(user_sql, part)

    behavior_rows: list[tuple[Any, ...]] = []
    comment_rows: list[tuple[Any, ...]] = []
    stat_rows: list[tuple[Any, ...]] = []

    weights = [0.08, 0.1, 0.12, 0.14, 0.16, 0.18, 0.22]
    today = date.today()

    for v in video_rows:
        video_id, _, _, _, _, play_count, like_count, comment_count, _ = v
        author = video_to_author[video_id]
        user_id = author_to_user_id[author]
        publish_time = video_publish_times[video_id]
        start_ts = int(publish_time.timestamp())
        end_ts = int(now.timestamp())
        if end_ts <= start_ts:
            start_ts = end_ts - 3600

        play_events = max(4, min(80, int(math.sqrt(max(play_count, 1) / 800.0) + 4)))
        like_events = max(1, min(35, int(math.sqrt(max(like_count, 1) / 200.0) + 1)))
        comment_events = max(1, min(20, int(math.sqrt(max(comment_count, 1) / 120.0))))
        total = min(150, play_events + like_events + comment_events)
        play_cutoff = min(play_events, total)
        like_cutoff = min(play_cutoff + like_events, total)

        for i in range(total):
            action = "play" if i < play_cutoff else ("like" if i < like_cutoff else "comment")
            ts = datetime.fromtimestamp(rng.randint(start_ts, end_ts))
            behavior_rows.append((user_id, video_id, action, ts.strftime("%Y-%m-%d %H:%M:%S")))

        comment_like = max(0, min(2000, like_count // 100))
        comment_rows.append((video_id, user_id, "鏉ヨ嚜CSV瀵煎叆鐨勬牱渚嬭瘎璁?, comment_like, now.strftime("%Y-%m-%d %H:%M:%S")))

        for i, w in enumerate(weights):
            stat_date = (today - timedelta(days=(len(weights) - 1 - i))).strftime("%Y-%m-%d")
            dp = max(1, int(play_count * w))
            dl = max(0, int(like_count * w))
            dc = max(0, int(comment_count * w))
            stat_rows.append((video_id, stat_date, dp, dl, dc))

    behavior_sql = "INSERT INTO user_behavior (user_id,video_id,action,time) VALUES (%s,%s,%s,%s)"
    for part in chunked(behavior_rows, 2000):
        cursor.executemany(behavior_sql, part)

    comment_sql = "INSERT INTO comment (video_id,user_id,content,like_count,time) VALUES (%s,%s,%s,%s,%s)"
    for part in chunked(comment_rows, 1000):
        cursor.executemany(comment_sql, part)

    stat_sql = (
        "INSERT INTO video_statistics (video_id,stat_date,daily_play,daily_like,daily_comment) "
        "VALUES (%s,%s,%s,%s,%s)"
    )
    for part in chunked(stat_rows, 2000):
        cursor.executemany(stat_sql, part)

    cluster_labels = {
        0: "娼滄按娴忚鍨?,
        1: "浜掑姩娲昏穬鍨?,
        2: "鍨傜被鍋忓ソ鍨?,
        3: "娣卞害娑堣垂鍨?,
    }
    interest_rows: list[tuple[Any, ...]] = []
    for author, st in author_stat.items():
        user_id = author_to_user_id[author]
        fav_category = st["cat"].most_common(1)[0][0] if st["cat"] else "鏈垎绫?
        cluster_id = int(stable_bigint("cluster", author) % 4)
        interest_rows.append(
            (
                user_id,
                cluster_id,
                cluster_labels[cluster_id],
                fav_category[:64],
                now.strftime("%Y-%m-%d %H:%M:%S"),
            )
        )
    interest_sql = (
        "INSERT INTO user_interest_result (user_id,cluster_id,cluster_label,favorite_category,updated_at) "
        "VALUES (%s,%s,%s,%s,%s)"
    )
    for part in chunked(interest_rows, 1000):
        cursor.executemany(interest_sql, part)

    cursor.execute(
        "UPDATE import_job SET success_count=%s, finished_at=%s, status=%s, notes=%s WHERE id=%s",
        (
            len(video_rows),
            now.strftime("%Y-%m-%d %H:%M:%S"),
            "SUCCESS",
            f"videos={len(video_rows)}, users={len(user_rows)}, behaviors={len(behavior_rows)}",
            import_job_id,
        ),
    )

    return {
        "import_job_id": import_job_id,
        "raw_rows": len(raw_rows),
        "video_rows": len(video_rows),
        "user_rows": len(user_rows),
        "behavior_rows": len(behavior_rows),
        "comment_rows": len(comment_rows),
        "stat_rows": len(stat_rows),
        "interest_rows": len(interest_rows),
    }


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="鏍规嵁BiliBili CSV閲嶅缓 video_analysis 鏁版嵁搴?)
    parser.add_argument("--csv-file", required=True, help="CSV 鏂囦欢璺緞")
    parser.add_argument("--host", default="localhost")
    parser.add_argument("--port", type=int, default=3306)
    parser.add_argument("--user", default="root")
    parser.add_argument("--password", default=os.getenv("MYSQL_PASSWORD", ""))
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    csv_path = Path(args.csv_file)
    if not csv_path.exists():
        raise FileNotFoundError(f"CSV 鏂囦欢涓嶅瓨鍦? {csv_path}")

    rows = load_csv_rows(csv_path)
    conn = mysql.connector.connect(
        host=args.host,
        port=args.port,
        user=args.user,
        password=args.password,
        autocommit=False,
        allow_local_infile=True,
    )
    try:
        cursor = conn.cursor()
        rebuild_schema(cursor)
        stats = import_data(cursor, rows, csv_path.name)
        conn.commit()
        print("鏁版嵁搴撻噸寤哄畬鎴愩€?)
        for k, v in stats.items():
            print(f"{k}: {v}")
    finally:
        conn.close()


if __name__ == "__main__":
    main()


