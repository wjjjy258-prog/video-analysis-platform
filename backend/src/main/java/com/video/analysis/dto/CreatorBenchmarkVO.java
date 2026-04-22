package com.video.analysis.dto;

public class CreatorBenchmarkVO {

    private String author;
    private String sourcePlatform;
    private String mainCategory;
    private Long videoCount;
    private Long totalPlayCount;
    private Long totalLikeCount;
    private Long totalCommentCount;
    private Double avgPlayPerVideo;
    private Double engagementPerThousandPlay;

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public String getSourcePlatform() {
        return sourcePlatform;
    }

    public void setSourcePlatform(String sourcePlatform) {
        this.sourcePlatform = sourcePlatform;
    }

    public String getMainCategory() {
        return mainCategory;
    }

    public void setMainCategory(String mainCategory) {
        this.mainCategory = mainCategory;
    }

    public Long getVideoCount() {
        return videoCount;
    }

    public void setVideoCount(Long videoCount) {
        this.videoCount = videoCount;
    }

    public Long getTotalPlayCount() {
        return totalPlayCount;
    }

    public void setTotalPlayCount(Long totalPlayCount) {
        this.totalPlayCount = totalPlayCount;
    }

    public Long getTotalLikeCount() {
        return totalLikeCount;
    }

    public void setTotalLikeCount(Long totalLikeCount) {
        this.totalLikeCount = totalLikeCount;
    }

    public Long getTotalCommentCount() {
        return totalCommentCount;
    }

    public void setTotalCommentCount(Long totalCommentCount) {
        this.totalCommentCount = totalCommentCount;
    }

    public Double getAvgPlayPerVideo() {
        return avgPlayPerVideo;
    }

    public void setAvgPlayPerVideo(Double avgPlayPerVideo) {
        this.avgPlayPerVideo = avgPlayPerVideo;
    }

    public Double getEngagementPerThousandPlay() {
        return engagementPerThousandPlay;
    }

    public void setEngagementPerThousandPlay(Double engagementPerThousandPlay) {
        this.engagementPerThousandPlay = engagementPerThousandPlay;
    }
}
