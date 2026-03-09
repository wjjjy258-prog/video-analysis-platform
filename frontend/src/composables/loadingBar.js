import { computed, ref } from "vue";

const activeRequests = ref(0);
const activeNavigations = ref(0);
const progress = ref(0);
const visible = ref(false);

let trickleTimer = null;
let hideTimer = null;

const isBusy = computed(() => activeRequests.value > 0 || activeNavigations.value > 0);

const clearTimers = () => {
  if (trickleTimer) {
    window.clearInterval(trickleTimer);
    trickleTimer = null;
  }
  if (hideTimer) {
    window.clearTimeout(hideTimer);
    hideTimer = null;
  }
};

const startTrickle = () => {
  if (trickleTimer) {
    return;
  }
  trickleTimer = window.setInterval(() => {
    if (!visible.value || !isBusy.value) {
      return;
    }
    const remain = 92 - progress.value;
    if (remain <= 0) {
      return;
    }
    const step = Math.max(0.4, remain * 0.1);
    progress.value = Math.min(92, progress.value + step);
  }, 180);
};

const ensureStarted = () => {
  clearTimers();
  if (!visible.value) {
    visible.value = true;
    progress.value = Math.max(progress.value, 8);
  } else {
    progress.value = Math.max(progress.value, 12);
  }
  startTrickle();
};

const finishIfIdle = () => {
  if (isBusy.value) {
    return;
  }
  clearTimers();
  progress.value = 100;
  hideTimer = window.setTimeout(() => {
    visible.value = false;
    progress.value = 0;
  }, 260);
};

export const beginRequest = () => {
  activeRequests.value += 1;
  ensureStarted();
};

export const endRequest = () => {
  activeRequests.value = Math.max(0, activeRequests.value - 1);
  finishIfIdle();
};

export const beginNavigation = () => {
  activeNavigations.value += 1;
  ensureStarted();
};

export const endNavigation = () => {
  activeNavigations.value = Math.max(0, activeNavigations.value - 1);
  finishIfIdle();
};

export const useLoadingBar = () => ({
  progress,
  visible,
  isBusy
});
