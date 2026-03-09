package com.video.analysis.dto;

import java.time.LocalDateTime;

public class SourceTraceVO {

    private Long id;
    private String title;
    private String author;
    private String sourcePlatform;
    private String sourceUrl;
    private String importType;
    private String sourceFile;
    private LocalDateTime importTime;
    private Double dataQualityScore;
    private String dedupeKey;

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

    public LocalDateTime getImportTime() {
        return importTime;
    }

    public void setImportTime(LocalDateTime importTime) {
        this.importTime = importTime;
    }

    public Double getDataQualityScore() {
        return dataQualityScore;
    }

    public void setDataQualityScore(Double dataQualityScore) {
        this.dataQualityScore = dataQualityScore;
    }

    public String getDedupeKey() {
        return dedupeKey;
    }

    public void setDedupeKey(String dedupeKey) {
        this.dedupeKey = dedupeKey;
    }
}
