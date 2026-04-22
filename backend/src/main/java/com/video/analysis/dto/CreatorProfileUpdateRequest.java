package com.video.analysis.dto;

public class CreatorProfileUpdateRequest {

    private String creatorPlatform;
    private String creatorFocusCategory;

    public String getCreatorPlatform() {
        return creatorPlatform;
    }

    public void setCreatorPlatform(String creatorPlatform) {
        this.creatorPlatform = creatorPlatform;
    }

    public String getCreatorFocusCategory() {
        return creatorFocusCategory;
    }

    public void setCreatorFocusCategory(String creatorFocusCategory) {
        this.creatorFocusCategory = creatorFocusCategory;
    }
}

