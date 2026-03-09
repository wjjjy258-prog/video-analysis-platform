package com.video.analysis.dto;

public class PlatformStatVO {

    private String sourcePlatform;
    private Long videoCount;
    private Long totalPlay;

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
}
