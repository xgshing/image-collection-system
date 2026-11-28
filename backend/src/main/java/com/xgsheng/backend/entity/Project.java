package com.xgsheng.backend.entity;

import java.time.LocalDateTime;

public class Project {

    private Long id;
    private String name;
    private Long ownerUserId;
    private Integer watermarkEnabled;
    private String defaultWatermarkPosition;
    private String status;
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

    public Long getOwnerUserId() {
        return ownerUserId;
    }

    public void setOwnerUserId(Long ownerUserId) {
        this.ownerUserId = ownerUserId;
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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
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
