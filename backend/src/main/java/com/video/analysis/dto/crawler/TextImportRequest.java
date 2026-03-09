package com.video.analysis.dto.crawler;

import jakarta.validation.constraints.NotBlank;

public class TextImportRequest {

    @NotBlank
    private String text;

    private String defaultPlatform = "unknown";
    private boolean aiAssist = false;

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getDefaultPlatform() {
        return defaultPlatform;
    }

    public void setDefaultPlatform(String defaultPlatform) {
        this.defaultPlatform = defaultPlatform;
    }

    public boolean isAiAssist() {
        return aiAssist;
    }

    public void setAiAssist(boolean aiAssist) {
        this.aiAssist = aiAssist;
    }
}
