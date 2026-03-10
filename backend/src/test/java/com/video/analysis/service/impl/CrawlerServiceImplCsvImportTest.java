package com.video.analysis.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CrawlerServiceImplCsvImportTest {

    private static final String UNKNOWN = "unknown";
    private static final String FILE_IMPORT = "file_import";

    @Test
    void parseCsv_shouldMapChineseHeadersAndMetrics() throws Exception {
        String csv =
                "\u6392\u540d,\u5206\u533a,\u6807\u9898,\u4f5c\u8005,\u64ad\u653e\u91cf,\u5f39\u5e55\u91cf,\u8bc4\u8bba\u91cf,\u786c\u5e01,\u70b9\u8d5e\u91cf,\u5206\u4eab\u91cf,\u6536\u85cf\u91cf,\u5c01\u9762,\u94fe\u63a5\n" +
                "1,\u97f3\u4e50,\u3010\u6625\u665a<em class=\"\"keyword\"\">\u9b3c\u755c</em>\u3011\u6d4b\u8bd5\u89c6\u9891,Alice,123.4\u4e07,456,789,0,1.2\u4e07,88,99,https://img.example/a.jpg,https://www.bilibili.com/video/BV1xx411c7mD\n";

        List<Object> rows = parseCsv(csv, "test.csv");
        assertFalse(rows.isEmpty());

        Object row = rows.get(0);
        String title = (String) getField(row, "title");
        assertFalse(title.contains("<"));
        assertFalse(title.contains("em"));
        assertEquals("\u97f3\u4e50", getField(row, "category"));
        assertEquals("bilibili", getField(row, "sourcePlatform"));
        assertEquals(1_234_000L, getField(row, "playCount"));
        assertEquals(12_000L, getField(row, "likeCount"));
        assertEquals(789L, getField(row, "commentCount"));
    }

    @Test
    void parseCsv_shouldSupportQuotedMultilineCells() throws Exception {
        String csv = "title,author,platform,play_count,like_count,comment_count,category\n" +
                "\"第一行\\n第二行\",Alice,bilibili,1200,88,12,科技\n";
        List<Object> rows = parseCsv(csv, "multiline.csv");
        assertEquals(1, rows.size());

        Object row = rows.get(0);
        String title = (String) getField(row, "title");
        assertNotNull(title);
        assertTrue(title.contains("第一行"));
        assertTrue(title.contains("第二行"));
        assertEquals("bilibili", getField(row, "sourcePlatform"));
        assertEquals(1200L, getField(row, "playCount"));
    }

    @Test
    void parseTable_shouldMapMixedChineseHeaders() throws Exception {
        String markdown = """
                | 标题（title） | 作者 | 来源平台 | 播放量(次) | 点赞量 | 评论量 | 分类 |
                | --- | --- | --- | --- | --- | --- | --- |
                | 测试A | 阿明 | 哔哩哔哩 | 1.5万 | 200 | 30 | 音乐 |
                """;
        CrawlerServiceImpl service = new CrawlerServiceImpl(null, new ObjectMapper(), null);
        Method parseTable = CrawlerServiceImpl.class.getDeclaredMethod(
                "parseTable",
                String.class,
                String.class,
                String.class,
                String.class,
                long.class
        );
        parseTable.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<Object> rows = (List<Object>) parseTable.invoke(service, markdown, UNKNOWN, FILE_IMPORT, "table.md", 1L);
        assertEquals(1, rows.size());

        Object row = rows.get(0);
        assertEquals("测试A", getField(row, "title"));
        assertEquals("bilibili", getField(row, "sourcePlatform"));
        assertEquals(15_000L, getField(row, "playCount"));
        assertEquals(200L, getField(row, "likeCount"));
        assertEquals(30L, getField(row, "commentCount"));
        assertEquals("音乐", getField(row, "category"));
    }

    private List<Object> parseCsv(String csv, String sourceFile) throws Exception {
        CrawlerServiceImpl service = new CrawlerServiceImpl(null, new ObjectMapper(), null);
        Method parseCsv = CrawlerServiceImpl.class.getDeclaredMethod(
                "parseCsv",
                String.class,
                String.class,
                String.class,
                String.class,
                long.class
        );
        parseCsv.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<Object> rows = (List<Object>) parseCsv.invoke(service, csv, UNKNOWN, FILE_IMPORT, sourceFile, 1L);
        return rows;
    }

    private Object getField(Object target, String fieldName) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(target);
    }
}
