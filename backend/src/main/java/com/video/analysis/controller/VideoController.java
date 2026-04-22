package com.video.analysis.controller;

import com.video.analysis.dto.CategoryStatVO;
import com.video.analysis.dto.CategoryEngagementVO;
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
import com.video.analysis.service.VideoService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Locale;
import java.util.Map;

@Validated
@RestController
@RequestMapping("/video")
public class VideoController {

    private final VideoService videoService;

    public VideoController(VideoService videoService) {
        this.videoService = videoService;
    }

    @GetMapping("/overview")
    public Map<String, Object> overview(@RequestParam(required = false) String platform) {
        return videoService.getOverview(normalizePlatform(platform));
    }

    @GetMapping("/dashboard")
    public Map<String, Object> dashboard(
            @RequestParam(defaultValue = "5") @Min(1) @Max(20) Integer hotLimit,
            @RequestParam(required = false) String platform
    ) {
        return videoService.getDashboard(hotLimit, normalizePlatform(platform));
    }

    @GetMapping("/hot")
    public List<HotVideoVO> hotVideos(
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) Integer limit,
            @RequestParam(required = false) String platform
    ) {
        return videoService.getHotVideos(limit, normalizePlatform(platform));
    }

    @GetMapping("/category")
    public List<CategoryStatVO> categoryStats(@RequestParam(required = false) String platform) {
        return videoService.getCategoryStats(normalizePlatform(platform));
    }

    @GetMapping("/trend")
    public List<TrendPointVO> trend(
            @RequestParam(defaultValue = "30") @Min(1) @Max(365) Integer days,
            @RequestParam(required = false) String platform
    ) {
        return videoService.getTrend(days, normalizePlatform(platform));
    }

    @GetMapping("/engagement/category")
    public List<CategoryEngagementVO> categoryEngagement(@RequestParam(required = false) String platform) {
        return videoService.getCategoryEngagement(normalizePlatform(platform));
    }

    @GetMapping("/engagement/video")
    public List<VideoEngagementVO> topEngagementVideos(
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) Integer limit,
            @RequestParam(defaultValue = "10000") @Min(0) Long minPlay,
            @RequestParam(required = false) String platform
    ) {
        return videoService.getTopEngagementVideos(limit, minPlay, normalizePlatform(platform));
    }

    @GetMapping("/user")
    public List<UserInterestVO> userInterest(
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) Integer limit,
            @RequestParam(required = false) String platform
    ) {
        return videoService.getUserInterest(limit, normalizePlatform(platform));
    }

    @GetMapping("/platform")
    public List<PlatformStatVO> platformStats(@RequestParam(required = false) String platform) {
        return videoService.getPlatformStats(normalizePlatform(platform));
    }

    @GetMapping("/funnel")
    public List<FunnelStatVO> platformFunnel(@RequestParam(required = false) String platform) {
        return videoService.getPlatformFunnel(normalizePlatform(platform));
    }

    @GetMapping("/platform/benchmark")
    public List<PlatformBenchmarkVO> platformBenchmark(@RequestParam(required = false) String platform) {
        return videoService.getPlatformBenchmark(normalizePlatform(platform));
    }

    @GetMapping("/source-trace")
    public List<SourceTraceVO> sourceTrace(
            @RequestParam(defaultValue = "30") @Min(1) @Max(500) Integer limit,
            @RequestParam(required = false) String platform
    ) {
        return videoService.getSourceTrace(limit, normalizePlatform(platform));
    }

    @GetMapping("/insight")
    public List<InsightCardVO> insight(@RequestParam(required = false) String platform) {
        return videoService.getInsightCards(normalizePlatform(platform));
    }

    @GetMapping("/creator/dashboard")
    public Map<String, Object> creatorDashboard(@RequestParam(required = false) String platform) {
        return videoService.getCreatorDashboard(normalizePlatform(platform));
    }

    @PostMapping("/creator/profile")
    public Map<String, Object> updateCreatorProfile(@RequestBody(required = false) CreatorProfileUpdateRequest request) {
        CreatorProfileUpdateRequest safeRequest = request == null ? new CreatorProfileUpdateRequest() : request;
        return videoService.updateCreatorProfile(safeRequest);
    }

    private String normalizePlatform(String platform) {
        if (platform == null) {
            return null;
        }
        String normalized = platform.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty() || "all".equals(normalized)) {
            return null;
        }
        return normalized;
    }
}
