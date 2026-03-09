import argparse
import os
import html
import re

import mysql.connector

TAG_RE = re.compile(r"<[^>]+>")
SPACE_RE = re.compile(r"\s+")

TARGET_COLUMNS = [
    ("video", "title"),
    ("bilibili_video_raw", "title"),
]


def clean_text(value: str) -> str:
    if value is None:
        return value
    text = value
    for _ in range(2):
        decoded = html.unescape(text)
        if decoded == text:
            break
        text = decoded
    text = TAG_RE.sub("", text)
    text = text.replace("\u00A0", " ")
    text = SPACE_RE.sub(" ", text).strip()
    return text


def detect_primary_key(cursor, table: str) -> str:
    cursor.execute(f"SHOW KEYS FROM `{table}` WHERE Key_name = 'PRIMARY'")
    row = cursor.fetchone()
    if row:
        return row[4]
    cursor.execute(f"SHOW COLUMNS FROM `{table}`")
    return cursor.fetchone()[0]


def main() -> None:
    parser = argparse.ArgumentParser(description="Clean HTML tags/entities from known text columns.")
    parser.add_argument("--host", default="localhost")
    parser.add_argument("--port", type=int, default=3306)
    parser.add_argument("--user", default="root")
    parser.add_argument("--password", default=os.getenv("MYSQL_PASSWORD", ""))
    parser.add_argument("--database", default="video_analysis")
    args = parser.parse_args()

    conn = mysql.connector.connect(
        host=args.host,
        port=args.port,
        user=args.user,
        password=args.password,
        database=args.database,
        charset="utf8mb4",
    )
    cursor = conn.cursor()

    total_updated = 0
    for table, column in TARGET_COLUMNS:
        key = detect_primary_key(cursor, table)
        where_clause = (
            f"`{column}` REGEXP '<[^>]+>' OR "
            f"`{column}` LIKE '%&lt;%' OR "
            f"`{column}` LIKE '%&gt;%' OR "
            f"`{column}` LIKE '%&amp;%' OR "
            f"`{column}` LIKE '%&quot;%' OR "
            f"`{column}` LIKE '%&#%'"
        )
        cursor.execute(
            f"SELECT `{key}`, `{column}` FROM `{table}` WHERE {where_clause}"
        )
        rows = cursor.fetchall()
        updated = 0
        for row_id, value in rows:
            cleaned = clean_text(value)
            if cleaned != value:
                cursor.execute(
                    f"UPDATE `{table}` SET `{column}`=%s WHERE `{key}`=%s",
                    (cleaned, row_id),
                )
                updated += 1
        conn.commit()

        cursor.execute(
            f"SELECT COUNT(*) FROM `{table}` WHERE {where_clause}"
        )
        remaining = cursor.fetchone()[0]

        total_updated += updated
        print(f"{table}.{column}: matched={len(rows)}, updated={updated}, remaining={remaining}")

    cursor.close()
    conn.close()
    print(f"Done. total_updated={total_updated}")


if __name__ == "__main__":
    main()


