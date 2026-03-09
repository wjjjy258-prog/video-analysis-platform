export const stripHtmlTags = (value) => String(value ?? "").replace(/<[^>]+>/g, " ");

const collapseWhitespace = (value) =>
  String(value ?? "")
    .replace(/&nbsp;/gi, " ")
    .replace(/\s+/g, " ")
    .trim();

const MOJIBAKE_PATTERN = /[�锛鎵璇銆鍙鍔瑙鐧颁负]/g;

export const isLikelyMojibake = (value) => {
  const text = String(value ?? "").trim();
  if (!text) {
    return false;
  }
  const hits = text.match(MOJIBAKE_PATTERN);
  if (!hits) {
    return false;
  }
  return hits.length >= Math.max(2, Math.floor(text.length * 0.12));
};

export const normalizeServerMessage = (value, fallback = "") => {
  const cleaned = collapseWhitespace(stripHtmlTags(value));
  if (!cleaned) {
    return fallback;
  }
  if (isLikelyMojibake(cleaned)) {
    return fallback;
  }
  return cleaned;
};

const isTimeoutError = (error) => {
  const code = String(error?.code || "").toUpperCase();
  const message = String(error?.message || "").toLowerCase();
  return code === "ECONNABORTED" || message.includes("timeout") || message.includes("timed out");
};

export const getErrorMessage = (error, fallback = "请求失败，请稍后重试。") => {
  const status = Number(error?.response?.status || 0);

  if (isTimeoutError(error)) {
    return "请求超时：当前数据量较大，系统仍在处理。你可以稍后刷新查看结果。";
  }

  const payload = error?.response?.data;
  const raw = payload?.message || payload?.error || (typeof payload === "string" ? payload : "") || error?.message;
  const message = normalizeServerMessage(raw);
  if (message) {
    return message;
  }

  if (!status) {
    return "网络连接失败，请确认后端服务是否已启动。";
  }
  if (status === 401) {
    return "登录状态已失效，请重新登录。";
  }
  if (status === 403) {
    return "当前账号没有权限执行该操作。";
  }
  if (status === 404) {
    return "接口不存在，请检查后端是否已启动并更新。";
  }
  if (status >= 500) {
    return "服务端异常，请稍后重试。";
  }
  return fallback;
};

export const getSuccessMessage = (value, fallback = "操作成功。") => normalizeServerMessage(value, fallback);
