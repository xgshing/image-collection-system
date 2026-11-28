package com.xgsheng.backend.project;

public enum ProjectWatermarkPosition {
    TOP_LEFT("top_left"),
    TOP_RIGHT("top_right"),
    BOTTOM_LEFT("bottom_left"),
    BOTTOM_RIGHT("bottom_right");

    public static final String DEFAULT = BOTTOM_LEFT.code;

    private final String code;

    ProjectWatermarkPosition(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    public static ProjectWatermarkPosition fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (ProjectWatermarkPosition position : values()) {
            if (position.code.equals(code)) {
                return position;
            }
        }
        return null;
    }
}
