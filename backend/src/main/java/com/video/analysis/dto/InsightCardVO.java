package com.video.analysis.dto;

public class InsightCardVO {

    private String title;
    private String value;
    private String description;
    private String level;

    public InsightCardVO() {
    }

    public InsightCardVO(String title, String value, String description, String level) {
        this.title = title;
        this.value = value;
        this.description = description;
        this.level = level;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getLevel() {
        return level;
    }

    public void setLevel(String level) {
        this.level = level;
    }
}
