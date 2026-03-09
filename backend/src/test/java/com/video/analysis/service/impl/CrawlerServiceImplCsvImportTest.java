package com.video.analysis.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class CrawlerServiceImplCsvImportTest {

    @Test
    void parseCsv_shouldMapChineseHeadersAndMetrics() throws Exception {
        String csv =
                "\u6392\u540d,\u5206\u533a,\u6807\u9898,\u4f5c\u8005,\u64ad\u653e\u91cf,\u5f39\u5e55\u91cf,\u8bc4\u8bba\u91cf,\u786c\u5e01,\u70b9\u8d5e\u91cf,\u5206\u4eab\u91cf,\u6536\u85cf\u91cf,\u5c01\u9762,\u94fe\u63a5\n" +
                "1,\u97f3\u4e50,\u3010\u6625\u665a<em class=\"\"keyword\"\">\u9b3c\u755c</em>\u3011\u6d4b\u8bd5\u89c6\u9891,Alice,123.4\u4e07,456,789,0,1.2\u4e07,88,99,https://img.example/a.jpg,https://www.bilibili.com/video/BV1xx411c7mD\n";

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
        List<Object> rows = (List<Object>) parseCsv.invoke(service, csv, "unknown", "file_import", "test.csv", 1L);
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

    private Object getField(Object target, String fieldName) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(target);
    }
}
