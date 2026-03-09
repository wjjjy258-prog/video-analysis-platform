<template>
  <div class="toast-center" aria-live="polite" aria-atomic="true">
    <TransitionGroup name="toast" tag="div" class="toast-stack">
      <article v-for="item in toasts" :key="item.id" class="toast-item" :class="`toast-${item.type}`">
        <header class="toast-head">
          <span class="toast-title">{{ item.title }}</span>
          <button class="toast-close" type="button" @click="removeToast(item.id)">×</button>
        </header>
        <p class="toast-text">{{ item.text }}</p>
        <div v-if="item.duration > 0" class="toast-progress">
          <span :style="{ animationDuration: `${item.duration}ms` }"></span>
        </div>
      </article>
    </TransitionGroup>
  </div>
</template>

<script setup>
import { useNotify } from "../composables/notify";

const { toasts, removeToast } = useNotify();
</script>

<style scoped>
.toast-center {
  position: fixed;
  right: 18px;
  top: 14px;
  z-index: 90;
  pointer-events: none;
}

.toast-stack {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.toast-item {
  width: min(380px, calc(100vw - 24px));
  pointer-events: auto;
  border-radius: 14px;
  border: 1px solid #d8e5f6;
  background: rgba(255, 255, 255, 0.96);
  backdrop-filter: blur(8px);
  box-shadow: 0 12px 34px rgba(14, 42, 82, 0.18);
  padding: 10px 12px;
}

.toast-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.toast-title {
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.2px;
}

.toast-close {
  border: none;
  background: transparent;
  color: #64748b;
  font-size: 17px;
  line-height: 1;
  cursor: pointer;
  padding: 0;
}

.toast-close:hover {
  color: #1f2937;
}

.toast-text {
  margin: 8px 0 0;
  color: #334155;
  line-height: 1.55;
  font-size: 13px;
}

.toast-progress {
  margin-top: 8px;
  height: 3px;
  border-radius: 999px;
  overflow: hidden;
  background: rgba(148, 163, 184, 0.22);
}

.toast-progress span {
  display: block;
  height: 100%;
  transform-origin: left center;
  animation: toast-shrink linear forwards;
}

.toast-info .toast-title {
  color: #1d4ed8;
}

.toast-info .toast-progress span {
  background: linear-gradient(90deg, #60a5fa, #2563eb);
}

.toast-success .toast-title {
  color: #0f8b4c;
}

.toast-success .toast-progress span {
  background: linear-gradient(90deg, #4ade80, #16a34a);
}

.toast-warning .toast-title {
  color: #b7791f;
}

.toast-warning .toast-progress span {
  background: linear-gradient(90deg, #f6ad55, #d69e2e);
}

.toast-error .toast-title {
  color: #c53030;
}

.toast-error .toast-progress span {
  background: linear-gradient(90deg, #f87171, #dc2626);
}

.toast-enter-active,
.toast-leave-active {
  transition: transform 0.2s ease, opacity 0.2s ease;
}

.toast-enter-from,
.toast-leave-to {
  transform: translateY(-10px);
  opacity: 0;
}

@keyframes toast-shrink {
  from {
    transform: scaleX(1);
  }
  to {
    transform: scaleX(0);
  }
}
</style>
