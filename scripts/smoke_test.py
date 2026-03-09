from __future__ import annotations

import argparse
import os
import csv
import json
import time
from datetime import datetime
from pathlib import Path

import mysql.connector
import requests


def table_exists(cursor, table_name: str) -> bool:
    cursor.execute(
        """
        SELECT COUNT(*) FROM information_schema.TABLES
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = %s
        """,
        (table_name,),
    )
    return (cursor.fetchone() or [0])[0] > 0


def check_db(host: str, port: int, user: str, password: str, database: str) -> dict:
    conn = mysql.connector.connect(
        host=host,
        port=port,
        user=user,
        password=password,
        database=database,
        autocommit=True,
    )
    try:
        cursor = conn.cursor()
        cursor.execute("SELECT COUNT(*) FROM video")
        video_count = cursor.fetchone()[0]
        cursor.execute("SELECT COUNT(*) FROM user_behavior")
        behavior_count = cursor.fetchone()[0]
        import_job_count = 0
        if table_exists(cursor, "import_job"):
            cursor.execute("SELECT COUNT(*) FROM import_job")
            import_job_count = cursor.fetchone()[0]
        print(f"[DB] video={video_count}, user_behavior={behavior_count}, import_job={import_job_count}")
        return {
            "videoCount": int(video_count),
            "behaviorCount": int(behavior_count),
            "importJobCount": int(import_job_count),
        }
    finally:
        conn.close()


def fetch_with_retry(url: str, timeout: int, retries: int, *, method: str = "GET", headers: dict | None = None, json_data: dict | None = None) -> requests.Response:
    last_error: Exception | None = None
    for attempt in range(1, retries + 2):
        try:
            if method.upper() == "POST":
                resp = requests.post(url, timeout=timeout, headers=headers, json=json_data)
            else:
                resp = requests.get(url, timeout=timeout, headers=headers)
            resp.raise_for_status()
            return resp
        except requests.RequestException as exc:
            last_error = exc
            if attempt <= retries:
                wait_s = min(6, attempt * 2)
                print(f"[WARN] request failed ({attempt}/{retries + 1}): {url} -> {exc}; retry in {wait_s}s")
                time.sleep(wait_s)
                continue
            break
    assert last_error is not None
    raise last_error


def login_token(base_url: str, username: str, password: str, timeout: int, retries: int) -> str:
    url = f"{base_url.rstrip('/')}/auth/login"
    resp = fetch_with_retry(
        url,
        timeout=timeout,
        retries=retries,
        method="POST",
        json_data={"username": username, "password": password},
    )
    data = resp.json()
    token = str(data.get("token") or "").strip()
    if not token:
        raise RuntimeError("Auth login succeeded but token is empty.")
    print(f"[AUTH] login OK -> user={username}")
    return token


def check_api(base_url: str, timeout: int, retries: int, token: str) -> list[dict]:
    paths = [
        "/health",
        "/video/overview",
        "/video/hot",
        "/video/category",
        "/video/funnel",
        "/video/platform/benchmark",
        "/video/user",
        "/video/source-trace",
        "/video/insight",
    ]
    optional_paths = {
        "/video/funnel",
        "/video/platform/benchmark",
        "/video/source-trace",
        "/video/insight",
    }
    rows: list[dict] = []
    for path in paths:
        url = f"{base_url.rstrip('/')}{path}"
        headers = {"Authorization": f"Bearer {token}"} if path.startswith("/video/") else None
        try:
            resp = fetch_with_retry(url, timeout=timeout, retries=retries, headers=headers)
            try:
                data = resp.json()
                preview = json.dumps(data, ensure_ascii=False)[:220]
            except Exception:
                preview = resp.text[:220]
            print(f"[API] {path} OK -> {preview}")
            rows.append({
                "path": path,
                "status": "OK",
                "statusCode": resp.status_code,
                "preview": preview,
            })
        except requests.HTTPError as ex:
            status_code = ex.response.status_code if ex.response is not None else 0
            if status_code == 404 and path in optional_paths:
                preview = "Endpoint not found in current backend process. Restart backend to load latest code."
                print(f"[API] {path} WARN -> {preview}")
                rows.append({
                    "path": path,
                    "status": "WARN",
                    "statusCode": status_code,
                    "preview": preview,
                })
                continue
            raise
    return rows


def ensure_report_dir(path: str | None) -> Path:
    if path:
        report_dir = Path(path).expanduser().resolve()
    else:
        report_dir = (Path(__file__).resolve().parent.parent / "analysis" / "reports").resolve()
    report_dir.mkdir(parents=True, exist_ok=True)
    return report_dir


def write_reports(report_dir: Path, report: dict) -> dict:
    ts = datetime.now().strftime("%Y%m%d_%H%M%S")
    json_path = report_dir / f"smoke_{ts}.json"
    md_path = report_dir / f"smoke_{ts}.md"
    api_csv_path = report_dir / f"smoke_api_{ts}.csv"
    latest_json = report_dir / "latest_smoke.json"
    latest_md = report_dir / "latest_smoke.md"
    latest_csv = report_dir / "latest_smoke_api.csv"

    json_text = json.dumps(report, ensure_ascii=False, indent=2)
    json_path.write_text(json_text, encoding="utf-8")
    latest_json.write_text(json_text, encoding="utf-8")

    lines = [
        "# 鑷祴鎶ュ憡",
        "",
        f"- 鏃堕棿: {report.get('runAt', '-')}",
        f"- 缁撴灉: {report.get('status', '-')}",
        "",
        "## 鏁版嵁搴?,
        f"- 瑙嗛鏁? {report['db'].get('videoCount', 0)}",
        f"- 琛屼负鏃ュ織鏁? {report['db'].get('behaviorCount', 0)}",
        f"- 瀵煎叆浠诲姟鏁? {report['db'].get('importJobCount', 0)}",
        "",
        "## API",
    ]
    for row in report.get("api", []):
        lines.append(f"- {row.get('path')} -> {row.get('status')}")
    md_text = "\n".join(lines) + "\n"
    md_path.write_text(md_text, encoding="utf-8")
    latest_md.write_text(md_text, encoding="utf-8")

    with api_csv_path.open("w", encoding="utf-8-sig", newline="") as f:
        writer = csv.DictWriter(f, fieldnames=["path", "status", "statusCode", "preview"])
        writer.writeheader()
        writer.writerows(report.get("api", []))
    latest_csv.write_bytes(api_csv_path.read_bytes())

    return {
        "json": str(json_path),
        "markdown": str(md_path),
        "api_csv": str(api_csv_path),
        "latest_json": str(latest_json),
        "latest_markdown": str(latest_md),
        "latest_api_csv": str(latest_csv),
    }


def main() -> None:
    parser = argparse.ArgumentParser(description="骞冲彴閮ㄧ讲鍚庤嚜娴嬭剼鏈紙鏃犻渶 mysql CLI锛?)
    parser.add_argument("--mysql-host", default="localhost")
    parser.add_argument("--mysql-port", type=int, default=3306)
    parser.add_argument("--mysql-user", default="root")
    parser.add_argument("--mysql-password", default=os.getenv("MYSQL_PASSWORD", ""))
    parser.add_argument("--mysql-db", default="video_analysis")
    parser.add_argument("--api-base", default="http://localhost:8080")
    parser.add_argument("--api-timeout", type=int, default=30, help="API read timeout (seconds)")
    parser.add_argument("--api-retries", type=int, default=2, help="API retries per endpoint")
    parser.add_argument("--skip-api", action="store_true")
    parser.add_argument("--auth-user", default="demo")
    parser.add_argument("--auth-password", default="123456")
    parser.add_argument("--report-dir", default="", help="report output directory; default is analysis/reports")
    args = parser.parse_args()

    report = {
        "runAt": datetime.now().strftime("%Y-%m-%d %H:%M:%S"),
        "status": "SUCCESS",
        "db": {},
        "api": [],
        "error": "",
    }

    try:
        report["db"] = check_db(
            host=args.mysql_host,
            port=args.mysql_port,
            user=args.mysql_user,
            password=args.mysql_password,
            database=args.mysql_db,
        )

        if args.skip_api:
            print("Skipping API checks (--skip-api).")
            print("Self-check completed: database is reachable.")
        else:
            token = login_token(
                args.api_base,
                args.auth_user,
                args.auth_password,
                timeout=args.api_timeout,
                retries=args.api_retries,
            )
            report["api"] = check_api(args.api_base, timeout=args.api_timeout, retries=args.api_retries, token=token)
            if any(item.get("status") == "WARN" for item in report["api"]):
                report["status"] = "PARTIAL"
                print("Self-check completed with warnings: restart backend to load latest endpoints.")
            else:
                print("Self-check completed: database and backend APIs are reachable.")
    except Exception as ex:
        report["status"] = "FAILED"
        report["error"] = str(ex)
        raise
    finally:
        report_dir = ensure_report_dir(args.report_dir or None)
        paths = write_reports(report_dir, report)
        print(f"[REPORT] json={paths['latest_json']}")
        print(f"[REPORT] md={paths['latest_markdown']}")
        print(f"[REPORT] csv={paths['latest_api_csv']}")


if __name__ == "__main__":
    main()


