package com.video.analysis.service.impl;

import com.video.analysis.auth.AuthContext;
import com.video.analysis.dto.CategoryEngagementVO;
import com.video.analysis.dto.CategoryStatVO;
import com.video.analysis.dto.FunnelStatVO;
import com.video.analysis.dto.HotVideoVO;
import com.video.analysis.dto.InsightCardVO;
import com.video.analysis.dto.PlatformBenchmarkVO;
import com.video.analysis.dto.PlatformStatVO;
import com.video.analysis.dto.SourceTraceVO;
import com.video.analysis.dto.TrendPointVO;
import com.video.analysis.dto.UserInterestVO;
import com.video.analysis.dto.VideoEngagementVO;
import com.video.analysis.mapper.VideoMapper;
import com.video.analysis.service.VideoService;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

@Service
public class VideoServiceImpl implements VideoService {

    private static final Pattern HTML_TAG_PATTERN = Pattern.compile("<[^>]+>");
    private static final Pattern MULTI_SPACE_PATTERN = Pattern.compile("\\s+");
    private static final String OVERVIEW_CACHE_ALL = "__all__";

    private final VideoMapper videoMapper;

    public VideoServiceImpl(VideoMapper videoMapper) {
        this.videoMapper = videoMapper;
    }

    @Override
    public List<HotVideoVO> getHotVideos(Integer limit, String platform) {
        Long tenantUserId = AuthContext.requireUserId();
        List<HotVideoVO> rows = videoMapper.selectHotVideos(tenantUserId, limit, platform);
        rows.forEach(this::sanitizeHotVideo);
        return rows;
    }

    @Override
    public List<CategoryStatVO> getCategoryStats(String platform) {
        Long tenantUserId = AuthContext.requireUserId();
        List<CategoryStatVO> rows = videoMapper.selectCategoryStats(tenantUserId, platform);
        rows.forEach(row -> row.setCategory(cleanText(row.getCategory())));
        return rows;
    }

    @Override
    public List<TrendPointVO> getTrend(Integer days, String platform) {
        Long tenantUserId = AuthContext.requireUserId();
        return videoMapper.selectTrend(tenantUserId, days, platform);
    }

    @Override
    public List<CategoryEngagementVO> getCategoryEngagement(String platform) {
        Long tenantUserId = AuthContext.requireUserId();
        List<CategoryEngagementVO> rows = videoMapper.selectCategoryEngagement(tenantUserId, platform);
        rows.forEach(row -> row.setCategory(cleanText(row.getCategory())));
        return rows;
    }

    @Override
    public List<VideoEngagementVO> getTopEngagementVideos(Integer limit, Long minPlay, String platform) {
        Long tenantUserId = AuthContext.requireUserId();
        List<VideoEngagementVO> rows = videoMapper.selectTopEngagementVideos(tenantUserId, limit, minPlay, platform);
        rows.forEach(this::sanitizeVideoEngagement);
        return rows;
    }

    @Override
    public List<UserInterestVO> getUserInterest(Integer limit, String platform) {
        Long tenantUserId = AuthContext.requireUserId();
        List<UserInterestVO> rows = videoMapper.selectUserInterest(tenantUserId, limit, platform);
        for (UserInterestVO row : rows) {
            sanitizeUserInterest(row);
            row.setProfileInsight(buildProfileInsight(row.getProfileLabel()));
        }
        return rows;
    }

    @Override
    public List<PlatformStatVO> getPlatformStats(String platform) {
        Long tenantUserId = AuthContext.requireUserId();
        return videoMapper.selectPlatformStats(tenantUserId, platform);
    }

    @Override
    public List<FunnelStatVO> getPlatformFunnel(String platform) {
        Long tenantUserId = AuthContext.requireUserId();
        return videoMapper.selectPlatformFunnel(tenantUserId, platform);
    }

    @Override
    public List<PlatformBenchmarkVO> getPlatformBenchmark(String platform) {
        Long tenantUserId = AuthContext.requireUserId();
        return videoMapper.selectPlatformBenchmark(tenantUserId, platform);
    }

    @Override
    public List<SourceTraceVO> getSourceTrace(Integer limit, String platform) {
        Long tenantUserId = AuthContext.requireUserId();
        List<SourceTraceVO> rows = videoMapper.selectSourceTrace(tenantUserId, limit, platform);
        rows.forEach(this::sanitizeSourceTrace);
        return rows;
    }

    @Override
    public List<InsightCardVO> getInsightCards(String platform) {
        Long tenantUserId = AuthContext.requireUserId();
        Map<String, Object> overview = loadOverview(tenantUserId, platform);
        List<PlatformBenchmarkVO> benchmark = videoMapper.selectPlatformBenchmark(tenantUserId, platform);
        List<FunnelStatVO> funnel = videoMapper.selectPlatformFunnel(tenantUserId, platform);
        List<SourceTraceVO> traces = videoMapper.selectSourceTrace(tenantUserId, 400, platform);
        List<HotVideoVO> hotTop1 = videoMapper.selectHotVideos(tenantUserId, 1, platform);

        traces.forEach(this::sanitizeSourceTrace);
        hotTop1.forEach(this::sanitizeHotVideo);
        return buildInsightCards(overview, benchmark, funnel, traces, hotTop1);
    }

    @Override
    public Map<String, Object> getDashboard(Integer hotLimit, String platform) {
        Long tenantUserId = AuthContext.requireUserId();
        int limit = hotLimit == null ? 5 : Math.max(1, Math.min(20, hotLimit));

        Map<String, Object> overview = loadOverview(tenantUserId, platform);
        List<HotVideoVO> hotVideos = videoMapper.selectHotVideos(tenantUserId, limit, platform);
        List<PlatformStatVO> platformStats = videoMapper.selectPlatformStats(tenantUserId, platform);
        List<PlatformBenchmarkVO> benchmark = videoMapper.selectPlatformBenchmark(tenantUserId, platform);
        List<FunnelStatVO> funnel = videoMapper.selectPlatformFunnel(tenantUserId, platform);
        List<SourceTraceVO> traces = videoMapper.selectSourceTrace(tenantUserId, 400, platform);

        hotVideos.forEach(this::sanitizeHotVideo);
        traces.forEach(this::sanitizeSourceTrace);

        List<HotVideoVO> hotTop1 = hotVideos.isEmpty()
                ? List.of()
                : List.of(hotVideos.get(0));
        List<InsightCardVO> insightCards = buildInsightCards(overview, benchmark, funnel, traces, hotTop1);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("overview", overview);
        result.put("hotVideos", hotVideos);
        result.put("platformStats", platformStats);
        result.put("insightCards", insightCards);
        return result;
    }

    private List<InsightCardVO> buildInsightCards(
            Map<String, Object> overview,
            List<PlatformBenchmarkVO> benchmark,
            List<FunnelStatVO> funnel,
            List<SourceTraceVO> traces,
            List<HotVideoVO> hotTop1
    ) {
        List<InsightCardVO> cards = new ArrayList<>();

        long videoCount = asLong(overview.get("videoCount"));
        long sourcePlatformCount = asLong(overview.get("sourcePlatformCount"));
        cards.add(new InsightCardVO(
                "数据规模",
                formatNumber(videoCount) + " 条视频",
                "已覆盖 " + sourcePlatformCount + " 个来源平台，可支持跨平台对比分析。",
                "info"
        ));

        if (!benchmark.isEmpty()) {
            PlatformBenchmarkVO best = benchmark.stream()
                    .max(Comparator.comparingDouble(v -> safeDouble(v.getEngagementPerThousandPlay())))
                    .orElse(benchmark.get(0));
            cards.add(new InsightCardVO(
                    "互动效率领先平台",
                    normalizePlatform(best.getSourcePlatform()),
                    "每千播放互动值 " + formatDecimal(best.getEngagementPerThousandPlay()) + "，建议优先配置互动型内容。",
                    "good"
            ));
        }

        if (!funnel.isEmpty()) {
            double maxLikeRate = funnel.stream().mapToDouble(v -> safeDouble(v.getLikeConversionRate())).max().orElse(0);
            double minLikeRate = funnel.stream().mapToDouble(v -> safeDouble(v.getLikeConversionRate())).min().orElse(0);
            cards.add(new InsightCardVO(
                    "平台转化差异",
                    formatPercent(maxLikeRate - minLikeRate),
                    "点赞转化存在明显平台差异，建议按平台做差异化标题与封面策略。",
                    maxLikeRate - minLikeRate >= 0.03 ? "warn" : "info"
            ));
        }

        if (!traces.isEmpty()) {
            double avgQuality = traces.stream().mapToDouble(v -> safeDouble(v.getDataQualityScore())).average().orElse(0);
            long lowQualityCount = traces.stream().filter(v -> safeDouble(v.getDataQualityScore()) < 55).count();
            String level = avgQuality >= 75 ? "good" : (avgQuality >= 60 ? "info" : "warn");
            cards.add(new InsightCardVO(
                    "数据质量评分",
                    formatDecimal(avgQuality),
                    "最近入库中低质量记录 " + lowQualityCount + " 条，建议优先补齐作者、来源URL和互动量字段。",
                    level
            ));
        }

        if (!hotTop1.isEmpty()) {
            HotVideoVO top = hotTop1.get(0);
            cards.add(new InsightCardVO(
                    "当前最热视频",
                    trim(top.getTitle(), 24),
                    "来源 " + normalizePlatform(top.getSourcePlatform()) + "，播放量 " + formatNumber(top.getPlayCount()) + "。",
                    "info"
            ));
        }

        return cards.size() <= 5 ? cards : cards.subList(0, 5);
    }

    @Override
    public Map<String, Object> getOverview(String platform) {
        Long tenantUserId = AuthContext.requireUserId();
        return loadOverview(tenantUserId, platform);
    }

    private Map<String, Object> loadOverview(Long tenantUserId, String platform) {
        String cachePlatform = (platform == null || platform.isBlank()) ? OVERVIEW_CACHE_ALL : platform;
        Map<String, Object> cached = videoMapper.selectOverviewCache(tenantUserId, cachePlatform);
        if (cached != null && !cached.isEmpty()) {
            return cached;
        }

        Map<String, Object> live = videoMapper.selectOverviewLive(tenantUserId, platform);
        if (live == null) {
            live = new LinkedHashMap<>();
        }
        upsertOverviewCache(tenantUserId, cachePlatform, live);
        return live;
    }

    private void upsertOverviewCache(Long tenantUserId, String cachePlatform, Map<String, Object> overview) {
        long videoCount = asLong(overview.get("videoCount"));
        long userCount = asLong(overview.get("userCount"));
        long commentCount = asLong(overview.get("commentCount"));
        long behaviorCount = asLong(overview.get("behaviorCount"));
        long totalPlayCount = asLong(overview.get("totalPlayCount"));
        int sourcePlatformCount = (int) Math.max(0, asLong(overview.get("sourcePlatformCount")));

        videoMapper.upsertOverviewCache(
                tenantUserId,
                cachePlatform,
                videoCount,
                userCount,
                commentCount,
                behaviorCount,
                totalPlayCount,
                sourcePlatformCount
        );
    }

    private String buildProfileInsight(String profileLabel) {
        if (profileLabel == null) {
            return "行为样本较少，建议先扩大观测周期。";
        }
        return switch (profileLabel) {
            case "high_interaction_discuss" -> "互动深度高，适合投放观点讨论、问答和深度解读类内容。";
            case "cross_platform_explorer" -> "跨平台行为明显，适合统一主题的多平台分发策略。";
            case "high_frequency_active" -> "活跃频次高，适合高频更新与系列化内容运营。";
            case "like_driven" -> "点赞高于评论，建议强化封面与标题吸引力以提升浅层转化。";
            case "light_browser" -> "浏览轻量，建议通过短时长高信息密度内容提升停留。";
            case "non_play_interactor" -> "非播放互动占比偏高，需排查数据采集来源和行为映射规则。";
            default -> "行为稳定，建议持续跟踪其分类偏好变化。";
        };
    }

    private void sanitizeHotVideo(HotVideoVO row) {
        if (row == null) {
            return;
        }
        row.setTitle(cleanText(row.getTitle()));
        row.setAuthor(cleanText(row.getAuthor()));
        row.setCategory(cleanText(row.getCategory()));
        row.setImportType(cleanText(row.getImportType()));
        row.setSourceFile(cleanText(row.getSourceFile()));
    }

    private void sanitizeVideoEngagement(VideoEngagementVO row) {
        if (row == null) {
            return;
        }
        row.setTitle(cleanText(row.getTitle()));
        row.setAuthor(cleanText(row.getAuthor()));
        row.setCategory(cleanText(row.getCategory()));
    }

    private void sanitizeSourceTrace(SourceTraceVO row) {
        if (row == null) {
            return;
        }
        row.setTitle(cleanText(row.getTitle()));
        row.setAuthor(cleanText(row.getAuthor()));
        row.setImportType(cleanText(row.getImportType()));
        row.setSourceFile(cleanText(row.getSourceFile()));
        row.setDedupeKey(cleanText(row.getDedupeKey()));
    }

    private void sanitizeUserInterest(UserInterestVO row) {
        if (row == null) {
            return;
        }
        row.setUserName(cleanText(row.getUserName()));
        row.setFavoriteCategory(cleanText(row.getFavoriteCategory()));
        row.setFavoritePlatform(cleanText(row.getFavoritePlatform()));
    }

    private String cleanText(String text) {
        if (text == null) {
            return null;
        }

        String value = text;
        for (int i = 0; i < 2; i++) {
            String decoded = HtmlUtils.htmlUnescape(value);
            if (decoded.equals(value)) {
                break;
            }
            value = decoded;
        }

        value = HTML_TAG_PATTERN.matcher(value).replaceAll("");
        value = value.replace('\u00A0', ' ');
        value = MULTI_SPACE_PATTERN.matcher(value).replaceAll(" ").trim();
        return value;
    }

    private long asLong(Object value) {
        if (value == null) {
            return 0L;
        }
        if (value instanceof Number n) {
            return n.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (Exception ex) {
            return 0L;
        }
    }

    private double safeDouble(Number value) {
        return value == null ? 0D : value.doubleValue();
    }

    private String formatNumber(Number value) {
        return String.format(Locale.CHINA, "%,d", value == null ? 0L : value.longValue());
    }

    private String formatDecimal(Number value) {
        return String.format(Locale.CHINA, "%.2f", value == null ? 0D : value.doubleValue());
    }

    private String formatPercent(double value) {
        return String.format(Locale.CHINA, "%.2f%%", value * 100D);
    }

    private String normalizePlatform(String platform) {
        if (platform == null || platform.isBlank()) {
            return "unknown";
        }
        return switch (platform.toLowerCase(Locale.ROOT)) {
            case "bilibili" -> "Bilibili";
            case "douyin" -> "Douyin";
            case "kuaishou" -> "Kuaishou";
            case "xiaohongshu" -> "Xiaohongshu";
            case "xigua" -> "Xigua";
            case "weibo" -> "Weibo";
            case "youtube" -> "YouTube";
            case "tiktok" -> "TikTok";
            case "acfun" -> "AcFun";
            default -> platform;
        };
    }

    private String trim(String text, int maxLen) {
        if (text == null || text.isBlank()) {
            return "-";
        }
        return text.length() <= maxLen ? text : text.substring(0, maxLen) + "...";
    }
}
