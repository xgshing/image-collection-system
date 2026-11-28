package com.xgsheng.backend.access;

public enum ProjectRole {
    OWNER("owner"),
    MEMBER("member");

    private final String code;

    ProjectRole(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    public static ProjectRole fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (ProjectRole role : values()) {
            if (role.code.equals(code)) {
                return role;
            }
        }
        return null;
    }
}
