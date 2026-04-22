import http from "./http";

// 【说明】当平台为“全部”时不传平台筛选参数，后端返回全平台聚合结果。
const buildPlatformParams = (platform) => {
  const normalized = String(platform ?? "").trim().toLowerCase();
  if (!normalized || normalized === "all") {
    return {};
  }
  return { platform: normalized };
};

export const getOverview = async (platform = "all") => {
  const { data } = await http.get("/video/overview", { params: buildPlatformParams(platform) });
  return data;
};

export const getHomeDashboard = async (hotLimit = 5, platform = "all") => {
  const { data } = await http.get("/video/dashboard", {
    params: { hotLimit, ...buildPlatformParams(platform) }
  });
  return data;
};

export const getCreatorDashboard = async (platform = "all") => {
  const { data } = await http.get("/video/creator/dashboard", {
    params: buildPlatformParams(platform),
    silentError: true
  });
  return data;
};

export const updateCreatorProfile = async (payload) => {
  const { data } = await http.post("/video/creator/profile", payload || {}, {
    silentError: true
  });
  return data;
};

export const getHotVideos = async (limit = 10, platform = "all") => {
  const { data } = await http.get("/video/hot", { params: { limit, ...buildPlatformParams(platform) } });
  return data;
};

export const getCategoryStats = async (platform = "all") => {
  const { data } = await http.get("/video/category", { params: buildPlatformParams(platform) });
  return data;
};

export const getTrendStats = async (days = 30, platform = "all") => {
  const { data } = await http.get("/video/trend", { params: { days, ...buildPlatformParams(platform) } });
  return data;
};

export const getCategoryEngagementStats = async (platform = "all") => {
  const { data } = await http.get("/video/engagement/category", { params: buildPlatformParams(platform) });
  return data;
};

export const getTopEngagementVideos = async (limit = 20, minPlay = 10000, platform = "all") => {
  const { data } = await http.get("/video/engagement/video", {
    params: { limit, minPlay, ...buildPlatformParams(platform) }
  });
  return data;
};

export const getUserInterest = async (limit = 10, platform = "all") => {
  const { data } = await http.get("/video/user", { params: { limit, ...buildPlatformParams(platform) } });
  return data;
};

export const getPlatformStats = async (platform = "all") => {
  const { data } = await http.get("/video/platform", { params: buildPlatformParams(platform) });
  return data;
};

export const getPlatformFunnel = async (platform = "all") => {
  const { data } = await http.get("/video/funnel", { params: buildPlatformParams(platform) });
  return data;
};

export const getPlatformBenchmark = async (platform = "all") => {
  const { data } = await http.get("/video/platform/benchmark", { params: buildPlatformParams(platform) });
  return data;
};

export const getSourceTrace = async (limit = 30, platform = "all") => {
  const { data } = await http.get("/video/source-trace", { params: { limit, ...buildPlatformParams(platform) } });
  return data;
};

export const getInsightCards = async (platform = "all") => {
  const { data } = await http.get("/video/insight", { params: buildPlatformParams(platform) });
  return data;
};

export const runCrawlerByUrls = async (payload) => {
  const { data } = await http.post("/crawler/run-url", payload, {
    silentError: true,
    timeout: 0
  });
  return data;
};

export const runCrawlerMock = async () => {
  const { data } = await http.post("/crawler/run-mock", null, {
    silentError: true,
    timeout: 0
  });
  return data;
};

export const clearCrawlerData = async () => {
  const { data } = await http.post("/crawler/clear-data", { confirm: true }, { silentError: true, timeout: 0 });
  return data;
};

export const getImportRejects = async (limit = 50) => {
  // 【说明】获取被质量门禁拒绝的记录及可执行修复建议。
  const { data } = await http.get("/crawler/rejects", { params: { limit }, silentError: true });
  return data;
};

export const importCrawlerText = async (payload) => {
  const { data } = await http.post("/crawler/import-text", payload, {
    silentError: true,
    timeout: 0
  });
  return data;
};

export const importCrawlerFile = async (file, defaultPlatform = "unknown", aiAssist = false) => {
  const formData = new FormData();
  formData.append("file", file);
  formData.append("defaultPlatform", defaultPlatform);
  formData.append("aiAssist", String(Boolean(aiAssist)));
  const { data } = await http.post("/crawler/import-file", formData, {
    silentError: true,
    timeout: 0,
    headers: {
      "Content-Type": "multipart/form-data"
    }
  });
  return data;
};
