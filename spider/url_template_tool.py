from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path
from urllib.parse import urlparse

from platform_collectors import detect_platform


TEMPLATE_TEXT = """# URL batch import template
# Rules:
# 1) One URL per line
# 2) Lines starting with # are comments
# 3) Keep only public and authorized URLs
# 4) Do NOT put private or sensitive links

# bilibili examples (placeholder, replace with your own legal URLs)
<BILIBILI_VIDEO_URL_1>
<BILIBILI_VIDEO_URL_2>

# douyin examples (placeholder, replace with your own legal URLs)
<DOUYIN_VIDEO_URL_1>
<DOUYIN_VIDEO_URL_2>

# kuaishou examples
<KUAISHOU_VIDEO_URL_1>

# xiaohongshu examples
<XIAOHONGSHU_VIDEO_URL_1>

# xigua examples
<XIGUA_VIDEO_URL_1>

# weibo examples
<WEIBO_VIDEO_URL_1>

# youtube examples
<YOUTUBE_VIDEO_URL_1>

# tiktok examples
<TIKTOK_VIDEO_URL_1>

# acfun examples
<ACFUN_VIDEO_URL_1>
"""


@dataclass
class ValidateIssue:
    line_no: int
    value: str
    reason: str


def generate_template(output_path: str) -> Path:
    path = Path(output_path).expanduser().resolve()
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(TEMPLATE_TEXT, encoding="utf-8")
    return path


def _normalize_url(raw: str) -> str:
    value = raw.replace("\ufeff", "").strip()
    if not value:
        return value
    if value.startswith("<") and value.endswith(">"):
        return value
    if not value.startswith(("http://", "https://")):
        return "https://" + value
    return value


def validate_url_lines(lines: list[str], platform: str = "auto") -> tuple[list[str], list[ValidateIssue]]:
    valid_urls: list[str] = []
    issues: list[ValidateIssue] = []

    platform = platform.lower()
    for idx, raw in enumerate(lines, start=1):
        stripped = raw.strip()
        if not stripped or stripped.startswith("#"):
            continue

        normalized = _normalize_url(stripped)
        if normalized.startswith("<") and normalized.endswith(">"):
            issues.append(ValidateIssue(idx, stripped, "placeholder value found; replace with real URL"))
            continue

        parsed = urlparse(normalized)
        if parsed.scheme not in {"http", "https"}:
            issues.append(ValidateIssue(idx, stripped, "scheme must be http or https"))
            continue
        if not parsed.netloc:
            issues.append(ValidateIssue(idx, stripped, "host is missing"))
            continue

        detected = detect_platform(normalized)
        if detected == "unknown":
            issues.append(ValidateIssue(idx, stripped, "unsupported platform host"))
            continue
        if platform != "auto" and detected != platform:
            issues.append(ValidateIssue(idx, stripped, f"platform mismatch, expected {platform}, got {detected}"))
            continue

        valid_urls.append(normalized)

    return valid_urls, issues


def validate_url_file(path: str, platform: str = "auto") -> tuple[list[str], list[ValidateIssue], Path]:
    file_path = Path(path).expanduser().resolve()
    if not file_path.exists():
        raise FileNotFoundError(f"URL file not found: {file_path}")

    lines = file_path.read_text(encoding="utf-8-sig").splitlines()
    valid_urls, issues = validate_url_lines(lines, platform=platform)
    return valid_urls, issues, file_path
