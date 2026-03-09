package com.video.analysis.dto;

public class PlatformBenchmarkVO {

    private String sourcePlatform;
    private Long videoCount;
    private Long totalPlay;
    private Long totalLike;
    private Long totalComment;
    private Double avgPlayPerVideo;
    private Double avgLikePerVideo;
    private Double avgCommentPerVideo;
    private Double engagementPerThousandPlay;

    public String getSourcePlatform() {
        return sourcePlatform;
    }

    public void setSourcePlatform(String sourcePlatform) {
        this.sourcePlatform = sourcePlatform;
    }

    public Long getVideoCount() {
        return videoCount;
    }

    public void setVideoCount(Long videoCount) {
        this.videoCount = videoCount;
    }

    public Long getTotalPlay() {
        return totalPlay;
    }

    public void setTotalPlay(Long totalPlay) {
        this.totalPlay = totalPlay;
    }

    public Long getTotalLike() {
        return totalLike;
    }

    public void setTotalLike(Long totalLike) {
        this.totalLike = totalLike;
    }

    public Long getTotalComment() {
        return totalComment;
    }

    public void setTotalComment(Long totalComment) {
        this.totalComment = totalComment;
    }

    public Double getAvgPlayPerVideo() {
        return avgPlayPerVideo;
    }

    public void setAvgPlayPerVideo(Double avgPlayPerVideo) {
        this.avgPlayPerVideo = avgPlayPerVideo;
    }

    public Double getAvgLikePerVideo() {
        return avgLikePerVideo;
    }

    public void setAvgLikePerVideo(Double avgLikePerVideo) {
        this.avgLikePerVideo = avgLikePerVideo;
    }

    public Double getAvgCommentPerVideo() {
        return avgCommentPerVideo;
    }

    public void setAvgCommentPerVideo(Double avgCommentPerVideo) {
        this.avgCommentPerVideo = avgCommentPerVideo;
    }

    public Double getEngagementPerThousandPlay() {
        return engagementPerThousandPlay;
    }

    public void setEngagementPerThousandPlay(Double engagementPerThousandPlay) {
        this.engagementPerThousandPlay = engagementPerThousandPlay;
    }
}
