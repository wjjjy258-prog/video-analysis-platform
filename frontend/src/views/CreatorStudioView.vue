<template>
  <LoadingPanel v-if="loading" text="创作者中心数据加载中，请稍候..." />

  <template v-else>
    <ErrorPanel
      v-if="loadError && !hasAnyCreatorData"
      title="创作者中心加载失败"
      :message="loadError"
      retry-text="重新加载"
      @retry="loadData"
    />

    <section v-else-if="loadError" class="panel warning-panel">
      <h2>操作提醒</h2>
      <p class="warning-text">{{ loadError }}</p>
    </section>

    <section class="hero-card creator-hero">
      <div class="hero-main">
        <div class="hero-avatar">{{ creatorInitial }}</div>
        <div>
          <p class="hero-kicker">创作者专属工作台</p>
          <h2 class="hero-title">{{ creatorDisplayName }}</h2>
          <p class="hero-desc">
            聚焦你的作品表现、内容方向与同行对标结果，帮助你更快判断“当前强项在哪、差距在哪、下一步该做什么”。
          </p>
          <div class="hero-tags">
            <span>登录账号：{{ profile.username || "--" }}</span>
            <span>主运营平台：{{ toPlatformLabel(profile.creatorPlatform || "unknown") }}</span>
            <span>当前分析范围：{{ activePlatformLabel }}</span>
            <span>主打方向：{{ profile.creatorFocusCategory || "未设置" }}</span>
          </div>
          <div class="creator-profile-editor">
            <button class="creator-edit-trigger" type="button" @click="toggleProfileEdit">
              {{ editingProfile ? "取消编辑" : "编辑主运营平台和主打方向" }}
            </button>
            <form v-if="editingProfile" class="creator-edit-form" @submit.prevent="saveProfileEdit">
              <label class="creator-edit-field">
                <span>主运营平台</span>
                <select v-model="profileEditForm.creatorPlatform" :disabled="savingProfile">
                  <option v-for="item in creatorPlatformOptions" :key="item.value" :value="item.value">
                    {{ item.label }}
                  </option>
                </select>
              </label>
              <label class="creator-edit-field creator-edit-field-grow">
                <span>主打方向</span>
                <input
                  v-model.trim="profileEditForm.creatorFocusCategory"
                  type="text"
                  maxlength="60"
                  :disabled="savingProfile"
                  placeholder="例如：科技、知识、剧情、游戏、财经"
                />
              </label>
              <button class="creator-save-btn" type="submit" :disabled="savingProfile">
                {{ savingProfile ? "保存中..." : "保存设置" }}
              </button>
            </form>
          </div>
        </div>
      </div>

      <div class="hero-side">
        <article class="signal-card" :class="playSignal.tone">
          <p class="signal-label">平均播放/条 对比同行</p>
          <p class="signal-value">{{ playSignal.value }}</p>
          <p class="signal-desc">{{ playSignal.desc }}</p>
        </article>
        <article class="signal-card" :class="engagementSignal.tone">
          <p class="signal-label">每千播放互动值 对比同行</p>
          <p class="signal-value">{{ engagementSignal.value }}</p>
          <p class="signal-desc">{{ engagementSignal.desc }}</p>
        </article>
      </div>
    </section>

    <section class="panel">
      <h2>我的数据总览</h2>
      <div class="metric-grid">
        <div class="metric-item">
          <p class="metric-label">我的视频数</p>
          <p class="metric-value">{{ formatInt(ownOverview.videoCount) }}</p>
        </div>
        <div class="metric-item">
          <p class="metric-label">总播放量</p>
          <p class="metric-value">{{ formatInt(ownOverview.totalPlayCount) }}</p>
        </div>
        <div class="metric-item">
          <p class="metric-label">总点赞量</p>
          <p class="metric-value">{{ formatInt(ownOverview.totalLikeCount) }}</p>
        </div>
        <div class="metric-item">
          <p class="metric-label">总评论量</p>
          <p class="metric-value">{{ formatInt(ownOverview.totalCommentCount) }}</p>
        </div>
        <div class="metric-item">
          <p class="metric-label">平均播放/条</p>
          <p class="metric-value">{{ formatDecimal(ownOverview.avgPlayPerVideo, 0) }}</p>
        </div>
        <div class="metric-item">
          <p class="metric-label">每千播放互动值</p>
          <p class="metric-value">{{ formatDecimal(ownOverview.engagementPerThousandPlay) }}</p>
        </div>
      </div>
    </section>

    <section class="panel">
      <h2>同行对标概览</h2>
      <div class="metric-grid">
        <div class="metric-item">
          <p class="metric-label">同行样本数</p>
          <p class="metric-value">{{ formatInt(rivalSummary.rivalCount) }}</p>
        </div>
        <div class="metric-item">
          <p class="metric-label">同行平均播放/条</p>
          <p class="metric-value">{{ formatDecimal(rivalSummary.avgPlayPerVideo, 0) }}</p>
        </div>
        <div class="metric-item">
          <p class="metric-label">同行每千播放互动值</p>
          <p class="metric-value">{{ formatDecimal(rivalSummary.engagementPerThousandPlay) }}</p>
        </div>
        <div class="metric-item">
          <p class="metric-label">领先同行作者</p>
          <p class="metric-value metric-value-small">{{ rivalSummary.leadingAuthor || "暂无" }}</p>
        </div>
        <div class="metric-item">
          <p class="metric-label">领先作者平均播放/条</p>
          <p class="metric-value">{{ formatDecimal(rivalSummary.leadingAuthorAvgPlay, 0) }}</p>
        </div>
        <div class="metric-item">
          <p class="metric-label">领先作者每千播放互动值</p>
          <p class="metric-value">{{ formatDecimal(rivalSummary.leadingAuthorEngagement) }}</p>
        </div>
      </div>
    </section>

    <section class="panel panel-grid">
      <div>
        <h2>我的表现 vs 同行均值</h2>
        <EChartPanel v-if="hasBenchmarkData" :option="benchmarkOption" />
        <p v-else class="empty-hint">暂无可对比样本，请先导入本人及同行数据。</p>
      </div>
      <div>
        <h2>我的内容方向分布（按播放量）</h2>
        <EChartPanel v-if="ownCategoryStats.length" :option="categoryOption" />
        <p v-else class="empty-hint">暂无分类样本。</p>
      </div>
    </section>

    <section class="panel panel-grid">
      <div>
        <h2>我与同行分布（平均播放 × 互动值）</h2>
        <EChartPanel v-if="rivalAuthors.length || ownOverview.videoCount" :option="rivalScatterOption" />
        <p v-else class="empty-hint">暂无同行样本，无法生成分布图。</p>
      </div>
      <div>
        <h2>创作建议</h2>
        <div v-if="suggestions.length" class="suggestion-list">
          <article v-for="(item, index) in suggestions" :key="`${index}-${item}`" class="suggestion-card">
            <span class="suggestion-index">{{ index + 1 }}</span>
            <p>{{ item }}</p>
          </article>
        </div>
        <p v-else class="empty-hint">样本不足，暂无法给出建议。</p>
      </div>
    </section>

    <section class="panel">
      <div class="section-head">
        <h2>我的热门作品 TOP 5</h2>
        <p class="section-tip">按播放量排序</p>
      </div>
      <div class="table-wrap">
        <table v-if="ownTopVideos.length">
          <thead>
            <tr>
              <th>标题</th>
              <th>平台</th>
              <th>分类</th>
              <th>播放量</th>
              <th>点赞量</th>
              <th>评论量</th>
              <th>质量分</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="item in ownTopVideos" :key="item.id">
              <td :title="item.title">{{ item.title || "--" }}</td>
              <td>{{ toPlatformLabel(item.sourcePlatform) }}</td>
              <td>{{ item.category || "--" }}</td>
              <td>{{ formatInt(item.playCount) }}</td>
              <td>{{ formatInt(item.likeCount) }}</td>
              <td>{{ formatInt(item.commentCount) }}</td>
              <td>{{ formatDecimal(item.dataQualityScore) }}</td>
            </tr>
          </tbody>
        </table>
        <p v-else class="empty-hint">暂无个人作品数据。</p>
      </div>
    </section>

    <section class="panel">
      <div class="section-head">
        <h2>同行样本明细</h2>
        <p class="section-tip">当前样本按“平均播放/条”降序排列</p>
      </div>
      <div class="table-wrap">
        <table v-if="rivalAuthors.length">
          <thead>
            <tr>
              <th>作者</th>
              <th>平台</th>
              <th>主分类</th>
              <th>视频数</th>
              <th>总播放量</th>
              <th>平均播放/条</th>
              <th>每千播放互动值</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="item in rivalAuthors" :key="`${item.author}-${item.sourcePlatform}`">
              <td>{{ item.author || "--" }}</td>
              <td>{{ toPlatformLabel(item.sourcePlatform) }}</td>
              <td>{{ item.mainCategory || "--" }}</td>
              <td>{{ formatInt(item.videoCount) }}</td>
              <td>{{ formatInt(item.totalPlayCount) }}</td>
              <td>{{ formatDecimal(item.avgPlayPerVideo, 0) }}</td>
              <td>{{ formatDecimal(item.engagementPerThousandPlay) }}</td>
            </tr>
          </tbody>
        </table>
        <p v-else class="empty-hint">暂无同行数据。</p>
      </div>
    </section>
  </template>
</template>

<script setup>
import { computed, reactive, ref, watch } from "vue";
import { getCreatorDashboard, updateCreatorProfile } from "../api/video";
import { usePlatformFilter } from "../composables/platformFilter";
import { notifyError, notifySuccess } from "../composables/notify";
import { getErrorMessage } from "../utils/feedback";
import EChartPanel from "../components/EChartPanel.vue";
import ErrorPanel from "../components/ErrorPanel.vue";
import LoadingPanel from "../components/LoadingPanel.vue";

const creatorPlatformOptions = [
  { value: "unknown", label: "未知来源" },
  { value: "bilibili", label: "哔哩哔哩" },
  { value: "douyin", label: "抖音" },
  { value: "kuaishou", label: "快手" },
  { value: "xiaohongshu", label: "小红书" },
  { value: "xigua", label: "西瓜视频" },
  { value: "weibo", label: "微博" },
  { value: "youtube", label: "YouTube" },
  { value: "tiktok", label: "TikTok" },
  { value: "acfun", label: "AcFun" }
];

const platformLabelMap = new Map([
  ["all", "全部平台"],
  ["seed", "系统内置数据"],
  ["manual_text", "未知来源"],
  ...creatorPlatformOptions.map((item) => [item.value, item.label])
]);

const { selectedPlatform, refreshSignal } = usePlatformFilter();

const loading = ref(true);
const loaded = ref(false);
const loadError = ref("");
const editingProfile = ref(false);
const savingProfile = ref(false);

const profile = reactive({
  username: "",
  role: "",
  creatorName: "",
  creatorPlatform: "unknown",
  creatorFocusCategory: "",
  activePlatform: "全部平台"
});

const profileEditForm = reactive({
  creatorPlatform: "unknown",
  creatorFocusCategory: ""
});

const ownOverview = reactive({
  videoCount: 0,
  totalPlayCount: 0,
  totalLikeCount: 0,
  totalCommentCount: 0,
  avgPlayPerVideo: 0,
  engagementPerThousandPlay: 0
});

const rivalSummary = reactive({
  rivalCount: 0,
  avgPlayPerVideo: 0,
  engagementPerThousandPlay: 0,
  leadingAuthor: "",
  leadingAuthorAvgPlay: 0,
  leadingAuthorEngagement: 0
});

const ownTopVideos = ref([]);
const ownCategoryStats = ref([]);
const rivalAuthors = ref([]);
const suggestions = ref([]);

const hasAnyCreatorData = computed(() => {
  return (
    Number(ownOverview.videoCount ?? 0) > 0 ||
    ownTopVideos.value.length > 0 ||
    ownCategoryStats.value.length > 0 ||
    rivalAuthors.value.length > 0
  );
});

const hasBenchmarkData = computed(() => {
  return (
    Number(ownOverview.avgPlayPerVideo ?? 0) > 0 ||
    Number(ownOverview.engagementPerThousandPlay ?? 0) > 0 ||
    Number(rivalSummary.avgPlayPerVideo ?? 0) > 0 ||
    Number(rivalSummary.engagementPerThousandPlay ?? 0) > 0
  );
});

const toNumber = (value) => {
  const num = Number(value ?? 0);
  return Number.isFinite(num) ? num : 0;
};

const formatInt = (value) => {
  const num = toNumber(value);
  if (Math.abs(num) >= 100000000) {
    return `${(num / 100000000).toFixed(2)}亿`;
  }
  if (Math.abs(num) >= 10000) {
    return `${(num / 10000).toFixed(1)}万`;
  }
  return Math.round(num).toLocaleString("zh-CN");
};

const formatDecimal = (value, digits = 2) => {
  const num = toNumber(value);
  return num.toLocaleString("zh-CN", {
    minimumFractionDigits: digits,
    maximumFractionDigits: digits
  });
};

const toPlatformLabel = (platform) => {
  const raw = String(platform ?? "").trim();
  if (!raw) {
    return "未知来源";
  }
  const key = raw.toLowerCase();
  return platformLabelMap.get(key) || raw;
};

const creatorDisplayName = computed(() => profile.creatorName || profile.username || "未命名创作者");
const creatorInitial = computed(() => creatorDisplayName.value.slice(0, 1) || "创");
const activePlatformLabel = computed(() => toPlatformLabel(profile.activePlatform || "all"));

const gapText = (selfValue, rivalValue, unit = "%") => {
  const self = toNumber(selfValue);
  const rival = toNumber(rivalValue);
  if (self <= 0 && rival <= 0) {
    return "暂无可比数据";
  }
  if (rival <= 0) {
    return "暂无同行基线";
  }
  const ratio = ((self - rival) / rival) * 100;
  const prefix = ratio >= 0 ? "高于同行" : "低于同行";
  return `${prefix} ${Math.abs(ratio).toFixed(1)}${unit}`;
};

const playSignal = computed(() => {
  const self = toNumber(ownOverview.avgPlayPerVideo);
  const rival = toNumber(rivalSummary.avgPlayPerVideo);
  const ahead = self >= rival && self > 0;
  return {
    tone: ahead ? "good" : "warn",
    value: gapText(self, rival),
    desc: ahead
      ? `你当前平均播放/条为 ${formatDecimal(self, 0)}，保持高表现题材并继续做系列化。`
      : `你当前平均播放/条为 ${formatDecimal(self, 0)}，建议优先优化选题、标题与封面。`
  };
});

const engagementSignal = computed(() => {
  const self = toNumber(ownOverview.engagementPerThousandPlay);
  const rival = toNumber(rivalSummary.engagementPerThousandPlay);
  const ahead = self >= rival && self > 0;
  return {
    tone: ahead ? "good" : "warn",
    value: gapText(self, rival),
    desc: ahead
      ? `你的每千播放互动值为 ${formatDecimal(self)}，可继续强化评论互动和话题运营。`
      : `你的每千播放互动值为 ${formatDecimal(self)}，建议增加提问式结尾和评论区引导。`
  };
});

const benchmarkOption = computed(() => {
  const selfAvgPlay = toNumber(ownOverview.avgPlayPerVideo);
  const selfEng = toNumber(ownOverview.engagementPerThousandPlay);
  const rivalAvgPlay = toNumber(rivalSummary.avgPlayPerVideo);
  const rivalEng = toNumber(rivalSummary.engagementPerThousandPlay);

  const maxPlay = Math.max(selfAvgPlay, rivalAvgPlay, 1);
  const maxEng = Math.max(selfEng, rivalEng, 0.1);

  const valueFormatter = (value, digits = 0) => formatDecimal(value, digits);

  return {
    tooltip: {
      trigger: "axis",
      axisPointer: { type: "shadow" },
      formatter(params) {
        const rows = params
          .filter((item) => item.value !== null && item.value !== undefined)
          .map((item) => {
            const isEng = item.seriesName.includes("每千");
            return `${item.marker}${item.seriesName}：${valueFormatter(item.value, isEng ? 2 : 0)}`;
          });
        return [`维度：${params?.[0]?.axisValue || "--"}`, ...rows].join("<br/>");
      }
    },
    legend: {
      data: [
        "我自己（平均播放）",
        "同行均值（平均播放）",
        "我自己（每千互动）",
        "同行均值（每千互动）"
      ],
      textStyle: { color: "#475569", fontSize: 12 }
    },
    grid: { top: 58, left: 84, right: 84, bottom: 58 },
    xAxis: {
      type: "category",
      axisLabel: { color: "#4b5563" },
      data: ["平均播放/条", "每千播放互动值"]
    },
    yAxis: [
      {
        type: "value",
        name: "平均播放/条",
        min: 0,
        max: Math.ceil(maxPlay * 1.15),
        axisLabel: { color: "#4b5563", formatter: (val) => formatInt(val) },
        splitLine: { lineStyle: { color: "#e6edf6" } }
      },
      {
        type: "value",
        name: "每千播放互动值",
        position: "right",
        min: 0,
        max: Math.ceil(maxEng * 1.2 * 10) / 10,
        axisLabel: { color: "#4b5563", formatter: (val) => formatDecimal(val, 1) },
        splitLine: { show: false }
      }
    ],
    series: [
      {
        name: "我自己（平均播放）",
        type: "bar",
        yAxisIndex: 0,
        barMaxWidth: 30,
        data: [selfAvgPlay, null],
        itemStyle: { color: "#1f93ff", borderRadius: [8, 8, 0, 0] },
        label: {
          show: true,
          position: "top",
          color: "#1f2937",
          formatter: (p) => (p.value == null ? "" : valueFormatter(p.value, 0))
        }
      },
      {
        name: "同行均值（平均播放）",
        type: "bar",
        yAxisIndex: 0,
        barMaxWidth: 30,
        data: [rivalAvgPlay, null],
        itemStyle: { color: "#94a3b8", borderRadius: [8, 8, 0, 0] },
        label: {
          show: true,
          position: "top",
          color: "#1f2937",
          formatter: (p) => (p.value == null ? "" : valueFormatter(p.value, 0))
        }
      },
      {
        name: "我自己（每千互动）",
        type: "bar",
        yAxisIndex: 1,
        barMaxWidth: 30,
        data: [null, selfEng],
        itemStyle: { color: "#40a9ff", borderRadius: [8, 8, 0, 0] },
        label: {
          show: true,
          position: "top",
          color: "#1f2937",
          formatter: (p) => (p.value == null ? "" : valueFormatter(p.value, 2))
        }
      },
      {
        name: "同行均值（每千互动）",
        type: "bar",
        yAxisIndex: 1,
        barMaxWidth: 30,
        data: [null, rivalEng],
        itemStyle: { color: "#a0aec0", borderRadius: [8, 8, 0, 0] },
        label: {
          show: true,
          position: "top",
          color: "#1f2937",
          formatter: (p) => (p.value == null ? "" : valueFormatter(p.value, 2))
        }
      }
    ]
  };
});

const categoryOption = computed(() => ({
  tooltip: { trigger: "item" },
  legend: {
    bottom: 0,
    textStyle: { color: "#475569" }
  },
  series: [
    {
      type: "pie",
      radius: ["38%", "68%"],
      center: ["50%", "44%"],
      label: { color: "#334155", formatter: "{b}: {d}%" },
      data: ownCategoryStats.value.map((item, index) => ({
        name: item.category || "未分类",
        value: toNumber(item.totalPlay ?? item.videoCount),
        itemStyle: {
          color: ["#1f93ff", "#20b274", "#f59f0b", "#7c4dff", "#f97316", "#0ea5e9"][index % 6]
        }
      }))
    }
  ]
}));

const scatterPalette = ["#2f9bff", "#22c55e", "#f59e0b", "#a855f7", "#f97316", "#14b8a6", "#ef4444"];

const rivalScatterOption = computed(() => {
  const categoryColorMap = new Map();
  let colorIndex = 0;
  const getColorByCategory = (category) => {
    const key = String(category || "未分类");
    if (!categoryColorMap.has(key)) {
      categoryColorMap.set(key, scatterPalette[colorIndex % scatterPalette.length]);
      colorIndex += 1;
    }
    return categoryColorMap.get(key);
  };

  const rivalPoints = rivalAuthors.value.map((item, index) => {
    const avgPlay = toNumber(item.avgPlayPerVideo);
    const engagement = toNumber(item.engagementPerThousandPlay);
    const category = item.mainCategory || "未分类";
    return {
      value: [avgPlay, engagement],
      author: item.author || `同行${index + 1}`,
      platform: toPlatformLabel(item.sourcePlatform),
      category,
      videoCount: toNumber(item.videoCount),
      itemStyle: { color: getColorByCategory(category), opacity: 0.84 }
    };
  });

  const selfPoint = {
    value: [toNumber(ownOverview.avgPlayPerVideo), toNumber(ownOverview.engagementPerThousandPlay)],
    author: creatorDisplayName.value,
    platform: activePlatformLabel.value,
    category: profile.creatorFocusCategory || "未设置",
    videoCount: toNumber(ownOverview.videoCount)
  };

  return {
    tooltip: {
      trigger: "item",
      formatter(params) {
        const data = params?.data || {};
        return [
          `作者：${data.author || "--"}`,
          `平台：${data.platform || "--"}`,
          `方向：${data.category || "--"}`,
          `视频数：${formatInt(data.videoCount || 0)}`,
          `平均播放/条：${formatDecimal(data.value?.[0], 0)}`,
          `每千播放互动值：${formatDecimal(data.value?.[1])}`
        ].join("<br/>");
      }
    },
    grid: { top: 24, left: 72, right: 24, bottom: 58 },
    xAxis: {
      type: "value",
      name: "平均播放/条",
      nameTextStyle: { color: "#4b5563" },
      axisLabel: { color: "#4b5563", formatter: (val) => formatInt(val) },
      splitLine: { lineStyle: { color: "#e6edf6" } }
    },
    yAxis: {
      type: "value",
      name: "每千播放互动值",
      nameTextStyle: { color: "#4b5563" },
      axisLabel: { color: "#4b5563", formatter: (val) => formatDecimal(val, 1) },
      splitLine: { lineStyle: { color: "#e6edf6" } }
    },
    series: [
      {
        name: "同行样本",
        type: "scatter",
        symbolSize: 16,
        data: rivalPoints
      },
      {
        name: "我自己",
        type: "scatter",
        symbol: "diamond",
        symbolSize: 28,
        data: toNumber(ownOverview.videoCount) > 0 ? [selfPoint] : [],
        itemStyle: {
          color: "#f97316",
          borderColor: "#ffffff",
          borderWidth: 2
        },
        label: {
          show: true,
          position: "top",
          color: "#9a3412",
          fontSize: 12,
          formatter: "我自己"
        }
      }
    ]
  };
});

const applyPayload = (payload) => {
  Object.assign(profile, payload?.profile ?? {});
  Object.assign(ownOverview, payload?.ownOverview ?? {});
  Object.assign(rivalSummary, payload?.rivalSummary ?? {});

  ownTopVideos.value = Array.isArray(payload?.ownTopVideos) ? payload.ownTopVideos : [];
  ownCategoryStats.value = Array.isArray(payload?.ownCategoryStats) ? payload.ownCategoryStats : [];
  rivalAuthors.value = Array.isArray(payload?.rivalAuthors) ? payload.rivalAuthors : [];
  suggestions.value = Array.isArray(payload?.suggestions) ? payload.suggestions : [];

  if (!editingProfile.value) {
    profileEditForm.creatorPlatform = String(profile.creatorPlatform || "unknown").trim().toLowerCase();
    profileEditForm.creatorFocusCategory = profile.creatorFocusCategory || "";
  }
};

const resetData = () => {
  ownTopVideos.value = [];
  ownCategoryStats.value = [];
  rivalAuthors.value = [];
  suggestions.value = [];

  Object.assign(profile, {
    username: "",
    role: "",
    creatorName: "",
    creatorPlatform: "unknown",
    creatorFocusCategory: "",
    activePlatform: "全部平台"
  });

  Object.assign(ownOverview, {
    videoCount: 0,
    totalPlayCount: 0,
    totalLikeCount: 0,
    totalCommentCount: 0,
    avgPlayPerVideo: 0,
    engagementPerThousandPlay: 0
  });

  Object.assign(rivalSummary, {
    rivalCount: 0,
    avgPlayPerVideo: 0,
    engagementPerThousandPlay: 0,
    leadingAuthor: "",
    leadingAuthorAvgPlay: 0,
    leadingAuthorEngagement: 0
  });
};

const toggleProfileEdit = () => {
  if (editingProfile.value) {
    editingProfile.value = false;
    profileEditForm.creatorPlatform = String(profile.creatorPlatform || "unknown").trim().toLowerCase();
    profileEditForm.creatorFocusCategory = profile.creatorFocusCategory || "";
    return;
  }
  editingProfile.value = true;
  profileEditForm.creatorPlatform = String(profile.creatorPlatform || "unknown").trim().toLowerCase();
  profileEditForm.creatorFocusCategory = profile.creatorFocusCategory || "";
};

const saveProfileEdit = async () => {
  if (savingProfile.value) {
    return;
  }
  savingProfile.value = true;
  try {
    const payload = await updateCreatorProfile({
      creatorPlatform: profileEditForm.creatorPlatform,
      creatorFocusCategory: profileEditForm.creatorFocusCategory
    });
    profile.creatorPlatform = payload?.creatorPlatform || profileEditForm.creatorPlatform || "unknown";
    profile.creatorFocusCategory = payload?.creatorFocusCategory ?? profileEditForm.creatorFocusCategory ?? "";
    editingProfile.value = false;
    notifySuccess("创作者资料已更新。");
    await loadData();
  } catch (error) {
    notifyError(getErrorMessage(error, "更新创作者资料失败，请稍后重试。"));
  } finally {
    savingProfile.value = false;
  }
};

const loadData = async () => {
  loading.value = true;
  loadError.value = "";
  try {
    const payload = await getCreatorDashboard(selectedPlatform.value);
    applyPayload(payload);
  } catch (error) {
    resetData();
    loadError.value = getErrorMessage(error, "创作者中心数据加载失败，请稍后重试。");
  } finally {
    loading.value = false;
    loaded.value = true;
  }
};

watch(
  () => selectedPlatform.value,
  () => {
    void loadData();
  }
);

watch(
  () => refreshSignal.value,
  () => {
    if (!loaded.value) {
      return;
    }
    void loadData();
  }
);

void loadData();
</script>

<style scoped>
.hero-card {
  margin-bottom: 16px;
  border-radius: 20px;
  padding: 28px;
  border: 1px solid #d5e6fa;
  background:
    radial-gradient(circle at 10% 10%, rgba(68, 145, 255, 0.11), transparent 44%),
    repeating-linear-gradient(
      90deg,
      rgba(255, 255, 255, 0.62) 0,
      rgba(255, 255, 255, 0.62) 16px,
      rgba(219, 236, 255, 0.32) 16px,
      rgba(219, 236, 255, 0.32) 32px
    );
}

.creator-hero {
  display: grid;
  grid-template-columns: 1.35fr 0.85fr;
  gap: 18px;
  align-items: stretch;
}

.hero-main {
  display: flex;
  gap: 18px;
  align-items: flex-start;
}

.hero-avatar {
  width: 72px;
  height: 72px;
  border-radius: 22px;
  display: grid;
  place-items: center;
  font-size: 28px;
  font-weight: 700;
  color: #ffffff;
  background: linear-gradient(135deg, #1f93ff, #2db0ff);
  box-shadow: 0 10px 24px rgba(31, 147, 255, 0.25);
  flex: 0 0 auto;
}

.hero-kicker,
.hero-title,
.hero-desc {
  margin: 0;
}

.hero-kicker {
  color: #1d76d2;
  font-size: 13px;
  font-weight: 700;
  letter-spacing: 0.3px;
}

.hero-title {
  margin-top: 10px;
  font-size: clamp(28px, 3vw, 40px);
  line-height: 1.14;
}

.hero-desc {
  margin-top: 10px;
  color: #55657a;
  font-size: 15px;
  line-height: 1.7;
  max-width: 680px;
}

.hero-tags {
  margin-top: 14px;
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.hero-tags span {
  display: inline-flex;
  align-items: center;
  border: 1px solid #cfe2f8;
  border-radius: 999px;
  padding: 6px 12px;
  font-size: 13px;
  color: #1d76d2;
  background: rgba(255, 255, 255, 0.8);
}

.creator-profile-editor {
  margin-top: 12px;
}

.creator-edit-trigger {
  border: 1px solid #bcd8f8;
  border-radius: 999px;
  background: #f1f8ff;
  color: #1f6fc5;
  padding: 8px 14px;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
}

.creator-edit-trigger:hover {
  background: #e6f3ff;
}

.creator-edit-form {
  margin-top: 10px;
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
  align-items: flex-end;
  padding: 12px;
  border: 1px solid #d4e5f9;
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.9);
}

.creator-edit-field {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.creator-edit-field span {
  font-size: 12px;
  color: #52657e;
}

.creator-edit-field select,
.creator-edit-field input {
  border: 1px solid #cfe2f8;
  border-radius: 10px;
  padding: 8px 10px;
  min-height: 36px;
  font-size: 13px;
  background: #ffffff;
  color: #0f172a;
  outline: none;
}

.creator-edit-field select:focus,
.creator-edit-field input:focus {
  border-color: #8ec4ff;
}

.creator-edit-field-grow {
  min-width: 220px;
  flex: 1 1 280px;
}

.creator-save-btn {
  border: none;
  border-radius: 999px;
  min-height: 36px;
  padding: 0 16px;
  background: linear-gradient(120deg, #1f93ff, #53b8ff);
  color: #ffffff;
  font-weight: 700;
  cursor: pointer;
}

.creator-save-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.hero-side {
  display: grid;
  gap: 12px;
}

.signal-card {
  border: 1px solid #dce8f8;
  border-radius: 16px;
  padding: 14px;
  background: linear-gradient(180deg, #f8fbff, #f2f7ff);
}

.signal-card.good {
  border-color: #c8e6d6;
  background: linear-gradient(180deg, #f7fffb, #eefcf4);
}

.signal-card.warn {
  border-color: #f2deb9;
  background: linear-gradient(180deg, #fffcf6, #fff7eb);
}

.signal-label,
.signal-value,
.signal-desc {
  margin: 0;
}

.signal-label {
  color: #64748b;
  font-size: 13px;
}

.signal-value {
  margin-top: 7px;
  font-size: 24px;
  font-weight: 700;
  color: #0f172a;
}

.signal-desc {
  margin-top: 8px;
  color: #55657a;
  font-size: 13px;
  line-height: 1.55;
}

.panel-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(340px, 1fr));
  gap: 14px;
}

.suggestion-list {
  display: grid;
  gap: 12px;
}

.suggestion-card {
  display: grid;
  grid-template-columns: 34px 1fr;
  gap: 12px;
  align-items: start;
  border: 1px solid #dce8f8;
  border-radius: 14px;
  padding: 12px;
  background: linear-gradient(180deg, #f8fbff, #f2f7ff);
}

.suggestion-card p {
  margin: 0;
  color: #445468;
  line-height: 1.7;
  font-size: 14px;
}

.suggestion-index {
  width: 34px;
  height: 34px;
  border-radius: 50%;
  display: grid;
  place-items: center;
  color: #ffffff;
  font-weight: 700;
  background: linear-gradient(135deg, #1f93ff, #4bb4ff);
}

.section-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}

.section-tip {
  margin: 0 0 14px;
  color: #64748b;
  font-size: 13px;
}

.metric-value-small {
  font-size: 18px;
  line-height: 1.35;
}

.warning-panel {
  border-color: #f2deb9;
  background: linear-gradient(180deg, #fffcf6, #fff8ea);
}

.warning-text {
  margin: 0;
  color: #8a6a2a;
}

@media (max-width: 980px) {
  .creator-hero {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 680px) {
  .hero-card {
    padding: 20px 16px;
  }

  .hero-main {
    flex-direction: column;
  }

  .hero-avatar {
    width: 60px;
    height: 60px;
    font-size: 24px;
  }

  .hero-title {
    font-size: 28px;
  }

  .creator-edit-form {
    flex-direction: column;
    align-items: stretch;
  }

  .creator-save-btn {
    width: 100%;
  }
}
</style>
