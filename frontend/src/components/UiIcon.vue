<template>
  <span class="ui-icon" :style="iconStyle" aria-hidden="true">
    <svg
      :viewBox="iconDef.viewBox"
      fill="none"
      xmlns="http://www.w3.org/2000/svg"
      :stroke-width="strokeWidth"
      stroke="currentColor"
      stroke-linecap="round"
      stroke-linejoin="round"
    >
      <path v-for="(d, idx) in iconDef.paths" :key="idx" :d="d" />
      <circle v-for="(c, idx) in iconDef.circles" :key="`c-${idx}`" :cx="c.cx" :cy="c.cy" :r="c.r" />
      <rect
        v-for="(r, idx) in iconDef.rects"
        :key="`r-${idx}`"
        :x="r.x"
        :y="r.y"
        :width="r.w"
        :height="r.h"
        :rx="r.rx || 0"
      />
    </svg>
  </span>
</template>

<script setup>
import { computed } from "vue";

const props = defineProps({
  name: {
    type: String,
    default: "spark"
  },
  size: {
    type: Number,
    default: 16
  },
  strokeWidth: {
    type: [Number, String],
    default: 1.9
  }
});

const ICONS = {
  spark: {
    viewBox: "0 0 24 24",
    paths: ["M12 3l2.2 5.2L20 10l-5.8 1.8L12 17l-2.2-5.2L4 10l5.8-1.8L12 3"],
    circles: [],
    rects: []
  },
  home: {
    viewBox: "0 0 24 24",
    paths: ["M3 10.5L12 3l9 7.5", "M6.5 9.8V21h11V9.8", "M10 21v-5.4h4V21"],
    circles: [],
    rects: []
  },
  hot: {
    viewBox: "0 0 24 24",
    paths: ["M12 3c1.2 2.3 3.6 3.7 3.6 6.3 0 1.7-1 3.1-2.5 3.9", "M9.2 7.2c-2.1 2-3.2 4-3.2 6.1A6 6 0 0 0 12 19.3a6 6 0 0 0 6-6c0-2.4-1.3-4.5-3.2-6.2", "M12 11.2c1.7 1.2 2.5 2.4 2.5 3.9A2.5 2.5 0 0 1 12 17.6a2.5 2.5 0 0 1-2.5-2.5c0-1.4.8-2.6 2.5-3.9"],
    circles: [],
    rects: []
  },
  category: {
    viewBox: "0 0 24 24",
    paths: ["M11 3.5a8.5 8.5 0 1 0 8.5 8.5H11V3.5z", "M13 3.5v6h6", "M4.5 13h4", "M6.5 16h2", "M6.5 10h2"],
    circles: [],
    rects: []
  },
  trend: {
    viewBox: "0 0 24 24",
    paths: ["M3 17.5h18", "M5 15l4-4 3 3 5-6 2 2", "M18.5 8H15", "M18.5 8v3.5"],
    circles: [],
    rects: []
  },
  user: {
    viewBox: "0 0 24 24",
    paths: ["M12 13a4.5 4.5 0 1 0 0-9 4.5 4.5 0 0 0 0 9z", "M4 20c1.8-3 4.5-4.5 8-4.5s6.2 1.5 8 4.5"],
    circles: [],
    rects: []
  },
  creator: {
    viewBox: "0 0 24 24",
    paths: [
      "M12 12.8a4.3 4.3 0 1 0 0-8.6 4.3 4.3 0 0 0 0 8.6z",
      "M4.2 20c1.6-2.7 4.2-4.1 7.8-4.1 1.7 0 3.1.3 4.4.8",
      "M16.8 13.8l1.8-1.8 3.2 3.2-1.8 1.8",
      "M15.7 15l3.3 3.3",
      "M14.8 19.2l1-4.2 3.2 3.2-4.2 1z"
    ],
    circles: [],
    rects: []
  },
  data: {
    viewBox: "0 0 24 24",
    paths: ["M5 6c0-1.7 3.1-3 7-3s7 1.3 7 3-3.1 3-7 3-7-1.3-7-3z", "M5 6v6c0 1.7 3.1 3 7 3s7-1.3 7-3V6", "M5 12v6c0 1.7 3.1 3 7 3s7-1.3 7-3v-6"],
    circles: [],
    rects: []
  },
  filter: {
    viewBox: "0 0 24 24",
    paths: ["M4 5h16", "M7 12h10", "M10 19h4"],
    circles: [],
    rects: []
  },
  refresh: {
    viewBox: "0 0 24 24",
    paths: ["M20 6v5h-5", "M4 18v-5h5", "M19 11a7 7 0 0 0-12-3", "M5 13a7 7 0 0 0 12 3"],
    circles: [],
    rects: []
  },
  logout: {
    viewBox: "0 0 24 24",
    paths: ["M10 4H6a2 2 0 0 0-2 2v12a2 2 0 0 0 2 2h4", "M14 8l6 4-6 4", "M9 12h11"],
    circles: [],
    rects: []
  }
};

const iconDef = computed(() => ICONS[props.name] || ICONS.spark);
const iconStyle = computed(() => ({
  width: `${props.size}px`,
  height: `${props.size}px`
}));
</script>

<style scoped>
.ui-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex: 0 0 auto;
}

.ui-icon svg {
  width: 100%;
  height: 100%;
}
</style>
