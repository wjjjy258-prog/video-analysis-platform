from __future__ import annotations

import argparse
import os
from pathlib import Path

import mysql.connector


def split_sql_statements(sql_text: str) -> list[str]:
    statements: list[str] = []
    buf: list[str] = []
    in_single = False
    in_double = False
    i = 0
    while i < len(sql_text):
        ch = sql_text[i]
        prev = sql_text[i - 1] if i > 0 else ""
        if ch == "'" and not in_double and prev != "\\":
            in_single = not in_single
        elif ch == '"' and not in_single and prev != "\\":
            in_double = not in_double

        if ch == ";" and not in_single and not in_double:
            stmt = "".join(buf).strip()
            if stmt:
                statements.append(stmt)
            buf = []
        else:
            buf.append(ch)
        i += 1

    tail = "".join(buf).strip()
    if tail:
        statements.append(tail)
    return statements


def table_exists(cursor, table_name: str) -> bool:
    cursor.execute(
        """
        SELECT COUNT(*) FROM information_schema.TABLES
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = %s
        """,
        (table_name,),
    )
    return (cursor.fetchone() or [0])[0] > 0


def column_exists(cursor, table_name: str, column_name: str) -> bool:
    cursor.execute(
        """
        SELECT COUNT(*) FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = %s AND COLUMN_NAME = %s
        """,
        (table_name, column_name),
    )
    return (cursor.fetchone() or [0])[0] > 0


def add_column_if_missing(cursor, table_name: str, column_name: str, definition: str) -> None:
    if not table_exists(cursor, table_name):
        return
    if column_exists(cursor, table_name, column_name):
        return
    cursor.execute(f"ALTER TABLE `{table_name}` ADD COLUMN `{column_name}` {definition}")


def pre_upgrade_schema(cursor) -> None:
    # Ensure old databases can execute the latest init.sql inserts.
    add_column_if_missing(cursor, "video", "tenant_user_id", "BIGINT NOT NULL DEFAULT 1 AFTER id")
    add_column_if_missing(cursor, "user", "tenant_user_id", "BIGINT NOT NULL DEFAULT 1 AFTER user_id")
    add_column_if_missing(cursor, "comment", "tenant_user_id", "BIGINT NOT NULL DEFAULT 1 AFTER comment_id")
    add_column_if_missing(cursor, "user_behavior", "tenant_user_id", "BIGINT NOT NULL DEFAULT 1 AFTER id")
    add_column_if_missing(cursor, "video_statistics", "tenant_user_id", "BIGINT NOT NULL DEFAULT 1 AFTER id")
    add_column_if_missing(cursor, "user_interest_result", "tenant_user_id", "BIGINT NOT NULL DEFAULT 1 AFTER id")
    add_column_if_missing(cursor, "import_job", "tenant_user_id", "BIGINT NOT NULL DEFAULT 1 AFTER id")

    if table_exists(cursor, "video") and column_exists(cursor, "video", "tenant_user_id"):
        cursor.execute("UPDATE video SET tenant_user_id = 1 WHERE tenant_user_id IS NULL OR tenant_user_id <= 0")
    if table_exists(cursor, "user") and column_exists(cursor, "user", "tenant_user_id"):
        cursor.execute("UPDATE `user` SET tenant_user_id = 1 WHERE tenant_user_id IS NULL OR tenant_user_id <= 0")
    if table_exists(cursor, "comment") and column_exists(cursor, "comment", "tenant_user_id"):
        cursor.execute("UPDATE comment SET tenant_user_id = 1 WHERE tenant_user_id IS NULL OR tenant_user_id <= 0")
    if table_exists(cursor, "user_behavior") and column_exists(cursor, "user_behavior", "tenant_user_id"):
        cursor.execute("UPDATE user_behavior SET tenant_user_id = 1 WHERE tenant_user_id IS NULL OR tenant_user_id <= 0")
    if table_exists(cursor, "video_statistics") and column_exists(cursor, "video_statistics", "tenant_user_id"):
        cursor.execute("UPDATE video_statistics SET tenant_user_id = 1 WHERE tenant_user_id IS NULL OR tenant_user_id <= 0")
    if table_exists(cursor, "user_interest_result") and column_exists(cursor, "user_interest_result", "tenant_user_id"):
        cursor.execute("UPDATE user_interest_result SET tenant_user_id = 1 WHERE tenant_user_id IS NULL OR tenant_user_id <= 0")
    if table_exists(cursor, "import_job") and column_exists(cursor, "import_job", "tenant_user_id"):
        cursor.execute("UPDATE import_job SET tenant_user_id = 1 WHERE tenant_user_id IS NULL OR tenant_user_id <= 0")


def main() -> None:
    parser = argparse.ArgumentParser(description="Initialize video_analysis database without mysql CLI")
    parser.add_argument("--host", default=os.getenv("MYSQL_HOST", "localhost"))
    parser.add_argument("--port", type=int, default=int(os.getenv("MYSQL_PORT", "3306")))
    parser.add_argument("--user", default=os.getenv("MYSQL_USER", "root"))
    parser.add_argument("--password", default=os.getenv("MYSQL_PASSWORD", ""))
    parser.add_argument("--sql-file", default=str(Path(__file__).resolve().parents[1] / "database" / "init.sql"))
    args = parser.parse_args()

    sql_path = Path(args.sql_file)
    if not sql_path.exists():
        raise FileNotFoundError(f"SQL file not found: {sql_path}")

    sql_text = sql_path.read_text(encoding="utf-8")
    statements = split_sql_statements(sql_text)
    if not statements:
        print("No SQL statements parsed.")
        return

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
        cursor.execute("CREATE DATABASE IF NOT EXISTS video_analysis DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci")
        cursor.execute("USE video_analysis")
        pre_upgrade_schema(cursor)
        for stmt in statements:
            cursor.execute(stmt)
        conn.commit()
        print(f"Database initialized successfully, executed statements: {len(statements)}")
    finally:
        conn.close()


if __name__ == "__main__":
    main()

