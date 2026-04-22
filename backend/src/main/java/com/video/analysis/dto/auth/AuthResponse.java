package com.video.analysis.dto.auth;

import java.time.LocalDateTime;

public class AuthResponse {

    private boolean success;
    private String message;
    private String token;
    private LocalDateTime expiresAt;
    private UserInfo user;

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }

    public UserInfo getUser() {
        return user;
    }

    public void setUser(UserInfo user) {
        this.user = user;
    }

    public static AuthResponse fail(String message) {
        AuthResponse response = new AuthResponse();
        response.setSuccess(false);
        response.setMessage(message);
        return response;
    }

    public static AuthResponse success(String message, String token, LocalDateTime expiresAt, UserInfo user) {
        AuthResponse response = new AuthResponse();
        response.setSuccess(true);
        response.setMessage(message);
        response.setToken(token);
        response.setExpiresAt(expiresAt);
        response.setUser(user);
        return response;
    }

    public static class UserInfo {
        private Long id;
        private String username;
        private String role;
        private String roleLabel;
        private String creatorName;
        private String creatorPlatform;
        private String creatorFocusCategory;
        private LocalDateTime createdAt;

        public UserInfo() {
        }

        public UserInfo(
                Long id,
                String username,
                String role,
                String roleLabel,
                String creatorName,
                String creatorPlatform,
                String creatorFocusCategory,
                LocalDateTime createdAt
        ) {
            this.id = id;
            this.username = username;
            this.role = role;
            this.roleLabel = roleLabel;
            this.creatorName = creatorName;
            this.creatorPlatform = creatorPlatform;
            this.creatorFocusCategory = creatorFocusCategory;
            this.createdAt = createdAt;
        }

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getRole() {
            return role;
        }

        public void setRole(String role) {
            this.role = role;
        }

        public String getRoleLabel() {
            return roleLabel;
        }

        public void setRoleLabel(String roleLabel) {
            this.roleLabel = roleLabel;
        }

        public String getCreatorName() {
            return creatorName;
        }

        public void setCreatorName(String creatorName) {
            this.creatorName = creatorName;
        }

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

        public LocalDateTime getCreatedAt() {
            return createdAt;
        }

        public void setCreatedAt(LocalDateTime createdAt) {
            this.createdAt = createdAt;
        }
    }
}
