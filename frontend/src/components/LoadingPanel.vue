<template>
  <section class="panel loading-panel">
    <div class="loading-head">
      <span class="loading-orb" :class="{ steady: hasManualProgress }"></span>
      <span class="loading-title">{{ text }}</span>
      <span class="loading-percent">{{ displayProgress }}%</span>
      <span class="loading-dots" aria-hidden="true">
        <i></i>
        <i></i>
        <i></i>
      </span>
    </div>
    <div class="loading-strip">
      <div class="loading-track">
        <div class="loading-fill is-determinate" :style="{ width: `${displayProgress}%` }"></div>
      </div>
    </div>
  </section>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from "vue";

const props = defineProps({
  text: {
    type: String,
    default: "正在加载数据"
  },
  progress: {
    type: Number,
    default: null
  }
});

const autoProgress = ref(6);
let timer = null;

const clamp = (value) => {
  if (value === null || value === undefined || value === "") {
    return null;
  }
  const n = Number(value);
  if (!Number.isFinite(n)) return null;
  if (n <= 0) return 0;
  if (n >= 100) return 100;
  return Math.round(n);
};

const hasManualProgress = computed(() => clamp(props.progress) !== null);
const displayProgress = computed(() => {
  const manual = clamp(props.progress);
  return manual === null ? Math.round(autoProgress.value) : manual;
});

const stopTimer = () => {
  if (timer) {
    window.clearInterval(timer);
    timer = null;
  }
};

const startAutoProgress = () => {
  stopTimer();
  autoProgress.value = 6;
  timer = window.setInterval(() => {
    autoProgress.value = Math.min(96, autoProgress.value + Math.max(0.6, (96 - autoProgress.value) * 0.08));
  }, 180);
};

watch(
  () => props.progress,
  (value) => {
    if (clamp(value) === null) {
      if (!timer) {
        startAutoProgress();
      }
      return;
    }
    stopTimer();
  }
);

onMounted(() => {
  if (!hasManualProgress.value) {
    startAutoProgress();
  }
});

onBeforeUnmount(() => {
  stopTimer();
});
</script>

<style scoped>
.loading-panel {
  position: relative;
  overflow: hidden;
}

.loading-panel::after {
  content: "";
  position: absolute;
  inset: 0;
  pointer-events: none;
  background: radial-gradient(circle at 12% 20%, rgba(31, 147, 255, 0.08), transparent 46%);
}

.loading-head {
  position: relative;
  z-index: 1;
  display: inline-flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 14px;
  color: #2d4e76;
  font-size: 15px;
  font-weight: 600;
}

.loading-orb {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: #1f93ff;
  box-shadow: 0 0 0 0 rgba(31, 147, 255, 0.55);
  animation: orb-pulse 1.3s ease infinite;
}

.loading-orb.steady {
  animation: none;
  box-shadow: 0 0 0 4px rgba(31, 147, 255, 0.18);
}

.loading-percent {
  font-size: 13px;
  color: #1d6fc7;
  font-weight: 700;
  min-width: 48px;
  text-align: right;
}

.loading-dots {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.loading-dots i {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: #4daeff;
  opacity: 0.35;
  animation: dot-bounce 1s ease infinite;
}

.loading-dots i:nth-child(2) {
  animation-delay: 0.14s;
}

.loading-dots i:nth-child(3) {
  animation-delay: 0.28s;
}

@keyframes orb-pulse {
  0% {
    transform: scale(0.95);
    box-shadow: 0 0 0 0 rgba(31, 147, 255, 0.45);
  }
  70% {
    transform: scale(1);
    box-shadow: 0 0 0 11px rgba(31, 147, 255, 0);
  }
  100% {
    transform: scale(0.95);
    box-shadow: 0 0 0 0 rgba(31, 147, 255, 0);
  }
}

@keyframes dot-bounce {
  0%,
  80%,
  100% {
    transform: translateY(0);
    opacity: 0.35;
  }
  40% {
    transform: translateY(-3px);
    opacity: 1;
  }
}
</style>
