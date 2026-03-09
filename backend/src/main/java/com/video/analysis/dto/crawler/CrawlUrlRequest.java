package com.video.analysis.dto.crawler;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public class CrawlUrlRequest {

    @NotNull
    private String platform = "auto";

    @NotEmpty
    private List<String> urls;

    private boolean confirmRisk;

    public String getPlatform() {
        return platform;
    }

    public void setPlatform(String platform) {
        this.platform = platform;
    }

    public List<String> getUrls() {
        return urls;
    }

    public void setUrls(List<String> urls) {
        this.urls = urls;
    }

    public boolean isConfirmRisk() {
        return confirmRisk;
    }

    public void setConfirmRisk(boolean confirmRisk) {
        this.confirmRisk = confirmRisk;
    }
}
