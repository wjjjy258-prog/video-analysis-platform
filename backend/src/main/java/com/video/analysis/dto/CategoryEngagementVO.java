package com.video.analysis.dto;

public class CategoryEngagementVO {

    private String category;
    private Long videoCount;
    private Long totalPlay;
    private Long totalLike;
    private Long totalComment;
    private Double likeRate;
    private Double commentRate;
    private Double engagementRate;

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
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

    public Double getLikeRate() {
        return likeRate;
    }

    public void setLikeRate(Double likeRate) {
        this.likeRate = likeRate;
    }

    public Double getCommentRate() {
        return commentRate;
    }

    public void setCommentRate(Double commentRate) {
        this.commentRate = commentRate;
    }

    public Double getEngagementRate() {
        return engagementRate;
    }

    public void setEngagementRate(Double engagementRate) {
        this.engagementRate = engagementRate;
    }
}

