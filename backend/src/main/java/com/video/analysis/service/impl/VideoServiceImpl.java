package com.video.analysis.service.impl;

import com.video.analysis.auth.AuthContext;
import com.video.analysis.dto.CategoryEngagementVO;
import com.video.analysis.dto.CategoryStatVO;
import com.video.analysis.dto.CreatorBenchmarkVO;
import com.video.analysis.dto.CreatorProfileUpdateRequest;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.HtmlUtils;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.FORBIDDEN;

@Service
public class VideoServiceImpl implements VideoService {

    private static final Pattern HTML_TAG_PATTERN = Pattern.compile("<[^>]+>");
    private static final Pattern MULTI_SPACE_PATTERN = Pattern.compile("\\s+");
    private static final String OVERVIEW_CACHE_ALL = "__all__";
    private static final String ROLE_CREATOR = "creator";
    private static final Set<String> CREATOR_PLATFORM_WHITELIST = new HashSet<>(Arrays.asList(
            "unknown", "bilibili", "douyin", "kuaishou", "xiaohongshu", "xigua", "weibo", "youtube", "tiktok", "acfun"
    ));

    private final VideoMapper videoMapper;
    private final JdbcTemplate jdbcTemplate;

    public VideoServiceImpl(VideoMapper videoMapper, JdbcTemplate jdbcTemplate) {
        this.videoMapper = videoMapper;
        this.jdbcTemplate = jdbcTemplate;
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

    @Override
    public Map<String, Object> getCreatorDashboard(String platform) {
        Long tenantUserId = AuthContext.requireUserId();
        CreatorProfile profile = loadCreatorProfile(tenantUserId);
        if (profile == null) {
            throw new ResponseStatusException(FORBIDDEN, "\u672a\u627e\u5230\u5f53\u524d\u8d26\u53f7\u4fe1\u606f\u3002");
        }
        if (!ROLE_CREATOR.equalsIgnoreCase(profile.role())) {
            throw new ResponseStatusException(FORBIDDEN, "\u5f53\u524d\u8d26\u53f7\u4e0d\u662f\u5185\u5bb9\u521b\u4f5c\u8005\u3002");
        }

        String effectivePlatform = resolveCreatorPlatform(platform, profile.creatorPlatform());
        String normalizedCreatorName = normalizeAuthorKey(profile.creatorName());

        CreatorOverview ownOverview = queryCreatorOverview(tenantUserId, normalizedCreatorName, effectivePlatform);
        List<HotVideoVO> ownTopVideos = queryCreatorTopVideos(tenantUserId, normalizedCreatorName, effectivePlatform, 5);
        List<CategoryStatVO> ownCategoryStats = queryCreatorCategoryStats(tenantUserId, normalizedCreatorName, effectivePlatform, 6);
        List<CreatorBenchmarkVO> rivalAuthors = queryRivalAuthors(
                tenantUserId,
                normalizedCreatorName,
                effectivePlatform,
                profile.creatorFocusCategory(),
                6
        );

        ownTopVideos.forEach(this::sanitizeHotVideo);
        ownCategoryStats.forEach(row -> row.setCategory(cleanText(row.getCategory())));
        rivalAuthors.forEach(this::sanitizeCreatorBenchmark);
        Map<String, Object> rivalSummary = buildRivalSummary(rivalAuthors);

        Map<String, Object> profileMap = new LinkedHashMap<>();
        profileMap.put("username", cleanText(profile.username()));
        profileMap.put("role", cleanText(profile.role()));
        profileMap.put("creatorName", cleanText(profile.creatorName()));
        profileMap.put("creatorPlatform", normalizePlatform(profile.creatorPlatform()));
        profileMap.put("creatorFocusCategory", cleanText(profile.creatorFocusCategory()));
        profileMap.put("activePlatform", effectivePlatform == null ? "\u5168\u90e8\u5e73\u53f0" : normalizePlatform(effectivePlatform));

        Map<String, Object> ownOverviewMap = new LinkedHashMap<>();
        ownOverviewMap.put("videoCount", ownOverview.videoCount());
        ownOverviewMap.put("totalPlayCount", ownOverview.totalPlayCount());
        ownOverviewMap.put("totalLikeCount", ownOverview.totalLikeCount());
        ownOverviewMap.put("totalCommentCount", ownOverview.totalCommentCount());
        ownOverviewMap.put("avgPlayPerVideo", ownOverview.avgPlayPerVideo());
        ownOverviewMap.put("engagementPerThousandPlay", ownOverview.engagementPerThousandPlay());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("profile", profileMap);
        result.put("ownOverview", ownOverviewMap);
        result.put("ownTopVideos", ownTopVideos);
        result.put("ownCategoryStats", ownCategoryStats);
        result.put("rivalAuthors", rivalAuthors);
        result.put("rivalSummary", rivalSummary);
        result.put("suggestions", buildCreatorSuggestions(profile, ownOverview, ownCategoryStats, rivalSummary));
        return result;
    }

    @Override
    public Map<String, Object> updateCreatorProfile(CreatorProfileUpdateRequest request) {
        Long tenantUserId = AuthContext.requireUserId();
        CreatorProfile profile = loadCreatorProfile(tenantUserId);
        if (profile == null) {
            throw new ResponseStatusException(FORBIDDEN, "\u672a\u627e\u5230\u5f53\u524d\u8d26\u53f7\u4fe1\u606f\u3002");
        }
        if (!ROLE_CREATOR.equalsIgnoreCase(profile.role())) {
            throw new ResponseStatusException(FORBIDDEN, "\u5f53\u524d\u8d26\u53f7\u4e0d\u662f\u5185\u5bb9\u521b\u4f5c\u8005\u3002");
        }

        String creatorPlatform = normalizeCreatorPlatformForUpdate(request == null ? null : request.getCreatorPlatform());
        String creatorFocusCategory = normalizeCreatorFocusCategoryForUpdate(request == null ? null : request.getCreatorFocusCategory());

        jdbcTemplate.update(
                "UPDATE app_user SET creator_platform=?, creator_focus_category=?, updated_at=NOW() WHERE id=?",
                creatorPlatform,
                creatorFocusCategory,
                tenantUserId
        );

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("creatorPlatform", creatorPlatform);
        result.put("creatorFocusCategory", creatorFocusCategory);
        return result;
    }

    private CreatorProfile loadCreatorProfile(Long tenantUserId) {
        List<CreatorProfile> rows = jdbcTemplate.query(
                "SELECT id, username, COALESCE(user_role, 'viewer') AS user_role, " +
                        "COALESCE(NULLIF(creator_name, ''), username) AS creator_name, " +
                        "COALESCE(NULLIF(creator_platform, ''), 'unknown') AS creator_platform, " +
                        "NULLIF(creator_focus_category, '') AS creator_focus_category " +
                        "FROM app_user WHERE id=? LIMIT 1",
                (rs, idx) -> new CreatorProfile(
                        rs.getLong("id"),
                        rs.getString("username"),
                        rs.getString("user_role"),
                        rs.getString("creator_name"),
                        rs.getString("creator_platform"),
                        rs.getString("creator_focus_category")
                ),
                tenantUserId
        );
        return rows.isEmpty() ? null : rows.get(0);
    }

    private CreatorOverview queryCreatorOverview(Long tenantUserId, String normalizedCreatorName, String platform) {
        StringBuilder sql = new StringBuilder(
                "SELECT COUNT(*) AS video_count, " +
                        "COALESCE(SUM(play_count), 0) AS total_play_count, " +
                        "COALESCE(SUM(like_count), 0) AS total_like_count, " +
                        "COALESCE(SUM(comment_count), 0) AS total_comment_count, " +
                        "ROUND(CASE WHEN COUNT(*) = 0 THEN 0 ELSE COALESCE(SUM(play_count), 0) / COUNT(*) END, 2) AS avg_play_per_video, " +
                        "ROUND(CASE WHEN COALESCE(SUM(play_count), 0) = 0 THEN 0 " +
                        "ELSE (COALESCE(SUM(like_count), 0) + COALESCE(SUM(comment_count), 0)) * 1000 / COALESCE(SUM(play_count), 0) END, 2) AS engagement_per_thousand_play " +
                        "FROM video WHERE tenant_user_id=? AND " + authorMatchExpression("author") + "=?"
        );
        List<Object> args = new ArrayList<>();
        args.add(tenantUserId);
        args.add(normalizedCreatorName);
        appendPlatformClause(sql, args, platform, "source_platform");

        return jdbcTemplate.queryForObject(
                sql.toString(),
                (rs, idx) -> new CreatorOverview(
                        rs.getLong("video_count"),
                        rs.getLong("total_play_count"),
                        rs.getLong("total_like_count"),
                        rs.getLong("total_comment_count"),
                        rs.getDouble("avg_play_per_video"),
                        rs.getDouble("engagement_per_thousand_play")
                ),
                args.toArray()
        );
    }

    private List<HotVideoVO> queryCreatorTopVideos(Long tenantUserId, String normalizedCreatorName, String platform, int limit) {
        StringBuilder sql = new StringBuilder(
                "SELECT id, title, author, COALESCE(source_platform, 'unknown') AS source_platform, " +
                        "source_url, import_type, source_file, data_quality_score, category, play_count, like_count, comment_count, publish_time, import_time " +
                        "FROM video WHERE tenant_user_id=? AND " + authorMatchExpression("author") + "=?"
        );
        List<Object> args = new ArrayList<>();
        args.add(tenantUserId);
        args.add(normalizedCreatorName);
        appendPlatformClause(sql, args, platform, "source_platform");
        sql.append(" ORDER BY play_count DESC, id DESC LIMIT ?");
        args.add(limit);

        return jdbcTemplate.query(
                sql.toString(),
                (rs, idx) -> {
                    HotVideoVO row = new HotVideoVO();
                    row.setId(rs.getLong("id"));
                    row.setTitle(rs.getString("title"));
                    row.setAuthor(rs.getString("author"));
                    row.setSourcePlatform(rs.getString("source_platform"));
                    row.setSourceUrl(rs.getString("source_url"));
                    row.setImportType(rs.getString("import_type"));
                    row.setSourceFile(rs.getString("source_file"));
                    row.setDataQualityScore(rs.getDouble("data_quality_score"));
                    row.setCategory(rs.getString("category"));
                    row.setPlayCount(rs.getLong("play_count"));
                    row.setLikeCount(rs.getLong("like_count"));
                    row.setCommentCount(rs.getLong("comment_count"));
                    Timestamp publishTime = rs.getTimestamp("publish_time");
                    Timestamp importTime = rs.getTimestamp("import_time");
                    row.setPublishTime(publishTime == null ? null : publishTime.toLocalDateTime());
                    row.setImportTime(importTime == null ? null : importTime.toLocalDateTime());
                    return row;
                },
                args.toArray()
        );
    }

    private List<CategoryStatVO> queryCreatorCategoryStats(Long tenantUserId, String normalizedCreatorName, String platform, int limit) {
        StringBuilder sql = new StringBuilder(
                "SELECT category, COUNT(*) AS video_count, COALESCE(SUM(play_count), 0) AS total_play, " +
                        "COALESCE(SUM(like_count), 0) AS total_like, COALESCE(SUM(comment_count), 0) AS total_comment " +
                        "FROM video WHERE tenant_user_id=? AND " + authorMatchExpression("author") + "=?"
        );
        List<Object> args = new ArrayList<>();
        args.add(tenantUserId);
        args.add(normalizedCreatorName);
        appendPlatformClause(sql, args, platform, "source_platform");
        sql.append(" GROUP BY category ORDER BY total_play DESC, video_count DESC LIMIT ?");
        args.add(limit);

        return jdbcTemplate.query(
                sql.toString(),
                (rs, idx) -> {
                    CategoryStatVO row = new CategoryStatVO();
                    row.setCategory(rs.getString("category"));
                    row.setVideoCount(rs.getLong("video_count"));
                    row.setTotalPlay(rs.getLong("total_play"));
                    row.setTotalLike(rs.getLong("total_like"));
                    row.setTotalComment(rs.getLong("total_comment"));
                    return row;
                },
                args.toArray()
        );
    }

    private List<CreatorBenchmarkVO> queryRivalAuthors(
            Long tenantUserId,
            String normalizedCreatorName,
            String platform,
            String focusCategory,
            int limit
    ) {
        StringBuilder sql = new StringBuilder(
                "SELECT author, COALESCE(source_platform, 'unknown') AS source_platform, " +
                        "SUBSTRING_INDEX(GROUP_CONCAT(category ORDER BY play_count DESC SEPARATOR ','), ',', 1) AS main_category, " +
                        "COUNT(*) AS video_count, COALESCE(SUM(play_count), 0) AS total_play_count, " +
                        "COALESCE(SUM(like_count), 0) AS total_like_count, COALESCE(SUM(comment_count), 0) AS total_comment_count, " +
                        "ROUND(CASE WHEN COUNT(*) = 0 THEN 0 ELSE COALESCE(SUM(play_count), 0) / COUNT(*) END, 2) AS avg_play_per_video, " +
                        "ROUND(CASE WHEN COALESCE(SUM(play_count), 0) = 0 THEN 0 " +
                        "ELSE (COALESCE(SUM(like_count), 0) + COALESCE(SUM(comment_count), 0)) * 1000 / COALESCE(SUM(play_count), 0) END, 2) AS engagement_per_thousand_play " +
                        "FROM video WHERE tenant_user_id=? AND " + authorMatchExpression("author") + "<>?"
        );
        List<Object> args = new ArrayList<>();
        args.add(tenantUserId);
        args.add(normalizedCreatorName);

        String cleanedFocus = cleanText(focusCategory);
        if (cleanedFocus != null && !cleanedFocus.isBlank()) {
            sql.append(" AND COALESCE(category, '')=?");
            args.add(cleanedFocus);
        }
        appendPlatformClause(sql, args, platform, "source_platform");

        sql.append(" GROUP BY author, COALESCE(source_platform, 'unknown')");
        sql.append(" ORDER BY avg_play_per_video DESC, engagement_per_thousand_play DESC LIMIT ?");
        args.add(limit);

        return jdbcTemplate.query(
                sql.toString(),
                (rs, idx) -> {
                    CreatorBenchmarkVO row = new CreatorBenchmarkVO();
                    row.setAuthor(rs.getString("author"));
                    row.setSourcePlatform(rs.getString("source_platform"));
                    row.setMainCategory(rs.getString("main_category"));
                    row.setVideoCount(rs.getLong("video_count"));
                    row.setTotalPlayCount(rs.getLong("total_play_count"));
                    row.setTotalLikeCount(rs.getLong("total_like_count"));
                    row.setTotalCommentCount(rs.getLong("total_comment_count"));
                    row.setAvgPlayPerVideo(rs.getDouble("avg_play_per_video"));
                    row.setEngagementPerThousandPlay(rs.getDouble("engagement_per_thousand_play"));
                    return row;
                },
                args.toArray()
        );
    }

    private Map<String, Object> buildRivalSummary(List<CreatorBenchmarkVO> rivalAuthors) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (rivalAuthors == null || rivalAuthors.isEmpty()) {
            result.put("rivalCount", 0);
            result.put("avgPlayPerVideo", 0D);
            result.put("engagementPerThousandPlay", 0D);
            result.put("leadingAuthor", null);
            result.put("leadingAuthorAvgPlay", 0D);
            result.put("leadingAuthorEngagement", 0D);
            return result;
        }

        double avgPlay = rivalAuthors.stream().mapToDouble(v -> safeDouble(v.getAvgPlayPerVideo())).average().orElse(0D);
        double avgEngagement = rivalAuthors.stream().mapToDouble(v -> safeDouble(v.getEngagementPerThousandPlay())).average().orElse(0D);
        CreatorBenchmarkVO leader = rivalAuthors.get(0);

        result.put("rivalCount", rivalAuthors.size());
        result.put("avgPlayPerVideo", avgPlay);
        result.put("engagementPerThousandPlay", avgEngagement);
        result.put("leadingAuthor", cleanText(leader.getAuthor()));
        result.put("leadingAuthorAvgPlay", safeDouble(leader.getAvgPlayPerVideo()));
        result.put("leadingAuthorEngagement", safeDouble(leader.getEngagementPerThousandPlay()));
        return result;
    }

    private List<String> buildCreatorSuggestions(
            CreatorProfile profile,
            CreatorOverview ownOverview,
            List<CategoryStatVO> ownCategoryStats,
            Map<String, Object> rivalSummary
    ) {
        List<String> suggestions = new ArrayList<>();
        if (ownOverview.videoCount() <= 0) {
            suggestions.add("\u672a\u8bc6\u522b\u5230\u4f60\u7684\u4f5c\u54c1\u6837\u672c\u3002\u8bf7\u5148\u5bfc\u5165\u672c\u4eba\u4f5c\u54c1\u6570\u636e\uff0c\u6216\u68c0\u67e5\u4f5c\u8005\u540d\u662f\u5426\u4e0e\u8d26\u53f7\u5339\u914d\u3002");
            suggestions.add("\u5efa\u8bae\u5148\u5bfc\u5165 3 \u5230 5 \u4e2a\u540c\u8d5b\u9053\u540c\u884c\u8d26\u53f7\uff0c\u518d\u8fdb\u884c\u5bf9\u6807\u5206\u6790\uff0c\u7ed3\u8bba\u4f1a\u66f4\u7a33\u5b9a\u3002");
            return suggestions;
        }

        double rivalAvgPlay = safeDouble((Number) rivalSummary.get("avgPlayPerVideo"));
        double rivalAvgEngagement = safeDouble((Number) rivalSummary.get("engagementPerThousandPlay"));
        if (rivalAvgPlay > 0 && ownOverview.avgPlayPerVideo() < rivalAvgPlay) {
            suggestions.add("\u4f60\u7684\u5355\u6761\u5e73\u5747\u64ad\u653e\u4f4e\u4e8e\u540c\u884c\u5747\u503c\u3002\u5efa\u8bae\u4f18\u5148\u4f18\u5316\u9009\u9898\u3001\u6807\u9898\u4e0e\u5c01\u9762\u3002");
        } else if (rivalAvgPlay > 0) {
            suggestions.add("\u4f60\u7684\u5355\u6761\u5e73\u5747\u64ad\u653e\u4e0d\u4f4e\u4e8e\u540c\u884c\u3002\u5efa\u8bae\u4fdd\u6301\u9ad8\u8868\u73b0\u9898\u6750\u5e76\u6301\u7eed\u8fed\u4ee3\u7cfb\u5217\u5185\u5bb9\u3002");
        } else {
            suggestions.add("\u540c\u884c\u6837\u672c\u4e0d\u8db3\uff0c\u6682\u65e0\u6cd5\u7a33\u5b9a\u8bc4\u4f30\u64ad\u653e\u5dee\u8ddd\u3002\u5efa\u8bae\u8865\u5145\u540c\u8d5b\u9053\u8d26\u53f7\u540e\u518d\u89c2\u5bdf\u3002");
        }

        if (rivalAvgEngagement > 0 && ownOverview.engagementPerThousandPlay() < rivalAvgEngagement) {
            suggestions.add("\u4f60\u7684\u6bcf\u5343\u64ad\u653e\u4e92\u52a8\u503c\u4f4e\u4e8e\u540c\u884c\u3002\u5efa\u8bae\u589e\u52a0\u8bc4\u8bba\u5f15\u5bfc\u3001\u63d0\u95ee\u5f0f\u7ed3\u5c3e\u548c\u8bdd\u9898\u4e92\u52a8\u3002");
        } else if (rivalAvgEngagement > 0) {
            suggestions.add("\u4f60\u7684\u6bcf\u5343\u64ad\u653e\u4e92\u52a8\u503c\u5177\u5907\u7ade\u4e89\u529b\u3002\u53ef\u7ee7\u7eed\u5f3a\u5316\u9ad8\u4e92\u52a8\u5185\u5bb9\u5e76\u63d0\u5347\u66f4\u65b0\u7a33\u5b9a\u6027\u3002");
        } else {
            suggestions.add("\u540c\u884c\u4e92\u52a8\u6837\u672c\u4e0d\u8db3\uff0c\u6682\u65e0\u6cd5\u7a33\u5b9a\u8bc4\u4f30\u4e92\u52a8\u5dee\u8ddd\u3002\u5efa\u8bae\u8865\u5145\u540c\u8d5b\u9053\u6837\u672c\u3002");
        }

        if (ownCategoryStats != null && !ownCategoryStats.isEmpty()) {
            CategoryStatVO bestCategory = ownCategoryStats.get(0);
            suggestions.add("\u5f53\u524d\u8868\u73b0\u6700\u597d\u7684\u65b9\u5411\u662f\u201c" + cleanText(bestCategory.getCategory()) + "\u201d\uff0c\u5efa\u8bae\u7ee7\u7eed\u6df1\u8015\u5f62\u6210\u7a33\u5b9a\u5185\u5bb9\u652f\u67f1\u3002");
        }

        if (profile.creatorFocusCategory() == null || profile.creatorFocusCategory().isBlank()) {
            suggestions.add("\u4f60\u8fd8\u6ca1\u6709\u8bbe\u7f6e\u4e3b\u6253\u65b9\u5411\u3002\u8bbe\u7f6e\u540e\u53ef\u83b7\u5f97\u66f4\u7cbe\u51c6\u7684\u540c\u884c\u5bf9\u6807\u7ed3\u679c\u3002");
        }
        return suggestions;
    }

    private void appendPlatformClause(StringBuilder sql, List<Object> args, String platform, String columnName) {
        if (platform == null || platform.isBlank()) {
            return;
        }
        sql.append(" AND COALESCE(").append(columnName).append(", 'unknown')=?");
        args.add(platform);
    }

    private String resolveCreatorPlatform(String requestedPlatform, String creatorPlatform) {
        if (requestedPlatform != null && !requestedPlatform.isBlank()) {
            return requestedPlatform;
        }
        if (creatorPlatform == null || creatorPlatform.isBlank() || "unknown".equalsIgnoreCase(creatorPlatform)) {
            return null;
        }
        return creatorPlatform.toLowerCase(Locale.ROOT);
    }

    private String normalizeCreatorPlatformForUpdate(String creatorPlatform) {
        String normalized = creatorPlatform == null ? "" : creatorPlatform.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            return "unknown";
        }
        if (!CREATOR_PLATFORM_WHITELIST.contains(normalized)) {
            throw new ResponseStatusException(BAD_REQUEST, "\u4e3b\u8fd0\u8425\u5e73\u53f0\u53d6\u503c\u4e0d\u5408\u6cd5\u3002");
        }
        return normalized;
    }

    private String normalizeCreatorFocusCategoryForUpdate(String creatorFocusCategory) {
        String normalized = cleanText(creatorFocusCategory);
        if (normalized == null || normalized.isBlank()) {
            return null;
        }
        if (normalized.length() > 60) {
            throw new ResponseStatusException(BAD_REQUEST, "\u4e3b\u6253\u65b9\u5411\u957f\u5ea6\u4e0d\u80fd\u8d85\u8fc7 60 \u4e2a\u5b57\u7b26\u3002");
        }
        return normalized;
    }

    private String normalizeAuthorKey(String author) {
        return author == null ? "" : author.replace(" ", "").replace("\u00A0", "").trim().toLowerCase(Locale.ROOT);
    }

    private String authorMatchExpression(String columnName) {
        return "LOWER(REPLACE(REPLACE(TRIM(" + columnName + "), ' ', ''), '\u00A0', ''))";
    }

    private void sanitizeCreatorBenchmark(CreatorBenchmarkVO row) {
        if (row == null) {
            return;
        }
        row.setAuthor(cleanText(row.getAuthor()));
        row.setMainCategory(cleanText(row.getMainCategory()));
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
                "\u6570\u636e\u89c4\u6a21",
                formatNumber(videoCount) + " \u6761\u89c6\u9891",
                "\u5f53\u524d\u8986\u76d6 " + sourcePlatformCount + " \u4e2a\u6765\u6e90\u5e73\u53f0\uff0c\u53ef\u7528\u4e8e\u8de8\u5e73\u53f0\u5bf9\u6bd4\u5206\u6790\u3002",
                "info"
        ));

        if (!benchmark.isEmpty()) {
            PlatformBenchmarkVO best = benchmark.stream()
                    .max(Comparator.comparingDouble(v -> safeDouble(v.getEngagementPerThousandPlay())))
                    .orElse(benchmark.get(0));
            cards.add(new InsightCardVO(
                    "\u4e92\u52a8\u6548\u7387\u6700\u4f73\u5e73\u53f0",
                    normalizePlatform(best.getSourcePlatform()),
                    "\u6bcf\u5343\u64ad\u653e\u4e92\u52a8\u503c\u4e3a " + formatDecimal(best.getEngagementPerThousandPlay()) + "\uff0c\u5efa\u8bae\u4f18\u5148\u5728\u8be5\u5e73\u53f0\u5e03\u5c40\u4e3b\u9635\u5730\u3002",
                    "good"
            ));
        }

        if (!funnel.isEmpty()) {
            FunnelStatVO topLike = funnel.stream()
                    .max(Comparator.comparingDouble(v -> safeDouble(v.getLikeConversionRate())))
                    .orElse(funnel.get(0));
            FunnelStatVO lowLike = funnel.stream()
                    .min(Comparator.comparingDouble(v -> safeDouble(v.getLikeConversionRate())))
                    .orElse(funnel.get(0));
            double maxLikeRate = safeDouble(topLike.getLikeConversionRate());
            double minLikeRate = safeDouble(lowLike.getLikeConversionRate());
            double gap = maxLikeRate - minLikeRate;
            cards.add(new InsightCardVO(
                    "\u5e73\u53f0\u70b9\u8d5e\u8f6c\u5316\u5dee\u5f02",
                    formatPercent(gap),
                    "\u6700\u9ad8\uff1a" + normalizePlatform(topLike.getSourcePlatform()) + "\uff08" + formatPercent(maxLikeRate) + "\uff09\uff0c"
                            + "\u6700\u4f4e\uff1a" + normalizePlatform(lowLike.getSourcePlatform()) + "\uff08" + formatPercent(minLikeRate) + "\uff09\u3002",
                    gap >= 0.03 ? "warn" : "info"
            ));
        }

        if (!traces.isEmpty()) {
            double avgQuality = traces.stream().mapToDouble(v -> safeDouble(v.getDataQualityScore())).average().orElse(0);
            long lowQualityCount = traces.stream().filter(v -> safeDouble(v.getDataQualityScore()) < 55).count();
            String level = avgQuality >= 75 ? "good" : (avgQuality >= 60 ? "info" : "warn");
            cards.add(new InsightCardVO(
                    "\u6570\u636e\u8d28\u91cf\u5065\u5eb7\u5ea6",
                    formatDecimal(avgQuality),
                    "\u4f4e\u8d28\u91cf\u8bb0\u5f55 " + lowQualityCount + " \u6761\u3002\u5efa\u8bae\u4f18\u5148\u8865\u5168\u4f5c\u8005\u3001\u6765\u6e90 URL \u548c\u4e92\u52a8\u5b57\u6bb5\u3002",
                    level
            ));
        }

        if (!hotTop1.isEmpty()) {
            HotVideoVO top = hotTop1.get(0);
            cards.add(new InsightCardVO(
                    "\u5f53\u524d\u64ad\u653e\u51a0\u519b",
                    trim(top.getTitle(), 24),
                    "\u6765\u6e90 " + normalizePlatform(top.getSourcePlatform()) + "\uff0c\u64ad\u653e\u91cf " + formatNumber(top.getPlayCount()) + "\u3002",
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
            return "行为样本仍偏少，建议继续积累观察数据后再下结论。";
        }
        return switch (profileLabel) {
            case "高互动讨论型" -> "互动率高，适合使用提问式结尾、热点争议点和评论区互动来放大讨论。";
            case "高频活跃型" -> "行为频次高，适合保持高频更新与系列化运营，强化持续触达。";
            case "稳定观看型" -> "播放与互动相对稳定，建议持续跟踪内容方向变化，逐步提升转化。";
            default -> "画像特征仍在形成中，建议继续补充数据后观察变化。";
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
            return "未知来源";
        }
        return switch (platform.toLowerCase(Locale.ROOT)) {
            case "bilibili" -> "哔哩哔哩";
            case "douyin" -> "抖音";
            case "kuaishou" -> "快手";
            case "xiaohongshu" -> "小红书";
            case "xigua" -> "西瓜视频";
            case "weibo" -> "微博";
            case "youtube" -> "YouTube";
            case "tiktok" -> "TikTok";
            case "acfun" -> "AcFun";
            case "unknown", "manual_text" -> "未知来源";
            default -> cleanText(platform);
        };
    }

    private String trim(String text, int maxLen) {
        if (text == null || text.isBlank()) {
            return "-";
        }
        return text.length() <= maxLen ? text : text.substring(0, maxLen) + "...";
    }

    private record CreatorProfile(
            Long userId,
            String username,
            String role,
            String creatorName,
            String creatorPlatform,
            String creatorFocusCategory
    ) {
    }

    private record CreatorOverview(
            long videoCount,
            long totalPlayCount,
            long totalLikeCount,
            long totalCommentCount,
            double avgPlayPerVideo,
            double engagementPerThousandPlay
    ) {
    }
}

