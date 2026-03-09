package com.video.analysis.service.ai;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class AiImportExtractor {

    private static final String SYSTEM_PROMPT = """
            你是视频数据结构化抽取助手。请从输入文本中提取视频记录，严格返回 JSON。
            仅返回 JSON，不要返回 Markdown 或解释。

            输出格式：
            {
              "records": [
                {
                  "title": "视频标题",
                  "author": "作者",
                  "platform": "来源平台，如 bilibili/douyin/kuaishou/xiaohongshu/xigua/weibo/youtube/tiktok/acfun",
                  "category": "分区或分类",
                  "play_count": "播放量，可带万/亿",
                  "like_count": "点赞量，可带万/亿",
                  "comment_count": "评论量，可带万/亿",
                  "publish_time": "发布时间，尽量 yyyy-MM-dd HH:mm:ss",
                  "url": "视频链接"
                }
              ]
            }
            要求：
            1. 不要编造数据，缺失字段写 null。
            2. 一行/一段对应一条记录时尽量都提取出来。
            3. 平台字段尽量使用英文标准值。
            """;

    @Value("${crawler.ai.enabled:false}")
    private boolean enabled;

    @Value("${crawler.ai.api-url:https://api.openai.com/v1/chat/completions}")
    private String apiUrl;

    @Value("${crawler.ai.model:gpt-4o-mini}")
    private String model;

    @Value("${crawler.ai.api-key:}")
    private String apiKey;

    @Value("${crawler.ai.timeout-seconds:120}")
    private int timeoutSeconds;

    @Value("${crawler.ai.max-input-chars:120000}")
    private int maxInputChars;

    @Value("${crawler.ai.temperature:0.1}")
    private double temperature;

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public AiImportExtractor(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
    }

    public AiExtractResult extractRecords(String rawText, String defaultPlatform) {
        if (!enabled) {
            return AiExtractResult.skip("AI 增强未开启（crawler.ai.enabled=false）");
        }
        if (apiKey == null || apiKey.isBlank()) {
            return AiExtractResult.skip("AI 增强未配置 API Key（crawler.ai.api-key）");
        }
        if (rawText == null || rawText.isBlank()) {
            return AiExtractResult.fail("输入文本为空");
        }

        String input = rawText;
        if (input.length() > maxInputChars) {
            input = input.substring(0, maxInputChars);
        }

        try {
            String payload = objectMapper.writeValueAsString(buildRequest(defaultPlatform, input));
            HttpRequest request = HttpRequest.newBuilder(URI.create(apiUrl))
                    .timeout(Duration.ofSeconds(Math.max(10, timeoutSeconds)))
                    .header("Authorization", "Bearer " + apiKey.trim())
                    .header("Content-Type", "application/json; charset=utf-8")
                    .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                String body = String.valueOf(response.body());
                String shortBody = body.length() > 300 ? body.substring(0, 300) + "..." : body;
                return AiExtractResult.fail("AI 接口返回异常状态 " + response.statusCode() + "，" + shortBody);
            }

            JsonNode root = objectMapper.readTree(response.body());
            String content = extractContent(root);
            if (content == null || content.isBlank()) {
                return AiExtractResult.fail("AI 返回内容为空");
            }

            List<Map<String, Object>> records = parseRecords(content);
            if (records.isEmpty()) {
                return AiExtractResult.fail("AI 未抽取到可入库记录");
            }
            return AiExtractResult.success(records, "AI 抽取记录数: " + records.size());
        } catch (Exception ex) {
            return AiExtractResult.fail("AI 增强解析失败: " + ex.getMessage());
        }
    }

    private Map<String, Object> buildRequest(String defaultPlatform, String input) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", model);
        payload.put("temperature", temperature);
        payload.put("response_format", Map.of("type", "json_object"));

        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", SYSTEM_PROMPT));
        messages.add(Map.of(
                "role", "user",
                "content", "默认平台: " + (defaultPlatform == null ? "unknown" : defaultPlatform) + "\n\n原始文本：\n" + input
        ));
        payload.put("messages", messages);
        return payload;
    }

    private String extractContent(JsonNode root) {
        JsonNode choices = root.path("choices");
        if (choices.isArray() && !choices.isEmpty()) {
            JsonNode message = choices.get(0).path("message");
            JsonNode content = message.path("content");
            if (content.isTextual()) {
                return content.asText();
            }
            if (content.isArray()) {
                StringBuilder sb = new StringBuilder();
                for (JsonNode item : content) {
                    JsonNode text = item.path("text");
                    if (text.isTextual()) {
                        sb.append(text.asText());
                    }
                }
                if (!sb.isEmpty()) {
                    return sb.toString();
                }
            }
        }

        JsonNode outputText = root.path("output_text");
        if (outputText.isTextual()) {
            return outputText.asText();
        }
        return null;
    }

    private List<Map<String, Object>> parseRecords(String content) throws Exception {
        JsonNode node = objectMapper.readTree(content);
        JsonNode recordsNode = node.path("records");
        if (recordsNode.isMissingNode() || recordsNode.isNull()) {
            recordsNode = node.path("videos");
        }
        if ((recordsNode.isMissingNode() || recordsNode.isNull()) && node.isArray()) {
            recordsNode = node;
        }
        if (!recordsNode.isArray()) {
            return List.of();
        }

        List<Map<String, Object>> records = new ArrayList<>();
        for (JsonNode item : recordsNode) {
            if (!item.isObject()) {
                continue;
            }
            Map<String, Object> map = objectMapper.convertValue(item, new TypeReference<>() {
            });
            records.add(map);
        }
        return records;
    }

    public record AiExtractResult(
            boolean used,
            boolean success,
            String message,
            List<Map<String, Object>> records
    ) {
        public static AiExtractResult success(List<Map<String, Object>> records, String message) {
            return new AiExtractResult(true, true, message, records == null ? List.of() : records);
        }

        public static AiExtractResult fail(String message) {
            return new AiExtractResult(true, false, message, List.of());
        }

        public static AiExtractResult skip(String message) {
            return new AiExtractResult(false, false, message, List.of());
        }
    }
}
