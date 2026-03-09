package com.video.analysis.dto;

public class FunnelStatVO {

    private String sourcePlatform;
    private Long videoCount;
    private Long totalPlay;
    private Long totalLike;
    private Long totalComment;
    private Double likeConversionRate;
    private Double commentConversionRate;

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

    public Double getLikeConversionRate() {
        return likeConversionRate;
    }

    public void setLikeConversionRate(Double likeConversionRate) {
        this.likeConversionRate = likeConversionRate;
    }

    public Double getCommentConversionRate() {
        return commentConversionRate;
    }

    public void setCommentConversionRate(Double commentConversionRate) {
        this.commentConversionRate = commentConversionRate;
    }
}
