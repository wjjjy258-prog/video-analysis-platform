from __future__ import annotations

import hashlib
import random
from datetime import datetime, timedelta
from typing import Any


def parse_video_items(raw_data: dict[str, Any]) -> list[dict[str, Any]]:
    data = raw_data.get("data", {})
    items = data.get("list", [])
    result = []

    for item in items:
        video_id = int(item.get("aid") or item.get("id") or 0)
        if video_id == 0:
            continue
        owner = item.get("owner") or {}
        stat = item.get("stat") or {}
        pub_ts = item.get("pubdate")
        publish_time = datetime.now() if not pub_ts else datetime.fromtimestamp(pub_ts)
        result.append(
            {
                "id": video_id,
                "title": str(item.get("title", "")).strip()[:255],
                "author": str(owner.get("name", "unknown"))[:100],
                "source_platform": "bilibili",
                "category": str(item.get("tname", "其他"))[:60],
                "play_count": int(stat.get("view", 0)),
                "like_count": int(stat.get("like", 0)),
                "comment_count": int(stat.get("reply", 0)),
                "publish_time": publish_time.strftime("%Y-%m-%d %H:%M:%S"),
                "owner_mid": int(owner.get("mid") or video_id),
            }
        )

    return result


def build_users(videos: list[dict[str, Any]]) -> list[dict[str, Any]]:
    users_map: dict[int, dict[str, Any]] = {}
    for item in videos:
        user_id = int(item["owner_mid"])
        users_map[user_id] = {
            "user_id": user_id,
            "user_name": item["author"],
            "fans": max(50, item["play_count"] // random.randint(80, 160)),
            "follow": max(20, item["play_count"] // random.randint(150, 260)),
            "level": random.randint(3, 7),
        }
    return list(users_map.values())


def build_mock_comments(videos: list[dict[str, Any]], users: list[dict[str, Any]]) -> list[dict[str, Any]]:
    contents = [
        "讲得很清晰，学到了。",
        "这个视频质量不错。",
        "支持一下作者！",
        "数据分析思路很好。",
        "收藏了，回头复习。",
    ]
    now = datetime.now()
    comments: list[dict[str, Any]] = []
    for item in videos[:20]:
        for _ in range(random.randint(1, 3)):
            user = random.choice(users)
            comments.append(
                {
                    "video_id": item["id"],
                    "user_id": user["user_id"],
                    "content": random.choice(contents),
                    "like_count": random.randint(0, 50),
                    "time": (now - timedelta(minutes=random.randint(10, 4000))).strftime("%Y-%m-%d %H:%M:%S"),
                }
            )
    return comments


def build_mock_behaviors(videos: list[dict[str, Any]], users: list[dict[str, Any]]) -> list[dict[str, Any]]:
    actions = ["play", "like", "comment"]
    now = datetime.now()
    behaviors: list[dict[str, Any]] = []
    for user in users:
        sample = random.sample(videos, k=min(len(videos), random.randint(3, 8)))
        for video in sample:
            behaviors.append(
                {
                    "user_id": user["user_id"],
                    "video_id": video["id"],
                    "action": random.choices(actions, weights=[7, 2, 1])[0],
                    "time": (now - timedelta(minutes=random.randint(10, 9000))).strftime("%Y-%m-%d %H:%M:%S"),
                }
            )
    return behaviors


def fallback_demo_videos() -> list[dict[str, Any]]:
    now = datetime.now()
    demo = [
        ("Spark SQL 实战精讲", "大数据课堂", "技术", 420000, 26000, 1800, 101),
        ("Vue3 数据看板从0到1", "前端研究社", "技术", 390000, 24100, 1500, 102),
        ("年度热门影视混剪", "镜头工坊", "影视", 670000, 43000, 3300, 103),
        ("游戏高分局复盘", "电竞笔记", "游戏", 520000, 30100, 2200, 104),
        ("城市旅行日记", "生活记录者", "生活", 240000, 15000, 870, 105),
        ("AI 行业周报", "科技观察局", "新闻", 210000, 12900, 740, 106),
    ]
    result = []
    for idx, row in enumerate(demo, start=1):
        result.append(
            {
                "id": 9000 + idx,
                "title": row[0],
                "author": row[1],
                "source_platform": "mock",
                "category": row[2],
                "play_count": row[3],
                "like_count": row[4],
                "comment_count": row[5],
                "publish_time": (now - timedelta(days=idx * 3)).strftime("%Y-%m-%d %H:%M:%S"),
                "owner_mid": 60000 + row[6],
            }
        )
    return result


def stable_numeric_id(text: str, namespace: str = "default") -> int:
    """
    根据文本键构建稳定且可落入 BIGINT 的正整数 ID。
    """
    digest = hashlib.sha1(f"{namespace}:{text}".encode("utf-8")).hexdigest()
    value = int(digest[:15], 16)  # 【说明】最大约 1e18，可安全落在有符号 BIGINT 范围内。
    if value <= 0:
        return 1
    return value


def merge_users(*user_groups: list[dict[str, Any]]) -> list[dict[str, Any]]:
    merged: dict[int, dict[str, Any]] = {}
    for group in user_groups:
        for user in group:
            user_id = int(user["user_id"])
            existing = merged.get(user_id)
            if existing is None:
                merged[user_id] = {
                    "user_id": user_id,
                    "user_name": str(user.get("user_name", f"user_{user_id}"))[:100],
                    "fans": int(user.get("fans", 0)),
                    "follow": int(user.get("follow", 0)),
                    "level": int(user.get("level", 1)),
                }
            else:
                if len(str(user.get("user_name", ""))) > len(existing["user_name"]):
                    existing["user_name"] = str(user.get("user_name"))[:100]
                existing["fans"] = max(existing["fans"], int(user.get("fans", 0)))
                existing["follow"] = max(existing["follow"], int(user.get("follow", 0)))
                existing["level"] = max(existing["level"], int(user.get("level", 1)))
    return list(merged.values())


def build_behaviors_from_crawl(
    videos: list[dict[str, Any]],
    users: list[dict[str, Any]],
    comments: list[dict[str, Any]],
) -> list[dict[str, Any]]:
    if not videos or not users:
        return []

    now = datetime.now()
    user_ids = [int(u["user_id"]) for u in users]
    behaviors: list[dict[str, Any]] = []

    for video in videos:
        video_id = int(video["id"])
        play_count = int(video.get("play_count", 0))
        like_count = int(video.get("like_count", 0))
        comment_count = int(video.get("comment_count", 0))

        sampled_play = min(120, max(8, play_count // 20000 if play_count > 0 else 12))
        sampled_like = min(50, max(2, like_count // 10000 if like_count > 0 else 4))
        sampled_comment = min(40, max(1, comment_count // 8000 if comment_count > 0 else 2))

        for _ in range(sampled_play):
            behaviors.append(
                {
                    "user_id": random.choice(user_ids),
                    "video_id": video_id,
                    "action": "play",
                    "time": (now - timedelta(minutes=random.randint(5, 60 * 24 * 7))).strftime("%Y-%m-%d %H:%M:%S"),
                }
            )

        for _ in range(sampled_like):
            behaviors.append(
                {
                    "user_id": random.choice(user_ids),
                    "video_id": video_id,
                    "action": "like",
                    "time": (now - timedelta(minutes=random.randint(5, 60 * 24 * 7))).strftime("%Y-%m-%d %H:%M:%S"),
                }
            )

        for _ in range(sampled_comment):
            behaviors.append(
                {
                    "user_id": random.choice(user_ids),
                    "video_id": video_id,
                    "action": "comment",
                    "time": (now - timedelta(minutes=random.randint(5, 60 * 24 * 7))).strftime("%Y-%m-%d %H:%M:%S"),
                }
            )

    for comment in comments:
        behaviors.append(
            {
                "user_id": int(comment["user_id"]),
                "video_id": int(comment["video_id"]),
                "action": "comment",
                "time": comment["time"],
            }
        )

    return behaviors
