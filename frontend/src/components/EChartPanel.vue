<template>
  <div ref="chartRef" class="chart-panel"></div>
</template>

<script setup>
import { onBeforeUnmount, onMounted, ref, watch } from "vue";
import * as echarts from "echarts";

const props = defineProps({
  option: {
    type: Object,
    required: true
  }
});

const chartRef = ref(null);
let chartInstance = null;

const renderChart = () => {
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
  () => renderChart(),
  { deep: true }
);

onMounted(() => {
  renderChart();
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
