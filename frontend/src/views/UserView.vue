<template>
  <LoadingPanel v-if="loading" text="正在生成用户画像分析" />

  <template v-else>
    <ErrorPanel
      v-if="loadError && !interestData.length"
      title="用户画像加载失败"
      :message="loadError"
      retry-text="重试加载"
      @retry="loadData"
    />

    <section v-else-if="loadError" class="panel warning-panel">
      <h2>提示</h2>
      <p class="warning-text">{{ loadError }}</p>
    </section>

    <section class="panel">
      <div class="header-row">
        <h2>用户画像分析</h2>
        <div class="action-row">
          <label class="form-field inline-field">
            <span>分析用户数</span>
            <input v-model.number="limit" type="number" min="5" max="100" step="5" />
          </label>
          <button class="btn-primary" :disabled="loading" @click="loadData">刷新画像</button>
        </div>
      </div>
      <div v-if="interestData.length" class="metric-grid">
        <div class="metric-item">
          <p class="metric-label">样本用户数</p>
          <p class="metric-value">{{ summary.sampleSize }}</p>
        </div>
        <div class="metric-item">
          <p class="metric-label">人均互动率</p>
          <p class="metric-value">{{ toPercent(summary.avgInteractionRate) }}</p>
        </div>
        <div class="metric-item">
          <p class="metric-label">平均活跃天数</p>
          <p class="metric-value">{{ summary.avgActiveDays }}</p>
        </div>
        <div class="metric-item">
          <p class="metric-label">平均兴趣广度</p>
          <p class="metric-value">{{ summary.avgCategoryDiversity }}</p>
        </div>
        <div class="metric-item">
          <p class="metric-label">跨平台探索占比</p>
          <p class="metric-value">{{ toPercent(summary.crossPlatformRatio) }}</p>
        </div>
        <div class="metric-item">
          <p class="metric-label">高互动用户占比</p>
          <p class="metric-value">{{ toPercent(summary.highInteractionRatio) }}</p>
        </div>
      </div>
      <p v-else-if="loaded" class="empty-hint">暂无用户行为数据，请先导入数据。</p>
    </section>

    <section class="panel">
      <h2>行为结构对比（Top 用户）</h2>
      <EChartPanel v-if="interestData.length" :option="behaviorOption" />
      <p v-else-if="loaded" class="empty-hint">暂无数据。</p>
    </section>

    <section class="panel panel-grid">
      <div>
        <h2>兴趣广度 vs 互动深度</h2>
        <EChartPanel v-if="interestData.length" :option="scatterOption" />
        <p v-else-if="loaded" class="empty-hint">暂无数据。</p>
      </div>
      <div>
        <h2>用户画像标签分布</h2>
        <EChartPanel v-if="interestData.length" :option="profilePieOption" />
        <p v-else-if="loaded" class="empty-hint">暂无数据。</p>
      </div>
    </section>

    <section class="panel">
      <h2>画像运营建议</h2>
      <div v-if="profileInsightRows.length" class="profile-insight-grid">
        <article v-for="row in profileInsightRows" :key="row.key" class="profile-insight-card">
          <p class="profile-insight-title">{{ row.label }}</p>
          <p class="profile-insight-metric">占比 {{ toPercent(row.ratio) }}（{{ row.count }} 人）</p>
          <p class="profile-insight-desc">{{ row.advice }}</p>
        </article>
      </div>
      <p v-else-if="loaded" class="empty-hint">暂无可生成的画像建议。</p>
    </section>

    <section class="panel">
      <h2>用户画像明细</h2>
      <div class="table-wrap">
        <table v-if="interestData.length">
          <thead>
            <tr>
              <th>用户ID</th>
              <th>用户名</th>
              <th>画像标签</th>
              <th>偏好平台</th>
              <th>偏好分类</th>
              <th>总行为</th>
              <th>播放</th>
              <th>点赞</th>
              <th>评论</th>
              <th>活跃天数</th>
              <th>日均行为</th>
              <th>分类广度</th>
              <th>平台广度</th>
              <th>点赞率</th>
              <th>评论率</th>
              <th>互动率</th>
              <th>画像解释</th>
              <th>最近活跃时间</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="item in interestData" :key="item.userId">
              <td>{{ item.userId }}</td>
              <td>{{ item.userName || "--" }}</td>
              <td>
                <span class="profile-tag">{{ profileLabelText(item.profileLabel) }}</span>
              </td>
              <td>{{ toPlatformLabel(item.favoritePlatform) }}</td>
              <td>{{ item.favoriteCategory || "unknown" }}</td>
              <td>{{ formatInt(item.actionCount) }}</td>
              <td>{{ formatInt(item.playCount) }}</td>
              <td>{{ formatInt(item.likeCount) }}</td>
              <td>{{ formatInt(item.commentActionCount) }}</td>
              <td>{{ formatInt(item.activeDays) }}</td>
              <td>{{ formatDecimal(item.avgDailyActions, 2) }}</td>
              <td>{{ formatInt(item.categoryDiversity) }}</td>
              <td>{{ formatInt(item.platformDiversity) }}</td>
              <td>{{ toPercent(item.likeRate) }}</td>
              <td>{{ toPercent(item.commentRate) }}</td>
              <td>{{ toPercent(item.interactionRate) }}</td>
              <td :title="item.profileInsight || '--'">{{ item.profileInsight || "--" }}</td>
              <td>{{ formatDateTime(item.lastActiveTime) }}</td>
            </tr>
          </tbody>
        </table>
        <p v-else-if="loaded" class="empty-hint">暂无数据。</p>
      </div>
    </section>
  </template>
</template>

<script setup>
import { computed, onMounted, ref, watch } from "vue";
import EChartPanel from "../components/EChartPanel.vue";
import ErrorPanel from "../components/ErrorPanel.vue";
import LoadingPanel from "../components/LoadingPanel.vue";
import { getUserInterest } from "../api/video";
import { usePlatformFilter } from "../composables/platformFilter";
import { getErrorMessage } from "../utils/feedback";
import { toPlatformLabel } from "../utils/platform";

const loading = ref(true);
const loaded = ref(false);
const loadError = ref("");
const limit = ref(20);
const interestData = ref([]);
const { selectedPlatform, refreshSignal } = usePlatformFilter();

const PROFILE_LABEL_TEXT = {
  light_browser: "轻度浏览型",
  non_play_interactor: "非播放互动型",
  high_interaction_discuss: "高互动讨论型",
  cross_platform_explorer: "跨平台探索型",
  high_frequency_active: "高频活跃型",
  like_driven: "点赞驱动型",
  steady_viewer: "稳定观看型"
};

const PROFILE_ADVICE = {
  high_interaction_discuss: "建议推送观点类、争议话题和问答互动，重点提升评论深度。",
  cross_platform_explorer: "建议做跨平台同主题分发，统一主线但按平台改封面与标题。",
  high_frequency_active: "建议采用固定更新节奏，提升系列连看与回访率。",
  like_driven: "建议优化封面首屏信息密度，提升点赞到评论的转化。",
  light_browser: "建议投放短时长、高信息密度内容，降低流失。",
  non_play_interactor: "建议回查行为采集映射，避免数据口径偏移。",
  steady_viewer: "建议保持稳定题材供给，逐步测试内容扩展边界。"
};

const profileLabelText = (label) => PROFILE_LABEL_TEXT[label] ?? "未分类";

const formatInt = (v) => Number(v ?? 0).toLocaleString("zh-CN");
const formatDecimal = (v, n = 2) => Number(v ?? 0).toFixed(n);
const toPercent = (v) => `${(Number(v ?? 0) * 100).toFixed(2)}%`;

const formatDateTime = (value) => {
  if (!value) return "--";
  const source = String(value).replace(" ", "T");
  const parsed = new Date(source);
  if (Number.isNaN(parsed.getTime())) return String(value);
  return parsed.toLocaleString("zh-CN", { hour12: false });
};

const summary = computed(() => {
  const rows = interestData.value;
  const n = rows.length;
  if (!n) {
    return {
      sampleSize: 0,
      avgInteractionRate: 0,
      avgActiveDays: 0,
      avgCategoryDiversity: 0,
      crossPlatformRatio: 0,
      highInteractionRatio: 0
    };
  }

  const interactionSum = rows.reduce((acc, item) => acc + Number(item.interactionRate ?? 0), 0);
  const activeDaysSum = rows.reduce((acc, item) => acc + Number(item.activeDays ?? 0), 0);
  const diversitySum = rows.reduce((acc, item) => acc + Number(item.categoryDiversity ?? 0), 0);
  const crossPlatformCount = rows.filter((item) => Number(item.platformDiversity ?? 0) > 1).length;
  const highInteractionCount = rows.filter((item) => item.profileLabel === "high_interaction_discuss").length;

  return {
    sampleSize: n,
    avgInteractionRate: interactionSum / n,
    avgActiveDays: (activeDaysSum / n).toFixed(1),
    avgCategoryDiversity: (diversitySum / n).toFixed(1),
    crossPlatformRatio: crossPlatformCount / n,
    highInteractionRatio: highInteractionCount / n
  };
});

const behaviorOption = computed(() => ({
  tooltip: { trigger: "axis", axisPointer: { type: "shadow" } },
  legend: { data: ["播放", "点赞", "评论"], textStyle: { color: "#475569" } },
  grid: { top: 44, left: 54, right: 20, bottom: 86 },
  xAxis: {
    type: "category",
    axisLabel: { color: "#4b5563", rotate: 25 },
    data: interestData.value.map((item) => item.userName || `U${item.userId}`)
  },
  yAxis: {
    type: "value",
    axisLabel: { color: "#4b5563" },
    splitLine: { lineStyle: { color: "#e6edf6" } }
  },
  series: [
    {
      name: "播放",
      type: "bar",
      stack: "actions",
      data: interestData.value.map((item) => Number(item.playCount ?? 0)),
      itemStyle: { color: "#1f93ff" }
    },
    {
      name: "点赞",
      type: "bar",
      stack: "actions",
      data: interestData.value.map((item) => Number(item.likeCount ?? 0)),
      itemStyle: { color: "#20b274" }
    },
    {
      name: "评论",
      type: "bar",
      stack: "actions",
      data: interestData.value.map((item) => Number(item.commentActionCount ?? 0)),
      itemStyle: { color: "#f59f0b" }
    }
  ]
}));

const scatterOption = computed(() => ({
  tooltip: {
    trigger: "item",
    formatter: (params) => {
      const [categoryDiversity, interactionRate, avgDailyActions, userName, platform, actionCount, profileLabel] = params.value;
      return [
        `用户：${userName}`,
        `画像：${profileLabelText(profileLabel)}`,
        `偏好平台：${toPlatformLabel(platform)}`,
        `分类广度：${categoryDiversity}`,
        `互动率：${Number(interactionRate).toFixed(2)}%`,
        `日均行为：${Number(avgDailyActions).toFixed(2)}`,
        `总行为数：${formatInt(actionCount)}`
      ].join("<br/>");
    }
  },
  grid: { top: 24, left: 60, right: 30, bottom: 56 },
  xAxis: {
    type: "value",
    name: "分类广度",
    nameTextStyle: { color: "#4b5563" },
    axisLabel: { color: "#4b5563" }
  },
  yAxis: {
    type: "value",
    name: "互动率(%)",
    nameTextStyle: { color: "#4b5563" },
    axisLabel: { color: "#4b5563" },
    splitLine: { lineStyle: { color: "#e6edf6" } }
  },
  series: [
    {
      type: "scatter",
      data: interestData.value.map((item) => [
        Number(item.categoryDiversity ?? 0),
        Number(item.interactionRate ?? 0) * 100,
        Number(item.avgDailyActions ?? 0),
        item.userName,
        item.favoritePlatform,
        Number(item.actionCount ?? 0),
        item.profileLabel
      ]),
      symbolSize: (val) => {
        const size = Number(val?.[2] ?? 0) * 2 + 10;
        return Math.min(44, Math.max(12, size));
      },
      itemStyle: {
        color: (params) => {
          const platform = String(params.value?.[4] ?? "").toLowerCase();
          if (platform.includes("bilibili")) return "#38bdf8";
          if (platform.includes("douyin")) return "#f97316";
          if (platform.includes("kuaishou")) return "#ef4444";
          if (platform.includes("xiaohongshu")) return "#ec4899";
          if (platform.includes("xigua")) return "#f59e0b";
          if (platform.includes("weibo")) return "#f43f5e";
          if (platform.includes("youtube")) return "#dc2626";
          if (platform.includes("tiktok")) return "#14b8a6";
          if (platform.includes("acfun")) return "#6366f1";
          if (platform.includes("seed")) return "#4f87ff";
          return "#1f93ff";
        },
        opacity: 0.9
      }
    }
  ]
}));

const profilePieOption = computed(() => {
  const grouped = interestData.value.reduce((acc, item) => {
    const key = profileLabelText(item.profileLabel);
    acc[key] = (acc[key] ?? 0) + 1;
    return acc;
  }, {});

  return {
    tooltip: { trigger: "item" },
    legend: { bottom: 0, textStyle: { color: "#475569" } },
    series: [
      {
        type: "pie",
        radius: ["38%", "68%"],
        center: ["50%", "44%"],
        label: { color: "#334155", formatter: "{b}: {d}%" },
        data: Object.entries(grouped).map(([name, value]) => ({ name, value })),
        color: ["#1f93ff", "#20b274", "#f59f0b", "#f97316", "#4f87ff", "#1fa4b8", "#94a3b8"]
      }
    ]
  };
});

const profileInsightRows = computed(() => {
  const rows = interestData.value;
  if (!rows.length) {
    return [];
  }

  const grouped = rows.reduce((acc, item) => {
    const key = item.profileLabel || "steady_viewer";
    acc[key] = (acc[key] ?? 0) + 1;
    return acc;
  }, {});

  return Object.entries(grouped)
    .map(([key, count]) => ({
      key,
      count,
      ratio: count / rows.length,
      label: profileLabelText(key),
      advice: PROFILE_ADVICE[key] || "建议继续观察其行为变化。"
    }))
    .sort((a, b) => b.count - a.count)
    .slice(0, 4);
});

const loadData = async () => {
  loading.value = true;
  loadError.value = "";
  try {
    const safeLimit = Math.max(5, Math.min(100, Number(limit.value || 20)));
    limit.value = safeLimit;
    interestData.value = (await getUserInterest(safeLimit, selectedPlatform.value)) ?? [];
  } catch (error) {
    interestData.value = [];
    loadError.value = getErrorMessage(error, "用户画像分析加载失败。请稍后重试。");
  } finally {
    loading.value = false;
    loaded.value = true;
  }
};

onMounted(() => {
  loadData();
});

watch([selectedPlatform, refreshSignal], () => {
  loadData();
});
</script>

<style scoped>
.header-row {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  gap: 12px;
  flex-wrap: wrap;
}

.inline-field {
  max-width: 160px;
}

.inline-field input {
  border-radius: 10px;
  border: 1px solid #d4deeb;
  background: #ffffff;
  color: #0f172a;
  padding: 10px 12px;
}

.panel-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(340px, 1fr));
  gap: 14px;
}

.profile-tag {
  display: inline-block;
  padding: 4px 10px;
  border-radius: 999px;
  font-size: 12px;
  color: #165dab;
  background: #e7f2ff;
  border: 1px solid #bddcff;
}

.profile-insight-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 12px;
}

.profile-insight-card {
  border: 1px solid #dce7f6;
  border-radius: 14px;
  padding: 12px;
  background: linear-gradient(180deg, #f8fbff, #f2f7ff);
}

.profile-insight-title,
.profile-insight-metric,
.profile-insight-desc {
  margin: 0;
}

.profile-insight-title {
  font-size: 14px;
  color: #1f2937;
  font-weight: 600;
}

.profile-insight-metric {
  margin-top: 6px;
  color: #2563eb;
  font-size: 13px;
}

.profile-insight-desc {
  margin-top: 8px;
  color: #55657a;
  font-size: 13px;
  line-height: 1.55;
}

.warning-panel {
  border-color: #f2deb9;
  background: linear-gradient(180deg, #fffcf6, #fff8ea);
}

.warning-text {
  margin: 0;
  color: #8a6a2a;
}
</style>
