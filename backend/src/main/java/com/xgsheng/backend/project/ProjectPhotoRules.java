package com.xgsheng.backend.project;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.util.Set;

/**
 * 创建照片前的字段校验。GPS 成功才保留坐标，其它状态不允许带经纬度。
 */
public final class ProjectPhotoRules {

    public static final String GPS_INVALID = "gps payload invalid";
    public static final String IMAGE_NOT_FOUND = "image not found";
    public static final String IMAGE_USED = "image already belongs to another project";
    public static final String PHOTO_NOT_FOUND = "photo not found";
    public static final String ONLY_PHOTOGRAPHER = "only photographer can edit photo";
    public static final String DELETE_BLOCKED = "image is used by a project photo";
    public static final String RENDER_FAILED = "watermark render failed";
    public static final String ARCHIVED_READ_ONLY = "archived project is read only";
    public static final int NOTE_MAX = 500;
    public static final int REQUEST_ID_MAX = 64;

    private static final Set<String> GPS_STATUSES = Set.of(
            "ok", "low_accuracy", "denied", "system_off", "timeout", "empty");
    private static final Set<String> LOCATED = Set.of("ok", "low_accuracy");

    private ProjectPhotoRules() {
    }

    public static boolean isLocated(String gpsStatus) {
        return LOCATED.contains(gpsStatus);
    }

    public static void validateCreate(CreateProjectPhotoRequest request) {
        if (request == null || request.getImageId() == null || request.getImageId() <= 0) {
            throw new IllegalArgumentException("imageId is required");
        }
        String requestId = request.getClientRequestId() == null ? "" : request.getClientRequestId().trim();
        if (requestId.isEmpty() || requestId.length() > REQUEST_ID_MAX) {
            throw new IllegalArgumentException("clientRequestId is required");
        }
        request.setClientRequestId(requestId);
        if (request.getTakenAt() == null) {
            throw new IllegalArgumentException("takenAt is required");
        }
        String timezone = request.getTimezone() == null || request.getTimezone().isBlank()
                ? "Asia/Shanghai"
                : request.getTimezone().trim();
        try {
            ZoneId.of(timezone);
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException("timezone is invalid");
        }
        request.setTimezone(timezone);
        request.setNote(normalizeNote(request.getNote()));
        request.setGpsStatus(normalizeGps(request.getGpsStatus()));
        validateCoordinates(request);
        request.setWatermarkPosition(ProjectRules.normalizePosition(request.getWatermarkPosition(), false));
        if (request.getWatermarkVisible() == null) {
            request.setWatermarkVisible(true);
        }
        validatePixel(request.getWidthPx());
        validatePixel(request.getHeightPx());
    }

    public static String normalizeNote(String note) {
        String value = note == null ? "" : note;
        if (value.codePointCount(0, value.length()) > NOTE_MAX) {
            throw new IllegalArgumentException("note is too long");
        }
        return value;
    }

    private static String normalizeGps(String gpsStatus) {
        String value = gpsStatus == null || gpsStatus.isBlank() ? "empty" : gpsStatus.trim();
        if (!GPS_STATUSES.contains(value)) {
            throw new IllegalArgumentException(GPS_INVALID);
        }
        return value;
    }

    private static void validateCoordinates(CreateProjectPhotoRequest request) {
        boolean located = isLocated(request.getGpsStatus());
        if (!located) {
            if (request.getLatitude() != null || request.getLongitude() != null || request.getCoordinateSystem() != null) {
                throw new IllegalArgumentException(GPS_INVALID);
            }
            return;
        }
        if (request.getLatitude() == null || request.getLongitude() == null || !"gcj02".equals(request.getCoordinateSystem())) {
            throw new IllegalArgumentException(GPS_INVALID);
        }
        if (request.getLatitude().compareTo(BigDecimal.valueOf(-90)) < 0
                || request.getLatitude().compareTo(BigDecimal.valueOf(90)) > 0
                || request.getLongitude().compareTo(BigDecimal.valueOf(-180)) < 0
                || request.getLongitude().compareTo(BigDecimal.valueOf(180)) > 0) {
            throw new IllegalArgumentException(GPS_INVALID);
        }
        if (request.getLocationAccuracyMeters() != null
                && request.getLocationAccuracyMeters().compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException(GPS_INVALID);
        }
    }

    private static void validatePixel(Integer value) {
        if (value != null && value <= 0) {
            throw new IllegalArgumentException("photo size is invalid");
        }
    }
}
