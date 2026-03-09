package com.video.analysis.service;

import com.video.analysis.dto.crawler.CrawlRunResponse;

import java.util.List;
import java.util.Map;

public interface CrawlerService {

    CrawlRunResponse runUrlCrawl(String platform, List<String> urls, boolean confirmRisk);

    CrawlRunResponse runMockCrawl();

    CrawlRunResponse importFromText(String text, String defaultPlatform, boolean aiAssist);

    CrawlRunResponse importFromFile(String fileName, byte[] content, String defaultPlatform, boolean aiAssist);

    CrawlRunResponse clearTenantData(boolean confirm);

    List<Map<String, Object>> listImportRejects(Integer limit);
}
