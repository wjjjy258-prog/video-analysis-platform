<template>
  <LoadingPanel v-if="loading" text="正在计算互动效率指标" />

  <template v-else>
    <ErrorPanel
      v-if="loadError && !hasAnyData"
      title="互动效率分析加载失败"
      :message="loadError"
      retry-text="重试加载"
      @retry="loadData"
    />

    <section v-else-if="loadError" class="panel warning-panel">
      <h2>提示</h2>
      <p class="warning-text">{{ loadError }}</p>
    </section>

    <section class="panel">
      <h2>互动效率分析</h2>
      <div class="action-row">
        <label class="form-field inline-field">
          <span>最小播放量</span>
          <input v-model.number="minPlay" type="number" min="0" step="1000" />
        </label>
        <label class="form-field inline-field">
          <span>TOP 视频数</span>
          <input v-model.number="videoLimit" type="number" min="5" max="100" step="5" />
        </label>
        <button class="btn-primary" :disabled="loading" @click="loadData">刷新分析</button>
      </div>
    </section>

    <section class="panel">
      <h2>分区互动率（点赞+评论 / 播放）</h2>
      <EChartPanel v-if="categoryStats.length" :option="categoryOption" />
      <p v-else-if="loaded" class="empty-hint">暂无数据。</p>
    </section>

    <section class="panel">
      <h2>平台互动转化（播放 → 点赞 → 评论）</h2>
      <p class="section-tip">说明：点赞转化率=点赞/播放；评论转化率=评论/播放；评赞比=评论/点赞。</p>
      <div v-if="funnelStats.length" class="metric-grid compact-grid">
        <div class="metric-item">
          <p class="metric-label">总播放</p>
          <p class="metric-value">{{ formatInt(funnelSummary.totalPlay) }}</p>
        </div>
        <div class="metric-item">
          <p class="metric-label">总点赞</p>
          <p class="metric-value">{{ formatInt(funnelSummary.totalLike) }}</p>
        </div>
        <div class="metric-item">
          <p class="metric-label">总评论</p>
          <p class="metric-value">{{ formatInt(funnelSummary.totalComment) }}</p>
        </div>
        <div class="metric-item">
          <p class="metric-label">点赞转化率（点赞/播放）</p>
          <p class="metric-value">{{ toPercent(funnelSummary.likeRate) }}</p>
        </div>
        <div class="metric-item">
          <p class="metric-label">评论转化率（评论/播放）</p>
          <p class="metric-value">{{ toPercent(funnelSummary.commentRate) }}</p>
        </div>
        <div class="metric-item">
          <p class="metric-label">评赞比（评论/点赞）</p>
          <p class="metric-value">{{ toPercent(funnelSummary.commentLikeRate) }}</p>
        </div>
      </div>
      <div v-if="funnelStats.length" class="table-wrap top-gap">
        <table>
          <thead>
            <tr>
              <th>平台</th>
              <th>视频数</th>
              <th>总播放</th>
              <th>总点赞</th>
              <th>总评论</th>
              <th>点赞转化率（点赞/播放）</th>
              <th>评论转化率（评论/播放）</th>
              <th>评赞比（评论/点赞）</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in funnelStats" :key="row.sourcePlatform">
              <td>{{ normalizePlatform(row.sourcePlatform) }}</td>
              <td>{{ formatInt(row.videoCount) }}</td>
              <td>{{ formatInt(row.totalPlay) }}</td>
              <td>{{ formatInt(row.totalLike) }}</td>
              <td>{{ formatInt(row.totalComment) }}</td>
              <td>{{ toPercent(row.likeConversionRate) }}</td>
              <td>{{ toPercent(row.commentConversionRate) }}</td>
              <td>{{ toPercent(commentByLike(row)) }}</td>
            </tr>
          </tbody>
        </table>
      </div>
      <p v-else-if="loaded" class="empty-hint">暂无平台漏斗数据。</p>
    </section>

    <section class="panel">
      <div class="panel-head">
        <h2>平台标准化对比（单视频均值 + 每千播放互动）</h2>
        <div class="view-switch">
          <button :class="benchmarkMode === '2d' ? 'is-active' : ''" @click="benchmarkMode = '2d'">2D 图表</button>
          <button :class="benchmarkMode === '3d' ? 'is-active' : ''" @click="benchmarkMode = '3d'">3D 图表</button>
        </div>
      </div>
      <EChartPanel v-if="benchmarkStats.length && benchmarkMode === '2d'" :option="benchmarkOption" />
      <div v-else-if="benchmarkStats.length && benchmarkMode === '3d'">
        <ThreeBarScene :items="benchmarkThreeItems" />
        <p class="view-tip">3D 柱高代表每千播放互动值，适合快速比较平台效率。</p>
      </div>
      <p v-else-if="loaded" class="empty-hint">暂无平台标准化数据。</p>
    </section>

    <section class="panel">
      <h2>高互动效率视频 TOP</h2>
      <div class="table-wrap">
        <table v-if="topVideos.length">
          <thead>
            <tr>
              <th>ID</th>
              <th>标题</th>
              <th>作者</th>
              <th>平台</th>
              <th>分区</th>
              <th>播放量</th>
              <th>点赞量</th>
              <th>评论量</th>
              <th>互动率</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="item in topVideos" :key="item.id">
              <td>{{ item.id }}</td>
              <td :title="item.title">{{ item.title }}</td>
              <td>{{ item.author }}</td>
              <td>{{ normalizePlatform(item.sourcePlatform) }}</td>
              <td>{{ item.category }}</td>
              <td>{{ formatInt(item.playCount) }}</td>
              <td>{{ formatInt(item.likeCount) }}</td>
              <td>{{ formatInt(item.commentCount) }}</td>
              <td>{{ toPercent(item.engagementRate) }}</td>
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
import {
  getCategoryEngagementStats,
  getPlatformBenchmark,
  getPlatformFunnel,
  getTopEngagementVideos
} from "../api/video";
import EChartPanel from "../components/EChartPanel.vue";
import ErrorPanel from "../components/ErrorPanel.vue";
import LoadingPanel from "../components/LoadingPanel.vue";
import ThreeBarScene from "../components/ThreeBarScene.vue";
import { usePlatformFilter } from "../composables/platformFilter";
import { getErrorMessage } from "../utils/feedback";
import { toPlatformLabel } from "../utils/platform";

const loading = ref(true);
const loaded = ref(false);
const loadError = ref("");
const minPlay = ref(10000);
const videoLimit = ref(20);
const categoryStats = ref([]);
const topVideos = ref([]);
const funnelStats = ref([]);
const benchmarkStats = ref([]);
const benchmarkMode = ref("2d");
const { selectedPlatform, refreshSignal } = usePlatformFilter();

const hasAnyData = computed(
  () => categoryStats.value.length > 0 || topVideos.value.length > 0 || funnelStats.value.length > 0 || benchmarkStats.value.length > 0
);

const formatInt = (v) => Number(v ?? 0).toLocaleString("zh-CN");
const toPercent = (v) => `${(Number(v ?? 0) * 100).toFixed(2)}%`;
const toCompact = (v) => {
  const n = Number(v ?? 0);
  if (!Number.isFinite(n)) {
    return "0";
  }
  if (Math.abs(n) >= 100000000) {
    return `${(n / 100000000).toFixed(1)}亿`;
  }
  if (Math.abs(n) >= 10000) {
    return `${(n / 10000).toFixed(1)}万`;
  }
  return Math.round(n).toLocaleString("zh-CN");
};

const normalizePlatform = (name) => toPlatformLabel(name);
const commentByLike = (row) => {
  const totalLike = Number(row?.totalLike ?? 0);
  const totalComment = Number(row?.totalComment ?? 0);
  if (totalLike <= 0) {
    return 0;
  }
  return totalComment / totalLike;
};

const funnelSummary = computed(() => {
  const totalPlay = funnelStats.value.reduce((sum, row) => sum + Number(row.totalPlay ?? 0), 0);
  const totalLike = funnelStats.value.reduce((sum, row) => sum + Number(row.totalLike ?? 0), 0);
  const totalComment = funnelStats.value.reduce((sum, row) => sum + Number(row.totalComment ?? 0), 0);
  const likeRate = totalPlay > 0 ? totalLike / totalPlay : 0;
  const commentRate = totalPlay > 0 ? totalComment / totalPlay : 0;
  const commentLikeRate = totalLike > 0 ? totalComment / totalLike : 0;
  return {
    totalPlay,
    totalLike,
    totalComment,
    likeRate,
    commentRate,
    commentLikeRate
  };
});

const categoryOption = computed(() => ({
  tooltip: {
    trigger: "axis",
    axisPointer: { type: "shadow" }
  },
  legend: { textStyle: { color: "#475569" } },
  grid: { left: 56, right: 20, top: 30, bottom: 80 },
  xAxis: {
    type: "category",
    axisLabel: { color: "#4b5563", rotate: 30 },
    axisLine: { lineStyle: { color: "#cfd8e5" } },
    data: categoryStats.value.map((item) => item.category)
  },
  yAxis: {
    type: "value",
    axisLabel: {
      color: "#4b5563",
      formatter: (value) => `${(value * 100).toFixed(1)}%`
    },
    splitLine: { lineStyle: { color: "#e7edf6" } }
  },
  series: [
    {
      name: "互动率",
      type: "bar",
      itemStyle: { color: "#1f93ff" },
      data: categoryStats.value.map((item) => Number(item.engagementRate ?? 0))
    },
    {
      name: "点赞率",
      type: "line",
      smooth: true,
      lineStyle: { color: "#20b274" },
      data: categoryStats.value.map((item) => Number(item.likeRate ?? 0))
    },
    {
      name: "评论率",
      type: "line",
      smooth: true,
      lineStyle: { color: "#f59f0b" },
      data: categoryStats.value.map((item) => Number(item.commentRate ?? 0))
    }
  ]
}));

const benchmarkOption = computed(() => {
  const playValues = benchmarkStats.value.map((row) => Number(row.avgPlayPerVideo ?? 0));
  const likeValues = benchmarkStats.value.map((row) => Number(row.avgLikePerVideo ?? 0));
  const engagementValues = benchmarkStats.value.map((row) => Number(row.engagementPerThousandPlay ?? 0));
  const singlePlatform = benchmarkStats.value.length <= 1;

  const maxPlay = Math.max(...playValues, 1);
  const maxLike = Math.max(...likeValues, 1);
  const playIndex = playValues.map((v) => (v / maxPlay) * 100);
  const likeIndex = likeValues.map((v) => (v / maxLike) * 100);

  return {
  tooltip: {
    trigger: "axis",
    axisPointer: { type: "shadow" },
    formatter(params) {
      const index = params?.[0]?.dataIndex ?? 0;
      const platform = benchmarkStats.value[index];
      if (!platform) {
        return "";
      }
      return [
        `<b>${normalizePlatform(platform.sourcePlatform)}</b>`,
        `单视频均播放：${toCompact(platform.avgPlayPerVideo)}（指数 ${playIndex[index].toFixed(1)}）`,
        `单视频均点赞：${toCompact(platform.avgLikePerVideo)}（指数 ${likeIndex[index].toFixed(1)}）`,
        `每千播放互动值：${Number(platform.engagementPerThousandPlay ?? 0).toFixed(2)}`
      ].join("<br/>");
    }
  },
  legend: { top: 2, textStyle: { color: "#475569" } },
  grid: { top: 54, left: 96, right: 96, bottom: 64, containLabel: true },
  xAxis: {
    type: "category",
    data: benchmarkStats.value.map((row) => normalizePlatform(row.sourcePlatform)),
    axisLabel: { color: "#4b5563" },
    axisLine: { lineStyle: { color: "#d2dceb" } }
  },
  yAxis: [
    {
      type: "value",
      name: "标准化指数",
      min: 0,
      max: 100,
      axisLabel: {
        color: "#4b5563",
        formatter: (value) => `${value}%`
      },
      splitLine: { lineStyle: { color: "#e8eef7" } }
    },
    {
      type: "value",
      name: "每千播放互动值",
      axisLabel: { color: "#4b5563", formatter: (value) => Number(value).toFixed(1) }
    }
  ],
  series: [
    {
      name: "单视频均播放指数",
      type: "bar",
      data: playIndex,
      barMaxWidth: 30,
      barCategoryGap: singlePlatform ? "62%" : "46%",
      barGap: singlePlatform ? "55%" : "30%",
      itemStyle: { color: "#1f93ff" },
      label: {
        show: !singlePlatform,
        position: "top",
        color: "#334155",
        formatter: (p) => `${Number(p.value ?? 0).toFixed(1)}%`
      }
    },
    {
      name: "单视频均点赞指数",
      type: "bar",
      data: likeIndex,
      barMaxWidth: 30,
      barCategoryGap: singlePlatform ? "62%" : "46%",
      barGap: singlePlatform ? "55%" : "30%",
      itemStyle: { color: "#20b274" },
      label: {
        show: !singlePlatform,
        position: "top",
        color: "#334155",
        formatter: (p) => `${Number(p.value ?? 0).toFixed(1)}%`
      }
    },
    {
      name: "每千播放互动值",
      type: "line",
      yAxisIndex: 1,
      smooth: true,
      data: engagementValues,
      symbolSize: 8,
      lineStyle: { color: "#7c3aed", width: 3 },
      itemStyle: { color: "#7c3aed" },
      label: {
        show: true,
        position: singlePlatform ? "right" : "top",
        offset: singlePlatform ? [10, -2] : [0, -2],
        color: "#5b21b6",
        formatter: (p) => Number(p.value ?? 0).toFixed(2)
      }
    }
  ]
};
});

const benchmarkThreeItems = computed(() =>
  benchmarkStats.value.map((row) => ({
    label: normalizePlatform(row.sourcePlatform),
    value: Number(row.engagementPerThousandPlay ?? 0)
  }))
);

const loadData = async () => {
  loading.value = true;
  loadError.value = "";

  const [cRes, vRes, fRes, bRes] = await Promise.allSettled([
    getCategoryEngagementStats(selectedPlatform.value),
    getTopEngagementVideos(videoLimit.value, minPlay.value, selectedPlatform.value),
    getPlatformFunnel(selectedPlatform.value),
    getPlatformBenchmark(selectedPlatform.value)
  ]);

  categoryStats.value = cRes.status === "fulfilled" ? cRes.value ?? [] : [];
  topVideos.value = vRes.status === "fulfilled" ? vRes.value ?? [] : [];
  funnelStats.value = fRes.status === "fulfilled" ? fRes.value ?? [] : [];
  benchmarkStats.value = bRes.status === "fulfilled" ? bRes.value ?? [] : [];

  const failedMessages = [cRes, vRes, fRes, bRes]
    .filter((item) => item.status === "rejected")
    .map((item) => getErrorMessage(item.reason, "请求失败"));

  if (failedMessages.length) {
    loadError.value =
      failedMessages.length === 4
        ? failedMessages[0]
        : `部分分析数据加载失败（${failedMessages.length}/4），已展示可用结果。`;
  }

  loading.value = false;
  loaded.value = true;
};

onMounted(() => {
  loadData().catch((error) => {
    loadError.value = getErrorMessage(error, "互动效率分析加载失败。请稍后重试。");
    loading.value = false;
    loaded.value = true;
  });
});

watch([selectedPlatform, refreshSignal], () => {
  loadData();
});
</script>

<style scoped>
.inline-field {
  max-width: 180px;
}

.inline-field input {
  border-radius: 10px;
  border: 1px solid #d4deeb;
  background: #ffffff;
  color: #0f172a;
  padding: 10px 12px;
}

.panel-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.view-switch {
  display: inline-flex;
  border: 1px solid #cddbf0;
  border-radius: 999px;
  overflow: hidden;
}

.view-switch button {
  border: none;
  background: #f6f9ff;
  color: #4b5563;
  padding: 7px 14px;
  cursor: pointer;
  font-size: 13px;
}

.view-switch button.is-active {
  background: #1f93ff;
  color: #ffffff;
}

.view-tip {
  margin: 10px 2px 0;
  color: #64748b;
  font-size: 13px;
}

.section-tip {
  margin: 0 0 12px;
  color: #64748b;
  font-size: 13px;
}

.compact-grid {
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
}

.top-gap {
  margin-top: 12px;
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
