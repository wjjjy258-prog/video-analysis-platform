const PLATFORM_LABEL_MAP = {
  all: "全部平台",
  auto: "自动识别",
  bilibili: "哔哩哔哩",
  douyin: "抖音",
  kuaishou: "快手",
  xiaohongshu: "小红书",
  xigua: "西瓜视频",
  weibo: "微博",
  youtube: "YouTube",
  tiktok: "TikTok",
  acfun: "AcFun",
  seed: "未知来源",
  unknown: "未知来源",
  mixed: "多平台"
};

export const toPlatformLabel = (value) => {
  const key = String(value ?? "").trim().toLowerCase();
  if (!key) {
    return PLATFORM_LABEL_MAP.unknown;
  }
  return PLATFORM_LABEL_MAP[key] || value || PLATFORM_LABEL_MAP.unknown;
};

export const CRAWLER_PLATFORM_OPTIONS = [
  { value: "auto", label: PLATFORM_LABEL_MAP.auto },
  { value: "bilibili", label: PLATFORM_LABEL_MAP.bilibili },
  { value: "douyin", label: PLATFORM_LABEL_MAP.douyin },
  { value: "kuaishou", label: PLATFORM_LABEL_MAP.kuaishou },
  { value: "xiaohongshu", label: PLATFORM_LABEL_MAP.xiaohongshu },
  { value: "xigua", label: PLATFORM_LABEL_MAP.xigua },
  { value: "weibo", label: PLATFORM_LABEL_MAP.weibo },
  { value: "youtube", label: PLATFORM_LABEL_MAP.youtube },
  { value: "tiktok", label: PLATFORM_LABEL_MAP.tiktok },
  { value: "acfun", label: PLATFORM_LABEL_MAP.acfun }
];

export const IMPORT_DEFAULT_PLATFORM_OPTIONS = [
  { value: "unknown", label: PLATFORM_LABEL_MAP.unknown },
  { value: "bilibili", label: PLATFORM_LABEL_MAP.bilibili },
  { value: "douyin", label: PLATFORM_LABEL_MAP.douyin },
  { value: "kuaishou", label: PLATFORM_LABEL_MAP.kuaishou },
  { value: "xiaohongshu", label: PLATFORM_LABEL_MAP.xiaohongshu },
  { value: "xigua", label: PLATFORM_LABEL_MAP.xigua },
  { value: "weibo", label: PLATFORM_LABEL_MAP.weibo },
  { value: "youtube", label: PLATFORM_LABEL_MAP.youtube },
  { value: "tiktok", label: PLATFORM_LABEL_MAP.tiktok },
  { value: "acfun", label: PLATFORM_LABEL_MAP.acfun }
];
