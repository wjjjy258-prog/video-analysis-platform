<template>
  <section class="hero-card">
    <div>
      <h2 class="hero-title">平台简介</h2>
      <p class="hero-desc">
        本平台面向抖音、哔哩哔哩、快手、小红书、西瓜视频、微博、YouTube、TikTok、AcFun 等视频数据分析场景，
        提供来源识别、热门排行、分类统计、互动效率分析、用户画像分析与数据采集入库能力，
        帮助你以统一界面完成从采集到洞察的全流程研究。
      </p>
    </div>
    <div class="hero-meta">
      <span>实时接口驱动</span>
      <span>支持 9+ 主流视频平台</span>
    </div>
  </section>

  <LoadingPanel v-if="loading" text="正在准备首页数据" />

  <template v-else>
    <ErrorPanel
      v-if="loadError && !hasAnyData"
      title="首页数据加载失败"
      :message="loadError"
      retry-text="重新加载首页"
      @retry="reloadData"
    />

    <section v-else-if="loadError" class="panel warning-panel">
      <h2>提示</h2>
      <p class="warning-text">{{ loadError }}</p>
    </section>

    <section class="panel">
      <h2>平台总览</h2>
      <div class="metric-grid">
        <div class="metric-item">
          <p class="metric-label">视频总数</p>
          <p class="metric-value">{{ formatInt(overview.videoCount) }}</p>
        </div>
        <div class="metric-item">
          <p class="metric-label">用户总数</p>
          <p class="metric-value">{{ formatInt(overview.userCount) }}</p>
        </div>
        <div class="metric-item">
          <p class="metric-label">评论总数</p>
          <p class="metric-value">{{ formatInt(overview.commentCount) }}</p>
        </div>
        <div class="metric-item">
          <p class="metric-label">行为日志总数</p>
          <p class="metric-value">{{ formatInt(overview.behaviorCount) }}</p>
        </div>
        <div class="metric-item">
          <p class="metric-label">累计播放量</p>
          <p class="metric-value">{{ formatInt(overview.totalPlayCount) }}</p>
        </div>
        <div class="metric-item">
          <p class="metric-label">来源平台数</p>
          <p class="metric-value">{{ formatInt(overview.sourcePlatformCount) }}</p>
        </div>
      </div>
    </section>

    <section class="panel">
      <h2>关键洞察</h2>
      <div v-if="insightCards.length" class="insight-grid">
        <article
          v-for="(card, idx) in insightCards"
          :key="`${card.title}-${idx}`"
          class="insight-card"
          :class="`level-${card.level || 'info'}`"
        >
          <p class="insight-title">{{ card.title }}</p>
          <p class="insight-value">{{ card.value }}</p>
          <p class="insight-desc">{{ card.description }}</p>
        </article>
      </div>
      <p v-else-if="loaded" class="empty-hint">暂无洞察数据。</p>
    </section>

    <section class="panel">
      <h2>来源平台概览</h2>
      <div v-if="platformStats.length" class="platform-grid">
        <article v-for="row in platformStats" :key="row.sourcePlatform" class="platform-card">
          <p class="platform-name">{{ normalizePlatform(row.sourcePlatform) }}</p>
          <p class="platform-main">{{ formatInt(row.videoCount) }} 个视频</p>
          <p class="platform-sub">累计播放 {{ formatInt(row.totalPlay) }}</p>
          <div class="platform-progress">
            <div class="platform-progress-fill" :style="{ width: platformWidth(row.videoCount) }"></div>
          </div>
        </article>
      </div>
      <p v-else-if="loaded" class="empty-hint">暂无来源平台数据。</p>
    </section>

    <section class="panel">
      <h2>热门视频 TOP 5</h2>
      <div class="table-wrap">
        <table v-if="hotVideos.length">
          <thead>
            <tr>
              <th>视频标题</th>
              <th>作者</th>
              <th>来源平台</th>
              <th>分类</th>
              <th>播放量</th>
              <th>点赞量</th>
              <th>评论量</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="item in hotVideos" :key="item.id">
              <td>{{ item.title }}</td>
              <td>{{ item.author }}</td>
              <td>{{ normalizePlatform(item.sourcePlatform) }}</td>
              <td>{{ item.category }}</td>
              <td>{{ formatInt(item.playCount) }}</td>
              <td>{{ formatInt(item.likeCount) }}</td>
              <td>{{ formatInt(item.commentCount) }}</td>
            </tr>
          </tbody>
        </table>
        <p v-else-if="loaded" class="empty-hint">暂无数据，请先初始化数据库或运行采集。</p>
      </div>
    </section>
  </template>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from "vue";
import { getHomeDashboard, getHotVideos, getInsightCards, getOverview, getPlatformStats } from "../api/video";
import ErrorPanel from "../components/ErrorPanel.vue";
import LoadingPanel from "../components/LoadingPanel.vue";
import { getErrorMessage } from "../utils/feedback";
import { toPlatformLabel } from "../utils/platform";

const HOME_CACHE_TTL_MS = 90 * 1000;
const EMPTY_OVERVIEW = {
  videoCount: 0,
  userCount: 0,
  commentCount: 0,
  behaviorCount: 0,
  totalPlayCount: 0,
  sourcePlatformCount: 0
};

const homeCache = {
  overview: { ...EMPTY_OVERVIEW },
  hotVideos: [],
  platformStats: [],
  insightCards: [],
  loadError: "",
  loadedAt: 0,
  pending: null
};

const cloneItems = (items) =>
  Array.isArray(items) ? items.map((item) => ({ ...item })) : [];

const isCacheAvailable = () => homeCache.loadedAt > 0;
const isCacheFresh = () => isCacheAvailable() && Date.now() - homeCache.loadedAt < HOME_CACHE_TTL_MS;

const overview = reactive({
  ...EMPTY_OVERVIEW
});
const hotVideos = ref([]);
const platformStats = ref([]);
const insightCards = ref([]);
const loading = ref(true);
const loaded = ref(false);
const loadError = ref("");

const maxPlatformVideoCount = computed(() =>
  Math.max(
    1,
    ...platformStats.value.map((item) => Number(item.videoCount ?? 0))
  )
);

const hasAnyData = computed(
  () =>
    hotVideos.value.length > 0 ||
    platformStats.value.length > 0 ||
    insightCards.value.length > 0 ||
    Number(overview.videoCount ?? 0) > 0
);

const formatInt = (v) => Number(v ?? 0).toLocaleString("zh-CN");
const platformWidth = (count) =>
  `${Math.max(12, Math.round((Number(count ?? 0) / maxPlatformVideoCount.value) * 100))}%`;
const normalizePlatform = (name) => toPlatformLabel(name);

const applyState = (payload) => {
  Object.assign(overview, payload.overview ?? EMPTY_OVERVIEW);
  hotVideos.value = cloneItems(payload.hotVideos);
  platformStats.value = cloneItems(payload.platformStats);
  insightCards.value = cloneItems(payload.insightCards);
  loadError.value = payload.loadError || "";
  loaded.value = true;
};

const applyCache = () => {
  if (!isCacheAvailable()) {
    return false;
  }
  applyState({
    overview: homeCache.overview,
    hotVideos: homeCache.hotVideos,
    platformStats: homeCache.platformStats,
    insightCards: homeCache.insightCards,
    loadError: homeCache.loadError
  });
  return true;
};

const buildPartialError = (settledResults) => {
  const failedMessages = settledResults
    .filter((item) => item.status === "rejected")
    .map((item) => getErrorMessage(item.reason, "请求失败"));

  if (!failedMessages.length) {
    return "";
  }
  if (failedMessages.length === settledResults.length) {
    return failedMessages[0];
  }
  return `部分数据加载失败（${failedMessages.length}/${settledResults.length}），已展示可用结果。`;
};

const fetchHomePayload = async () => {
  try {
    const dashboard = await getHomeDashboard(5);
    return {
      overview: dashboard?.overview ?? { ...EMPTY_OVERVIEW },
      hotVideos: dashboard?.hotVideos ?? [],
      platformStats: dashboard?.platformStats ?? [],
      insightCards: dashboard?.insightCards ?? [],
      loadError: ""
    };
  } catch (_dashboardError) {
    // 【说明】兼容旧版后端返回结构，避免升级过程中首页报错。
  }

  const settledResults = await Promise.allSettled([
    getOverview(),
    getHotVideos(5),
    getPlatformStats(),
    getInsightCards()
  ]);

  const payload = {
    overview: settledResults[0].status === "fulfilled" ? settledResults[0].value ?? {} : { ...EMPTY_OVERVIEW },
    hotVideos: settledResults[1].status === "fulfilled" ? settledResults[1].value ?? [] : [],
    platformStats: settledResults[2].status === "fulfilled" ? settledResults[2].value ?? [] : [],
    insightCards: settledResults[3].status === "fulfilled" ? settledResults[3].value ?? [] : [],
    loadError: buildPartialError(settledResults)
  };

  if (!payload.overview || typeof payload.overview !== "object") {
    payload.overview = { ...EMPTY_OVERVIEW };
  }
  return payload;
};

const fetchWithDedup = async () => {
  if (homeCache.pending) {
    return homeCache.pending;
  }
  homeCache.pending = (async () => {
    const payload = await fetchHomePayload();
    homeCache.overview = { ...EMPTY_OVERVIEW, ...(payload.overview ?? {}) };
    homeCache.hotVideos = cloneItems(payload.hotVideos);
    homeCache.platformStats = cloneItems(payload.platformStats);
    homeCache.insightCards = cloneItems(payload.insightCards);
    homeCache.loadError = payload.loadError || "";
    homeCache.loadedAt = Date.now();
    return payload;
  })()
    .finally(() => {
      homeCache.pending = null;
    });

  return homeCache.pending;
};

const loadData = async ({ force = false, background = false } = {}) => {
  const showBlockingLoading = !background && (!isCacheAvailable() || force);
  if (showBlockingLoading) {
    loading.value = true;
    if (!force) {
      loadError.value = "";
    }
  }

  try {
    if (!force && isCacheFresh() && !background) {
      applyCache();
      return;
    }
    const payload = await fetchWithDedup();
    applyState(payload);
  } catch (error) {
    if (applyCache()) {
      loadError.value = getErrorMessage(error, "后台刷新失败，已展示缓存数据。");
    } else {
      loadError.value = getErrorMessage(error, "首页数据加载失败。请稍后重试。");
      Object.assign(overview, EMPTY_OVERVIEW);
      hotVideos.value = [];
      platformStats.value = [];
      insightCards.value = [];
      loaded.value = true;
    }
  } finally {
    loading.value = false;
  }
};

const reloadData = () => {
  void loadData({ force: true, background: false });
};

onMounted(() => {
  const hasCache = applyCache();
  if (hasCache) {
    loading.value = false;
  }

  if (!hasCache) {
    void loadData({ force: true, background: false });
    return;
  }
  void loadData({ force: false, background: true });
});
</script>

<style scoped>
.hero-card {
  margin-bottom: 16px;
  border-radius: 20px;
  padding: 36px 28px;
  border: 1px solid #d5e6fa;
  background:
    linear-gradient(130deg, rgba(255, 255, 255, 0.93), rgba(241, 248, 255, 0.92)),
    repeating-linear-gradient(
      90deg,
      rgba(11, 132, 255, 0.08) 0,
      rgba(11, 132, 255, 0.08) 36px,
      rgba(11, 132, 255, 0.02) 36px,
      rgba(11, 132, 255, 0.02) 72px
    );
  box-shadow: 0 16px 34px rgba(20, 56, 108, 0.1);
  display: grid;
  grid-template-columns: 1fr auto;
  gap: 18px;
  align-items: end;
}

.hero-title {
  margin: 0 0 12px;
  font-size: clamp(28px, 3.7vw, 48px);
  line-height: 1.16;
}

.hero-desc {
  margin: 0;
  max-width: 760px;
  color: #55657a;
  font-size: 16px;
}

.hero-meta {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
  justify-content: flex-end;
}

.hero-meta span {
  display: inline-flex;
  align-items: center;
  border: 1px solid #cfe2f8;
  border-radius: 999px;
  padding: 6px 12px;
  font-size: 13px;
  color: #1d76d2;
  background: rgba(255, 255, 255, 0.8);
}

.warning-panel {
  border-color: #f2deb9;
  background: linear-gradient(180deg, #fffcf6, #fff8ea);
}

.warning-text {
  margin: 0;
  color: #8a6a2a;
}

@media (max-width: 860px) {
  .hero-card {
    grid-template-columns: 1fr;
    padding: 26px 18px;
    gap: 14px;
  }

  .hero-meta {
    justify-content: flex-start;
  }
}

.platform-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(230px, 1fr));
  gap: 12px;
}

.platform-card {
  border: 1px solid #d8e6f8;
  border-radius: 14px;
  background: linear-gradient(180deg, #f8fbff, #f1f7ff);
  padding: 12px;
}

.platform-name,
.platform-main,
.platform-sub {
  margin: 0;
}

.platform-name {
  color: #1d76d2;
  font-size: 13px;
}

.platform-main {
  margin-top: 8px;
  font-size: 22px;
  font-weight: 700;
  color: #0f172a;
}

.platform-sub {
  margin-top: 6px;
  color: #55657a;
  font-size: 13px;
}

.platform-progress {
  margin-top: 9px;
  height: 7px;
  border-radius: 999px;
  background: #dce9f8;
  overflow: hidden;
}

.platform-progress-fill {
  height: 100%;
  border-radius: inherit;
  background: linear-gradient(90deg, #1f93ff, #4fb0ff);
}

.insight-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 12px;
}

.insight-card {
  border: 1px solid #dce8f8;
  border-radius: 14px;
  padding: 12px;
  background: linear-gradient(180deg, #f8fbff, #f4f8ff);
}

.insight-title,
.insight-value,
.insight-desc {
  margin: 0;
}

.insight-title {
  font-size: 13px;
  color: #64748b;
}

.insight-value {
  margin-top: 6px;
  font-size: 22px;
  font-weight: 700;
  color: #0f172a;
}

.insight-desc {
  margin-top: 8px;
  font-size: 13px;
  color: #55657a;
  line-height: 1.55;
}

.insight-card.level-good {
  border-color: #c8e6d6;
  background: linear-gradient(180deg, #f6fffa, #effcf4);
}

.insight-card.level-warn {
  border-color: #f2deb9;
  background: linear-gradient(180deg, #fffbf3, #fff6e7);
}
</style>
