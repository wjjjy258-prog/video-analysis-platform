from __future__ import annotations

import hashlib
import os
from contextlib import contextmanager
from datetime import datetime
from typing import Iterable

import mysql.connector


class MySQLUtil:
    def __init__(
        self,
        host: str = "localhost",
        port: int = 3306,
        user: str = "root",
        password: str | None = None,
        database: str = "video_analysis",
        tenant_user_id: int = 1,
    ) -> None:
        self._config = {
            "host": host,
            "port": port,
            "user": user,
            "password": password if password is not None else os.getenv("MYSQL_PASSWORD", ""),
            "database": database,
            "autocommit": False,
        }
        self._tenant_user_id = max(1, int(tenant_user_id or 1))
        self._video_id_map: dict[str, int] = {}
        self._user_id_map: dict[str, int] = {}

    @contextmanager
    def get_conn(self):
        conn = mysql.connector.connect(**self._config)
        try:
            yield conn
        finally:
            conn.close()

    def _stable_id(self, namespace: str, raw: str) -> int:
        seed = f"tenant:{self._tenant_user_id}:{namespace}:{raw}"
        digest = hashlib.sha1(seed.encode("utf-8")).digest()
        value = int.from_bytes(digest[:8], byteorder="big", signed=False)
        value = value & 0x7FFFFFFFFFFFFFFF
        return value or 1

    def _scoped_video_id(self, raw_video_id: str, dedupe_key: str) -> int:
        raw = (raw_video_id or "").strip()
        key = raw or dedupe_key
        scoped = self._stable_id("video", key)
        if raw:
            self._video_id_map[raw] = scoped
        return scoped

    def _scoped_user_id(self, raw_user_id: str, fallback_name: str) -> int:
        raw = (raw_user_id or "").strip()
        key = raw or fallback_name.strip()
        scoped = self._stable_id("author", key)
        if raw:
            self._user_id_map[raw] = scoped
        return scoped

    def _resolve_video_id(self, raw_video_id: str) -> int:
        raw = (raw_video_id or "").strip()
        if raw in self._video_id_map:
            return self._video_id_map[raw]
        return self._stable_id("video", raw or "unknown")

    def _resolve_user_id(self, raw_user_id: str) -> int:
        raw = (raw_user_id or "").strip()
        if raw in self._user_id_map:
            return self._user_id_map[raw]
        return self._stable_id("author", raw or "unknown")

    def upsert_videos(self, videos: Iterable[dict]) -> int:
        def normalize_url(url: str | None) -> str | None:
            if not url:
                return None
            cleaned = str(url).strip()
            if "?" in cleaned:
                cleaned = cleaned.split("?", 1)[0]
            if "#" in cleaned:
                cleaned = cleaned.split("#", 1)[0]
            return cleaned.rstrip("/") or None

        def build_dedupe_key(platform: str, source_url: str | None, title: str, author: str) -> str:
            if source_url:
                seed = f"{platform.lower()}|{source_url.lower()}"
            else:
                seed = f"{platform.lower()}|{title.strip().lower()}|{author.strip().lower()}"
            return hashlib.sha1(seed.encode("utf-8")).hexdigest()

        def quality_score(title: str, author: str, platform: str, source_url: str | None, play: int, like: int, comment: int) -> float:
            score = 0.0
            if title.strip():
                score += 24
            if author.strip():
                score += 14
            if platform.strip():
                score += 10
            if play > 0:
                score += 18
            if like >= 0:
                score += 8
            if comment >= 0:
                score += 8
            if source_url:
                score += 8
            if play > 0 and like > play:
                score -= 10
            if play > 0 and comment > play:
                score -= 8
            score = max(0.0, min(100.0, score))
            return round(score, 2)

        rows = []
        for item in videos:
            source_platform = str(item.get("source_platform", "unknown"))
            source_url = normalize_url(item.get("source_url"))
            title = str(item["title"])
            author = str(item["author"])
            dedupe_key = build_dedupe_key(source_platform, source_url, title, author)
            scoped_video_id = self._scoped_video_id(str(item.get("id", "")), dedupe_key)

            rows.append(
                (
                    self._tenant_user_id,
                    scoped_video_id,
                    dedupe_key,
                    title,
                    author,
                    source_platform,
                    source_url,
                    item["category"],
                    int(item["play_count"]),
                    int(item["like_count"]),
                    int(item["comment_count"]),
                    item["publish_time"],
                    item.get("import_type", "crawler_url" if source_url else "spider_import"),
                    item.get("source_file"),
                    item.get("import_time", datetime.now().strftime("%Y-%m-%d %H:%M:%S")),
                    float(item.get("data_quality_score", quality_score(
                        title,
                        author,
                        source_platform,
                        source_url,
                        int(item["play_count"]),
                        int(item["like_count"]),
                        int(item["comment_count"]),
                    ))),
                )
            )

        if not rows:
            return 0

        sql = """
            INSERT INTO video
            (tenant_user_id, id, dedupe_key, title, author, source_platform, source_url, category, play_count, like_count, comment_count, publish_time,
             import_type, source_file, import_time, data_quality_score)
            VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s)
            ON DUPLICATE KEY UPDATE
                title = VALUES(title),
                author = VALUES(author),
                source_platform = VALUES(source_platform),
                source_url = VALUES(source_url),
                category = VALUES(category),
                play_count = GREATEST(play_count, VALUES(play_count)),
                like_count = GREATEST(like_count, VALUES(like_count)),
                comment_count = GREATEST(comment_count, VALUES(comment_count)),
                publish_time = LEAST(publish_time, VALUES(publish_time)),
                import_type = VALUES(import_type),
                source_file = VALUES(source_file),
                import_time = VALUES(import_time),
                data_quality_score = GREATEST(data_quality_score, VALUES(data_quality_score))
        """
        with self.get_conn() as conn:
            cursor = conn.cursor()
            cursor.executemany(sql, rows)
            conn.commit()
            return cursor.rowcount

    def upsert_users(self, users: Iterable[dict]) -> int:
        rows = []
        for item in users:
            raw_user_id = str(item.get("user_id", "")).strip()
            user_name = str(item["user_name"])
            scoped_user_id = self._scoped_user_id(raw_user_id, user_name)
            rows.append(
                (
                    self._tenant_user_id,
                    scoped_user_id,
                    user_name,
                    int(item["fans"]),
                    int(item["follow"]),
                    int(item["level"]),
                )
            )

        if not rows:
            return 0

        sql = """
            INSERT INTO `user`
            (tenant_user_id, user_id, user_name, fans, `follow`, `level`)
            VALUES (%s, %s, %s, %s, %s, %s)
            ON DUPLICATE KEY UPDATE
                user_name = VALUES(user_name),
                fans = VALUES(fans),
                `follow` = VALUES(`follow`),
                `level` = VALUES(`level`)
        """
        with self.get_conn() as conn:
            cursor = conn.cursor()
            cursor.executemany(sql, rows)
            conn.commit()
            return cursor.rowcount

    def insert_comments(self, comments: Iterable[dict]) -> int:
        rows = []
        for item in comments:
            rows.append(
                (
                    self._tenant_user_id,
                    self._resolve_video_id(str(item.get("video_id", ""))),
                    self._resolve_user_id(str(item.get("user_id", ""))),
                    item["content"],
                    int(item["like_count"]),
                    item["time"],
                )
            )

        if not rows:
            return 0

        sql = """
            INSERT INTO `comment`
            (tenant_user_id, video_id, user_id, content, like_count, time)
            VALUES (%s, %s, %s, %s, %s, %s)
        """
        with self.get_conn() as conn:
            cursor = conn.cursor()
            cursor.executemany(sql, rows)
            conn.commit()
            return cursor.rowcount

    def insert_behaviors(self, behaviors: Iterable[dict]) -> int:
        rows = []
        for item in behaviors:
            rows.append(
                (
                    self._tenant_user_id,
                    self._resolve_user_id(str(item.get("user_id", ""))),
                    self._resolve_video_id(str(item.get("video_id", ""))),
                    item["action"],
                    item["time"],
                )
            )

        if not rows:
            return 0

        sql = """
            INSERT INTO user_behavior
            (tenant_user_id, user_id, video_id, action, time)
            VALUES (%s, %s, %s, %s, %s)
        """
        with self.get_conn() as conn:
            cursor = conn.cursor()
            cursor.executemany(sql, rows)
            conn.commit()
            return cursor.rowcount

