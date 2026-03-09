package com.video.analysis.entity;

import java.time.LocalDate;

public class VideoStatistics {

    private Long id;
    private Long videoId;
    private LocalDate statDate;
    private Long dailyPlay;
    private Long dailyLike;
    private Long dailyComment;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getVideoId() {
        return videoId;
    }

    public void setVideoId(Long videoId) {
        this.videoId = videoId;
    }

    public LocalDate getStatDate() {
        return statDate;
    }

    public void setStatDate(LocalDate statDate) {
        this.statDate = statDate;
    }

    public Long getDailyPlay() {
        return dailyPlay;
    }

    public void setDailyPlay(Long dailyPlay) {
        this.dailyPlay = dailyPlay;
    }

    public Long getDailyLike() {
        return dailyLike;
    }

    public void setDailyLike(Long dailyLike) {
        this.dailyLike = dailyLike;
    }

    public Long getDailyComment() {
        return dailyComment;
    }

    public void setDailyComment(Long dailyComment) {
        this.dailyComment = dailyComment;
    }
}
