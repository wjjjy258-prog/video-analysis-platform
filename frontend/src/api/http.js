import axios from "axios";
import { beginRequest, endRequest } from "../composables/loadingBar";
import { useAuth } from "../composables/auth";
import { notifyError, notifyWarning } from "../composables/notify";
import { getErrorMessage } from "../utils/feedback";

const apiBaseUrl = import.meta.env.VITE_API_BASE_URL || "http://localhost:8080";
const timeoutEnv = Number(import.meta.env.VITE_API_TIMEOUT_MS);
const requestTimeout = Number.isFinite(timeoutEnv) && timeoutEnv >= 0 ? timeoutEnv : 0;
const { token, clearAuth } = useAuth();

const http = axios.create({
  baseURL: apiBaseUrl,
  timeout: requestTimeout
});

http.interceptors.request.use(
  (config) => {
    beginRequest();
    const authToken = String(token.value || "").trim();
    if (authToken) {
      config.headers = config.headers || {};
      config.headers.Authorization = `Bearer ${authToken}`;
    }
    return config;
  },
  (error) => {
    endRequest();
    if (error?.response?.status === 401) {
      clearAuth();
      const requestUrl = String(error?.config?.url || "");
      const isAuthApi = requestUrl.includes("/auth/login") || requestUrl.includes("/auth/register");
      if (!isAuthApi && typeof window !== "undefined" && window.location.pathname !== "/") {
        window.location.href = "/";
      }
    }
    if (!error?.config?.silentError) {
      notifyError(getErrorMessage(error, "请求发送失败。"), { title: "请求失败" });
    }
    return Promise.reject(error);
  }
);

http.interceptors.response.use(
  (response) => {
    endRequest();
    return response;
  },
  (error) => {
    endRequest();
    const status = Number(error?.response?.status || 0);
    const requestUrl = String(error?.config?.url || "");
    const isAuthApi = requestUrl.includes("/auth/login") || requestUrl.includes("/auth/register");

    if (status === 401) {
      clearAuth();
      if (!isAuthApi && typeof window !== "undefined" && window.location.pathname !== "/") {
        notifyWarning("登录状态已失效，已为你跳转到登录页。", { title: "会话过期" });
        window.location.href = "/";
      }
    } else if (!error?.config?.silentError) {
      notifyError(getErrorMessage(error), { title: "请求失败" });
    }

    return Promise.reject(error);
  }
);

export default http;
