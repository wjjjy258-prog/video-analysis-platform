package com.video.analysis.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.video.analysis.auth.AuthContext;
import com.video.analysis.dto.crawler.CrawlRunResponse;
import com.video.analysis.service.ai.AiImportExtractor;
import com.video.analysis.service.CrawlerService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.HtmlUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class CrawlerServiceImpl implements CrawlerService {

    private static final Pattern JSON_BLOCK_PATTERN = Pattern.compile("(?s)```(?:json)?\\s*(\\{.*?}|\\[.*?])\\s*```");
    private static final Pattern TABLE_SPLIT_PATTERN = Pattern.compile("\\s*\\|\\s*");
    private static final Pattern TABLE_SEPARATOR_PATTERN = Pattern.compile("^\\s*\\|?\\s*[:\\-\\s|]+\\|?\\s*$");
    private static final Pattern KEY_VALUE_PATTERN = Pattern.compile("^[-*\\s]*([^:\uFF1A]{1,80})\\s*[:\uFF1A]\\s*(.+)$");
    private static final Pattern NUMBER_PATTERN = Pattern.compile("([0-9]+(?:\\.[0-9]+)?)\\s*([wWkK\u4e07\u4ebf]?)");
    private static final Pattern URL_PATTERN = Pattern.compile("https?://\\S+");
    private static final Pattern DOUYIN_ID_PATTERN = Pattern.compile("/video/(\\d{8,25})");
    private static final Pattern BILI_AV_PATTERN = Pattern.compile("/video/av(\\d+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern BILI_BV_PATTERN = Pattern.compile("(BV[0-9A-Za-z]{10})");
    private static final Pattern HTML_TAG_PATTERN = Pattern.compile("<[^>]+>");
    private static final Pattern MULTI_SPACE_PATTERN = Pattern.compile("\\s+");
    private static final int QUALITY_REJECT_THRESHOLD = 40;
    private static final int QUALITY_GOOD_THRESHOLD = 60;
    private static final int MAX_REJECT_RAW_LENGTH = 4000;
    private static final Set<String> SUPPORTED_URL_PLATFORMS = Set.of(
            "auto",
            "bilibili",
            "douyin",
            "kuaishou",
            "xiaohongshu",
            "xigua",
            "weibo",
            "youtube",
            "tiktok",
            "acfun"
    );

    @Value("${crawler.python-command:python}")
    private String pythonCommand;

    @Value("${crawler.script-path:../spider/spider.py}")
    private String spiderScriptPath;

    @Value("${crawler.max-output-lines:3000}")
    private int maxOutputLines;

    @Value("${crawler.import.video-batch-size:1000}")
    private int videoBatchSize;

    @Value("${crawler.import.user-batch-size:1000}")
    private int userBatchSize;

    @Value("${crawler.import.behavior-batch-size:5000}")
    private int behaviorBatchSize;

    @Value("${crawler.import.dedupe-query-batch-size:2000}")
    private int dedupeQueryBatchSize;

    @Value("${crawler.import.behavior-scale:1.0}")
    private double behaviorScale;

    @Value("${crawler.import.behavior-max-rows-per-import:300000}")
    private int behaviorMaxRowsPerImport;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final AiImportExtractor aiImportExtractor;

    public CrawlerServiceImpl(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper, AiImportExtractor aiImportExtractor) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.aiImportExtractor = aiImportExtractor;
    }

    @Override
    public CrawlRunResponse runUrlCrawl(String platform, List<String> urls, boolean confirmRisk) {
        if (!confirmRisk) {
            throw new IllegalArgumentException("URL crawl requires explicit risk confirmation.");
        }
        if (urls == null || urls.isEmpty()) {
            throw new IllegalArgumentException("Please provide at least one URL.");
        }

        String p = normalizePlatform(platform, "auto");
        if (!SUPPORTED_URL_PLATFORMS.contains(p)) {
            throw new IllegalArgumentException("platform濞寸姴鎳忛弫顕€骞?auto/bilibili/douyin/kuaishou/xiaohongshu/xigua/weibo/youtube/tiktok/acfun");
        }

        Path tempFile = null;
        try {
            tempFile = Files.createTempFile("video-crawl-urls-", ".txt");
            Files.write(tempFile, urls, StandardCharsets.UTF_8);
            return runProcess(List.of(
                    pythonCommand,
                    resolveScriptPath().toString(),
                    "--url-file", tempFile.toString(),
                    "--platform", p,
                    "--unsafe-allow-url-crawl"
            ));
        } catch (IOException ex) {
            throw new RuntimeException("闁圭瑳鍡╂斀URL闂佹彃娲▔锔藉緞鏉堫偉袝: " + ex.getMessage(), ex);
        } finally {
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException ignored) {
                }
            }
        }
    }

    @Override
    public CrawlRunResponse runMockCrawl() {
        return runProcess(List.of(pythonCommand, resolveScriptPath().toString(), "--mock-only"));
    }

    @Override
    public CrawlRunResponse clearTenantData(boolean confirm) {
        if (!confirm) {
            throw new IllegalArgumentException("Clearing data requires explicit confirmation.");
        }

        long tenantUserId = AuthContext.requireUserId();
        LocalDateTime start = LocalDateTime.now();

        int deletedBehavior = deleteByTenantInChunks("user_behavior", tenantUserId, 50_000);
        int deletedComment = jdbcTemplate.update("DELETE FROM comment WHERE tenant_user_id=?", tenantUserId);
        int deletedStat = jdbcTemplate.update("DELETE FROM video_statistics WHERE tenant_user_id=?", tenantUserId);
        int deletedInterest = jdbcTemplate.update("DELETE FROM user_interest_result WHERE tenant_user_id=?", tenantUserId);
        int deletedVideo = jdbcTemplate.update("DELETE FROM video WHERE tenant_user_id=?", tenantUserId);
        int deletedUser = jdbcTemplate.update("DELETE FROM `user` WHERE tenant_user_id=?", tenantUserId);
        int deletedReject = jdbcTemplate.update("DELETE FROM import_reject_record WHERE tenant_user_id=?", tenantUserId);
        int deletedImportJob = jdbcTemplate.update("DELETE FROM import_job WHERE tenant_user_id=?", tenantUserId);
        invalidateOverviewCache(tenantUserId);

        CrawlRunResponse response = new CrawlRunResponse();
        response.setStartedAt(start);
        response.setFinishedAt(LocalDateTime.now());
        response.setSuccess(true);
        response.setExitCode(0);
        response.setMessage("Tenant data cleared successfully.");
        response.setOutput(
                "deleted: video=" + deletedVideo +
                        ", user_behavior=" + deletedBehavior +
                        ", user=" + deletedUser +
                        ", comment=" + deletedComment +
                        ", video_statistics=" + deletedStat +
                        ", user_interest_result=" + deletedInterest +
                        ", import_job=" + deletedImportJob +
                        ", import_reject_record=" + deletedReject
        );
        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Map<String, Object>> listImportRejects(Integer limit) {
        long tenantUserId = AuthContext.requireUserId();
        int safeLimit = Math.max(10, Math.min(500, limit == null ? 50 : limit));
        return jdbcTemplate.query(
                "SELECT id, source_platform, source_file, reject_reason, suggest_fix, raw_excerpt, quality_score, ai_used, ai_confidence, import_time, import_job_id " +
                        "FROM import_reject_record " +
                        "WHERE tenant_user_id=? " +
                        "ORDER BY import_time DESC, id DESC " +
                        "LIMIT ?",
                (rs, rowNum) -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", rs.getLong("id"));
                    row.put("sourcePlatform", rs.getString("source_platform"));
                    row.put("sourceFile", rs.getString("source_file"));
                    row.put("rejectReason", rs.getString("reject_reason"));
                    row.put("suggestFix", rs.getString("suggest_fix"));
                    row.put("rawExcerpt", rs.getString("raw_excerpt"));
                    row.put("qualityScore", rs.getBigDecimal("quality_score"));
                    row.put("aiUsed", rs.getBoolean("ai_used"));
                    row.put("aiConfidence", rs.getBigDecimal("ai_confidence"));
                    row.put("importTime", rs.getTimestamp("import_time"));
                    row.put("importJobId", rs.getLong("import_job_id"));
                    return row;
                },
                tenantUserId,
                safeLimit
        );
    }

    @Override
    @Transactional
    public CrawlRunResponse importFromText(String text, String defaultPlatform, boolean aiAssist) {
        return importRowsFromText(text, defaultPlatform, "text_import", null, aiAssist);
    }

    @Override
    @Transactional
    public CrawlRunResponse importFromFile(String fileName, byte[] content, String defaultPlatform, boolean aiAssist) {
        if (content == null || content.length == 0) {
            throw new IllegalArgumentException("Uploaded file is empty.");
        }
        String name = fileName == null || fileName.isBlank() ? "upload.txt" : fileName;
        String lower = name.toLowerCase(Locale.ROOT);
        if (!(lower.endsWith(".md")
                || lower.endsWith(".markdown")
                || lower.endsWith(".txt")
                || lower.endsWith(".log")
                || lower.endsWith(".csv"))) {
            throw new IllegalArgumentException("濞寸姴鎳忛弫顕€骞?.md/.markdown/.txt/.log/.csv");
        }

        String text = decodeText(content);
        return importRowsFromText(text, defaultPlatform, "file_import", name, aiAssist);
    }

    private CrawlRunResponse importRowsFromText(String text, String defaultPlatform, String importType, String sourceFile, boolean aiAssist) {
        String raw = text == null ? "" : text.trim();
        if (raw.isEmpty()) {
            throw new IllegalArgumentException("Input text is empty.");
        }

        long tenantUserId = AuthContext.requireUserId();
        LocalDateTime start = LocalDateTime.now();
        String normalizedDefaultPlatform = normalizePlatform(defaultPlatform, "unknown");
        ParseContext parseContext = parseWithAiAssist(
                raw,
                normalizedDefaultPlatform,
                importType,
                sourceFile,
                tenantUserId,
                aiAssist
        );
        ParseResult parse = parseContext.parseResult();
        List<VideoRow> rows = parse.rows();
        if (rows.isEmpty()) {
            CrawlRunResponse resp = new CrawlRunResponse();
            resp.setStartedAt(start);
            resp.setFinishedAt(LocalDateTime.now());
            resp.setSuccess(false);
            resp.setExitCode(1);
            resp.setMessage("未解析到可入库的结构化记录。");
            String output = "请按以下方式检查并修改导入文件：" + System.lineSeparator()
                    + "1) CSV 使用英文逗号分隔，首行是表头，且至少包含 title（或 标题）列；" + System.lineSeparator()
                    + "2) Markdown 表格需包含标题列，建议字段：title/author/platform/play_count/like_count/comment_count/publish_time；" + System.lineSeparator()
                    + "3) JSON 使用对象或数组，至少包含 title（或 标题）字段；" + System.lineSeparator()
                    + "4) 文件编码建议 UTF-8（若乱码请另存为 UTF-8 后重试）；" + System.lineSeparator()
                    + "5) 纯文本建议勾选“AI 增强解析”后重试。";
            if (aiAssist) {
                output += System.lineSeparator()
                        + "AI 状态：aiAssist=true, aiUsed=" + parseContext.aiUsed()
                        + ", aiMessage=" + defaultIfBlank(parseContext.aiMessage(), "none");
            }
            resp.setOutput(output);
            return resp;
        }

        ImportResult result = saveRows(rows, parse.dedupedRecords(), tenantUserId);
        CrawlRunResponse resp = new CrawlRunResponse();
        resp.setStartedAt(start);
        resp.setFinishedAt(LocalDateTime.now());
        boolean success = result.acceptedRows() > 0;
        resp.setSuccess(success);
        resp.setExitCode(success ? 0 : 1);
        resp.setMessage(success ? "Import completed." : "No rows passed quality rules.");
        String output = "records=" + rows.size()
                + ", parseDedup=" + parse.dedupedRecords()
                + ", acceptedRows=" + result.acceptedRows()
                + ", lowQualityRows=" + result.lowQualityRows()
                + ", rejectedRows=" + result.rejectedRows()
                + ", newVideos=" + result.newVideoRows()
                + ", updatedVideos=" + result.updatedVideoRows()
                + ", users=" + result.userRows()
                + ", behaviors=" + result.behaviorRows()
                + ", jobId=" + result.importJobId();
        if (aiAssist) {
            output += ", aiUsed=" + parseContext.aiUsed()
                    + ", aiRecords=" + parseContext.aiRecords()
                    + ", aiMessage=" + defaultIfBlank(parseContext.aiMessage(), "none");
        }
        output += (sourceFile == null ? "" : ", file=" + sourceFile);
        resp.setOutput(output);
        return resp;
    }

    private String decodeText(byte[] content) {
        String utf8 = new String(content, StandardCharsets.UTF_8);
        if (!utf8.contains("\uFFFD")) {
            return utf8;
        }
        String gb18030 = new String(content, Charset.forName("GB18030"));
        int badUtf8 = countBadChar(utf8);
        int badGb = countBadChar(gb18030);
        return badGb < badUtf8 ? gb18030 : utf8;
    }

    private int deleteByTenantInChunks(String tableName, long tenantUserId, int chunkSize) {
        int safeChunk = Math.max(5_000, chunkSize);
        int total = 0;
        while (true) {
            int deleted = jdbcTemplate.update(
                    "DELETE FROM " + tableName + " WHERE tenant_user_id=? LIMIT " + safeChunk,
                    tenantUserId
            );
            total += deleted;
            if (deleted < safeChunk) {
                break;
            }
        }
        return total;
    }

    private void invalidateOverviewCache(long tenantUserId) {
        if (!tableExists("tenant_overview_cache")) {
            return;
        }
        jdbcTemplate.update("DELETE FROM tenant_overview_cache WHERE tenant_user_id=?", tenantUserId);
    }

    private boolean tableExists(String tableName) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ?",
                Integer.class,
                tableName
        );
        return count != null && count > 0;
    }

    private int countBadChar(String text) {
        int bad = 0;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\uFFFD') {
                bad++;
            }
        }
        return bad;
    }

    private Path resolveScriptPath() {
        Path configured = Paths.get(spiderScriptPath);
        if (configured.isAbsolute()) {
            return configured.normalize();
        }
        Path base = Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize();
        Path p1 = base.resolve(configured).normalize();
        if (Files.exists(p1)) {
            return p1;
        }
        Path p2 = base.resolve("spider").resolve("spider.py").normalize();
        if (Files.exists(p2)) {
            return p2;
        }
        return base.resolve("..").resolve("spider").resolve("spider.py").normalize();
    }

    private CrawlRunResponse runProcess(List<String> command) {
        CrawlRunResponse resp = new CrawlRunResponse();
        resp.setStartedAt(LocalDateTime.now());

        ProcessBuilder builder = new ProcessBuilder(command);
        Long tenantUserId = AuthContext.getUserId();
        if (tenantUserId != null && tenantUserId > 0) {
            builder.environment().put("TENANT_USER_ID", String.valueOf(tenantUserId));
        }
        Path script = resolveScriptPath();
        if (script.getParent() != null) {
            builder.directory(script.getParent().toFile());
        }
        builder.redirectErrorStream(true);

        StringBuilder out = new StringBuilder();
        int lineCount = 0;
        int exitCode;
        try {
            Process p = builder.start();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream(), Charset.defaultCharset()))) {
                String line;
                while ((line = br.readLine()) != null) {
                    if (lineCount < maxOutputLines) {
                        out.append(line).append(System.lineSeparator());
                    }
                    lineCount++;
                }
            }
            exitCode = p.waitFor();
        } catch (Exception ex) {
            throw new RuntimeException("Failed to execute crawler process: " + ex.getMessage(), ex);
        }

        resp.setFinishedAt(LocalDateTime.now());
        resp.setExitCode(exitCode);
        resp.setSuccess(exitCode == 0);
        resp.setMessage(exitCode == 0 ? "Crawl completed." : "Crawl failed.");
        resp.setOutput(out.toString());
        return resp;
    }

    private ParseResult parseRows(String text, String defaultPlatform, String importType, String sourceFile, long tenantUserId) {
        // Parse with multiple strategies and merge by dedupe key to maximize recall.
        Map<String, VideoRow> dedup = new LinkedHashMap<>();
        int[] dedupedRecords = new int[]{0};
        addRows(dedup, parseJson(text, defaultPlatform, importType, sourceFile, tenantUserId), dedupedRecords);
        addRows(dedup, parseTable(text, defaultPlatform, importType, sourceFile, tenantUserId), dedupedRecords);
        addRows(dedup, parseCsv(text, defaultPlatform, importType, sourceFile, tenantUserId), dedupedRecords);
        addRows(dedup, parseKeyValue(text, defaultPlatform, importType, sourceFile, tenantUserId), dedupedRecords);
        addRows(dedup, parseNarrative(text, defaultPlatform, importType, sourceFile, tenantUserId), dedupedRecords);
        return new ParseResult(new ArrayList<>(dedup.values()), dedupedRecords[0]);
    }

    private ParseContext parseWithAiAssist(
            String raw,
            String defaultPlatform,
            String importType,
            String sourceFile,
            long tenantUserId,
            boolean aiAssist
    ) {
        ParseResult ruleParse = parseRows(raw, defaultPlatform, importType, sourceFile, tenantUserId);
        // AI is only used when enabled and rule-based parsing quality is insufficient.
        if (!aiAssist || aiImportExtractor == null || !needsAiEnhancement(ruleParse.rows())) {
            return new ParseContext(ruleParse, false, 0, aiAssist ? "规则解析质量已足够，未触发 AI 增强。" : "");
        }

        AiImportExtractor.AiExtractResult aiResult = aiImportExtractor.extractRecords(raw, defaultPlatform);
        if (!aiResult.used()) {
            return new ParseContext(ruleParse, false, 0, aiResult.message());
        }
        if (!aiResult.success()) {
            return new ParseContext(ruleParse, true, 0, aiResult.message());
        }

        List<VideoRow> aiRows = toVideoRows(aiResult.records(), defaultPlatform, importType, sourceFile, tenantUserId);
        // Mark AI-produced rows for downstream audit and reject diagnostics.
        for (VideoRow aiRow : aiRows) {
            aiRow.aiUsed = true;
            if (aiRow.aiConfidence <= 0D) {
                aiRow.aiConfidence = 0.65D;
            }
            aiRow.dataQualityScore = computeQualityScore(aiRow);
        }
        if (aiRows.isEmpty()) {
            return new ParseContext(ruleParse, true, 0, "AI 返回记录为空或无法映射，已保留规则解析结果。");
        }

        ParseResult merged = mergeParseResult(ruleParse, aiRows);
        return new ParseContext(merged, true, aiRows.size(), aiResult.message());
    }

    private boolean needsAiEnhancement(List<VideoRow> rows) {
        if (rows == null || rows.isEmpty()) {
            return true;
        }
        int size = rows.size();
        long withMetric = rows.stream().filter(row -> row.playCount > 0 || row.likeCount > 0 || row.commentCount > 0).count();
        long withCategory = rows.stream().filter(row -> row.category != null && !row.category.isBlank() && !"other".equalsIgnoreCase(row.category)).count();
        double qualityRatio = Math.max(withMetric, withCategory) / (double) size;
        return qualityRatio < 0.45D;
    }

    private List<VideoRow> toVideoRows(
            List<Map<String, Object>> maps,
            String defaultPlatform,
            String importType,
            String sourceFile,
            long tenantUserId
    ) {
        if (maps == null || maps.isEmpty()) {
            return List.of();
        }
        List<VideoRow> rows = new ArrayList<>();
        for (Map<String, Object> map : maps) {
            VideoRow row = rowFromMap(map, defaultPlatform, importType, sourceFile, tenantUserId);
            if (row != null) {
                rows.add(row);
            }
        }
        return rows;
    }

    private ParseResult mergeParseResult(ParseResult base, List<VideoRow> extraRows) {
        Map<String, VideoRow> dedup = new LinkedHashMap<>();
        int[] deduped = new int[]{base == null ? 0 : base.dedupedRecords()};
        if (base != null && base.rows() != null) {
            addRows(dedup, base.rows(), deduped);
        }
        addRows(dedup, extraRows, deduped);
        return new ParseResult(new ArrayList<>(dedup.values()), deduped[0]);
    }

    private void addRows(Map<String, VideoRow> dedup, List<VideoRow> rows, int[] dedupedRecords) {
        for (VideoRow row : rows) {
            String key = defaultIfBlank(row.dedupeKey, "id:" + row.videoId);
            VideoRow existing = dedup.get(key);
            if (existing == null) {
                dedup.put(key, row);
                continue;
            }
            dedupedRecords[0] += 1;
            mergeVideoRows(existing, row);
        }
    }

    private void mergeVideoRows(VideoRow base, VideoRow incoming) {
        if (incoming == null || base == null) {
            return;
        }
        // Keep strongest metrics while preserving earlier publish time.
        base.playCount = Math.max(base.playCount, incoming.playCount);
        base.likeCount = Math.max(base.likeCount, incoming.likeCount);
        base.commentCount = Math.max(base.commentCount, incoming.commentCount);
        base.shareCount = Math.max(base.shareCount, incoming.shareCount);
        base.favoriteCount = Math.max(base.favoriteCount, incoming.favoriteCount);
        base.durationSec = Math.max(base.durationSec, incoming.durationSec);
        base.aiConfidence = Math.max(base.aiConfidence, incoming.aiConfidence);
        base.aiUsed = base.aiUsed || incoming.aiUsed;
        if (incoming.publishTime != null && (base.publishTime == null || incoming.publishTime.isBefore(base.publishTime))) {
            base.publishTime = incoming.publishTime;
        }
        if ((base.sourceUrl == null || base.sourceUrl.isBlank()) && incoming.sourceUrl != null && !incoming.sourceUrl.isBlank()) {
            base.sourceUrl = incoming.sourceUrl;
        }
        if ((base.platformVideoId == null || base.platformVideoId.isBlank())
                && incoming.platformVideoId != null
                && !incoming.platformVideoId.isBlank()) {
            base.platformVideoId = incoming.platformVideoId;
        }
        if ((base.sourceFile == null || base.sourceFile.isBlank()) && incoming.sourceFile != null && !incoming.sourceFile.isBlank()) {
            base.sourceFile = incoming.sourceFile;
        }
        if ((base.tagsJson == null || base.tagsJson.isBlank()) && incoming.tagsJson != null && !incoming.tagsJson.isBlank()) {
            base.tagsJson = incoming.tagsJson;
        }
        if ((base.extraJson == null || base.extraJson.isBlank()) && incoming.extraJson != null && !incoming.extraJson.isBlank()) {
            base.extraJson = incoming.extraJson;
        }
        if ((base.rawPayload == null || base.rawPayload.isBlank()) && incoming.rawPayload != null && !incoming.rawPayload.isBlank()) {
            base.rawPayload = incoming.rawPayload;
        }
        if (incoming.dataQualityScore > base.dataQualityScore) {
            base.dataQualityScore = incoming.dataQualityScore;
            if (incoming.category != null && !incoming.category.isBlank()) {
                base.category = incoming.category;
            }
            if (incoming.title != null && !incoming.title.isBlank()) {
                base.title = incoming.title;
            }
            if (incoming.author != null && !incoming.author.isBlank()) {
                base.author = incoming.author;
            }
        }
        base.dataQualityScore = computeQualityScore(base);
        base.qualityLevel = resolveQualityLevel(base.dataQualityScore);
    }

    private List<VideoRow> parseJson(String text, String defaultPlatform, String importType, String sourceFile, long tenantUserId) {
        List<VideoRow> rows = new ArrayList<>();
        Matcher matcher = JSON_BLOCK_PATTERN.matcher(text);
        while (matcher.find()) {
            rows.addAll(parseJsonCandidate(matcher.group(1), defaultPlatform, importType, sourceFile, tenantUserId));
        }
        String trimmed = text.trim();
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            rows.addAll(parseJsonCandidate(trimmed, defaultPlatform, importType, sourceFile, tenantUserId));
        }
        return rows;
    }

    private List<VideoRow> parseJsonCandidate(String candidate, String defaultPlatform, String importType, String sourceFile, long tenantUserId) {
        List<VideoRow> rows = new ArrayList<>();
        try {
            Object root = objectMapper.readValue(candidate, Object.class);
            List<Map<String, Object>> maps = new ArrayList<>();
            if (root instanceof Map<?, ?>) {
                Map<String, Object> map = objectMapper.convertValue(root, new TypeReference<>() {
                });
                Object videos = map.get("videos");
                Object data = map.get("data");
                Object list = map.get("list");
                if (videos instanceof List<?>) {
                    addMapList(maps, (List<?>) videos);
                } else if (data instanceof List<?>) {
                    addMapList(maps, (List<?>) data);
                } else if (list instanceof List<?>) {
                    addMapList(maps, (List<?>) list);
                } else {
                    maps.add(map);
                }
            } else if (root instanceof List<?>) {
                addMapList(maps, (List<?>) root);
            }

            for (Map<String, Object> map : maps) {
                VideoRow row = rowFromMap(map, defaultPlatform, importType, sourceFile, tenantUserId);
                if (row != null) {
                    rows.add(row);
                }
            }
        } catch (Exception ignored) {
        }
        return rows;
    }

    private void addMapList(List<Map<String, Object>> target, List<?> source) {
        for (Object item : source) {
            if (item instanceof Map<?, ?>) {
                target.add(objectMapper.convertValue(item, new TypeReference<>() {
                }));
            }
        }
    }

    private List<VideoRow> parseTable(String text, String defaultPlatform, String importType, String sourceFile, long tenantUserId) {
        List<VideoRow> rows = new ArrayList<>();
        String[] lines = text.split("\\r?\\n");
        for (int i = 0; i < lines.length - 1; i++) {
            if (!lines[i].contains("|")) {
                continue;
            }
            if (!TABLE_SEPARATOR_PATTERN.matcher(lines[i + 1]).matches()) {
                continue;
            }

            List<String> headers = splitTableLine(lines[i]);
            int rowIndex = i + 2;
            while (rowIndex < lines.length && lines[rowIndex].contains("|")) {
                if (TABLE_SEPARATOR_PATTERN.matcher(lines[rowIndex]).matches()) {
                    rowIndex++;
                    continue;
                }
                List<String> values = splitTableLine(lines[rowIndex]);
                Map<String, Object> map = new LinkedHashMap<>();
                for (int c = 0; c < Math.min(headers.size(), values.size()); c++) {
                    map.put(resolveHeaderKey(headers.get(c)), values.get(c));
                }
                VideoRow row = rowFromMap(map, defaultPlatform, importType, sourceFile, tenantUserId);
                if (row != null) {
                    rows.add(row);
                }
                rowIndex++;
            }
            i = rowIndex;
        }
        return rows;
    }

    private List<String> splitTableLine(String line) {
        String value = line.trim();
        if (value.startsWith("|")) {
            value = value.substring(1);
        }
        if (value.endsWith("|")) {
            value = value.substring(0, value.length() - 1);
        }
        if (value.isBlank()) {
            return List.of();
        }
        return TABLE_SPLIT_PATTERN.splitAsStream(value).map(String::trim).toList();
    }

    private List<VideoRow> parseCsv(String text, String defaultPlatform, String importType, String sourceFile, long tenantUserId) {
        List<VideoRow> rows = new ArrayList<>();
        List<List<String>> records = splitCsvRecords(text);
        if (records.size() < 2) {
            return rows;
        }

        int headerIndex = -1;
        List<String> headers = List.of();
        for (int i = 0; i < records.size(); i++) {
            List<String> row = records.get(i);
            if (row.size() < 2 || isBlankRow(row)) {
                continue;
            }
            if (isLikelyHeaderRow(row) || i == 0) {
                headers = normalizeHeaders(row);
                headerIndex = i;
                break;
            }
        }
        if (headerIndex < 0) {
            return rows;
        }

        for (int i = headerIndex + 1; i < records.size(); i++) {
            List<String> values = records.get(i);
            if (values.isEmpty() || isBlankRow(values)) {
                continue;
            }
            String firstCell = values.get(0) == null ? "" : values.get(0).trim();
            if (firstCell.startsWith("#")) {
                continue;
            }
            Map<String, Object> map = new LinkedHashMap<>();
            for (int c = 0; c < Math.min(headers.size(), values.size()); c++) {
                map.put(headers.get(c), values.get(c));
            }
            VideoRow row = rowFromMap(map, defaultPlatform, importType, sourceFile, tenantUserId);
            if (row != null) {
                rows.add(row);
            }
        }
        return rows;
    }

    private List<List<String>> splitCsvRecords(String text) {
        List<List<String>> records = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return records;
        }

        List<String> row = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        String source = stripBom(text);
        boolean inQuotes = false;

        for (int i = 0; i < source.length(); i++) {
            char ch = source.charAt(i);

            if (ch == '"') {
                if (inQuotes && i + 1 < source.length() && source.charAt(i + 1) == '"') {
                    cell.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
                continue;
            }

            if (ch == ',' && !inQuotes) {
                row.add(cell.toString().trim());
                cell.setLength(0);
                continue;
            }

            if ((ch == '\r' || ch == '\n') && !inQuotes) {
                if (ch == '\r' && i + 1 < source.length() && source.charAt(i + 1) == '\n') {
                    i++;
                }
                row.add(cell.toString().trim());
                cell.setLength(0);
                if (!isBlankRow(row)) {
                    records.add(row);
                }
                row = new ArrayList<>();
                continue;
            }

            if (ch == '\r' && inQuotes) {
                continue;
            }

            if (ch == '\n' && inQuotes) {
                cell.append('\n');
                continue;
            }

            cell.append(ch);
        }

        if (cell.length() > 0 || !row.isEmpty()) {
            row.add(cell.toString().trim());
            if (!isBlankRow(row)) {
                records.add(row);
            }
        }
        return records;
    }

    private List<String> normalizeHeaders(List<String> headers) {
        List<String> normalized = new ArrayList<>(headers.size());
        for (String header : headers) {
            normalized.add(resolveHeaderKey(header));
        }
        return normalized;
    }

    private String resolveHeaderKey(String rawHeader) {
        String canonical = canonicalFieldKey(rawHeader);
        if (canonical != null) {
            return canonical;
        }
        String sanitized = sanitizeText(rawHeader);
        return sanitized == null ? "" : sanitized;
    }

    private boolean isLikelyHeaderRow(List<String> row) {
        int aliasCount = 0;
        for (String cell : row) {
            if (canonicalFieldKey(cell) != null) {
                aliasCount++;
            }
        }
        return aliasCount >= 1;
    }

    private boolean isBlankRow(List<String> row) {
        for (String cell : row) {
            if (cell != null && !cell.trim().isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private String stripBom(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        return value.charAt(0) == '\uFEFF' ? value.substring(1) : value;
    }

    private List<VideoRow> parseKeyValue(String text, String defaultPlatform, String importType, String sourceFile, long tenantUserId) {
        List<VideoRow> rows = new ArrayList<>();
        String[] blocks = text.split("\\r?\\n\\s*\\r?\\n");
        for (String block : blocks) {
            Map<String, Object> map = new LinkedHashMap<>();
            for (String line : block.split("\\r?\\n")) {
                Matcher m = KEY_VALUE_PATTERN.matcher(line.trim());
                if (m.find()) {
                    map.put(m.group(1).trim(), m.group(2).trim());
                }
            }
            VideoRow row = rowFromMap(map, defaultPlatform, importType, sourceFile, tenantUserId);
            if (row != null) {
                rows.add(row);
            }
        }
        return rows;
    }

    private List<VideoRow> parseNarrative(String text, String defaultPlatform, String importType, String sourceFile, long tenantUserId) {
        List<VideoRow> rows = new ArrayList<>();
        String[] blocks = text.split("\\r?\\n\\s*\\r?\\n");
        for (String block : blocks) {
            Map<String, Object> map = new LinkedHashMap<>();
            putIfFound(map, "title", block, "(?im)(?:title|\\u6807\\u9898|\\u89c6\\u9891\\u6807\\u9898)\\s*[:\\uFF1A]\\s*(.+)$");
            putIfFound(map, "author", block, "(?im)(?:author|up|uploader|\\u4f5c\\u8005|up\\u4e3b|\\u535a\\u4e3b)\\s*[:\\uFF1A]\\s*(.+)$");
            putIfFound(map, "platform", block, "(?im)(?:platform|source[_\\s-]*platform|\\u5e73\\u53f0|\\u6765\\u6e90\\u5e73\\u53f0|\\u6765\\u6e90)\\s*[:\\uFF1A]\\s*(.+)$");
            putIfFound(map, "play_count", block, "(?im)(?:play(?:_count)?|views?|view[_\\s-]*count|\\u64ad\\u653e\\u91cf|\\u64ad\\u653e\\u6570|\\u89c2\\u770b\\u91cf|\\u6d4f\\u89c8\\u91cf)\\s*[:\\uFF1A]?\\s*([0-9.,kKwW\\u4e07\\u4ebf+]+)");
            putIfFound(map, "like_count", block, "(?im)(?:like(?:_count)?|likes?|thumbs[_\\s-]*up|\\u70b9\\u8d5e\\u91cf|\\u70b9\\u8d5e\\u6570|\\u8d5e)\\s*[:\\uFF1A]?\\s*([0-9.,kKwW\\u4e07\\u4ebf+]+)");
            putIfFound(map, "comment_count", block, "(?im)(?:comment(?:_count)?|comments?|reply(?:_count)?|danmaku(?:_count)?|\\u8bc4\\u8bba\\u91cf|\\u8bc4\\u8bba\\u6570|\\u56de\\u590d\\u91cf|\\u5f39\\u5e55\\u91cf)\\s*[:\\uFF1A]?\\s*([0-9.,kKwW\\u4e07\\u4ebf+]+)");
            putIfFound(map, "publish_time", block, "(?im)(?:publish(?:_time)?|publish[_\\s-]*date|date|time|\\u53d1\\u5e03\\u65f6\\u95f4|\\u53d1\\u5e03\\u65e5\\u671f|\\u65e5\\u671f)\\s*[:\\uFF1A]?\\s*([0-9T\\-/: ]+)");
            Matcher urlMatcher = URL_PATTERN.matcher(block);
            if (urlMatcher.find()) {
                map.put("url", urlMatcher.group());
            }
            VideoRow row = rowFromMap(map, defaultPlatform, importType, sourceFile, tenantUserId);
            if (row != null) {
                rows.add(row);
            }
        }
        return rows;
    }

    private void putIfFound(Map<String, Object> map, String key, String text, String regex) {
        try {
            Matcher m = Pattern.compile(regex).matcher(text);
            if (m.find()) {
                map.put(key, m.group(1).trim());
            }
        } catch (Exception ignored) {
            // Guard against malformed regex patterns to avoid failing the whole import.
        }
    }

    private VideoRow rowFromMap(Map<String, Object> map, String defaultPlatform, String importType, String sourceFile, long tenantUserId) {
        if (map == null || map.isEmpty()) {
            return null;
        }
        Map<String, Object> normalizedMap = normalizeInputMap(map);

        // Title is mandatory to avoid low-value noisy rows.
        String title = pick(
                normalizedMap,
                "title", "video_title", "name",
                "\u6807\u9898", "\u89c6\u9891\u6807\u9898", "\u540d\u79f0"
        );
        title = sanitizeText(title);
        if (title == null || title.isBlank()) {
            return null;
        }

        String author = pick(
                normalizedMap,
                "author", "up", "uploader", "up_name", "uname",
                "\u4f5c\u8005", "\u535a\u4e3b", "up\u4e3b", "\u4e0a\u4f20\u8005"
        );
        author = sanitizeText(author);
        if (author == null || author.isBlank()) {
            author = "unknown_author";
        }

        String platformRaw = pick(normalizedMap, "platform", "source_platform", "source", "\u5e73\u53f0", "\u6765\u6e90\u5e73\u53f0", "\u6765\u6e90");
        String platform = normalizePlatform(platformRaw, defaultPlatform);
        String url = pick(
                normalizedMap,
                "url", "link", "video_url", "href",
                "\u89c6\u9891\u94fe\u63a5", "\u94fe\u63a5", "\u5730\u5740", "\u89c6\u9891\u5730\u5740"
        );
        if (platformRaw == null || platformRaw.isBlank() || "unknown".equalsIgnoreCase(platform) || "auto".equalsIgnoreCase(platform)) {
            platform = inferPlatform(url, defaultIfBlank(platform, defaultPlatform));
        }

        String rawVideoId = pick(
                normalizedMap,
                "video_id", "id", "aid", "aweme_id", "bvid", "bv",
                "\u89c6\u9891id", "\u89c6\u9891\u7f16\u53f7"
        );
        if ((rawVideoId == null || rawVideoId.isBlank()) && url != null) {
            rawVideoId = extractIdFromUrl(url, platform);
        }

        String normalizedSourceUrl = normalizeSourceUrl(url);
        // dedupe_key is the single source of truth for idempotent import.
        String dedupeKey = buildDedupeKey(platform, normalizedSourceUrl, title, author);
        String videoSeed = defaultIfBlank(rawVideoId, dedupeKey);
        long videoId = hashLong("tenant:" + tenantUserId + ":video:" + videoSeed);
        String authorSeed = defaultIfBlank(
                pick(normalizedMap, "author_id", "uid", "mid", "user_id", "\u4f5c\u8005id", "\u7528\u6237id"),
                platform + ":" + author
        );
        long authorId = hashLong("tenant:" + tenantUserId + ":author:" + authorSeed);

        VideoRow row = new VideoRow();
        row.videoId = videoId;
        row.platformVideoId = trim(sanitizeText(rawVideoId), 128);
        row.dedupeKey = dedupeKey;
        row.title = trim(sanitizeText(title), 255);
        row.author = trim(sanitizeText(author), 100);
        row.sourcePlatform = trim(platform == null || platform.isBlank() ? defaultPlatform : platform, 32);
        row.sourceUrl = normalizedSourceUrl;

        String category = sanitizeText(defaultIfBlank(
                pick(normalizedMap, "category", "type", "tname", "\u5206\u533a", "\u5206\u7c7b", "\u7c7b\u522b", "\u6807\u7b7e", "\u9891\u9053"),
                "other"
        ));
        row.category = trim(normalizeCategory(defaultIfBlank(category, "other")), 60);

        row.playCount = parseCount(pick(
                normalizedMap,
                "play_count", "play", "view", "views", "view_count", "playcount",
                "\u64ad\u653e\u91cf", "\u64ad\u653e\u6570", "\u64ad\u653e", "\u89c2\u770b\u91cf", "\u89c2\u770b\u6570", "\u89c2\u770b", "\u6d4f\u89c8\u91cf"
        ));
        row.likeCount = parseCount(pick(
                normalizedMap,
                "like_count", "likes", "like", "up", "thumbs_up",
                "\u70b9\u8d5e\u91cf", "\u70b9\u8d5e\u6570", "\u70b9\u8d5e", "\u8d5e", "\u559c\u6b22\u91cf"
        ));
        row.commentCount = parseCount(pick(
                normalizedMap,
                "comment_count", "reply_count", "comments", "comment", "reply", "danmaku", "danmaku_count",
                "\u8bc4\u8bba\u91cf", "\u8bc4\u8bba\u6570", "\u8bc4\u8bba", "\u56de\u590d\u91cf", "\u56de\u590d", "\u5f39\u5e55\u91cf", "\u5f39\u5e55\u6570"
        ));
        row.shareCount = parseCount(pick(
                normalizedMap,
                "share_count", "shares", "share",
                "\u5206\u4eab\u91cf", "\u5206\u4eab\u6570", "\u8f6c\u53d1\u91cf", "\u8f6c\u53d1\u6570"
        ));
        row.favoriteCount = parseCount(pick(
                normalizedMap,
                "favorite_count", "favorites", "collect_count", "collect", "fav_count",
                "\u6536\u85cf\u91cf", "\u6536\u85cf\u6570", "\u6536\u85cf"
        ));
        row.durationSec = parseDurationSeconds(pick(
                normalizedMap,
                "duration", "duration_sec", "duration_seconds", "video_duration", "length",
                "\u65f6\u957f", "\u89c6\u9891\u65f6\u957f"
        ));
        row.publishTime = parseTime(pick(
                normalizedMap,
                "publish_time", "publishTime", "pubdate", "date", "time", "ctime",
                "\u53d1\u5e03\u65f6\u95f4", "\u53d1\u5e03\u65e5\u671f", "\u65e5\u671f", "\u65f6\u95f4"
        ));

        row.importType = trim(defaultIfBlank(importType, "manual_text"), 32);
        String normalizedSourceFile = sourceFile == null ? null : sanitizeText(sourceFile);
        row.sourceFile = normalizedSourceFile == null ? null : trim(normalizedSourceFile, 260);
        row.importTime = LocalDateTime.now();
        row.authorId = authorId;
        row.authorFans = parseCount(pick(normalizedMap, "author_fans", "fans", "\u7c89\u4e1d", "\u7c89\u4e1d\u91cf", "\u7c89\u4e1d\u6570"));
        row.authorFollow = parseCount(pick(normalizedMap, "author_follow", "follow", "following", "\u5173\u6ce8", "\u5173\u6ce8\u91cf", "\u5173\u6ce8\u6570"));
        long level = parseCount(pick(normalizedMap, "author_level", "level", "\u7b49\u7ea7"));
        row.authorLevel = (int) Math.max(1, Math.min(level <= 0 ? 1 : level, 10));
        row.tagsJson = normalizeTagsToJson(pick(
                normalizedMap,
                "tags", "tag", "keywords", "labels",
                "\u6807\u7b7e", "\u8bdd\u9898", "\u5173\u952e\u8bcd"
        ));
        row.extraJson = trimNullable(toJson(normalizedMap), 4000);
        row.rawPayload = trimNullable(toJson(normalizedMap), MAX_REJECT_RAW_LENGTH);
        row.aiConfidence = parseConfidence(pick(normalizedMap, "confidence", "ai_confidence", "\u7f6e\u4fe1\u5ea6"));
        row.aiUsed = false;
        row.dataQualityScore = computeQualityScore(row);
        row.qualityLevel = resolveQualityLevel(row.dataQualityScore);
        return row;
    }

    private String normalizeCategory(String rawCategory) {
        String value = defaultIfBlank(sanitizeText(rawCategory), "other").toLowerCase(Locale.ROOT);
        if (value.isBlank()) {
            return "other";
        }
        if (value.contains("\u6e38\u620f") || value.contains("game")) return "\u6e38\u620f";
        if (value.contains("\u97f3\u4e50") || value.contains("music") || value.contains("mv")) return "\u97f3\u4e50";
        if (value.contains("\u5f71\u89c6") || value.contains("\u7535\u5f71") || value.contains("\u5267") || value.contains("film") || value.contains("movie")) return "\u5f71\u89c6";
        if (value.contains("\u8d22\u7ecf") || value.contains("\u65b0\u95fb") || value.contains("\u65f6\u4e8b") || value.contains("news")) return "\u65b0\u95fb";
        if (value.contains("\u79d1\u6280") || value.contains("tech") || value.contains("it") || value.contains("ai")) return "\u79d1\u6280";
        if (value.contains("\u8fd0\u52a8") || value.contains("\u4f53\u80b2") || value.contains("sport")) return "\u4f53\u80b2";
        if (value.contains("\u751f\u6d3b") || value.contains("\u65c5\u884c") || value.contains("vlog") || value.contains("life")) return "\u751f\u6d3b";
        if (value.contains("\u77e5\u8bc6") || value.contains("\u6559\u80b2") || value.contains("\u6559\u5b66") || value.contains("edu")) return "\u77e5\u8bc6";
        return trim(rawCategory == null ? "other" : rawCategory, 60);
    }

    private String normalizeTagsToJson(String rawTags) {
        if (rawTags == null || rawTags.isBlank()) {
            return null;
        }
        String normalized = rawTags
                .replace('\u3001', ',')
                .replace("，", ",")
                .replace('|', ',')
                .replace('#', ' ')
                .trim();
        String[] parts = normalized.split(",");
        List<String> tags = new ArrayList<>();
        for (String part : parts) {
            String tag = sanitizeText(part);
            if (tag == null || tag.isBlank()) {
                continue;
            }
            if (tag.length() > 24) {
                tag = tag.substring(0, 24);
            }
            if (!tags.contains(tag)) {
                tags.add(tag);
            }
        }
        if (tags.isEmpty()) {
            return null;
        }
        return toJson(tags);
    }

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            return null;
        }
    }
    private String pick(Map<String, Object> normalizedMap, String... keys) {
        if (normalizedMap == null || normalizedMap.isEmpty()) {
            return null;
        }
        for (String key : keys) {
            Object value = normalizedMap.get(normKey(key));
            if (value != null) {
                String text = String.valueOf(value).trim();
                if (!text.isEmpty() && !"null".equalsIgnoreCase(text)) {
                    return text;
                }
            }
        }
        return null;
    }

    private Map<String, Object> normalizeInputMap(Map<String, Object> rawMap) {
        Map<String, Object> normalized = new HashMap<>();
        if (rawMap == null || rawMap.isEmpty()) {
            return normalized;
        }

        for (Map.Entry<String, Object> entry : rawMap.entrySet()) {
            String key = normKey(entry.getKey());
            if (key.isBlank()) {
                continue;
            }
            Object value = entry.getValue();
            normalized.putIfAbsent(key, value);

            String canonical = canonicalFieldKey(key);
            if (canonical != null) {
                normalized.putIfAbsent(normKey(canonical), value);
            }
        }
        return normalized;
    }

    private String canonicalFieldKey(String rawKey) {
        String key = normKey(rawKey);
        if (key.isBlank()) {
            return null;
        }
        if ("title".equals(key) || "videotitle".equals(key) || "name".equals(key)
                || "标题".equals(key) || "视频标题".equals(key) || "作品标题".equals(key) || "题目".equals(key)
                || key.contains("标题")) {
            return "title";
        }
        if ("author".equals(key) || "up".equals(key) || "uploader".equals(key) || "upname".equals(key) || "uname".equals(key)
                || "作者".equals(key) || "博主".equals(key) || "up主".equals(key) || "上传者".equals(key)) {
            return "author";
        }
        if ("platform".equals(key) || "sourceplatform".equals(key) || "source".equals(key)
                || "平台".equals(key) || "来源平台".equals(key) || "来源".equals(key)) {
            return "platform";
        }
        if ("category".equals(key) || "type".equals(key) || "tname".equals(key)
                || "分区".equals(key) || "分类".equals(key) || "类别".equals(key) || "频道".equals(key)) {
            return "category";
        }
        if ("videourl".equals(key) || "url".equals(key) || "link".equals(key) || "href".equals(key)
                || "链接".equals(key) || "视频链接".equals(key) || "地址".equals(key) || "来源链接".equals(key)) {
            return "url";
        }
        if ("videoid".equals(key) || "id".equals(key) || "aid".equals(key) || "awemeid".equals(key)
                || "bvid".equals(key) || "bv".equals(key) || "视频id".equals(key) || "视频编号".equals(key)) {
            return "video_id";
        }
        if ("playcount".equals(key) || "view".equals(key) || "views".equals(key) || "viewcount".equals(key)
                || "播放量".equals(key) || "播放数".equals(key) || "观看量".equals(key) || "浏览量".equals(key)
                || key.contains("播放")) {
            return "play_count";
        }
        if ("likecount".equals(key) || "likes".equals(key) || "like".equals(key) || "thumbsup".equals(key)
                || "点赞量".equals(key) || "点赞数".equals(key) || "点赞".equals(key) || "赞".equals(key)) {
            return "like_count";
        }
        if ("commentcount".equals(key) || "comments".equals(key) || "comment".equals(key) || "replycount".equals(key)
                || "danmakucount".equals(key) || "评论量".equals(key) || "评论数".equals(key) || "评论".equals(key)
                || "弹幕量".equals(key) || "弹幕数".equals(key) || key.contains("评论") || key.contains("弹幕")) {
            return "comment_count";
        }
        if ("sharecount".equals(key) || "shares".equals(key) || "share".equals(key)
                || "分享量".equals(key) || "分享数".equals(key) || "转发量".equals(key) || "转发数".equals(key)) {
            return "share_count";
        }
        if ("favoritecount".equals(key) || "favorites".equals(key) || "collectcount".equals(key) || "favcount".equals(key)
                || "收藏量".equals(key) || "收藏数".equals(key) || "收藏".equals(key)) {
            return "favorite_count";
        }
        if ("publishtime".equals(key) || "publishdate".equals(key) || "pubdate".equals(key) || "date".equals(key)
                || "time".equals(key) || "发布时间".equals(key) || "发布日期".equals(key) || "日期".equals(key)) {
            return "publish_time";
        }
        if ("tags".equals(key) || "tag".equals(key) || "keywords".equals(key) || "labels".equals(key)
                || "标签".equals(key) || "话题".equals(key) || "关键词".equals(key)) {
            return "tags";
        }
        return null;
    }

    private String normKey(String key) {
        if (key == null) {
            return "";
        }
        String value = key.trim().toLowerCase(Locale.ROOT);
        if (value.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (Character.isLetterOrDigit(ch) || (ch >= '\u4e00' && ch <= '\u9fff')) {
                sb.append(ch);
            }
        }
        return sb.toString();
    }

    private String normalizePlatform(String platform, String fallback) {
        String p = platform == null ? "" : platform.trim().toLowerCase(Locale.ROOT);
        if (p.isEmpty() || "auto".equals(p)) {
            return fallback;
        }
        if (p.contains("bilibili") || p.contains("b23") || p.contains("\u54d4\u54e9\u54d4\u54e9")) {
            return "bilibili";
        }
        if (p.contains("douyin") || p.contains("\u6296\u97f3") || p.contains("iesdouyin")) {
            return "douyin";
        }
        if (p.contains("kuaishou") || p.contains("\u5feb\u624b") || p.contains("kwai")) {
            return "kuaishou";
        }
        if (p.contains("xiaohongshu") || p.contains("xhs") || p.contains("rednote") || p.contains("\u5c0f\u7ea2\u4e66")) {
            return "xiaohongshu";
        }
        if (p.contains("xigua") || p.contains("ixigua") || p.contains("\u897f\u74dc")) {
            return "xigua";
        }
        if (p.contains("weibo") || p.contains("\u5fae\u535a")) {
            return "weibo";
        }
        if (p.contains("youtube") || p.contains("youtu")) {
            return "youtube";
        }
        if (p.contains("tiktok")) {
            return "tiktok";
        }
        if (p.contains("acfun") || p.contains("a\u7ad9")) {
            return "acfun";
        }
        return p.replaceAll("[^a-z0-9_\\-]", "_");
    }

    private String inferPlatform(String url, String fallback) {
        if (url == null || url.isBlank()) {
            return fallback;
        }
        String u = url.toLowerCase(Locale.ROOT);
        if (u.contains("bilibili.com") || u.contains("b23.tv")) {
            return "bilibili";
        }
        if (u.contains("douyin.com") || u.contains("iesdouyin.com")) {
            return "douyin";
        }
        if (u.contains("kuaishou.com") || u.contains("kwai.com")) {
            return "kuaishou";
        }
        if (u.contains("xiaohongshu.com") || u.contains("xhslink.com")) {
            return "xiaohongshu";
        }
        if (u.contains("ixigua.com") || u.contains("xigua.com")) {
            return "xigua";
        }
        if (u.contains("weibo.com")) {
            return "weibo";
        }
        if (u.contains("youtube.com") || u.contains("youtu.be")) {
            return "youtube";
        }
        if (u.contains("tiktok.com")) {
            return "tiktok";
        }
        if (u.contains("acfun.cn")) {
            return "acfun";
        }
        return fallback;
    }

    private String extractIdFromUrl(String url, String platform) {
        if (url == null || url.isBlank()) {
            return null;
        }
        if ("bilibili".equals(platform)) {
            Matcher avMatcher = BILI_AV_PATTERN.matcher(url);
            if (avMatcher.find()) {
                return avMatcher.group(1);
            }
            Matcher bvMatcher = BILI_BV_PATTERN.matcher(url);
            if (bvMatcher.find()) {
                return bvMatcher.group(1);
            }
        }
        if ("douyin".equals(platform)) {
            Matcher douyinMatcher = DOUYIN_ID_PATTERN.matcher(url);
            if (douyinMatcher.find()) {
                return douyinMatcher.group(1);
            }
        }
        return null;
    }
    private long parseCount(String raw) {
        if (raw == null || raw.isBlank()) {
            return 0L;
        }

        String text = raw.trim().toLowerCase(Locale.ROOT);
        text = text
                .replace(",", "")
                .replace("\u6b21", "")
                .replace("\u64ad\u653e", "")
                .replace("\u89c2\u770b", "")
                .replace("\u70b9\u8d5e", "")
                .replace("\u8bc4\u8bba", "")
                .replace("\u56de\u590d", "")
                .trim();

        if (text.isEmpty() || "--".equals(text) || "nan".equals(text) || "null".equals(text)) {
            return 0L;
        }

        double multiplier = 1D;
        if (text.contains("\u4ebf")) {
            multiplier = 100_000_000D;
            text = text.replace("\u4ebf", "");
        } else if (text.contains("\u4e07")) {
            multiplier = 10_000D;
            text = text.replace("\u4e07", "");
        } else if (text.endsWith("w")) {
            multiplier = 10_000D;
            text = text.substring(0, text.length() - 1);
        } else if (text.endsWith("k")) {
            multiplier = 1_000D;
            text = text.substring(0, text.length() - 1);
        } else if (text.endsWith("m")) {
            multiplier = 1_000_000D;
            text = text.substring(0, text.length() - 1);
        } else if (text.endsWith("b")) {
            multiplier = 1_000_000_000D;
            text = text.substring(0, text.length() - 1);
        }

        Matcher matcher = Pattern.compile("([0-9]+(?:\\.[0-9]+)?)").matcher(text);
        if (!matcher.find()) {
            return 0L;
        }

        double base;
        try {
            base = Double.parseDouble(matcher.group(1));
        } catch (NumberFormatException ex) {
            return 0L;
        }

        return (long) Math.max(0D, Math.round(base * multiplier));
    }

    private long parseDurationSeconds(String raw) {
        if (raw == null || raw.isBlank()) {
            return 0L;
        }
        String text = raw.trim().toLowerCase(Locale.ROOT);
        if (text.matches("\\d+")) {
            try {
                return Math.max(0L, Long.parseLong(text));
            } catch (NumberFormatException ignored) {
            }
        }
        Matcher hms = Pattern.compile("(?:(\\d+)\\s*[:h时])?\\s*(\\d{1,2})\\s*[:m分]\\s*(\\d{1,2})\\s*(?:s|秒)?").matcher(text);
        if (hms.find()) {
            long h = safeParseLong(hms.group(1));
            long m = safeParseLong(hms.group(2));
            long s = safeParseLong(hms.group(3));
            return Math.max(0L, h * 3600 + m * 60 + s);
        }
        Matcher ms = Pattern.compile("(\\d{1,3})\\s*[:：]\\s*(\\d{1,2})").matcher(text);
        if (ms.find()) {
            long m = safeParseLong(ms.group(1));
            long s = safeParseLong(ms.group(2));
            return Math.max(0L, m * 60 + s);
        }
        Matcher sec = Pattern.compile("([0-9]+(?:\\.[0-9]+)?)").matcher(text);
        if (sec.find()) {
            try {
                double value = Double.parseDouble(sec.group(1));
                if (text.contains("小时") || text.contains("h")) {
                    return Math.max(0L, Math.round(value * 3600));
                }
                if (text.contains("分钟") || text.contains("分") || text.contains("m")) {
                    return Math.max(0L, Math.round(value * 60));
                }
                return Math.max(0L, Math.round(value));
            } catch (NumberFormatException ignored) {
            }
        }
        return 0L;
    }

    private double parseConfidence(String raw) {
        if (raw == null || raw.isBlank()) {
            return 0D;
        }
        String text = raw.trim().toLowerCase(Locale.ROOT).replace("%", "");
        Matcher matcher = Pattern.compile("([0-9]+(?:\\.[0-9]+)?)").matcher(text);
        if (!matcher.find()) {
            return 0D;
        }
        double value;
        try {
            value = Double.parseDouble(matcher.group(1));
        } catch (NumberFormatException ex) {
            return 0D;
        }
        if (value > 1D) {
            value = value / 100D;
        }
        if (value < 0D) {
            return 0D;
        }
        if (value > 1D) {
            return 1D;
        }
        return value;
    }

    private long safeParseLong(String raw) {
        if (raw == null || raw.isBlank()) {
            return 0L;
        }
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException ex) {
            return 0L;
        }
    }

    private String resolveQualityLevel(double score) {
        if (score < QUALITY_REJECT_THRESHOLD) {
            return "REJECTED";
        }
        if (score < QUALITY_GOOD_THRESHOLD) {
            return "LOW";
        }
        return "GOOD";
    }

    private LocalDateTime parseTime(String raw) {
        if (raw == null || raw.isBlank()) {
            return LocalDateTime.now();
        }
        String text = raw.trim();
        if (text.matches("\\d{10}")) {
            return LocalDateTime.ofInstant(Instant.ofEpochSecond(Long.parseLong(text)), ZoneId.systemDefault());
        }
        if (text.matches("\\d{13}")) {
            return LocalDateTime.ofInstant(Instant.ofEpochMilli(Long.parseLong(text)), ZoneId.systemDefault());
        }
        for (String pattern : List.of("yyyy-MM-dd HH:mm:ss", "yyyy/MM/dd HH:mm:ss", "yyyy-MM-dd HH:mm", "yyyy/MM/dd HH:mm")) {
            try {
                return LocalDateTime.parse(text, DateTimeFormatter.ofPattern(pattern));
            } catch (Exception ignored) {
            }
        }
        for (String pattern : List.of("yyyy-MM-dd", "yyyy/MM/dd")) {
            try {
                return LocalDate.parse(text, DateTimeFormatter.ofPattern(pattern)).atStartOfDay();
            } catch (Exception ignored) {
            }
        }
        try {
            return LocalDateTime.parse(text, DateTimeFormatter.ISO_DATE_TIME);
        } catch (Exception ignored) {
        }
        return LocalDateTime.now();
    }

    private long idOrHash(String raw, String seed, String namespace) {
        if (raw != null && raw.trim().matches("\\d+")) {
            try {
                return Long.parseLong(raw.trim());
            } catch (Exception ignored) {
            }
        }
        return hashLong(namespace + ":" + seed);
    }

    private long hashLong(String text) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-1").digest(text.getBytes(StandardCharsets.UTF_8));
            long value = 0L;
            for (int i = 0; i < 8; i++) {
                value = (value << 8) | (digest[i] & 0xffL);
            }
            value = Math.abs(value);
            return value == 0 ? 1L : value;
        } catch (Exception ex) {
            long value = Math.abs(text.hashCode());
            return value == 0 ? 1L : value;
        }
    }

    private String normalizeSourceUrl(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        String cleaned = url.trim();
        int queryPos = cleaned.indexOf('?');
        if (queryPos > 0) {
            cleaned = cleaned.substring(0, queryPos);
        }
        int hashPos = cleaned.indexOf('#');
        if (hashPos > 0) {
            cleaned = cleaned.substring(0, hashPos);
        }
        cleaned = cleaned.replaceAll("/+$", "");
        return trim(cleaned, 1000);
    }

    private String buildDedupeKey(String platform, String sourceUrl, String title, String author) {
        String normalizedPlatform = defaultIfBlank(platform, "unknown").toLowerCase(Locale.ROOT);
        String normalizedTitle = defaultIfBlank(title, "").trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
        String normalizedAuthor = defaultIfBlank(author, "").trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
        String normalizedUrl = sourceUrl == null ? "" : sourceUrl.toLowerCase(Locale.ROOT);
        String seed = normalizedUrl.isBlank()
                ? normalizedPlatform + "|" + normalizedTitle + "|" + normalizedAuthor
                : normalizedPlatform + "|" + normalizedUrl;
        return sha1Hex(seed);
    }

    private String sha1Hex(String seed) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-1").digest(seed.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception ex) {
            return String.valueOf(Math.abs(seed.hashCode()));
        }
    }

    private double computeQualityScore(VideoRow row) {
        double score = 0;
        if (row.title != null && !row.title.isBlank()) score += 24;
        if (row.author != null && !row.author.isBlank() && !"unknown_author".equalsIgnoreCase(row.author)) score += 14;
        if (row.sourcePlatform != null && !row.sourcePlatform.isBlank()) score += 10;
        if (row.category != null && !row.category.isBlank() && !"other".equalsIgnoreCase(row.category)) score += 10;
        if (row.playCount > 0) score += 18;
        if (row.likeCount >= 0) score += 8;
        if (row.commentCount >= 0) score += 8;
        if (row.shareCount > 0 || row.favoriteCount > 0) score += 4;
        if (row.durationSec > 0) score += 2;
        if (row.tagsJson != null && !row.tagsJson.isBlank()) score += 2;
        if (row.platformVideoId != null && !row.platformVideoId.isBlank()) score += 2;
        if (row.publishTime != null) score += 6;
        if (row.sourceUrl != null && !row.sourceUrl.isBlank()) score += 8;
        if (row.aiConfidence > 0D) score += Math.min(4D, row.aiConfidence * 4D);
        if (row.likeCount > row.playCount && row.playCount > 0) score -= 10;
        if (row.commentCount > row.playCount && row.playCount > 0) score -= 8;
        if (row.shareCount > row.playCount && row.playCount > 0) score -= 6;
        if (row.favoriteCount > row.playCount && row.playCount > 0) score -= 6;
        if (row.title != null && row.title.length() < 2) score -= 8;
        score = Math.max(0, Math.min(100, score));
        return Math.round(score * 100.0) / 100.0;
    }

    private ImportResult saveRows(List<VideoRow> rows, int parseDeduped, long tenantUserId) {
        if (rows.isEmpty()) {
            return new ImportResult(0, 0, 0, 0, 0, 0, 0, 0, parseDeduped, 0);
        }

        String importType = defaultIfBlank(rows.get(0).importType, "manual_text");
        String sourceFile = rows.get(0).sourceFile;
        Set<String> platforms = rows.stream()
                .map(row -> defaultIfBlank(row.sourcePlatform, "unknown"))
                .collect(java.util.stream.Collectors.toSet());
        String sourcePlatform = platforms.size() == 1 ? platforms.iterator().next() : "mixed";

        long importJobId = createImportJob(importType, sourcePlatform, sourceFile, rows.size(), tenantUserId);
        LocalDateTime now = LocalDateTime.now();

        List<VideoRow> acceptedRows = new ArrayList<>();
        List<VideoRow> rejectedRows = new ArrayList<>();
        // Apply quality gate before touching main business tables.
        for (VideoRow row : rows) {
            row.importJobId = importJobId;
            row.importTime = now;
            row.dataQualityScore = computeQualityScore(row);
            row.qualityLevel = resolveQualityLevel(row.dataQualityScore);
            if ("REJECTED".equals(row.qualityLevel)) {
                row.rejectReason = buildRejectReason(row);
                row.rejectSuggestion = buildRejectSuggestion(row);
                rejectedRows.add(row);
            } else {
                acceptedRows.add(row);
            }
        }

        int lowQualityRows = (int) acceptedRows.stream()
                .filter(row -> "LOW".equals(row.qualityLevel))
                .count();

        Map<String, Long> existingByDedupe = queryExistingVideoIdByDedupe(acceptedRows, tenantUserId);
        int updatedVideoRows = 0;
        for (VideoRow row : acceptedRows) {
            Long existingId = existingByDedupe.get(row.dedupeKey);
            if (existingId != null) {
                row.existingBefore = true;
                row.videoId = existingId;
                updatedVideoRows++;
            }
        }
        int newVideoRows = Math.max(0, acceptedRows.size() - updatedVideoRows);

        int videoCount = 0;
        if (!acceptedRows.isEmpty()) {
            // Upsert keeps import idempotent and allows incremental enrichment.
            String videoSql = "INSERT INTO video " +
                    "(tenant_user_id,id,dedupe_key,platform_video_id,title,author,source_platform,source_url,category,play_count,like_count,comment_count,share_count,favorite_count,duration_sec,publish_time," +
                    "import_type,source_file,import_time,data_quality_score,ai_confidence,tags_json,extra_json,quality_level,import_job_id) " +
                    "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?) " +
                    "ON DUPLICATE KEY UPDATE " +
                    "platform_video_id=COALESCE(NULLIF(VALUES(platform_video_id),''), platform_video_id)," +
                    "title=VALUES(title)," +
                    "author=VALUES(author)," +
                    "source_platform=VALUES(source_platform)," +
                    "source_url=COALESCE(NULLIF(VALUES(source_url),''), source_url)," +
                    "category=VALUES(category)," +
                    "play_count=GREATEST(play_count, VALUES(play_count))," +
                    "like_count=GREATEST(like_count, VALUES(like_count))," +
                    "comment_count=GREATEST(comment_count, VALUES(comment_count))," +
                    "share_count=GREATEST(share_count, VALUES(share_count))," +
                    "favorite_count=GREATEST(favorite_count, VALUES(favorite_count))," +
                    "duration_sec=GREATEST(duration_sec, VALUES(duration_sec))," +
                    "publish_time=LEAST(publish_time, VALUES(publish_time))," +
                    "import_type=VALUES(import_type)," +
                    "source_file=COALESCE(NULLIF(VALUES(source_file),''), source_file)," +
                    "import_time=VALUES(import_time)," +
                    "data_quality_score=GREATEST(data_quality_score, VALUES(data_quality_score))," +
                    "ai_confidence=GREATEST(ai_confidence, VALUES(ai_confidence))," +
                    "tags_json=COALESCE(NULLIF(VALUES(tags_json),''), tags_json)," +
                    "extra_json=COALESCE(NULLIF(VALUES(extra_json),''), extra_json)," +
                    "quality_level=CASE WHEN VALUES(data_quality_score) >= data_quality_score THEN VALUES(quality_level) ELSE quality_level END," +
                    "import_job_id=VALUES(import_job_id)";

            videoCount = jdbcTemplate.batchUpdate(videoSql, acceptedRows, safeBatchSize(videoBatchSize, 1000), (ps, row) -> {
                ps.setLong(1, tenantUserId);
                ps.setLong(2, row.videoId);
                ps.setString(3, row.dedupeKey);
                ps.setString(4, trimNullable(row.platformVideoId, 128));
                ps.setString(5, row.title);
                ps.setString(6, row.author);
                ps.setString(7, row.sourcePlatform);
                ps.setString(8, trimNullable(row.sourceUrl, 1000));
                ps.setString(9, row.category);
                ps.setLong(10, row.playCount);
                ps.setLong(11, row.likeCount);
                ps.setLong(12, row.commentCount);
                ps.setLong(13, row.shareCount);
                ps.setLong(14, row.favoriteCount);
                ps.setLong(15, row.durationSec);
                ps.setTimestamp(16, Timestamp.valueOf(row.publishTime));
                ps.setString(17, row.importType);
                ps.setString(18, trimNullable(row.sourceFile, 260));
                ps.setTimestamp(19, Timestamp.valueOf(row.importTime));
                ps.setBigDecimal(20, BigDecimal.valueOf(row.dataQualityScore));
                ps.setBigDecimal(21, BigDecimal.valueOf(row.aiConfidence));
                ps.setString(22, trimNullable(row.tagsJson, 1200));
                ps.setString(23, trimNullable(row.extraJson, 4000));
                ps.setString(24, row.qualityLevel);
                ps.setLong(25, row.importJobId);
            }).length;
        }

        Map<Long, VideoRow> authorMap = new LinkedHashMap<>();
        for (VideoRow row : acceptedRows) {
            authorMap.put(row.authorId, row);
        }
        List<VideoRow> uniqueAuthors = new ArrayList<>(authorMap.values());

        int userCount = 0;
        if (!uniqueAuthors.isEmpty()) {
            String userSql = "INSERT INTO `user` (tenant_user_id,user_id,user_name,fans,`follow`,`level`) VALUES (?,?,?,?,?,?) "
                    + "ON DUPLICATE KEY UPDATE user_name=VALUES(user_name),fans=GREATEST(fans, VALUES(fans)),`follow`=GREATEST(`follow`, VALUES(`follow`)),`level`=GREATEST(`level`, VALUES(`level`))";

            userCount = jdbcTemplate.batchUpdate(userSql, uniqueAuthors, safeBatchSize(userBatchSize, 1000), (ps, row) -> {
                ps.setLong(1, tenantUserId);
                ps.setLong(2, row.authorId);
                ps.setString(3, row.author);
                ps.setLong(4, row.authorFans);
                ps.setLong(5, row.authorFollow);
                ps.setInt(6, row.authorLevel);
            }).length;
        }

        int behaviorCount = insertBehaviorRows(acceptedRows, tenantUserId);
        int rejectCount = insertRejectRows(rejectedRows, tenantUserId, importJobId);

        finishImportJob(
                importJobId,
                acceptedRows.size(),
                "SUCCESS",
                "accepted=" + acceptedRows.size()
                        + ",low=" + lowQualityRows
                        + ",rejected=" + rejectCount
                        + ",new=" + newVideoRows
                        + ",updated=" + updatedVideoRows
        );
        invalidateOverviewCache(tenantUserId);

        return new ImportResult(
                acceptedRows.size(),
                lowQualityRows,
                rejectCount,
                videoCount,
                newVideoRows,
                updatedVideoRows,
                userCount,
                behaviorCount,
                parseDeduped + updatedVideoRows,
                importJobId
        );
    }

    private int insertRejectRows(List<VideoRow> rejectedRows, long tenantUserId, long importJobId) {
        if (rejectedRows == null || rejectedRows.isEmpty()) {
            return 0;
        }
        // Reject rows are persisted for user feedback and iterative data fixing.
        String rejectSql = "INSERT INTO import_reject_record " +
                "(tenant_user_id,import_job_id,source_platform,source_file,reject_reason,suggest_fix,raw_excerpt,quality_score,ai_used,ai_confidence,import_time) " +
                "VALUES (?,?,?,?,?,?,?,?,?,?,?)";

        return jdbcTemplate.batchUpdate(rejectSql, rejectedRows, safeBatchSize(500, 500), (ps, row) -> {
            ps.setLong(1, tenantUserId);
            ps.setLong(2, importJobId);
            ps.setString(3, trim(defaultIfBlank(row.sourcePlatform, "unknown"), 32));
            ps.setString(4, trimNullable(row.sourceFile, 260));
            ps.setString(5, trim(defaultIfBlank(row.rejectReason, "质量分过低"), 300));
            ps.setString(6, trim(defaultIfBlank(row.rejectSuggestion, "请补齐关键字段后重试"), 300));
            ps.setString(7, trim(defaultIfBlank(buildRejectRawExcerpt(row), "{}"), MAX_REJECT_RAW_LENGTH));
            ps.setBigDecimal(8, BigDecimal.valueOf(row.dataQualityScore));
            ps.setBoolean(9, row.aiUsed);
            ps.setBigDecimal(10, BigDecimal.valueOf(row.aiConfidence));
            ps.setTimestamp(11, Timestamp.valueOf(defaultIfNull(row.importTime, LocalDateTime.now())));
        }).length;
    }

    private String buildRejectReason(VideoRow row) {
        List<String> reasons = new ArrayList<>();
        if (row.title == null || row.title.isBlank()) {
            reasons.add("标题缺失");
        }
        if (row.author == null || row.author.isBlank() || "unknown_author".equalsIgnoreCase(row.author)) {
            reasons.add("作者缺失");
        }
        if (row.playCount <= 0 && row.likeCount <= 0 && row.commentCount <= 0) {
            reasons.add("互动指标缺失");
        }
        if (row.sourcePlatform == null || row.sourcePlatform.isBlank() || "unknown".equalsIgnoreCase(row.sourcePlatform)) {
            reasons.add("平台未识别");
        }
        if (row.dataQualityScore < QUALITY_REJECT_THRESHOLD) {
            reasons.add("质量分<" + QUALITY_REJECT_THRESHOLD);
        }
        if (reasons.isEmpty()) {
            return "质量分过低";
        }
        return String.join("；", reasons);
    }

    private String buildRejectSuggestion(VideoRow row) {
        List<String> tips = new ArrayList<>();
        if (row.sourcePlatform == null || row.sourcePlatform.isBlank() || "unknown".equalsIgnoreCase(row.sourcePlatform)) {
            tips.add("选择正确平台或在文本中增加平台字段");
        }
        if (row.playCount <= 0 && row.likeCount <= 0 && row.commentCount <= 0) {
            tips.add("补充播放/点赞/评论任一指标");
        }
        if (row.category == null || row.category.isBlank() || "other".equalsIgnoreCase(row.category)) {
            tips.add("补充分类字段（如 游戏/音乐/科技）");
        }
        if (row.sourceUrl == null || row.sourceUrl.isBlank()) {
            tips.add("补充来源 URL 或平台视频 ID");
        }
        if (tips.isEmpty()) {
            tips.add("检查字段映射并开启 AI 增强解析后重试");
        }
        return String.join("；", tips);
    }

    private String buildRejectRawExcerpt(VideoRow row) {
        if (row.rawPayload != null && !row.rawPayload.isBlank()) {
            return row.rawPayload;
        }
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("title", row.title);
        raw.put("author", row.author);
        raw.put("platform", row.sourcePlatform);
        raw.put("url", row.sourceUrl);
        raw.put("category", row.category);
        raw.put("play_count", row.playCount);
        raw.put("like_count", row.likeCount);
        raw.put("comment_count", row.commentCount);
        raw.put("quality_score", row.dataQualityScore);
        return defaultIfBlank(toJson(raw), "{}");
    }

    private int insertBehaviorRows(List<VideoRow> rows, long tenantUserId) {
        long nowEpoch = Instant.now().getEpochSecond();
        int batchSize = safeBatchSize(behaviorBatchSize, 5000);
        String behaviorSql = "INSERT INTO user_behavior (tenant_user_id,user_id,video_id,action,time) VALUES (?,?,?,?,?)";
        double scaledFactor = Math.max(0D, behaviorScale);
        int maxRows = behaviorMaxRowsPerImport > 0 ? behaviorMaxRowsPerImport : Integer.MAX_VALUE;

        return jdbcTemplate.execute((ConnectionCallback<Integer>) connection -> {
            int totalInserted = 0;
            int pending = 0;

            try (PreparedStatement ps = connection.prepareStatement(behaviorSql)) {
                for (VideoRow row : rows) {
                    if (row.existingBefore) {
                        continue;
                    }
                    if (totalInserted + pending >= maxRows) {
                        break;
                    }

                    // Generate synthetic actions with diversified behavior profiles.
                    // Rates are anchored to source metrics to avoid collapsing all users into one profile.
                    BehaviorPlan plan = buildBehaviorPlan(row, tenantUserId, scaledFactor);
                    int play = plan.playActions();
                    int like = plan.likeActions();
                    int comment = plan.commentActions();
                    int total = Math.min(60, play + like + comment);
                    int remaining = maxRows - totalInserted - pending;
                    if (remaining <= 0) {
                        break;
                    }
                    if (total > remaining) {
                        total = remaining;
                    }
                    if (total <= 0) {
                        continue;
                    }

                    long fromEpoch = row.publishTime.atZone(ZoneId.systemDefault()).toEpochSecond();
                    if (fromEpoch >= nowEpoch) {
                        fromEpoch = nowEpoch - 600;
                    }
                    int playCutoff = Math.min(play, total);
                    int likeCutoff = Math.min(playCutoff + like, total);
                    ThreadLocalRandom random = ThreadLocalRandom.current();

                    for (int i = 0; i < total; i++) {
                        ps.setLong(1, tenantUserId);
                        ps.setLong(2, row.authorId);
                        ps.setLong(3, row.videoId);
                        ps.setString(4, i < playCutoff ? "play" : (i < likeCutoff ? "like" : "comment"));
                        long second = random.nextLong(fromEpoch, nowEpoch);
                        ps.setTimestamp(5, new Timestamp(second * 1000));
                        ps.addBatch();
                        pending++;

                        if (pending >= batchSize) {
                            totalInserted += countAffected(ps.executeBatch());
                            pending = 0;
                            if (totalInserted >= maxRows) {
                                break;
                            }
                        }
                    }
                    if (totalInserted >= maxRows) {
                        break;
                    }
                }

                if (pending > 0) {
                    totalInserted += countAffected(ps.executeBatch());
                }
            }
            return totalInserted;
        });
    }

    private BehaviorPlan buildBehaviorPlan(VideoRow row, long tenantUserId, double scaledFactor) {
        int profileBucket = resolveBehaviorProfileBucket(tenantUserId, row);
        double playMultiplier;
        double likeMultiplier;
        double commentMultiplier;
        double likeFloor;
        double commentFloor;

        // 0=light, 1=steady, 2=like-driven, 3=high-interaction, 4=high-frequency
        switch (profileBucket) {
            case 0 -> {
                playMultiplier = 0.75;
                likeMultiplier = 0.45;
                commentMultiplier = 0.35;
                likeFloor = 0.004;
                commentFloor = 0.0005;
            }
            case 1 -> {
                playMultiplier = 1.00;
                likeMultiplier = 0.85;
                commentMultiplier = 0.85;
                likeFloor = 0.010;
                commentFloor = 0.0015;
            }
            case 2 -> {
                playMultiplier = 1.10;
                likeMultiplier = 1.60;
                commentMultiplier = 0.55;
                likeFloor = 0.08;
                commentFloor = 0.003;
            }
            case 3 -> {
                playMultiplier = 1.20;
                likeMultiplier = 2.30;
                commentMultiplier = 8.50;
                likeFloor = 0.12;
                commentFloor = 0.06;
            }
            default -> {
                playMultiplier = 1.55;
                likeMultiplier = 1.10;
                commentMultiplier = 2.40;
                likeFloor = 0.05;
                commentFloor = 0.012;
            }
        }

        int playBase = estimatePlayActions(row.playCount);
        int playRaw = clampInt((int) Math.round(playBase * playMultiplier), 3, 36);

        double sourceLikeRate = row.playCount <= 0 ? 0D : (double) row.likeCount / (double) row.playCount;
        double sourceCommentRate = row.playCount <= 0 ? 0D : (double) row.commentCount / (double) row.playCount;
        double likeRate = clampDouble(Math.max(sourceLikeRate * likeMultiplier, likeFloor), 0.002, 0.35);
        double commentRate = clampDouble(Math.max(sourceCommentRate * commentMultiplier, commentFloor), 0.0005, 0.18);

        int likeRaw = clampInt((int) Math.round(playRaw * likeRate), 0, 20);
        int commentRaw = clampInt((int) Math.round(playRaw * commentRate), 0, 14);

        int play = scaleActionCountDeterministic(playRaw, scaledFactor, row.videoId ^ 0x9E3779B97F4A7C15L);
        int like = scaleActionCountDeterministic(likeRaw, scaledFactor, row.videoId ^ 0xC2B2AE3D27D4EB4FL);
        int comment = scaleActionCountDeterministic(commentRaw, scaledFactor, row.videoId ^ 0x165667B19E3779F9L);

        if (playRaw > 0) {
            play = clampInt(Math.max(play, 2), 2, 36);
        }
        // Keep rare interaction signals after scaling to avoid profile collapse.
        if (likeRaw > 0 && like == 0 && profileBucket >= 2) {
            like = 1;
        }
        if (commentRaw > 0 && comment == 0 && profileBucket >= 3) {
            comment = 1;
        }

        return new BehaviorPlan(play, like, comment);
    }

    private int resolveBehaviorProfileBucket(long tenantUserId, VideoRow row) {
        long seed = hashLong("tenant:" + tenantUserId + ":profile:" + row.authorId);
        int code = (int) Math.floorMod(seed, 100);
        if (code < 18) {
            return 0;
        }
        if (code < 48) {
            return 1;
        }
        if (code < 70) {
            return 2;
        }
        if (code < 88) {
            return 3;
        }
        return 4;
    }

    private int scaleActionCountDeterministic(int rawCount, double factor, long seed) {
        if (rawCount <= 0 || factor <= 0D) {
            return 0;
        }
        double scaled = rawCount * factor;
        int base = (int) Math.floor(scaled);
        double fraction = scaled - base;
        if (fraction <= 0D) {
            return Math.max(0, base);
        }
        int threshold = (int) Math.round(fraction * 10_000D);
        int dice = (int) Math.floorMod(seed, 10_000);
        if (dice < threshold) {
            base += 1;
        }
        return Math.max(0, base);
    }

    private int estimatePlayActions(long playCount) {
        int raw = (int) Math.round(Math.log10(Math.max(1D, playCount) + 9D) * 4.0D);
        return clampInt(raw, 3, 22);
    }

    private int clampInt(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private double clampDouble(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private int countAffected(int[] results) {
        if (results == null || results.length == 0) {
            return 0;
        }
        int count = 0;
        for (int value : results) {
            if (value > 0 || value == Statement.SUCCESS_NO_INFO) {
                count++;
            }
        }
        return count;
    }

    private long createImportJob(String importType, String sourcePlatform, String sourceFile, int sourceCount, long tenantUserId) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO import_job " +
                            "(tenant_user_id,import_type,source_platform,source_file,source_count,success_count,started_at,status,notes) " +
                            "VALUES (?,?,?,?,?,?,?,?,?)",
                    Statement.RETURN_GENERATED_KEYS
            );
            ps.setLong(1, tenantUserId);
            ps.setString(2, trim(defaultIfBlank(importType, "manual_text"), 32));
            ps.setString(3, trim(defaultIfBlank(sourcePlatform, "unknown"), 32));
            ps.setString(4, sourceFile == null ? null : trim(sourceFile, 260));
            ps.setInt(5, Math.max(0, sourceCount));
            ps.setInt(6, 0);
            ps.setTimestamp(7, Timestamp.valueOf(now));
            ps.setString(8, "RUNNING");
            ps.setString(9, "created by crawler import");
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        return key == null ? 0L : key.longValue();
    }

    private void finishImportJob(long importJobId, int successCount, String status, String notes) {
        if (importJobId <= 0) {
            return;
        }
        jdbcTemplate.update(
                "UPDATE import_job SET success_count=?, finished_at=?, status=?, notes=? WHERE id=?",
                successCount,
                Timestamp.valueOf(LocalDateTime.now()),
                trim(defaultIfBlank(status, "SUCCESS"), 16),
                trim(defaultIfBlank(notes, ""), 500),
                importJobId
        );
    }

    private Map<String, Long> queryExistingVideoIdByDedupe(List<VideoRow> rows, long tenantUserId) {
        List<String> dedupeKeys = rows.stream()
                .map(row -> row.dedupeKey)
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(key -> !key.isEmpty())
                .distinct()
                .toList();

        if (dedupeKeys.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<String, Long> result = new HashMap<>();
        int batchSize = safeBatchSize(dedupeQueryBatchSize, 2000);
        // Chunk IN queries to avoid oversized SQL and parameter limits.
        for (int from = 0; from < dedupeKeys.size(); from += batchSize) {
            int to = Math.min(dedupeKeys.size(), from + batchSize);
            List<String> part = dedupeKeys.subList(from, to);
            String placeholders = String.join(",", Collections.nCopies(part.size(), "?"));
            String sql = "SELECT id, dedupe_key FROM video WHERE tenant_user_id=? AND dedupe_key IN (" + placeholders + ")";
            List<Object> args = new ArrayList<>();
            args.add(tenantUserId);
            args.addAll(part);
            jdbcTemplate.query(sql, rs -> {
                result.put(rs.getString("dedupe_key"), rs.getLong("id"));
            }, args.toArray());
        }
        return result;
    }

    private int safeBatchSize(int configured, int fallback) {
        return configured > 0 ? configured : fallback;
    }

    private String sanitizeText(String value) {
        if (value == null) {
            return null;
        }
        String text = value;
        for (int i = 0; i < 2; i++) {
            String decoded = HtmlUtils.htmlUnescape(text);
            if (decoded.equals(text)) {
                break;
            }
            text = decoded;
        }
        text = HTML_TAG_PATTERN.matcher(text).replaceAll("");
        text = text.replace('\u00A0', ' ');
        text = MULTI_SPACE_PATTERN.matcher(text).replaceAll(" ").trim();
        return text;
    }

    private String defaultIfBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private <T> T defaultIfNull(T value, T fallback) {
        return value == null ? fallback : value;
    }

    private String trim(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        return trimmed.length() <= maxLength ? trimmed : trimmed.substring(0, maxLength);
    }

    private String trimNullable(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.length() <= maxLength ? trimmed : trimmed.substring(0, maxLength);
    }

    private static class VideoRow {
        long videoId;
        String dedupeKey;
        String platformVideoId;
        String title;
        String author;
        String sourcePlatform;
        String sourceUrl;
        String category;
        long playCount;
        long likeCount;
        long commentCount;
        long shareCount;
        long favoriteCount;
        long durationSec;
        LocalDateTime publishTime;
        String importType;
        String sourceFile;
        LocalDateTime importTime;
        double dataQualityScore;
        double aiConfidence;
        String tagsJson;
        String extraJson;
        String qualityLevel;
        boolean aiUsed;
        String rejectReason;
        String rejectSuggestion;
        String rawPayload;
        long importJobId;
        boolean existingBefore;
        long authorId;
        long authorFans;
        long authorFollow;
        int authorLevel;
    }

    private record ImportResult(
            int acceptedRows,
            int lowQualityRows,
            int rejectedRows,
            int videoRows,
            int newVideoRows,
            int updatedVideoRows,
            int userRows,
            int behaviorRows,
            int dedupedRecords,
            long importJobId
    ) {
    }

    private record ParseContext(
            ParseResult parseResult,
            boolean aiUsed,
            int aiRecords,
            String aiMessage
    ) {
    }

    private record ParseResult(List<VideoRow> rows, int dedupedRecords) {
    }

    private record BehaviorPlan(int playActions, int likeActions, int commentActions) {
    }

}


