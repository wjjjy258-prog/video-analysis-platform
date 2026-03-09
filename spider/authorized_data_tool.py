from __future__ import annotations

import csv
import json
from dataclasses import dataclass
from datetime import datetime
from pathlib import Path
from typing import Any

from parser import stable_numeric_id


REQUIRED_FIELDS = [
    "platform",
    "video_id",
    "title",
    "author",
    "category",
    "play_count",
    "like_count",
    "comment_count",
    "publish_time",
    "author_id",
    "author_fans",
    "author_follow",
    "author_level",
]

SUPPORTED_PLATFORMS = {
    "bilibili",
    "douyin",
    "kuaishou",
    "xiaohongshu",
    "xigua",
    "weibo",
    "youtube",
    "tiktok",
    "acfun",
}


@dataclass
class OfficialIssue:
    row_no: int
    reason: str
    value: str


def _safe_int(v: Any, default: int = 0) -> int:
    try:
        if v is None:
            return default
        if isinstance(v, str):
            txt = v.replace(",", "").strip()
            if txt == "":
                return default
            return int(float(txt))
        return int(v)
    except Exception:
        return default


def _normalize_time(v: Any) -> str:
    if v is None:
        return datetime.now().strftime("%Y-%m-%d %H:%M:%S")
    txt = str(v).strip()
    if txt == "":
        return datetime.now().strftime("%Y-%m-%d %H:%M:%S")
    for fmt in ("%Y-%m-%d %H:%M:%S", "%Y-%m-%d", "%Y/%m/%d %H:%M:%S", "%Y/%m/%d"):
        try:
            return datetime.strptime(txt, fmt).strftime("%Y-%m-%d %H:%M:%S")
        except Exception:
            pass
    try:
        return datetime.fromisoformat(txt.replace("Z", "+00:00")).strftime("%Y-%m-%d %H:%M:%S")
    except Exception:
        return datetime.now().strftime("%Y-%m-%d %H:%M:%S")


def generate_official_csv_template(output_path: str) -> Path:
    path = Path(output_path).expanduser().resolve()
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8-sig", newline="") as f:
        writer = csv.DictWriter(f, fieldnames=REQUIRED_FIELDS)
        writer.writeheader()
        writer.writerow(
            {
                "platform": "bilibili",
                "video_id": "1000000001",
                "title": "sample_title_replace_me",
                "author": "sample_author",
                "category": "tech",
                "play_count": "1000",
                "like_count": "200",
                "comment_count": "50",
                "publish_time": "2026-03-01 12:00:00",
                "author_id": "2000000001",
                "author_fans": "15000",
                "author_follow": "350",
                "author_level": "5",
            }
        )
    return path


def generate_official_json_template(output_path: str) -> Path:
    path = Path(output_path).expanduser().resolve()
    path.parent.mkdir(parents=True, exist_ok=True)
    payload = {
        "videos": [
            {
                "platform": "douyin",
                "video_id": "3000000001",
                "title": "sample_title_replace_me",
                "author": "sample_author",
                "category": "short_video",
                "play_count": 1200,
                "like_count": 300,
                "comment_count": 45,
                "publish_time": "2026-03-02 08:00:00",
                "author_id": "4000000001",
                "author_fans": 18000,
                "author_follow": 500,
                "author_level": 4,
            },
            {
                "platform": "xiaohongshu",
                "video_id": "3000000002",
                "title": "sample_title_replace_me",
                "author": "sample_author_2",
                "category": "lifestyle",
                "play_count": 980,
                "like_count": 210,
                "comment_count": 33,
                "publish_time": "2026-03-03 09:00:00",
                "author_id": "4000000002",
                "author_fans": 8600,
                "author_follow": 260,
                "author_level": 3,
            },
        ]
    }
    path.write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")
    return path


def _validate_rows(rows: list[dict[str, Any]]) -> tuple[list[dict[str, Any]], list[OfficialIssue]]:
    issues: list[OfficialIssue] = []
    normalized: list[dict[str, Any]] = []
    for i, row in enumerate(rows, start=2):  # header is row 1 for csv; good enough for json
        row_obj = {k: row.get(k) for k in REQUIRED_FIELDS}
        for field in REQUIRED_FIELDS:
            if row_obj.get(field) is None or str(row_obj.get(field)).strip() == "":
                issues.append(OfficialIssue(i, f"missing field: {field}", str(row_obj)))
        platform = str(row_obj.get("platform", "")).lower().strip()
        if platform not in SUPPORTED_PLATFORMS:
            issues.append(OfficialIssue(i, f"platform must be one of {sorted(SUPPORTED_PLATFORMS)}", str(row_obj.get("platform"))))

        video_id_txt = str(row_obj.get("video_id", "")).strip()
        author_id_txt = str(row_obj.get("author_id", "")).strip()
        if video_id_txt == "":
            issues.append(OfficialIssue(i, "video_id is empty", ""))
        if author_id_txt == "":
            issues.append(OfficialIssue(i, "author_id is empty", ""))

        normalized.append(
            {
                "platform": platform if platform in SUPPORTED_PLATFORMS else "bilibili",
                "video_id": video_id_txt,
                "title": str(row_obj.get("title", "")).strip(),
                "author": str(row_obj.get("author", "")).strip(),
                "category": str(row_obj.get("category", "")).strip() or "other",
                "play_count": max(0, _safe_int(row_obj.get("play_count"), 0)),
                "like_count": max(0, _safe_int(row_obj.get("like_count"), 0)),
                "comment_count": max(0, _safe_int(row_obj.get("comment_count"), 0)),
                "publish_time": _normalize_time(row_obj.get("publish_time")),
                "author_id": author_id_txt,
                "author_fans": max(0, _safe_int(row_obj.get("author_fans"), 0)),
                "author_follow": max(0, _safe_int(row_obj.get("author_follow"), 0)),
                "author_level": max(1, _safe_int(row_obj.get("author_level"), 1)),
            }
        )
    return normalized, issues


def validate_official_csv(path: str) -> tuple[list[dict[str, Any]], list[OfficialIssue], Path]:
    p = Path(path).expanduser().resolve()
    if not p.exists():
        raise FileNotFoundError(f"Official CSV file not found: {p}")
    with p.open("r", encoding="utf-8-sig", newline="") as f:
        reader = csv.DictReader(f)
        missing_headers = [h for h in REQUIRED_FIELDS if h not in (reader.fieldnames or [])]
        if missing_headers:
            issues = [OfficialIssue(1, "missing headers", ",".join(missing_headers))]
            return [], issues, p
        rows = [r for r in reader if any(str(v).strip() for v in r.values())]
    normalized, issues = _validate_rows(rows)
    return normalized, issues, p


def validate_official_json(path: str) -> tuple[list[dict[str, Any]], list[OfficialIssue], Path]:
    p = Path(path).expanduser().resolve()
    if not p.exists():
        raise FileNotFoundError(f"Official JSON file not found: {p}")
    raw = json.loads(p.read_text(encoding="utf-8"))
    if isinstance(raw, dict):
        rows = raw.get("videos") or []
    elif isinstance(raw, list):
        rows = raw
    else:
        rows = []
    if not isinstance(rows, list):
        rows = []
    normalized, issues = _validate_rows(rows)
    return normalized, issues, p


def build_entities_from_official_rows(rows: list[dict[str, Any]]) -> tuple[list[dict[str, Any]], list[dict[str, Any]], list[dict]]:
    videos: list[dict[str, Any]] = []
    users_map: dict[int, dict[str, Any]] = {}
    for row in rows:
        platform = row["platform"]
        raw_video_id = row["video_id"]
        raw_author_id = row["author_id"]

        video_id = (
            _safe_int(raw_video_id, 0)
            if str(raw_video_id).isdigit()
            else stable_numeric_id(str(raw_video_id), f"{platform}_video")
        )
        author_id = (
            _safe_int(raw_author_id, 0)
            if str(raw_author_id).isdigit()
            else stable_numeric_id(str(raw_author_id), f"{platform}_author")
        )

        videos.append(
            {
                "id": video_id,
                "title": row["title"][:255],
                "author": row["author"][:100],
                "source_platform": row["platform"],
                "category": row["category"][:60],
                "play_count": row["play_count"],
                "like_count": row["like_count"],
                "comment_count": row["comment_count"],
                "publish_time": row["publish_time"],
                "owner_mid": author_id,
            }
        )
        users_map[author_id] = {
            "user_id": author_id,
            "user_name": row["author"][:100],
            "fans": row["author_fans"],
            "follow": row["author_follow"],
            "level": row["author_level"],
        }

    return videos, list(users_map.values()), []
