package com.xgsheng.backend.access;

/**
 * 一次权限判断所需的项目与成员快照。由查找接口提供，本类不访问数据库。
 */
public class ProjectAccessRecord {

    private Long ownerUserId;
    private Long userId;
    private String role;
    private String status;

    public Long getOwnerUserId() {
        return ownerUserId;
    }

    public void setOwnerUserId(Long ownerUserId) {
        this.ownerUserId = ownerUserId;
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
}
