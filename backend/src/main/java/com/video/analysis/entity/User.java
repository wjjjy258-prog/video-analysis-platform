package com.video.analysis.entity;

public class User {

    private Long userId;
    private String userName;
    private Long fans;
    private Long follow;
    private Integer level;

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

    public Long getFans() {
        return fans;
    }

    public void setFans(Long fans) {
        this.fans = fans;
    }

    public Long getFollow() {
        return follow;
    }

    public void setFollow(Long follow) {
        this.follow = follow;
    }

    public Integer getLevel() {
        return level;
    }

    public void setLevel(Integer level) {
        this.level = level;
    }
}
