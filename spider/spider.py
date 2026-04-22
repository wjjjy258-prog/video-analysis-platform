from __future__ import annotations

import argparse
import json
import os
from typing import Any

import requests

from authorized_data_tool import (
    build_entities_from_official_rows,
    generate_official_csv_template,
    generate_official_json_template,
    validate_official_csv,
    validate_official_json,
)
from mysql_util import MySQLUtil
from platform_collectors import crawl_by_url
from parser import (
    build_behaviors_from_crawl,
    build_mock_behaviors,
    build_mock_comments,
    build_users,
    fallback_demo_videos,
    merge_users,
    parse_video_items,
)
from url_template_tool import generate_template, validate_url_file

try:
    from kafka import KafkaProducer
except Exception:  # 【说明】可选依赖；测试环境未安装时允许忽略。
    KafkaProducer = None


POPULAR_API = "https://api.bilibili.com/x/web-interface/popular"


def fetch_remote_videos(page: int, page_size: int) -> list[dict[str, Any]]:
    params = {"pn": page, "ps": page_size}
    headers = {
        "User-Agent": (
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
            "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Safari/537.36"
        )
    }
    response = requests.get(POPULAR_API, params=params, headers=headers, timeout=12)
    response.raise_for_status()
    return parse_video_items(response.json())


def load_urls(url: str | None, url_file: str | None, platform: str = "auto") -> list[str]:
    urls: list[str] = []
    if url:
        urls.append(url.strip())
    if url_file:
        valid_urls, issues, file_path = validate_url_file(url_file, platform=platform)
        if issues:
            print(f"URL鏂囦欢鏍￠獙澶辫触: {file_path}")
            for issue in issues:
                print(f"  line {issue.line_no}: {issue.reason} -> {issue.value}")
            raise ValueError("URL file validation failed.")
        urls.extend(valid_urls)
    return urls


def unique_by_key(items: list[dict[str, Any]], key: str) -> list[dict[str, Any]]:
    seen = set()
    result = []
    for item in items:
        value = item.get(key)
        if value in seen:
            continue
        seen.add(value)
        result.append(item)
    return result


def send_to_kafka(videos: list[dict[str, Any]]) -> int:
    kafka_enabled = os.getenv("KAFKA_ENABLED", "false").lower() == "true"
    if not kafka_enabled or KafkaProducer is None:
        return 0

    bootstrap = os.getenv("KAFKA_BOOTSTRAP_SERVERS", "localhost:9092")
    topic = os.getenv("KAFKA_TOPIC", "video_analysis_topic")

    producer = KafkaProducer(
        bootstrap_servers=bootstrap,
        value_serializer=lambda value: json.dumps(value, ensure_ascii=False).encode("utf-8"),
    )

    sent = 0
    try:
        for video in videos:
            producer.send(topic, video)
            sent += 1
        producer.flush(timeout=10)
    finally:
        producer.close()
    return sent


def main() -> None:
    parser = argparse.ArgumentParser(description="瑙嗛缃戠珯鏁版嵁閲囬泦鑴氭湰")
    parser.add_argument(
        "--generate-url-template",
        type=str,
        default=None,
        help="鐢熸垚鎵归噺URL妯℃澘鏂囦欢锛屼緥濡?.\\urls.txt",
    )
    parser.add_argument(
        "--validate-url-file",
        type=str,
        default=None,
        help="浠呮牎楠孶RL鏂囦欢鏍煎紡锛屼笉鎵ц閲囬泦",
    )
    parser.add_argument(
        "--generate-official-template",
        type=str,
        default=None,
        help="鐢熸垚瀹樻柟/鎺堟潈CSV妯℃澘鏂囦欢锛屼緥濡?.\\official_videos.csv",
    )
    parser.add_argument(
        "--generate-official-json-template",
        type=str,
        default=None,
        help="鐢熸垚瀹樻柟/鎺堟潈JSON妯℃澘鏂囦欢锛屼緥濡?.\\official_videos.json",
    )
    parser.add_argument(
        "--validate-official-file",
        type=str,
        default=None,
        help="浠呮牎楠屽畼鏂?鎺堟潈鏁版嵁鏂囦欢锛屼笉鎵ц閲囬泦",
    )
    parser.add_argument(
        "--validate-format",
        type=str,
        default="csv",
        choices=["csv", "json"],
        help="鏍￠獙鏍煎紡绫诲瀷锛岄厤鍚?--validate-official-file 浣跨敤",
    )
    parser.add_argument(
        "--official-csv-file",
        type=str,
        default=None,
        help="瀹樻柟/鎺堟潈CSV鏁版嵁鏂囦欢锛岀洿鎺ュ叆搴擄紙鎺ㄨ崘锛?,
    )
    parser.add_argument(
        "--official-json-file",
        type=str,
        default=None,
        help="瀹樻柟/鎺堟潈JSON鏁版嵁鏂囦欢锛岀洿鎺ュ叆搴擄紙鎺ㄨ崘锛?,
    )
    parser.add_argument("--url", type=str, default=None, help="鍗曟潯瑙嗛URL锛堟敮鎸佸骞冲彴锛?)
    parser.add_argument("--url-file", type=str, default=None, help="鎵归噺URL鏂囦欢锛堟瘡琛?鏉★級")
    parser.add_argument(
        "--platform",
        type=str,
        default="auto",
        choices=[
            "auto",
            "bilibili",
            "douyin",
            "kuaishou",
            "xiaohongshu",
            "xigua",
            "weibo",
            "youtube",
            "tiktok",
            "acfun",
        ],
        help="骞冲彴绫诲瀷锛岄粯璁よ嚜鍔ㄨ瘑鍒紙鏀寔 bilibili/douyin/kuaishou/xiaohongshu/xigua/weibo/youtube/tiktok/acfun锛?,
    )
    parser.add_argument("--page", type=int, default=1, help="閲囬泦椤电爜")
    parser.add_argument("--page-size", type=int, default=20, help="姣忛〉鏉℃暟")
    parser.add_argument("--use-popular-api", action="store_true", help="浣跨敤B绔欑儹闂ㄦ帴鍙ｏ紙楂橀闄╋紝榛樿鍏抽棴锛?)
    parser.add_argument(
        "--unsafe-allow-url-crawl",
        action="store_true",
        help="鍏佽URL鎶撳彇锛堝彲鑳借繚鍙嶅钩鍙版潯娆撅紝榛樿鍏抽棴锛?,
    )
    parser.add_argument("--mock-only", action="store_true", help="浠呬娇鐢ㄦ湰鍦版ā鎷熸暟鎹?)
    args = parser.parse_args()

    if args.generate_url_template:
        output_path = generate_template(args.generate_url_template)
        print(f"URL妯℃澘宸茬敓鎴? {output_path}")
        return

    if args.generate_official_template:
        output_path = generate_official_csv_template(args.generate_official_template)
        print(f"瀹樻柟CSV妯℃澘宸茬敓鎴? {output_path}")
        return

    if args.generate_official_json_template:
        output_path = generate_official_json_template(args.generate_official_json_template)
        print(f"瀹樻柟JSON妯℃澘宸茬敓鎴? {output_path}")
        return

    if args.validate_url_file:
        valid_urls, issues, file_path = validate_url_file(args.validate_url_file, platform=args.platform)
        print(f"URL鏂囦欢: {file_path}")
        print(f"鏈夋晥URL鏁伴噺: {len(valid_urls)}")
        if issues:
            print(f"鏍￠獙澶辫触鏉℃暟: {len(issues)}")
            for issue in issues:
                print(f"  line {issue.line_no}: {issue.reason} -> {issue.value}")
            raise SystemExit(1)
        print("鏍￠獙閫氳繃銆?)
        return

    if args.validate_official_file:
        if args.validate_format == "csv":
            rows, issues, file_path = validate_official_csv(args.validate_official_file)
        else:
            rows, issues, file_path = validate_official_json(args.validate_official_file)
        print(f"瀹樻柟鏁版嵁鏂囦欢: {file_path}")
        print(f"鏈夋晥璁板綍鏁? {len(rows)}")
        if issues:
            print(f"鏍￠獙澶辫触鏉℃暟: {len(issues)}")
            for issue in issues:
                print(f"  row {issue.row_no}: {issue.reason} -> {issue.value}")
            raise SystemExit(1)
        print("鏍￠獙閫氳繃銆?)
        return

    mysql_util = MySQLUtil(
        host=os.getenv("MYSQL_HOST", "localhost"),
        port=int(os.getenv("MYSQL_PORT", "3306")),
        user=os.getenv("MYSQL_USER", "root"),
        password=os.getenv("MYSQL_PASSWORD", ""),
        database=os.getenv("MYSQL_DATABASE", "video_analysis"),
    )

    videos: list[dict[str, Any]] = []
    users: list[dict[str, Any]] = []
    comments: list[dict[str, Any]] = []

    has_official_input = bool(args.official_csv_file or args.official_json_file)
    urls = load_urls(args.url, args.url_file, platform=args.platform) if (args.url or args.url_file) else []

    if has_official_input:
        source_file = args.official_csv_file or args.official_json_file or ""
        if args.official_csv_file:
            rows, issues, file_path = validate_official_csv(args.official_csv_file)
        else:
            rows, issues, file_path = validate_official_json(args.official_json_file)
        if issues:
            print(f"瀹樻柟鏁版嵁鏂囦欢鏍￠獙澶辫触: {file_path}")
            for issue in issues:
                print(f"  row {issue.row_no}: {issue.reason} -> {issue.value}")
            raise SystemExit(1)

        videos, users, comments = build_entities_from_official_rows(rows)
        for video in videos:
            video.setdefault("import_type", "official_file")
            video.setdefault("source_file", str(source_file))
            video.setdefault("source_url", video.get("source_url"))
        behaviors = build_behaviors_from_crawl(videos, users, comments)
        print(f"瀹樻柟鏁版嵁鍏ュ簱妯″紡瀹屾垚锛岃褰曟暟: {len(videos)}")
    elif urls:
        if not args.unsafe_allow_url_crawl:
            raise RuntimeError(
                "URL鎶撳彇榛樿宸茬鐢ㄣ€傝鏀圭敤 --official-csv-file / --official-json-file銆?
                "濡備綘纭椋庨櫓骞舵湁鎺堟潈锛岃鏄惧紡鍔?--unsafe-allow-url-crawl銆?
            )
        for idx, input_url in enumerate(urls, start=1):
            try:
                result = crawl_by_url(input_url, platform=args.platform)
                for video in result.videos:
                    video.setdefault("source_url", result.source_url)
                    video.setdefault("import_type", "crawler_url")
                videos.extend(result.videos)
                users.extend(result.users)
                comments.extend(result.comments)
                print(f"[{idx}/{len(urls)}] 鎴愬姛閲囬泦 {result.platform}: {result.source_url}")
            except Exception as exc:
                print(f"[{idx}/{len(urls)}] 閲囬泦澶辫触: {input_url} -> {exc}")

        videos = unique_by_key(videos, "id")
        users = merge_users(users)
        if not videos:
            raise RuntimeError("URL妯″紡鏈噰闆嗗埌鏈夋晥瑙嗛鏁版嵁锛岃妫€鏌ラ摼鎺ユ垨鍒囨崲鍒?--mock-only銆?)

        behaviors = build_behaviors_from_crawl(videos, users, comments)
        print(f"URL閲囬泦妯″紡瀹屾垚锛岃棰戞暟: {len(videos)}")
    elif args.mock_only:
        videos = fallback_demo_videos()
        for video in videos:
            video.setdefault("import_type", "mock")
        users = build_users(videos)
        comments = build_mock_comments(videos, users)
        behaviors = build_mock_behaviors(videos, users)
        print("浣跨敤鏈湴妯℃嫙鏁版嵁銆?)
    elif args.use_popular_api:
        if not args.unsafe_allow_url_crawl:
            raise RuntimeError(
                "鐑棬鎺ュ彛鎶撳彇榛樿宸茬鐢ㄣ€傝鏀圭敤瀹樻柟/鎺堟潈鏂囦欢鍏ュ簱銆?
                "濡備綘纭椋庨櫓骞舵湁鎺堟潈锛岃鏄惧紡鍔?--unsafe-allow-url-crawl銆?
            )
        try:
            videos = fetch_remote_videos(page=args.page, page_size=args.page_size)
            if not videos:
                videos = fallback_demo_videos()
                print("杩滅▼杩斿洖绌烘暟鎹紝鍒囨崲涓烘ā鎷熸暟鎹€?)
            else:
                print(f"杩滅▼閲囬泦鎴愬姛锛岃棰戞暟: {len(videos)}")
                for video in videos:
                    video.setdefault("import_type", "popular_api")
        except Exception as exc:
            print(f"杩滅▼閲囬泦澶辫触锛屽垏鎹㈡ā鎷熸暟鎹? {exc}")
            videos = fallback_demo_videos()
            for video in videos:
                video.setdefault("import_type", "mock")
        users = build_users(videos)
        comments = build_mock_comments(videos, users)
        behaviors = build_mock_behaviors(videos, users)
    else:
        raise RuntimeError(
            "鏈寚瀹氭暟鎹潵婧愩€傛帹鑽愪娇鐢?--official-csv-file 鎴?--official-json-file銆?
            "鍙厛鐢?--generate-official-template 鐢熸垚妯℃澘銆?
        )

    video_count = mysql_util.upsert_videos(videos)
    user_count = mysql_util.upsert_users(users)
    comment_count = mysql_util.insert_comments(comments)
    behavior_count = mysql_util.insert_behaviors(behaviors)

    try:
        kafka_count = send_to_kafka(videos)
    except Exception as exc:
        kafka_count = 0
        print(f"Kafka鍙戦€佸け璐ワ紙涓嶅奖鍝峂ySQL鍏ュ簱锛? {exc}")

    print("閲囬泦瀹屾垚锛?)
    print(f"- 瑙嗛鍏ュ簱: {video_count}")
    print(f"- 鐢ㄦ埛鍏ュ簱: {user_count}")
    print(f"- 璇勮鍏ュ簱: {comment_count}")
    print(f"- 琛屼负鍏ュ簱: {behavior_count}")
    print(f"- Kafka鍙戦€? {kafka_count}")


if __name__ == "__main__":
    main()

