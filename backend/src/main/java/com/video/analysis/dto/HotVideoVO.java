package com.video.analysis.dto;

import java.time.LocalDateTime;

public class HotVideoVO {

    private Long id;
    private String title;
    private String author;
    private String sourcePlatform;
    private String sourceUrl;
    private String importType;
    private String sourceFile;
    private Double dataQualityScore;
    private String category;
    private Long playCount;
    private Long likeCount;
    private Long commentCount;
    private LocalDateTime publishTime;
    private LocalDateTime importTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getSourcePlatform() {
        return sourcePlatform;
    }

    public void setSourcePlatform(String sourcePlatform) {
        this.sourcePlatform = sourcePlatform;
    }

    public String getSourceUrl() {
        return sourceUrl;
    }

    public void setSourceUrl(String sourceUrl) {
        this.sourceUrl = sourceUrl;
    }

    public String getImportType() {
        return importType;
    }

    public void setImportType(String importType) {
        this.importType = importType;
    }

    public String getSourceFile() {
        return sourceFile;
    }

    public void setSourceFile(String sourceFile) {
        this.sourceFile = sourceFile;
    }

    public Double getDataQualityScore() {
        return dataQualityScore;
    }

    public void setDataQualityScore(Double dataQualityScore) {
        this.dataQualityScore = dataQualityScore;
    }

    public Long getPlayCount() {
        return playCount;
    }

    public void setPlayCount(Long playCount) {
        this.playCount = playCount;
    }

    public Long getLikeCount() {
        return likeCount;
    }

    public void setLikeCount(Long likeCount) {
        this.likeCount = likeCount;
    }

    public Long getCommentCount() {
        return commentCount;
    }

    public void setCommentCount(Long commentCount) {
        this.commentCount = commentCount;
    }

    public LocalDateTime getPublishTime() {
        return publishTime;
    }

    public void setPublishTime(LocalDateTime publishTime) {
        this.publishTime = publishTime;
    }

    public LocalDateTime getImportTime() {
        return importTime;
    }

    public void setImportTime(LocalDateTime importTime) {
        this.importTime = importTime;
    }
}
