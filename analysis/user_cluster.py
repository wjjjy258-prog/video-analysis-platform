from __future__ import annotations

import argparse
from collections import defaultdict

from db_util import execute_many, execute_sql, query_all


def build_user_interest_rows() -> list[tuple]:
    rows = query_all(
        """
        SELECT ub.user_id, v.category, COUNT(*) AS action_count
        FROM user_behavior ub
        JOIN video v ON ub.video_id = v.id
        GROUP BY ub.user_id, v.category
        """
    )
    if not rows:
        return []

    category_map: dict[int, dict[str, int]] = defaultdict(dict)
    total_map: dict[int, int] = defaultdict(int)

    for row in rows:
        user_id = int(row["user_id"])
        category = row["category"] or "其他"
        cnt = int(row["action_count"])
        category_map[user_id][category] = cnt
        total_map[user_id] += cnt

    result = []
    for user_id, counts in category_map.items():
        favorite_category = max(counts.items(), key=lambda x: x[1])[0]
        total = total_map[user_id]
        if total >= 20:
            cluster_id, cluster_label = 0, "高活跃兴趣型"
        elif total >= 10:
            cluster_id, cluster_label = 1, "中活跃稳定型"
        else:
            cluster_id, cluster_label = 2, "轻度活跃型"
        result.append((user_id, cluster_id, cluster_label, favorite_category))
    return result


def main() -> None:
    execute_sql(
        """
        CREATE TABLE IF NOT EXISTS user_interest_result (
            id BIGINT PRIMARY KEY AUTO_INCREMENT,
            user_id BIGINT NOT NULL UNIQUE,
            cluster_id INT NOT NULL,
            cluster_label VARCHAR(64) NOT NULL,
            favorite_category VARCHAR(64) NOT NULL,
            updated_at DATETIME NOT NULL
        )
        """
    )

    rows = build_user_interest_rows()
    if not rows:
        print("用户行为数据不足，未生成聚类结果。")
        return

    upsert_sql = """
        INSERT INTO user_interest_result (user_id, cluster_id, cluster_label, favorite_category, updated_at)
        VALUES (%s, %s, %s, %s, NOW())
        ON DUPLICATE KEY UPDATE
            cluster_id = VALUES(cluster_id),
            cluster_label = VALUES(cluster_label),
            favorite_category = VALUES(favorite_category),
            updated_at = VALUES(updated_at)
    """
    count = execute_many(upsert_sql, rows)
    print(f"用户兴趣分析完成，写入行数: {count}")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="用户兴趣分析脚本")
    parser.parse_args()
    main()
