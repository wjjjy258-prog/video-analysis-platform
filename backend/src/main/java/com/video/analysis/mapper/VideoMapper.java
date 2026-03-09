package com.video.analysis.mapper;

import com.video.analysis.dto.CategoryStatVO;
import com.video.analysis.dto.CategoryEngagementVO;
import com.video.analysis.dto.FunnelStatVO;
import com.video.analysis.dto.HotVideoVO;
import com.video.analysis.dto.PlatformBenchmarkVO;
import com.video.analysis.dto.PlatformStatVO;
import com.video.analysis.dto.SourceTraceVO;
import com.video.analysis.dto.TrendPointVO;
import com.video.analysis.dto.UserInterestVO;
import com.video.analysis.dto.VideoEngagementVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface VideoMapper {

    List<HotVideoVO> selectHotVideos(@Param("tenantUserId") Long tenantUserId, @Param("limit") Integer limit, @Param("platform") String platform);

    List<CategoryStatVO> selectCategoryStats(@Param("tenantUserId") Long tenantUserId, @Param("platform") String platform);

    List<TrendPointVO> selectTrend(@Param("tenantUserId") Long tenantUserId, @Param("days") Integer days, @Param("platform") String platform);

    List<CategoryEngagementVO> selectCategoryEngagement(@Param("tenantUserId") Long tenantUserId, @Param("platform") String platform);

    List<VideoEngagementVO> selectTopEngagementVideos(@Param("tenantUserId") Long tenantUserId, @Param("limit") Integer limit, @Param("minPlay") Long minPlay, @Param("platform") String platform);

    List<UserInterestVO> selectUserInterest(@Param("tenantUserId") Long tenantUserId, @Param("limit") Integer limit, @Param("platform") String platform);

    List<PlatformStatVO> selectPlatformStats(@Param("tenantUserId") Long tenantUserId, @Param("platform") String platform);

    List<FunnelStatVO> selectPlatformFunnel(@Param("tenantUserId") Long tenantUserId, @Param("platform") String platform);

    List<PlatformBenchmarkVO> selectPlatformBenchmark(@Param("tenantUserId") Long tenantUserId, @Param("platform") String platform);

    List<SourceTraceVO> selectSourceTrace(@Param("tenantUserId") Long tenantUserId, @Param("limit") Integer limit, @Param("platform") String platform);

    Map<String, Object> selectOverview(@Param("tenantUserId") Long tenantUserId, @Param("platform") String platform);
}
