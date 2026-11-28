package com.xgsheng.backend.entity;

import java.time.LocalDateTime;

public class ProjectMemberRow {

    private Long id;
    private Long projectId;
    private Long userId;
    private String role;
    private String status;
    private String nickName;
    private String avatarUrl;
    private LocalDateTime joinedAt;
    private Long removedByUserId;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getProjectId() {
        return projectId;
    }

    public void setProjectId(Long projectId) {
        this.projectId = projectId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getNickName() {
        return nickName;
    }

    public void setNickName(String nickName) {
        this.nickName = nickName;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }

    public LocalDateTime getJoinedAt() {
        return joinedAt;
    }

    public void setJoinedAt(LocalDateTime joinedAt) {
        this.joinedAt = joinedAt;
    }

    public Long getRemovedByUserId() {
        return removedByUserId;
    }

    public void setRemovedByUserId(Long removedByUserId) {
        this.removedByUserId = removedByUserId;
    }
}
