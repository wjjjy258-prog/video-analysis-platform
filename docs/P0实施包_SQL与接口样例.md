# P0 实施包（SQL + 接口请求/响应样例）

本文用于直接指导开发落地以下三块能力：

1. 指标口径中心
2. 数据质量与血缘追踪
3. 异常预警中心

当前状态说明（重要）：

1. 本文档是“P0 设计与实现草案”。
2. 当前主系统已稳定运行，但本文中的 `/metrics`、`/alerts`、`/lineage` 接口尚未全部在后端启用。
3. 如需启用，请先执行 `database/upgrade_p0_metrics_quality_alert.sql`，再进行后端接口开发与联调。

---

## 1. 数据库 SQL 草案

SQL 文件已生成：

- `database/upgrade_p0_metrics_quality_alert.sql`

执行方式：

1. 进入 `video_analysis` 数据库后执行该文件
2. 建议先在测试库执行
3. 执行后检查表是否创建成功（`metric_definition/import_job_error/data_lineage/alert_rule/alert_event`）

说明：

- 已与现有表结构对齐（使用 `tenant_user_id` 多租户隔离）
- `import_job` 仅做增量字段扩展，不破坏原有逻辑

---

## 2. 接口风格约定（建议）

为避免影响现有接口（如 `/video/*` 直接返回列表），建议：

1. 旧接口保持不变
2. P0 新接口统一返回 `Envelope`：

```json
{
  "success": true,
  "message": "ok",
  "data": {}
}
```

失败示例：

```json
{
  "success": false,
  "message": "参数不合法: threshold 必须大于 0",
  "data": null
}
```

---

## 3. 接口清单与样例

## 3.1 指标口径中心

### 3.1.1 查询指标列表

- `GET /metrics/definitions?platform=all&enabled=1`

响应：

```json
{
  "success": true,
  "message": "ok",
  "data": [
    {
      "metricCode": "interaction_rate",
      "metricName": "互动率",
      "formulaText": "(点赞量 + 评论量 + 分享量) / max(播放量, 1)",
      "unit": "%",
      "description": "反映播放到互动的转化效率。",
      "platformScope": "all",
      "version": "v1",
      "enabled": true
    }
  ]
}
```

### 3.1.2 查询单个指标详情

- `GET /metrics/definitions/{metricCode}`
- 示例：`GET /metrics/definitions/play_count`

### 3.1.3 新增指标（管理员）

- `POST /metrics/definitions`

请求体：

```json
{
  "metricCode": "save_rate",
  "metricName": "收藏率",
  "formulaText": "收藏量 / max(播放量, 1)",
  "unit": "%",
  "description": "收藏行为占播放的比例",
  "platformScope": "bilibili,douyin",
  "version": "v1",
  "enabled": true
}
```

### 3.1.4 更新指标（管理员）

- `PUT /metrics/definitions/{id}`

---

## 3.2 导入质量与血缘

> 说明：你现有导入接口是 `/crawler/import-text`、`/crawler/import-file`。  
> P0 增量做法是保留原接口，并新增“任务查询接口”。

### 3.2.1 查询导入任务列表

- `GET /import/jobs?page=1&size=20&status=SUCCESS`

响应：

```json
{
  "success": true,
  "message": "ok",
  "data": {
    "total": 128,
    "items": [
      {
        "id": 5021,
        "importType": "file_import",
        "sourcePlatform": "bilibili",
        "sourceFile": "BiliBili_data.csv",
        "status": "PARTIAL",
        "sourceCount": 12008,
        "successCount": 11920,
        "failedCount": 88,
        "startedAt": "2026-03-09 10:12:31",
        "finishedAt": "2026-03-09 10:13:07",
        "durationMs": 36000,
        "errorSummary": "88 条格式不完整"
      }
    ]
  }
}
```

### 3.2.2 查询任务详情

- `GET /import/jobs/{jobId}`

### 3.2.3 查询任务错误明细

- `GET /import/jobs/{jobId}/errors?page=1&size=50`

响应：

```json
{
  "success": true,
  "message": "ok",
  "data": {
    "total": 2,
    "items": [
      {
        "lineNo": 27,
        "errorCode": "MISSING_TITLE",
        "errorMessage": "缺少标题字段",
        "suggestion": "请补充 title 或标题 列",
        "rawExcerpt": "{ \"author\": \"xxx\" }"
      }
    ]
  }
}
```

### 3.2.4 查询数据血缘

- `GET /lineage?dataType=video&dataId=19390801`

响应：

```json
{
  "success": true,
  "message": "ok",
  "data": {
    "dataType": "video",
    "dataId": 19390801,
    "importJobId": 5021,
    "sourceType": "file_import",
    "sourcePlatform": "bilibili",
    "sourceFile": "BiliBili_data.csv",
    "sourceRef": "line:127",
    "parserVersion": "v1",
    "normalizeVersion": "v1",
    "qualityScore": 92.4,
    "createdAt": "2026-03-09 10:13:08"
  }
}
```

---

## 3.3 异常预警中心

### 3.3.1 查询规则列表

- `GET /alerts/rules?enabled=1`

### 3.3.2 创建规则

- `POST /alerts/rules`

请求体：

```json
{
  "ruleName": "日播放环比异常上涨",
  "metricCode": "play_count",
  "dimensionJson": "{\"platform\":\"all\"}",
  "operator": ">",
  "threshold": 0.5,
  "windowSize": 1,
  "compareType": "day_over_day",
  "cooldownMinutes": 120,
  "severity": "HIGH",
  "enabled": true
}
```

响应（节选）：

```json
{
  "success": true,
  "message": "规则创建成功",
  "data": {
    "id": 11
  }
}
```

### 3.3.3 修改规则

- `PUT /alerts/rules/{id}`

### 3.3.4 启用/禁用规则

- `PATCH /alerts/rules/{id}/enable`

请求体：

```json
{
  "enabled": false
}
```

### 3.3.5 查询预警事件

- `GET /alerts/events?status=NEW&page=1&size=20`

响应（节选）：

```json
{
  "success": true,
  "message": "ok",
  "data": {
    "total": 1,
    "items": [
      {
        "id": 923,
        "ruleId": 11,
        "metricCode": "play_count",
        "eventTime": "2026-03-09 09:00:00",
        "currentValue": 107000000,
        "baselineValue": 65000000,
        "changeRatio": 0.6462,
        "severity": "HIGH",
        "message": "播放量日环比上涨 64.62%",
        "status": "NEW"
      }
    ]
  }
}
```

### 3.3.6 事件标记已读

- `PATCH /alerts/events/{id}/read`

### 3.3.7 手动触发一次规则计算

- `POST /alerts/evaluate-now`

响应：

```json
{
  "success": true,
  "message": "已触发规则计算",
  "data": {
    "taskId": "alert-eval-20260309-101530"
  }
}
```

---

## 4. 前端改动点（页面级）

1. 顶部导航新增：`指标口径`、`告警中心`
2. 首页和分析页指标卡增加 `i` 按钮，点击显示“定义+公式+版本”
3. 数据管理页增加“导入任务记录”模块（状态、成功/失败、耗时）
4. 数据管理页任务详情增加“错误明细”抽屉
5. 详情列表增加“来源追踪”弹窗（血缘信息）
6. 顶部增加告警角标（未读数量）

---

## 5. 工时评估（1人）

1. 数据库迁移脚本与验证：`6h`
2. 指标口径中心（后端+前端）：`10h`
3. 导入质量与血缘（后端+前端）：`14h`
4. 异常预警中心（后端+前端）：`14h`
5. 联调与测试：`8h`

总计：`52h`（约 6-7 个工作日）

---

## 6. 验收标准

1. 任一图表可查看指标公式与版本
2. 任一导入任务可追溯成功/失败条数和失败原因
3. 任一视频明细可查看来源血缘
4. 规则配置后可产生告警事件并支持已读状态流转
5. 全流程演示（登录-导入-分析-告警）可在 8 分钟内完成
