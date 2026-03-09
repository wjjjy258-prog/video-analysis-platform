package com.video.analysis.dto;

public class CategoryStatVO {

    private String category;
    private Long videoCount;
    private Long totalPlay;
    private Long totalLike;
    private Long totalComment;

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
}
