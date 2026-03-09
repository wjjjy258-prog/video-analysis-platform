import { ref } from "vue";

const STORAGE_KEY = "video-analysis-platform:selected-platform";

export const PLATFORM_OPTIONS = [
  { value: "all", label: "全部平台" },
  { value: "bilibili", label: "哔哩哔哩" },
  { value: "douyin", label: "抖音" },
  { value: "kuaishou", label: "快手" },
  { value: "xiaohongshu", label: "小红书" },
  { value: "xigua", label: "西瓜视频" },
  { value: "weibo", label: "微博" },
  { value: "youtube", label: "YouTube" },
  { value: "tiktok", label: "TikTok" },
  { value: "acfun", label: "AcFun" },
  { value: "seed", label: "系统内置数据" },
  { value: "unknown", label: "未知来源" }
];

const PLATFORM_SET = new Set(PLATFORM_OPTIONS.map((item) => item.value));

const normalizePlatformValue = (value) => {
  const normalized = String(value ?? "").trim().toLowerCase();
  if (!normalized || !PLATFORM_SET.has(normalized)) {
    return "all";
  }
  return normalized;
};

const getInitialPlatform = () => {
  if (typeof window === "undefined") {
    return "all";
  }
  return normalizePlatformValue(window.localStorage.getItem(STORAGE_KEY));
};

const selectedPlatform = ref(getInitialPlatform());
const refreshSignal = ref(0);

export const usePlatformFilter = () => {
  const setPlatform = (value) => {
    const normalized = normalizePlatformValue(value);
    selectedPlatform.value = normalized;
    if (typeof window !== "undefined") {
      window.localStorage.setItem(STORAGE_KEY, normalized);
    }
  };

  const refreshData = () => {
    refreshSignal.value += 1;
  };

  return {
    selectedPlatform,
    refreshSignal,
    platformOptions: PLATFORM_OPTIONS,
    setPlatform,
    refreshData
  };
};
