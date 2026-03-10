package com.video.analysis.service;

import com.video.analysis.dto.CategoryStatVO;
import com.video.analysis.dto.CategoryEngagementVO;
import com.video.analysis.dto.FunnelStatVO;
import com.video.analysis.dto.HotVideoVO;
import com.video.analysis.dto.InsightCardVO;
import com.video.analysis.dto.PlatformBenchmarkVO;
import com.video.analysis.dto.PlatformStatVO;
import com.video.analysis.dto.SourceTraceVO;
import com.video.analysis.dto.TrendPointVO;
import com.video.analysis.dto.UserInterestVO;
import com.video.analysis.dto.VideoEngagementVO;

import java.util.List;
import java.util.Map;

public interface VideoService {

    List<HotVideoVO> getHotVideos(Integer limit, String platform);

    List<CategoryStatVO> getCategoryStats(String platform);

    List<TrendPointVO> getTrend(Integer days, String platform);

    List<CategoryEngagementVO> getCategoryEngagement(String platform);

    List<VideoEngagementVO> getTopEngagementVideos(Integer limit, Long minPlay, String platform);

    List<UserInterestVO> getUserInterest(Integer limit, String platform);

    List<PlatformStatVO> getPlatformStats(String platform);

    List<FunnelStatVO> getPlatformFunnel(String platform);

    List<PlatformBenchmarkVO> getPlatformBenchmark(String platform);

    List<SourceTraceVO> getSourceTrace(Integer limit, String platform);

    List<InsightCardVO> getInsightCards(String platform);

    Map<String, Object> getOverview(String platform);

    Map<String, Object> getDashboard(Integer hotLimit, String platform);
}
