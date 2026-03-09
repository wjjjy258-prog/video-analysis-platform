from __future__ import annotations

import os
from contextlib import contextmanager
from typing import Iterable

import mysql.connector


def _get_config() -> dict:
    return {
        "host": os.getenv("MYSQL_HOST", "localhost"),
        "port": int(os.getenv("MYSQL_PORT", "3306")),
        "user": os.getenv("MYSQL_USER", "root"),
        "password": os.getenv("MYSQL_PASSWORD", ""),
        "database": os.getenv("MYSQL_DATABASE", "video_analysis"),
        "autocommit": False,
    }


@contextmanager
def get_conn():
    conn = mysql.connector.connect(**_get_config())
    try:
        yield conn
    finally:
        conn.close()


def execute_sql(sql: str, params: tuple | None = None) -> int:
    with get_conn() as conn:
        cursor = conn.cursor()
        cursor.execute(sql, params)
        conn.commit()
        return cursor.rowcount


def execute_many(sql: str, rows: Iterable[tuple]) -> int:
    rows = list(rows)
    if not rows:
        return 0
    with get_conn() as conn:
        cursor = conn.cursor()
        cursor.executemany(sql, rows)
        conn.commit()
        return cursor.rowcount


def query_all(sql: str, params: tuple | None = None) -> list[dict]:
    with get_conn() as conn:
        cursor = conn.cursor(dictionary=True)
        cursor.execute(sql, params)
        return cursor.fetchall()

