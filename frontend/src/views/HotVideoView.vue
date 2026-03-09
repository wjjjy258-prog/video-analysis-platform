<template>
  <LoadingPanel v-if="loading" text="正在加载热门视频数据" />

  <template v-else>
    <ErrorPanel
      v-if="loadError && !hotVideos.length"
      title="热门视频加载失败"
      :message="loadError"
      retry-text="重试加载"
      @retry="loadData"
    />

    <section v-else-if="loadError" class="panel warning-panel">
      <h2>提示</h2>
      <p class="warning-text">{{ loadError }}</p>
    </section>

    <section class="panel">
      <div class="panel-head">
        <h2>热门视频排行</h2>
        <div class="view-switch">
          <button :class="chartMode === '2d' ? 'is-active' : ''" @click="chartMode = '2d'">2D 图表</button>
          <button :class="chartMode === '3d' ? 'is-active' : ''" @click="chartMode = '3d'">3D 图表</button>
        </div>
      </div>
      <EChartPanel v-if="hotVideos.length && chartMode === '2d'" :option="chartOption" />
      <div v-else-if="hotVideos.length && chartMode === '3d'">
        <ThreeBarScene :items="threeItems" />
        <p class="view-tip">3D 柱高表示播放量，详细标题和作者请看下方明细列表。</p>
      </div>
      <p v-else-if="loaded" class="empty-hint">暂无数据，请先初始化数据库或运行采集。</p>
    </section>

    <section class="panel">
      <h2>明细列表</h2>
      <div class="table-wrap">
        <table v-if="hotVideos.length">
          <thead>
            <tr>
              <th>#</th>
              <th>标题</th>
              <th>作者</th>
              <th>来源平台</th>
              <th>导入类型</th>
              <th>分类</th>
              <th>播放量</th>
              <th>点赞量</th>
              <th>评论量</th>
              <th>质量分</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(item, idx) in hotVideos" :key="item.id">
              <td>{{ idx + 1 }}</td>
              <td>{{ item.title }}</td>
              <td>{{ item.author }}</td>
              <td>{{ normalizePlatform(item.sourcePlatform) }}</td>
              <td>{{ item.importType || "--" }}</td>
              <td>{{ item.category }}</td>
              <td>{{ formatInt(item.playCount) }}</td>
              <td>{{ formatInt(item.likeCount) }}</td>
              <td>{{ formatInt(item.commentCount) }}</td>
              <td>{{ Number(item.dataQualityScore ?? 0).toFixed(2) }}</td>
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
import { getHotVideos } from "../api/video";
import EChartPanel from "../components/EChartPanel.vue";
import ErrorPanel from "../components/ErrorPanel.vue";
import LoadingPanel from "../components/LoadingPanel.vue";
import ThreeBarScene from "../components/ThreeBarScene.vue";
import { usePlatformFilter } from "../composables/platformFilter";
import { getErrorMessage } from "../utils/feedback";
import { toPlatformLabel } from "../utils/platform";

const hotVideos = ref([]);
const chartMode = ref("2d");
const loading = ref(true);
const loaded = ref(false);
const loadError = ref("");
const { selectedPlatform, refreshSignal } = usePlatformFilter();

const formatInt = (v) => Number(v ?? 0).toLocaleString("zh-CN");
const normalizePlatform = (name) => toPlatformLabel(name);

const threeItems = computed(() =>
  hotVideos.value.slice(0, 8).map((item) => ({
    label: item.title,
    value: Number(item.playCount ?? 0)
  }))
);

const chartOption = computed(() => ({
  tooltip: { trigger: "axis" },
  textStyle: { color: "#1f2937" },
  grid: { top: 30, left: 120, right: 30, bottom: 20 },
  xAxis: {
    type: "value",
    axisLabel: { color: "#5b6473" },
    splitLine: { lineStyle: { color: "#e5ecf5" } }
  },
  yAxis: {
    type: "category",
    axisLabel: { color: "#334155" },
    data: hotVideos.value.map((item) => item.title).reverse()
  },
  series: [
    {
      type: "bar",
      data: hotVideos.value.map((item) => item.playCount).reverse(),
      itemStyle: { color: "#1f93ff" },
      label: { show: true, position: "right", color: "#1f2937" }
    }
  ]
}));

const loadData = async () => {
  loading.value = true;
  loadError.value = "";
  try {
    hotVideos.value = (await getHotVideos(10, selectedPlatform.value)) ?? [];
  } catch (error) {
    hotVideos.value = [];
    loadError.value = getErrorMessage(error, "热门视频数据加载失败。请稍后重试。");
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

.warning-panel {
  border-color: #f2deb9;
  background: linear-gradient(180deg, #fffcf6, #fff8ea);
}

.warning-text {
  margin: 0;
  color: #8a6a2a;
}
</style>
