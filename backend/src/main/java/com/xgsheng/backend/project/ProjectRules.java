package com.xgsheng.backend.project;

public final class ProjectRules {

    public static final int NAME_MAX_LENGTH = 100;

    private ProjectRules() {
    }

    public static String normalizeName(String name) {
        if (name == null) {
            throw new IllegalArgumentException("project name is required");
        }
        String trimmed = name.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("project name is required");
        }
        if (trimmed.codePointCount(0, trimmed.length()) > NAME_MAX_LENGTH) {
            throw new IllegalArgumentException("project name is too long");
        }
        if (containsControlCharacter(trimmed)) {
            throw new IllegalArgumentException("project name is invalid");
        }
        return trimmed;
    }

    public static String normalizePosition(String position, boolean useDefaultWhenMissing) {
        if (position == null || position.isBlank()) {
            if (useDefaultWhenMissing) {
                return ProjectWatermarkPosition.DEFAULT;
            }
            throw new IllegalArgumentException("invalid watermark position");
        }
        ProjectWatermarkPosition parsed = ProjectWatermarkPosition.fromCode(position);
        if (parsed == null) {
            throw new IllegalArgumentException("invalid watermark position");
        }
        return parsed.getCode();
    }

    private static boolean containsControlCharacter(String value) {
        return value.codePoints().anyMatch(code -> Character.isISOControl(code));
    }
}
