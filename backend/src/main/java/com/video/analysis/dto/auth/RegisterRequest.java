package com.video.analysis.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class RegisterRequest {

    @NotBlank(message = "用户名不能为空")
    @Size(min = 3, max = 64, message = "用户名长度需在 3-64 之间")
    @Pattern(regexp = "^[A-Za-z0-9_\\-]+$", message = "用户名仅支持字母、数字、下划线和短横线")
    private String username;

    @NotBlank(message = "密码不能为空")
    @Size(min = 8, max = 128, message = "密码长度需在 8-128 之间")
    private String password;

    @NotBlank(message = "确认密码不能为空")
    @Size(min = 8, max = 128, message = "确认密码长度需在 8-128 之间")
    private String confirmPassword;

    @NotBlank(message = "身份不能为空")
    @Pattern(regexp = "^(viewer|creator)$", message = "身份仅支持 viewer 或 creator")
    private String role;

    @Size(max = 100, message = "创作者名称长度不能超过 100")
    private String creatorName;

    @Pattern(
            regexp = "^$|^(bilibili|douyin|kuaishou|xiaohongshu|xigua|weibo|youtube|tiktok|acfun|unknown)$",
            message = "主运营平台不在支持范围内"
    )
    private String creatorPlatform;

    @Size(max = 60, message = "主打方向长度不能超过 60")
    private String creatorFocusCategory;

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getConfirmPassword() {
        return confirmPassword;
    }

    public void setConfirmPassword(String confirmPassword) {
        this.confirmPassword = confirmPassword;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
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
}
