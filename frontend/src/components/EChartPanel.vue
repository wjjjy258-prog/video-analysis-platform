<template>
  <div ref="chartRef" class="chart-panel"></div>
</template>

<script setup>
import { onBeforeUnmount, onMounted, ref, watch } from "vue";

const props = defineProps({
  option: {
    type: Object,
    required: true
  }
});

const chartRef = ref(null);
let chartInstance = null;
let echartsModule = null;
let echartsLoadingPromise = null;

const loadEcharts = async () => {
  if (echartsModule) return echartsModule;
  if (!echartsLoadingPromise) {
    echartsLoadingPromise = import("../utils/echarts").then((mod) => {
      echartsModule = mod.default;
      return echartsModule;
    });
  }
  return echartsLoadingPromise;
};

const renderChart = async () => {
  if (!chartRef.value) return;
  const echarts = await loadEcharts();
  if (!chartRef.value) return;
  if (!chartInstance) {
    chartInstance = echarts.init(chartRef.value);
  }
  chartInstance.setOption(props.option, true);
};

const resizeChart = () => {
  if (chartInstance) {
    chartInstance.resize();
  }
};

watch(
  () => props.option,
  () => void renderChart(),
  { deep: true }
);

onMounted(() => {
  void renderChart();
  window.addEventListener("resize", resizeChart);
});

onBeforeUnmount(() => {
  window.removeEventListener("resize", resizeChart);
  if (chartInstance) {
    chartInstance.dispose();
    chartInstance = null;
  }
});
</script>

<style scoped>
.chart-panel {
  width: 100%;
  min-height: 360px;
}
</style>
