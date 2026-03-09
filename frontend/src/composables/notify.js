import { readonly, ref } from "vue";
import { normalizeServerMessage } from "../utils/feedback";

const toasts = ref([]);
let toastSeed = 1;

const recentPushAt = new Map();
const DEDUPE_INTERVAL_MS = 1400;

const DEFAULT_DURATION = {
  info: 3200,
  success: 3200,
  warning: 4200,
  error: 5200
};

const titleMap = {
  info: "提示",
  success: "成功",
  warning: "警告",
  error: "失败"
};

const getToastDuration = (type, duration) => {
  if (duration === 0) {
    return 0;
  }
  if (typeof duration === "number" && Number.isFinite(duration) && duration > 0) {
    return Math.round(duration);
  }
  return DEFAULT_DURATION[type] ?? DEFAULT_DURATION.info;
};

const removeToast = (id) => {
  const index = toasts.value.findIndex((item) => item.id === id);
  if (index >= 0) {
    toasts.value.splice(index, 1);
  }
};

const shouldSkipByDedupe = (dedupeKey) => {
  const now = Date.now();
  const previous = recentPushAt.get(dedupeKey) || 0;
  recentPushAt.set(dedupeKey, now);
  return now - previous < DEDUPE_INTERVAL_MS;
};

const pushToast = (type, content, options = {}) => {
  const text = normalizeServerMessage(content, options.fallback || "操作完成");
  if (!text) {
    return null;
  }

  const dedupeKey = `${type}:${text}`;
  if (shouldSkipByDedupe(dedupeKey)) {
    return null;
  }

  const id = toastSeed;
  toastSeed += 1;

  const duration = getToastDuration(type, options.duration);
  const toast = {
    id,
    type,
    text,
    title: options.title || titleMap[type] || titleMap.info,
    duration,
    createdAt: Date.now()
  };

  toasts.value.push(toast);

  if (duration > 0) {
    window.setTimeout(() => removeToast(id), duration);
  }

  return id;
};

export const notifyInfo = (content, options = {}) => pushToast("info", content, options);
export const notifySuccess = (content, options = {}) => pushToast("success", content, options);
export const notifyWarning = (content, options = {}) => pushToast("warning", content, options);
export const notifyError = (content, options = {}) => pushToast("error", content, options);

export const clearToasts = () => {
  toasts.value = [];
};

export const useNotify = () => ({
  toasts: readonly(toasts),
  removeToast,
  clearToasts
});
