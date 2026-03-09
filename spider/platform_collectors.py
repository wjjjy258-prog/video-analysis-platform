from __future__ import annotations

import json
import re
from dataclasses import dataclass
from datetime import datetime
from html import unescape
from typing import Any
from urllib.parse import unquote, urlparse

import requests

from parser import stable_numeric_id


DEFAULT_HEADERS = {
    "User-Agent": (
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
        "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Safari/537.36"
    )
}

HOST_PLATFORM_HINTS: list[tuple[str, str]] = [
    ("bilibili.com", "bilibili"),
    ("b23.tv", "bilibili"),
    ("douyin.com", "douyin"),
    ("iesdouyin.com", "douyin"),
    ("kuaishou.com", "kuaishou"),
    ("kwai.com", "kuaishou"),
    ("xiaohongshu.com", "xiaohongshu"),
    ("xhslink.com", "xiaohongshu"),
    ("ixigua.com", "xigua"),
    ("xigua.com", "xigua"),
    ("weibo.com", "weibo"),
    ("youtube.com", "youtube"),
    ("youtu.be", "youtube"),
    ("tiktok.com", "tiktok"),
    ("acfun.cn", "acfun"),
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

PLATFORM_CATEGORY_HINTS = {
    "bilibili": "综合视频",
    "douyin": "短视频",
    "kuaishou": "短视频",
    "xiaohongshu": "生活方式",
    "xigua": "中长视频",
    "weibo": "社会热点",
    "youtube": "综合视频",
    "tiktok": "短视频",
    "acfun": "二次元",
}


@dataclass
class CrawlResult:
    videos: list[dict[str, Any]]
    users: list[dict[str, Any]]
    comments: list[dict[str, Any]]
    platform: str
    source_url: str


def detect_platform(url: str) -> str:
    host = (urlparse(url).hostname or "").lower()
    for keyword, platform in HOST_PLATFORM_HINTS:
        if keyword in host:
            return platform
    return "unknown"


def crawl_by_url(url: str, platform: str = "auto", timeout: int = 15) -> CrawlResult:
    if not url.startswith(("http://", "https://")):
        url = "https://" + url

    resolved_platform = detect_platform(url) if platform == "auto" else platform.lower()
    if resolved_platform not in SUPPORTED_PLATFORMS:
        raise ValueError(f"Unsupported platform for URL: {url}")
    if resolved_platform == "bilibili":
        return crawl_bilibili(url, timeout=timeout)
    if resolved_platform == "douyin":
        return crawl_douyin(url, timeout=timeout)
    return crawl_generic_by_meta(url, platform=resolved_platform, timeout=timeout)


def _safe_int(value: Any, default: int = 0) -> int:
    try:
        if value is None:
            return default
        if isinstance(value, str):
            cleaned = value.replace(",", "").strip()
            if cleaned == "":
                return default
            return int(float(cleaned))
        return int(value)
    except Exception:
        return default


def _format_ts(ts: Any) -> str:
    value = _safe_int(ts, 0)
    if value <= 0:
        return datetime.now().strftime("%Y-%m-%d %H:%M:%S")
    return datetime.fromtimestamp(value).strftime("%Y-%m-%d %H:%M:%S")


def _resolve_final_url(url: str, timeout: int = 15) -> tuple[str, str]:
    resp = requests.get(url, headers=DEFAULT_HEADERS, allow_redirects=True, timeout=timeout)
    resp.raise_for_status()
    return resp.url, resp.text


def crawl_bilibili(url: str, timeout: int = 15) -> CrawlResult:
    final_url, html = _resolve_final_url(url, timeout=timeout)
    bvid_match = re.search(r"(BV[0-9A-Za-z]{10})", final_url) or re.search(r"(BV[0-9A-Za-z]{10})", html)
    aid_match = re.search(r"/video/av(\d+)", final_url, re.IGNORECASE)

    params = {}
    if bvid_match:
        params["bvid"] = bvid_match.group(1)
    elif aid_match:
        params["aid"] = aid_match.group(1)
    else:
        raise ValueError("Cannot parse Bilibili video id from URL.")

    view_resp = requests.get(
        "https://api.bilibili.com/x/web-interface/view",
        params=params,
        headers=DEFAULT_HEADERS,
        timeout=timeout,
    )
    view_resp.raise_for_status()
    payload = view_resp.json()
    if payload.get("code") != 0:
        raise ValueError(f"Bilibili API failed: code={payload.get('code')}")

    data = payload.get("data") or {}
    owner = data.get("owner") or {}
    stat = data.get("stat") or {}
    aid = _safe_int(data.get("aid"), stable_numeric_id(str(params), "bilibili_video"))
    owner_mid = _safe_int(owner.get("mid"), stable_numeric_id(owner.get("name", "bili_user"), "bilibili_user"))

    user = {
        "user_id": owner_mid,
        "user_name": str(owner.get("name", "bilibili_user"))[:100],
        "fans": 0,
        "follow": 0,
        "level": _safe_int(owner.get("level"), 1),
    }

    try:
        relation_resp = requests.get(
            "https://api.bilibili.com/x/relation/stat",
            params={"vmid": owner_mid},
            headers=DEFAULT_HEADERS,
            timeout=timeout,
        )
        relation_data = relation_resp.json().get("data") or {}
        user["fans"] = _safe_int(relation_data.get("follower"), 0)
        user["follow"] = _safe_int(relation_data.get("following"), 0)
    except Exception:
        user["fans"] = max(0, _safe_int(stat.get("view"), 0) // 120)
        user["follow"] = max(0, _safe_int(stat.get("view"), 0) // 250)

    video = {
        "id": aid,
        "title": str(data.get("title", "未命名视频"))[:255],
        "author": user["user_name"],
        "source_platform": "bilibili",
        "category": str(data.get("tname", "Bilibili"))[:60],
        "play_count": _safe_int(stat.get("view"), 0),
        "like_count": _safe_int(stat.get("like"), 0),
        "comment_count": _safe_int(stat.get("reply"), 0),
        "publish_time": _format_ts(data.get("pubdate")),
        "owner_mid": owner_mid,
    }

    comments: list[dict[str, Any]] = []
    comment_users: dict[int, dict[str, Any]] = {}
    try:
        reply_resp = requests.get(
            "https://api.bilibili.com/x/v2/reply/main",
            params={"type": 1, "oid": aid, "next": 0, "mode": 3, "ps": 20},
            headers=DEFAULT_HEADERS,
            timeout=timeout,
        )
        reply_payload = reply_resp.json()
        replies = (reply_payload.get("data") or {}).get("replies") or []
        for item in replies:
            member = item.get("member") or {}
            mid = _safe_int(member.get("mid"), 0)
            uname = str(member.get("uname", f"user_{mid}"))[:100]
            if mid > 0:
                comment_users[mid] = {
                    "user_id": mid,
                    "user_name": uname,
                    "fans": 0,
                    "follow": 0,
                    "level": _safe_int((member.get("level_info") or {}).get("current_level"), 1),
                }
            comments.append(
                {
                    "video_id": aid,
                    "user_id": mid if mid > 0 else owner_mid,
                    "content": str((item.get("content") or {}).get("message", ""))[:1000],
                    "like_count": _safe_int(item.get("like"), 0),
                    "time": _format_ts(item.get("ctime")),
                }
            )
    except Exception:
        comments = []

    users = [user] + list(comment_users.values())
    return CrawlResult(videos=[video], users=users, comments=comments, platform="bilibili", source_url=final_url)


def _iter_dicts(obj: Any):
    if isinstance(obj, dict):
        yield obj
        for v in obj.values():
            yield from _iter_dicts(v)
    elif isinstance(obj, list):
        for item in obj:
            yield from _iter_dicts(item)


def _extract_text(html: str, pattern: str) -> str:
    match = re.search(pattern, html, re.IGNORECASE | re.DOTALL)
    if not match:
        return ""
    return unescape(match.group(1)).strip()


def _parse_ld_json(html: str) -> dict[str, Any]:
    scripts = re.findall(
        r'<script[^>]*type=["\']application/ld\+json["\'][^>]*>(.*?)</script>',
        html,
        flags=re.IGNORECASE | re.DOTALL,
    )
    for script in scripts:
        txt = script.strip()
        if not txt:
            continue
        try:
            data = json.loads(txt)
        except Exception:
            continue
        if isinstance(data, dict):
            return data
    return {}


def _extract_meta_content(html: str, key: str, attr: str = "property") -> str:
    pattern = rf'<meta[^>]*{attr}=["\']{re.escape(key)}["\'][^>]*content=["\'](.*?)["\']'
    return _extract_text(html, pattern)


def _meta_first(html: str, keys: list[tuple[str, str]]) -> str:
    for attr, key in keys:
        value = _extract_meta_content(html, key, attr=attr)
        if value:
            return value
    return ""


def _parse_count_text(text: str) -> int:
    if not text:
        return 0
    txt = text.strip().replace(",", "").replace("，", "")
    m = re.search(r"(\d+(?:\.\d+)?)", txt)
    if not m:
        return 0
    value = float(m.group(1))
    if "万" in txt:
        value *= 10_000
    elif "亿" in txt:
        value *= 100_000_000
    elif "k" in txt.lower():
        value *= 1_000
    elif "m" in txt.lower():
        value *= 1_000_000
    return _safe_int(value, 0)


def _parse_render_data(html: str) -> dict[str, Any]:
    match = re.search(
        r'<script[^>]*id=["\']RENDER_DATA["\'][^>]*>(.*?)</script>',
        html,
        flags=re.IGNORECASE | re.DOTALL,
    )
    if not match:
        return {}
    raw = match.group(1).strip()
    if not raw:
        return {}
    try:
        decoded = unquote(raw)
        return json.loads(decoded)
    except Exception:
        return {}


def crawl_douyin(url: str, timeout: int = 15) -> CrawlResult:
    final_url, html = _resolve_final_url(url, timeout=timeout)

    item_id = ""
    for pattern in [
        r"/video/(\d{8,25})",
        r"modal_id=(\d{8,25})",
        r"item_ids?=(\d{8,25})",
    ]:
        m = re.search(pattern, final_url)
        if m:
            item_id = m.group(1)
            break

    render_data = _parse_render_data(html)
    ld_json = _parse_ld_json(html)

    title = _extract_text(html, r'<meta[^>]*property=["\']og:title["\'][^>]*content=["\'](.*?)["\']')
    if not title:
        title = _extract_text(html, r"<title>(.*?)</title>")
    if not title:
        title = str(ld_json.get("name", "抖音视频")).strip()

    author_name = ""
    play_count = 0
    like_count = 0
    comment_count = 0
    publish_time = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
    author_raw_id = ""

    for node in _iter_dicts(render_data):
        if not item_id:
            for k in ("awemeId", "aweme_id", "itemId", "item_id"):
                if k in node and str(node[k]).isdigit():
                    item_id = str(node[k])
                    break

        if not author_name:
            for k in ("nickname", "authorName", "author_name"):
                if node.get(k):
                    author_name = str(node[k]).strip()
                    break

        if not author_raw_id:
            for k in ("uid", "authorId", "author_id", "secUid", "sec_uid"):
                if node.get(k):
                    author_raw_id = str(node[k]).strip()
                    break

        if play_count == 0 and ("playCount" in node or "play_count" in node):
            play_count = _safe_int(node.get("playCount") or node.get("play_count"), 0)
        if like_count == 0 and ("diggCount" in node or "digg_count" in node or "like_count" in node):
            like_count = _safe_int(node.get("diggCount") or node.get("digg_count") or node.get("like_count"), 0)
        if comment_count == 0 and ("commentCount" in node or "comment_count" in node):
            comment_count = _safe_int(node.get("commentCount") or node.get("comment_count"), 0)

        if "createTime" in node or "create_time" in node:
            publish_time = _format_ts(node.get("createTime") or node.get("create_time"))

    if not author_name:
        author_name = _extract_text(html, r'<meta[^>]*name=["\']author["\'][^>]*content=["\'](.*?)["\']')
    if not author_name:
        author = ld_json.get("author")
        if isinstance(author, dict):
            author_name = str(author.get("name", "")).strip()
    if not author_name:
        author_name = "douyin_user"

    if not item_id:
        item_id = str(stable_numeric_id(final_url, "douyin_item"))

    if play_count == 0 and isinstance(ld_json.get("interactionStatistic"), list):
        for item in ld_json["interactionStatistic"]:
            if isinstance(item, dict):
                stat_name = str(item.get("name", "")).lower()
                value = _safe_int(item.get("userInteractionCount"), 0)
                if "watch" in stat_name or "view" in stat_name:
                    play_count = max(play_count, value)
                elif "like" in stat_name:
                    like_count = max(like_count, value)
                elif "comment" in stat_name:
                    comment_count = max(comment_count, value)

    if ld_json.get("uploadDate"):
        try:
            publish_time = datetime.fromisoformat(str(ld_json["uploadDate"]).replace("Z", "+00:00")).strftime(
                "%Y-%m-%d %H:%M:%S"
            )
        except Exception:
            pass

    video_id = _safe_int(item_id, stable_numeric_id(item_id, "douyin_video"))
    user_id = _safe_int(author_raw_id, stable_numeric_id(author_name, "douyin_user"))
    user = {
        "user_id": user_id,
        "user_name": author_name[:100],
        "fans": max(0, play_count // 100 if play_count > 0 else 0),
        "follow": max(0, play_count // 300 if play_count > 0 else 0),
        "level": 3,
    }
    video = {
        "id": video_id,
        "title": title[:255] if title else "抖音视频",
        "author": user["user_name"],
        "source_platform": "douyin",
        "category": "短视频",
        "play_count": play_count,
        "like_count": like_count,
        "comment_count": comment_count,
        "publish_time": publish_time,
        "owner_mid": user_id,
    }
    return CrawlResult(videos=[video], users=[user], comments=[], platform="douyin", source_url=final_url)


def crawl_generic_by_meta(url: str, platform: str, timeout: int = 15) -> CrawlResult:
    final_url, html = _resolve_final_url(url, timeout=timeout)
    ld_json = _parse_ld_json(html)

    title = _meta_first(
        html,
        [
            ("property", "og:title"),
            ("name", "twitter:title"),
        ],
    )
    if not title:
        title = _extract_text(html, r"<title>(.*?)</title>")
    if not title:
        title = str(ld_json.get("name", f"{platform}_video")).strip()
    title = title[:255] if title else f"{platform}_video"

    author_name = _meta_first(
        html,
        [
            ("name", "author"),
            ("property", "og:site_name"),
        ],
    )
    if not author_name and isinstance(ld_json.get("author"), dict):
        author_name = str((ld_json.get("author") or {}).get("name", "")).strip()
    author_name = (author_name or f"{platform}_author")[:100]

    play_count = 0
    like_count = 0
    comment_count = 0
    if isinstance(ld_json.get("interactionStatistic"), list):
        for stat in ld_json.get("interactionStatistic") or []:
            if not isinstance(stat, dict):
                continue
            name = str(stat.get("name", "")).lower()
            value = _safe_int(stat.get("userInteractionCount"), 0)
            if "view" in name or "watch" in name or "play" in name:
                play_count = max(play_count, value)
            elif "like" in name or "digg" in name:
                like_count = max(like_count, value)
            elif "comment" in name:
                comment_count = max(comment_count, value)

    if play_count == 0:
        play_count = _parse_count_text(
            _meta_first(
                html,
                [
                    ("name", "video:play_count"),
                    ("property", "og:video:view_count"),
                ],
            )
        )
    if like_count == 0:
        like_count = _parse_count_text(_meta_first(html, [("name", "video:like_count")]))
    if comment_count == 0:
        comment_count = _parse_count_text(_meta_first(html, [("name", "video:comment_count")]))

    publish_time = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
    publish_text = (
        str(ld_json.get("uploadDate", "")).strip()
        or _meta_first(
            html,
            [
                ("property", "article:published_time"),
                ("name", "publish_date"),
                ("name", "date"),
            ],
        )
    )
    if publish_text:
        publish_time = _format_ts(_safe_int(publish_text, 0))
        if publish_time == datetime.now().strftime("%Y-%m-%d %H:%M:%S"):
            try:
                publish_time = datetime.fromisoformat(publish_text.replace("Z", "+00:00")).strftime(
                    "%Y-%m-%d %H:%M:%S"
                )
            except Exception:
                pass

    video_id = stable_numeric_id(final_url, f"{platform}_video")
    user_id = stable_numeric_id(author_name, f"{platform}_author")
    user = {
        "user_id": user_id,
        "user_name": author_name,
        "fans": max(0, play_count // 120 if play_count > 0 else 0),
        "follow": max(0, play_count // 300 if play_count > 0 else 0),
        "level": 3,
    }
    video = {
        "id": video_id,
        "title": title,
        "author": author_name,
        "source_platform": platform,
        "category": PLATFORM_CATEGORY_HINTS.get(platform, "其他"),
        "play_count": play_count,
        "like_count": like_count,
        "comment_count": comment_count,
        "publish_time": publish_time,
        "owner_mid": user_id,
    }
    return CrawlResult(videos=[video], users=[user], comments=[], platform=platform, source_url=final_url)
