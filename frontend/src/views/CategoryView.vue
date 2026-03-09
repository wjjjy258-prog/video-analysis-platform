<template>
  <LoadingPanel v-if="loading" text="正在加载分类统计数据" />

  <template v-else>
    <ErrorPanel
      v-if="loadError && !categoryData.length"
      title="分类统计加载失败"
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
        <h2>视频分类统计</h2>
        <div class="view-switch">
          <button :class="chartMode === '2d' ? 'is-active' : ''" @click="chartMode = '2d'">2D 图表</button>
          <button :class="chartMode === '3d' ? 'is-active' : ''" @click="chartMode = '3d'">3D 图表</button>
        </div>
      </div>
      <EChartPanel v-if="categoryData.length && chartMode === '2d'" :option="pieOption" />
      <div v-else-if="categoryData.length && chartMode === '3d'">
        <ThreeBarScene :items="threeItems" />
        <p class="view-tip">3D 柱高按总播放量排序，适合快速观察分类差距。</p>
      </div>
      <p v-else-if="loaded" class="empty-hint">暂无数据，请先初始化数据库或运行采集。</p>
    </section>

    <section class="panel">
      <h2>分类汇总</h2>
      <div class="table-wrap">
        <table v-if="categoryData.length">
          <thead>
            <tr>
              <th>分类</th>
              <th>视频数</th>
              <th>总播放量</th>
              <th>总点赞量</th>
              <th>总评论量</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="item in categoryData" :key="item.category">
              <td>{{ item.category }}</td>
              <td>{{ formatInt(item.videoCount) }}</td>
              <td>{{ formatInt(item.totalPlay) }}</td>
              <td>{{ formatInt(item.totalLike) }}</td>
              <td>{{ formatInt(item.totalComment) }}</td>
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
import { getCategoryStats } from "../api/video";
import EChartPanel from "../components/EChartPanel.vue";
import ErrorPanel from "../components/ErrorPanel.vue";
import LoadingPanel from "../components/LoadingPanel.vue";
import ThreeBarScene from "../components/ThreeBarScene.vue";
import { usePlatformFilter } from "../composables/platformFilter";
import { getErrorMessage } from "../utils/feedback";

const categoryData = ref([]);
const chartMode = ref("2d");
const loading = ref(true);
const loaded = ref(false);
const loadError = ref("");
const { selectedPlatform, refreshSignal } = usePlatformFilter();
const formatInt = (v) => Number(v ?? 0).toLocaleString("zh-CN");

const threeItems = computed(() =>
  categoryData.value.slice(0, 10).map((item) => ({
    label: item.category,
    value: Number(item.totalPlay ?? 0)
  }))
);

const pieOption = computed(() => ({
  tooltip: { trigger: "item" },
  legend: { bottom: 0, textStyle: { color: "#475569" } },
  series: [
    {
      name: "分类播放量",
      type: "pie",
      radius: ["38%", "72%"],
      center: ["50%", "45%"],
      data: categoryData.value.map((item) => ({
        name: item.category,
        value: item.totalPlay
      })),
      label: { color: "#334155" },
      itemStyle: {
        borderWidth: 1,
        borderColor: "#ffffff"
      },
      color: ["#1f93ff", "#2db0ff", "#4f87ff", "#3dc7c8", "#f59f0b", "#7a8cff", "#60a5fa"]
    }
  ]
}));

const loadData = async () => {
  loading.value = true;
  loadError.value = "";
  try {
    categoryData.value = (await getCategoryStats(selectedPlatform.value)) ?? [];
  } catch (error) {
    categoryData.value = [];
    loadError.value = getErrorMessage(error, "分类统计数据加载失败。请稍后重试。");
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
