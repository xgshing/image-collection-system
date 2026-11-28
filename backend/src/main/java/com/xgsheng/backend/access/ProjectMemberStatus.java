package com.xgsheng.backend.access;

public enum ProjectMemberStatus {
    ACTIVE("active"),
    LEFT("left"),
    REMOVED("removed");

    private final String code;

    ProjectMemberStatus(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    public static ProjectMemberStatus fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (ProjectMemberStatus status : values()) {
            if (status.code.equals(code)) {
                return status;
            }
        }
        return null;
    }
}
