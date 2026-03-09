package com.video.analysis.dto;

import java.time.LocalDateTime;

public class UserInterestVO {

    private Long userId;
    private String userName;
    private Long actionCount;
    private Long playCount;
    private Long likeCount;
    private Long commentActionCount;
    private String favoriteCategory;
    private String favoritePlatform;
    private Long categoryDiversity;
    private Long platformDiversity;
    private Long activeDays;
    private LocalDateTime lastActiveTime;
    private Double avgDailyActions;
    private Double likeRate;
    private Double commentRate;
    private Double interactionRate;
    private String profileLabel;
    private String profileInsight;

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public Long getActionCount() {
        return actionCount;
    }

    public void setActionCount(Long actionCount) {
        this.actionCount = actionCount;
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

    public Long getCommentActionCount() {
        return commentActionCount;
    }

    public void setCommentActionCount(Long commentActionCount) {
        this.commentActionCount = commentActionCount;
    }

    public String getFavoriteCategory() {
        return favoriteCategory;
    }

    public void setFavoriteCategory(String favoriteCategory) {
        this.favoriteCategory = favoriteCategory;
    }

    public String getFavoritePlatform() {
        return favoritePlatform;
    }

    public void setFavoritePlatform(String favoritePlatform) {
        this.favoritePlatform = favoritePlatform;
    }

    public Long getCategoryDiversity() {
        return categoryDiversity;
    }

    public void setCategoryDiversity(Long categoryDiversity) {
        this.categoryDiversity = categoryDiversity;
    }

    public Long getPlatformDiversity() {
        return platformDiversity;
    }

    public void setPlatformDiversity(Long platformDiversity) {
        this.platformDiversity = platformDiversity;
    }

    public Long getActiveDays() {
        return activeDays;
    }

    public void setActiveDays(Long activeDays) {
        this.activeDays = activeDays;
    }

    public LocalDateTime getLastActiveTime() {
        return lastActiveTime;
    }

    public void setLastActiveTime(LocalDateTime lastActiveTime) {
        this.lastActiveTime = lastActiveTime;
    }

    public Double getAvgDailyActions() {
        return avgDailyActions;
    }

    public void setAvgDailyActions(Double avgDailyActions) {
        this.avgDailyActions = avgDailyActions;
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

    public Double getInteractionRate() {
        return interactionRate;
    }

    public void setInteractionRate(Double interactionRate) {
        this.interactionRate = interactionRate;
    }

    public String getProfileLabel() {
        return profileLabel;
    }

    public void setProfileLabel(String profileLabel) {
        this.profileLabel = profileLabel;
    }

    public String getProfileInsight() {
        return profileInsight;
    }

    public void setProfileInsight(String profileInsight) {
        this.profileInsight = profileInsight;
    }
}
