<template>
  <section class="panel">
    <h2>数据管理入口</h2>
    <p class="form-help">请仅采集你有授权的数据来源。</p>

    <div class="form-grid">
      <label class="form-field">
        <span>采集平台</span>
        <select v-model="platform">
          <option v-for="item in crawlerPlatformOptions" :key="item.value" :value="item.value">
            {{ item.label }}
          </option>
        </select>
      </label>

      <label class="form-field">
        <span>URL 列表（每行一条）</span>
        <textarea
          v-model="urlText"
          rows="8"
          placeholder="输入授权可采集的 URL，每行一条"
        ></textarea>
      </label>
    </div>

    <label class="check-field">
      <input v-model="confirmRisk" type="checkbox" />
      我已确认授权与合规风险
    </label>

    <div class="action-row">
      <button class="btn-primary" :disabled="running || clearing" @click="startUrlCrawl">
        {{ runningTask === "url" ? "URL 采集中..." : "开始 URL 采集" }}
      </button>
      <button class="btn-secondary" :disabled="running || clearing" @click="runMock">
        {{ runningTask === "mock" ? "写入中..." : "写入模拟数据" }}
      </button>
    </div>
  </section>

  <section class="panel clear-panel">
    <h2>数据清理</h2>
    <p class="form-help">
      清除当前登录账号下的全部业务数据（视频、行为、画像、导入记录），不会删除登录账号本身。
    </p>
    <div class="action-row">
      <button class="btn-danger" :disabled="running || clearing" @click="clearData">
        {{ clearing ? "清理中..." : "清除当前账号全部数据" }}
      </button>
    </div>
  </section>

  <section class="panel">
    <h2>文本/文件智能入库</h2>
    <p class="form-help">支持 .md / .txt / .markdown / .log / .csv 文件导入。可选开启 AI 增强解析（适合全文本/混合文本）。</p>

    <div class="form-grid">
      <label class="form-field">
        <span>默认来源平台</span>
        <select v-model="textDefaultPlatform">
          <option v-for="item in importDefaultPlatformOptions" :key="item.value" :value="item.value">
            {{ item.label }}
          </option>
        </select>
      </label>

      <label class="form-field">
        <span>粘贴文本</span>
        <textarea
          v-model="rawText"
          rows="10"
          placeholder="可粘贴 JSON、Markdown 表格或普通文本"
        ></textarea>
      </label>

      <label class="form-field">
        <span>上传文件</span>
        <input
          type="file"
          accept=".md,.txt,.markdown,.log,.csv,text/plain,text/markdown,text/csv"
          @change="onFileChange"
        />
      </label>
    </div>

    <label class="check-field">
      <input v-model="aiAssist" type="checkbox" />
      启用 AI 增强解析（规则解析不足时自动补充）
    </label>

    <div class="action-row">
      <button class="btn-secondary" :disabled="running || clearing" @click="fillSampleTemplate">填充示例</button>
      <button class="btn-primary" :disabled="running || clearing" @click="importTextData">
        {{ runningTask === "import-text" ? "文本导入中..." : "导入文本" }}
      </button>
      <button class="btn-primary" :disabled="running || clearing || !selectedFile" @click="importFileData">
        {{ runningTask === "import-file" ? "文件导入中..." : "导入文件" }}
      </button>
    </div>
  </section>

  <section class="panel status-panel" :class="`status-${operation.phase}`">
    <h2>操作反馈</h2>
    <div class="status-head">
      <span class="status-dot"></span>
      <p class="status-title">{{ operation.title }}</p>
      <span class="status-tag">{{ phaseText(operation.phase) }}</span>
    </div>
    <p class="status-message">{{ operation.message }}</p>
    <p v-if="operation.detail" class="status-detail">{{ operation.detail }}</p>
    <p v-if="operation.time" class="status-time">更新时间：{{ operation.time }}</p>
    <div v-if="result.output" class="status-debug">
      <button class="btn-link" type="button" @click="showRawLog = !showRawLog">
        {{ showRawLog ? "收起详细日志" : "查看详细日志" }}
      </button>
      <pre v-if="showRawLog" class="log-box status-log">{{ result.output }}</pre>
    </div>
  </section>

  <LoadingPanel v-if="running" :text="runningLoadingText" />
  <LoadingPanel v-if="clearing" text="正在清理当前账号数据" />

  <LoadingPanel v-if="traceLoading" text="正在加载来源追踪数据" />

  <section v-else class="panel">
    <div class="panel-head">
      <h2>来源追踪（最近入库）</h2>
      <div class="action-row trace-actions">
        <label class="form-field inline-field">
          <span>显示条数</span>
          <input v-model.number="traceLimit" type="number" min="10" max="200" step="10" />
        </label>
        <button class="btn-secondary" :disabled="traceLoading || running || clearing" @click="loadTrace">刷新追踪</button>
      </div>
    </div>

    <p v-if="traceError" class="err-text trace-error">{{ traceError }}</p>

    <div class="table-wrap">
      <table v-if="sourceTraceRows.length">
        <thead>
          <tr>
            <th>ID</th>
            <th>标题</th>
            <th>平台</th>
            <th>导入类型</th>
            <th>来源文件</th>
            <th>质量分</th>
            <th>导入时间</th>
            <th>来源URL</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="row in sourceTraceRows" :key="row.id">
            <td>{{ row.id }}</td>
            <td :title="row.title">{{ row.title }}</td>
            <td>{{ normalizePlatform(row.sourcePlatform) }}</td>
            <td>{{ row.importType || "--" }}</td>
            <td>{{ row.sourceFile || "--" }}</td>
            <td>{{ formatQuality(row.dataQualityScore) }}</td>
            <td>{{ formatDateTime(row.importTime) }}</td>
            <td>
              <a v-if="row.sourceUrl" class="trace-link" :href="row.sourceUrl" target="_blank" rel="noreferrer">
                查看来源
              </a>
              <span v-else>--</span>
            </td>
          </tr>
        </tbody>
      </table>
      <p v-else-if="traceLoaded" class="empty-hint">暂无来源追踪数据。</p>
    </div>
  </section>

  <LoadingPanel v-if="rejectLoading" text="正在加载拒绝记录" />

  <section v-else class="panel">
    <div class="panel-head">
      <h2>低质量/拒绝记录</h2>
      <div class="action-row trace-actions">
        <label class="form-field inline-field">
          <span>显示条数</span>
          <input v-model.number="rejectLimit" type="number" min="10" max="500" step="10" />
        </label>
        <button class="btn-secondary" :disabled="rejectLoading || running || clearing" @click="loadRejects">刷新记录</button>
      </div>
    </div>

    <p v-if="rejectError" class="err-text trace-error">{{ rejectError }}</p>

    <div class="table-wrap">
      <table v-if="rejectRows.length">
        <thead>
          <tr>
            <th>ID</th>
            <th>平台</th>
            <th>来源文件</th>
            <th>质量分</th>
            <th>拒绝原因</th>
            <th>修复建议</th>
            <th>导入时间</th>
            <th>原始片段</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="row in rejectRows" :key="row.id">
            <td>{{ row.id }}</td>
            <td>{{ normalizePlatform(row.sourcePlatform) }}</td>
            <td>{{ row.sourceFile || "--" }}</td>
            <td>{{ formatQuality(row.qualityScore) }}</td>
            <td>{{ row.rejectReason || "--" }}</td>
            <td>{{ row.suggestFix || "--" }}</td>
            <td>{{ formatDateTime(row.importTime) }}</td>
            <td :title="row.rawExcerpt">{{ row.rawExcerpt || "--" }}</td>
          </tr>
        </tbody>
      </table>
      <p v-else-if="rejectLoaded" class="empty-hint">暂无拒绝记录。</p>
    </div>
  </section>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from "vue";
import {
  clearCrawlerData,
  getImportRejects,
  getSourceTrace,
  importCrawlerFile,
  importCrawlerText,
  runCrawlerByUrls,
  runCrawlerMock
} from "../api/video";
import LoadingPanel from "../components/LoadingPanel.vue";
import { notifyError, notifySuccess, notifyWarning } from "../composables/notify";
import { getErrorMessage, getSuccessMessage } from "../utils/feedback";
import { CRAWLER_PLATFORM_OPTIONS, IMPORT_DEFAULT_PLATFORM_OPTIONS, toPlatformLabel } from "../utils/platform";

const platform = ref("auto");
const urlText = ref("");
const confirmRisk = ref(false);
const running = ref(false);
const clearing = ref(false);
const runningTask = ref("");

const rawText = ref("");
const textDefaultPlatform = ref("unknown");
const crawlerPlatformOptions = CRAWLER_PLATFORM_OPTIONS;
const importDefaultPlatformOptions = IMPORT_DEFAULT_PLATFORM_OPTIONS;
const selectedFile = ref(null);
const aiAssist = ref(false);
const traceLimit = ref(30);
const traceLoading = ref(true);
const traceLoaded = ref(false);
const traceError = ref("");
const sourceTraceRows = ref([]);
const rejectLimit = ref(50);
const rejectLoading = ref(true);
const rejectLoaded = ref(false);
const rejectError = ref("");
const rejectRows = ref([]);
const showRawLog = ref(false);

const result = reactive({
  success: false,
  message: "",
  output: ""
});

const operation = reactive({
  phase: "idle",
  title: "等待操作",
  message: "请在上方选择采集、导入或清理操作，系统会给出成功/失败提示。",
  detail: "",
  time: ""
});

// 【说明】将当前任务类型映射为统一加载组件展示的文案。
const runningLoadingText = computed(() => {
  if (runningTask.value === "url") {
    return "正在执行 URL 采集与入库";
  }
  if (runningTask.value === "mock") {
    return "正在写入模拟数据";
  }
  if (runningTask.value === "import-text") {
    return "正在解析文本并入库";
  }
  if (runningTask.value === "import-file") {
    return "正在解析文件并入库";
  }
  return "正在执行任务";
});

const phaseText = (phase) => {
  if (phase === "running") return "进行中";
  if (phase === "success") return "成功";
  if (phase === "error") return "失败";
  return "待执行";
};

const normalizePlatform = (name) => toPlatformLabel(name);

const formatQuality = (score) => Number(score ?? 0).toFixed(2);

const formatDateTime = (value) => {
  if (!value) return "--";
  const source = String(value).replace(" ", "T");
  const parsed = new Date(source);
  if (Number.isNaN(parsed.getTime())) return String(value);
  return parsed.toLocaleString("zh-CN", { hour12: false });
};

const toUrls = (text) =>
  text
    .split(/\r?\n/)
    .map((line) => line.trim())
    .filter((line) => line && !line.startsWith("#"));

const updateOperation = (phase, title, message, detail = "") => {
  operation.phase = phase;
  operation.title = title;
  operation.message = message;
  operation.detail = detail;
  operation.time = new Date().toLocaleString("zh-CN", { hour12: false });
};

const extractMetric = (output, key) => {
  const match = String(output ?? "").match(new RegExp(`${key}=([0-9]+)`));
  return match ? Number(match[1]) : null;
};

// 【说明】将后端摘要串（键=值,键=值）转换成用户可读状态文案。
const buildImportDetail = (output) => {
  const records = extractMetric(output, "records");
  const acceptedRows = extractMetric(output, "acceptedRows");
  const lowQualityRows = extractMetric(output, "lowQualityRows");
  const rejectedRows = extractMetric(output, "rejectedRows");
  const newVideos = extractMetric(output, "newVideos");
  const updatedVideos = extractMetric(output, "updatedVideos");
  const users = extractMetric(output, "users");
  const behaviors = extractMetric(output, "behaviors");
  const aiRecords = extractMetric(output, "aiRecords");
  const aiUsed = /aiUsed=true/.test(String(output || ""));
  const parts = [];

  if (records !== null) parts.push(`解析记录 ${records}`);
  if (acceptedRows !== null) parts.push(`通过规则 ${acceptedRows}`);
  if (lowQualityRows !== null) parts.push(`低质量入库 ${lowQualityRows}`);
  if (rejectedRows !== null) parts.push(`拒绝 ${rejectedRows}`);
  if (newVideos !== null) parts.push(`新增视频 ${newVideos}`);
  if (updatedVideos !== null) parts.push(`更新视频 ${updatedVideos}`);
  if (users !== null) parts.push(`用户记录 ${users}`);
  if (behaviors !== null) parts.push(`行为日志 ${behaviors}`);
  if (aiUsed) parts.push("已启用 AI 增强");
  if (aiRecords !== null) parts.push(`AI 补充 ${aiRecords}`);

  return parts.join("，");
};

const isParseFailure = (message, output) => {
  const text = `${String(message || "")}\n${String(output || "")}`.toLowerCase();
  return text.includes("未解析到可入库的结构化记录") || text.includes("no valid structured records parsed");
};

const isQualityRejected = (message, output) => {
  const text = `${String(message || "")}\n${String(output || "")}`.toLowerCase();
  return text.includes("no rows passed quality rules") || text.includes("rejectedrows=");
};

const buildFailureDetail = (message, output) => {
  const normalizedOutput = String(output || "").trim();
  if (isParseFailure(message, output)) {
    const help = normalizedOutput || "请检查文件格式后重试。";
    return `${help}\n\n快速操作：\n- CSV 请确认首行有 title（或 标题）列；\n- 纯文本建议勾选“AI 增强解析”；\n- 可先点“填充示例”对照你的文件字段修改。`;
  }
  if (isQualityRejected(message, output)) {
    return `${normalizedOutput || "当前记录未通过质量规则。"}\n\n快速操作：\n- 在“低质量/拒绝记录”中查看具体拒绝原因；\n- 按建议补充平台、播放/点赞/评论、分类、来源 URL；\n- 修正后重新导入。`;
  }
  if (normalizedOutput) {
    return `详情：${normalizedOutput}`;
  }
  return "";
};

// 【说明】统一处理响应结果，保证导入/采集操作反馈风格一致。
const applyResponse = (data, actionName, successFallback, failFallback) => {
  const success = Boolean(data?.success);
  const message = getSuccessMessage(data?.message, success ? successFallback : failFallback);
  const output = String(data?.output || "");

  result.success = success;
  result.message = message;
  result.output = output;
  showRawLog.value = false;

  if (success) {
    const detail = buildImportDetail(output) || "你可以在“来源追踪”中查看新增记录。";
    updateOperation("success", `${actionName}完成`, message, detail);
    notifySuccess(`${actionName}成功：${message}`);
  } else {
    const detail = buildFailureDetail(message, output);
    updateOperation("error", `${actionName}失败`, message, detail);
    notifyError(`${actionName}失败：${message}`);
  }

  return success;
};

const startTask = (taskKey, title, message) => {
  runningTask.value = taskKey;
  running.value = true;
  updateOperation("running", title, message, "");
};

const finishTask = () => {
  running.value = false;
  runningTask.value = "";
};

const refreshTraceSilently = () => {
  // 【说明】每次写入后后台刷新普通追踪与拒绝追踪列表，避免界面信息过期。
  void loadTrace(false);
  void loadRejects(false);
};

const startUrlCrawl = async () => {
  const urls = toUrls(urlText.value);
  if (!urls.length) {
    const msg = "请至少输入 1 条 URL。";
    result.success = false;
    result.message = msg;
    result.output = "";
    updateOperation("error", "URL 采集失败", msg);
    notifyWarning(msg, { title: "参数不完整" });
    return;
  }

  if (!confirmRisk.value) {
    const msg = "请先勾选“我已确认授权与合规风险”。";
    result.success = false;
    result.message = msg;
    result.output = "";
    updateOperation("error", "URL 采集失败", msg);
    notifyWarning(msg, { title: "合规确认" });
    return;
  }

  startTask("url", "URL 采集中", "系统正在请求爬虫并写入数据库，请稍候。");
  try {
    const data = await runCrawlerByUrls({
      platform: platform.value,
      urls,
      confirmRisk: confirmRisk.value
    });
    const success = applyResponse(data, "URL 采集", "采集任务执行完成。", "采集任务执行失败。");
    if (success) {
      refreshTraceSilently();
    }
  } catch (error) {
    const message = getErrorMessage(error, "URL 采集失败。");
    result.success = false;
    result.message = message;
    result.output = "";
    updateOperation("error", "URL 采集失败", message);
    notifyError(`URL 采集失败：${message}`);
  } finally {
    finishTask();
  }
};

const runMock = async () => {
  startTask("mock", "模拟数据写入中", "系统正在写入模拟数据，请稍候。");
  try {
    const data = await runCrawlerMock();
    const success = applyResponse(data, "模拟采集", "模拟任务执行完成。", "模拟任务执行失败。");
    if (success) {
      refreshTraceSilently();
    }
  } catch (error) {
    const message = getErrorMessage(error, "模拟采集失败。");
    result.success = false;
    result.message = message;
    result.output = "";
    updateOperation("error", "模拟采集失败", message);
    notifyError(`模拟采集失败：${message}`);
  } finally {
    finishTask();
  }
};

const clearData = async () => {
  const confirmed = typeof window === "undefined"
    ? true
    : window.confirm("将清除当前账号的全部业务数据，且无法恢复。是否继续？");
  if (!confirmed) {
    notifyWarning("已取消清除操作。", { title: "操作取消" });
    return;
  }

  clearing.value = true;
  updateOperation("running", "数据清理中", "系统正在清理当前账号的所有业务数据。", "");
  try {
    const data = await clearCrawlerData();
    const success = Boolean(data?.success);
    const message = getSuccessMessage(data?.message, success ? "数据清理完成。" : "数据清理失败。");
    const output = String(data?.output || "");

    result.success = success;
    result.message = message;
    result.output = output;

    if (success) {
      updateOperation("success", "数据清理完成", message, "当前账号的数据已清空，可重新导入数据。");
      notifySuccess(`数据清理成功：${message}`);
      sourceTraceRows.value = [];
      traceLoaded.value = true;
      rejectRows.value = [];
      rejectLoaded.value = true;
      refreshTraceSilently();
    } else {
      updateOperation("error", "数据清理失败", message, output ? `详情：${output}` : "");
      notifyError(`数据清理失败：${message}`);
    }
  } catch (error) {
    const message = getErrorMessage(error, "数据清理失败。");
    result.success = false;
    result.message = message;
    result.output = "";
    updateOperation("error", "数据清理失败", message);
    notifyError(`数据清理失败：${message}`);
  } finally {
    clearing.value = false;
  }
};

const onFileChange = (event) => {
  const files = event?.target?.files;
  selectedFile.value = files?.length ? files[0] : null;
};

const fillSampleTemplate = () => {
  rawText.value = `| title | author | platform | play_count | like_count | comment_count | publish_time |
| --- | --- | --- | ---: | ---: | ---: | --- |
| 示例视频A | 示例作者A | bilibili | 12.3w | 4210 | 310 | 2026-03-01 09:30:00 |
| 示例视频B | 示例作者B | douyin | 8.9w | 3000 | 205 | 2026-03-02 11:20:00 |
| 示例视频C | 示例作者C | xiaohongshu | 5.1w | 1800 | 120 | 2026-03-03 14:10:00 |`;
};

const importTextData = async () => {
  if (!rawText.value.trim()) {
    const msg = "请先粘贴要导入的文本。";
    result.success = false;
    result.message = msg;
    result.output = "";
    updateOperation("error", "文本导入失败", msg);
    notifyWarning(msg, { title: "参数不完整" });
    return;
  }

  startTask(
    "import-text",
    "文本导入中",
    aiAssist.value ? "系统正在进行规则解析，并使用 AI 进行增强补充，请稍候。" : "系统正在解析文本并导入数据库，请稍候。"
  );
  try {
    const data = await importCrawlerText({
      text: rawText.value,
      defaultPlatform: textDefaultPlatform.value,
      aiAssist: aiAssist.value
    });
    const success = applyResponse(data, "文本导入", "文本导入完成。", "文本导入失败。");
    if (success) {
      refreshTraceSilently();
    }
  } catch (error) {
    const message = getErrorMessage(error, "文本导入失败。");
    result.success = false;
    result.message = message;
    result.output = "";
    updateOperation("error", "文本导入失败", message);
    notifyError(`文本导入失败：${message}`);
  } finally {
    finishTask();
  }
};

const importFileData = async () => {
  if (!selectedFile.value) {
    const msg = "请先选择文件。";
    result.success = false;
    result.message = msg;
    result.output = "";
    updateOperation("error", "文件导入失败", msg);
    notifyWarning(msg, { title: "参数不完整" });
    return;
  }

  startTask(
    "import-file",
    "文件导入中",
    aiAssist.value
      ? `系统正在解析文件 ${selectedFile.value.name}，并使用 AI 增强提取，请稍候。`
      : `系统正在解析文件 ${selectedFile.value.name} 并导入数据库。`
  );
  try {
    const data = await importCrawlerFile(selectedFile.value, textDefaultPlatform.value, aiAssist.value);
    const success = applyResponse(data, "文件导入", "文件导入完成。", "文件导入失败。");
    if (success) {
      refreshTraceSilently();
    }
  } catch (error) {
    const message = getErrorMessage(error, "文件导入失败。");
    result.success = false;
    result.message = message;
    result.output = "";
    updateOperation("error", "文件导入失败", message);
    notifyError(`文件导入失败：${message}`);
  } finally {
    finishTask();
  }
};

const loadTrace = async (notifyOnError = true) => {
  traceLoading.value = true;
  traceError.value = "";
  try {
    const safeLimit = Math.max(10, Math.min(200, Number(traceLimit.value || 30)));
    traceLimit.value = safeLimit;
    sourceTraceRows.value = (await getSourceTrace(safeLimit, "all")) ?? [];
  } catch (error) {
    const message = getErrorMessage(error, "来源追踪加载失败。请稍后重试。");
    traceError.value = message;
    sourceTraceRows.value = [];
    if (notifyOnError) {
      notifyError(`来源追踪加载失败：${message}`);
    }
  } finally {
    traceLoading.value = false;
    traceLoaded.value = true;
  }
};

const loadRejects = async (notifyOnError = true) => {
  // 【说明】拒绝列表用于指导用户修复源数据，避免盲目重复导入。
  rejectLoading.value = true;
  rejectError.value = "";
  try {
    const safeLimit = Math.max(10, Math.min(500, Number(rejectLimit.value || 50)));
    rejectLimit.value = safeLimit;
    rejectRows.value = (await getImportRejects(safeLimit)) ?? [];
  } catch (error) {
    const message = getErrorMessage(error, "拒绝记录加载失败。请稍后重试。");
    rejectError.value = message;
    rejectRows.value = [];
    if (notifyOnError) {
      notifyError(`拒绝记录加载失败：${message}`);
    }
  } finally {
    rejectLoading.value = false;
    rejectLoaded.value = true;
  }
};

onMounted(() => {
  loadTrace(false);
  loadRejects(false);
});
</script>

<style scoped>
.panel-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  gap: 10px;
  flex-wrap: wrap;
}

.trace-actions {
  margin-top: 0;
}

.inline-field {
  max-width: 120px;
}

.trace-link {
  color: #1f93ff;
  text-decoration: underline;
}

.trace-error {
  margin: 0 0 10px;
}

.status-panel {
  border-width: 2px;
}

.status-head {
  display: inline-flex;
  align-items: center;
  gap: 10px;
}

.status-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: #94a3b8;
  box-shadow: 0 0 0 6px rgba(148, 163, 184, 0.2);
}

.status-title {
  margin: 0;
  font-size: 16px;
  font-weight: 700;
  color: #334155;
}

.status-tag {
  border: 1px solid #d6e2f1;
  border-radius: 999px;
  padding: 3px 10px;
  font-size: 12px;
  color: #55657a;
  background: #f8fbff;
}

.status-message {
  margin: 12px 0 0;
  color: #1f2937;
  line-height: 1.6;
}

.status-detail {
  margin: 8px 0 0;
  color: #4b5563;
  line-height: 1.6;
  white-space: pre-line;
  border-left: 3px solid #e5e7eb;
  padding-left: 10px;
}

.status-time {
  margin: 10px 0 0;
  color: #64748b;
  font-size: 12px;
}

.status-debug {
  margin-top: 10px;
}

.btn-link {
  border: none;
  background: transparent;
  color: #1d76d2;
  font-size: 13px;
  padding: 0;
  cursor: pointer;
}

.btn-link:hover {
  text-decoration: underline;
}

.status-log {
  margin-top: 10px;
  margin-bottom: 0;
  min-height: 0;
  max-height: 220px;
  overflow: auto;
}

.status-running {
  border-color: #b7dbff;
  background: linear-gradient(180deg, #fbfeff 0%, #f2f9ff 100%);
}

.status-running .status-dot {
  background: #1f93ff;
  box-shadow: 0 0 0 6px rgba(31, 147, 255, 0.2);
}

.status-running .status-tag {
  border-color: #c7e2ff;
  color: #1d76d2;
  background: #eff6ff;
}

.status-success {
  border-color: #cbe8d7;
  background: linear-gradient(180deg, #fcfffd 0%, #f4fbf7 100%);
}

.status-success .status-dot {
  background: #16a34a;
  box-shadow: 0 0 0 6px rgba(22, 163, 74, 0.2);
}

.status-success .status-tag {
  border-color: #cde8d7;
  color: #0f8b4c;
  background: #effbf3;
}

.status-error {
  border-color: #f3cece;
  background: linear-gradient(180deg, #fffefe 0%, #fff7f7 100%);
}

.status-error .status-dot {
  background: #dc2626;
  box-shadow: 0 0 0 6px rgba(220, 38, 38, 0.16);
}

.status-error .status-tag {
  border-color: #f3cfcf;
  color: #b91c1c;
  background: #fff1f1;
}

.status-error .status-detail {
  border-left-color: #ef9a9a;
}

.clear-panel {
  border-color: #f3d4d4;
  background: linear-gradient(180deg, #fffdfd, #fff8f8);
}

.btn-danger {
  border: none;
  border-radius: 999px;
  padding: 10px 18px;
  color: #ffffff;
  cursor: pointer;
  font-size: 14px;
  background: linear-gradient(120deg, #dc2626, #ef4444);
  transition: transform 0.16s ease, filter 0.2s ease;
}

.btn-danger:hover {
  transform: translateY(-1px);
  filter: brightness(1.03);
}

.btn-danger:disabled {
  opacity: 0.55;
  cursor: not-allowed;
  transform: none;
}
</style>
