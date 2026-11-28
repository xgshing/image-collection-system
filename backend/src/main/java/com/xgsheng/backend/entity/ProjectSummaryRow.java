package com.xgsheng.backend.entity;

import java.time.LocalDateTime;

public class ProjectSummaryRow {

    private Long id;
    private String name;
    private String status;
    private Integer watermarkEnabled;
    private String defaultWatermarkPosition;
    private String role;
    private String ownerNickName;
    private Long memberCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getWatermarkEnabled() {
        return watermarkEnabled;
    }

    public void setWatermarkEnabled(Integer watermarkEnabled) {
        this.watermarkEnabled = watermarkEnabled;
    }

    public String getDefaultWatermarkPosition() {
        return defaultWatermarkPosition;
    }

    public void setDefaultWatermarkPosition(String defaultWatermarkPosition) {
        this.defaultWatermarkPosition = defaultWatermarkPosition;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getOwnerNickName() {
        return ownerNickName;
    }

    public void setOwnerNickName(String ownerNickName) {
        this.ownerNickName = ownerNickName;
    }

    public Long getMemberCount() {
        return memberCount;
    }

    public void setMemberCount(Long memberCount) {
        this.memberCount = memberCount;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
