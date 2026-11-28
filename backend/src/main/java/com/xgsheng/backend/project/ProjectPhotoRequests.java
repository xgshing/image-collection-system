package com.xgsheng.backend.project;

import com.fasterxml.jackson.databind.JsonNode;
import com.xgsheng.backend.exception.BusinessException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.Iterator;
import java.util.Set;

/**
 * 只接受设计里写明的字段。创建时出现地点或天气快照会拒绝；修改时出现其它字段也会拒绝。
 */
public final class ProjectPhotoRequests {

    public static final String CLIENT_SNAPSHOT = "client snapshot not allowed";
    public static final String FIELD_NOT_EDITABLE = "field not editable";

    private static final Set<String> CREATE_FIELDS = Set.of(
            "imageId", "clientRequestId", "takenAt", "timezone", "note",
            "latitude", "longitude", "coordinateSystem", "locationAccuracyMeters",
            "locationCapturedAt", "gpsStatus", "watermarkPosition", "watermarkVisible",
            "widthPx", "heightPx");
    private static final Set<String> SNAPSHOT_FIELDS = Set.of(
            "locationText", "weatherText", "temperatureC", "humidityPercent",
            "weatherCode", "weatherProvider", "weatherObservedAt", "weatherStatus", "geocodeStatus");
    private static final Set<String> PATCH_FIELDS = Set.of("note", "watermarkPosition", "watermarkVisible");

    private ProjectPhotoRequests() {
    }

    public static CreateProjectPhotoRequest parseCreate(JsonNode body) {
        JsonNode object = object(body);
        rejectUnknown(object, CREATE_FIELDS, SNAPSHOT_FIELDS);
        CreateProjectPhotoRequest request = new CreateProjectPhotoRequest();
        request.setImageId(longValue(object.get("imageId"), "imageId is required"));
        request.setClientRequestId(text(object.get("clientRequestId")));
        request.setTakenAt(time(object.get("takenAt"), "takenAt is required"));
        request.setTimezone(text(object.get("timezone")));
        request.setNote(object.has("note") ? text(object.get("note")) : null);
        request.setLatitude(decimal(object.get("latitude")));
        request.setLongitude(decimal(object.get("longitude")));
        request.setCoordinateSystem(text(object.get("coordinateSystem")));
        request.setLocationAccuracyMeters(decimal(object.get("locationAccuracyMeters")));
        request.setLocationCapturedAt(object.hasNonNull("locationCapturedAt")
                ? time(object.get("locationCapturedAt"), "locationCapturedAt is invalid")
                : null);
        request.setGpsStatus(text(object.get("gpsStatus")));
        request.setWatermarkPosition(text(object.get("watermarkPosition")));
        request.setWatermarkVisible(object.has("watermarkVisible") && !object.get("watermarkVisible").isNull()
                ? bool(object.get("watermarkVisible"))
                : null);
        request.setWidthPx(integer(object.get("widthPx")));
        request.setHeightPx(integer(object.get("heightPx")));
        return request;
    }

    public static UpdateProjectPhotoRequest parseUpdate(JsonNode body) {
        JsonNode object = object(body);
        Iterator<String> names = object.fieldNames();
        boolean any = false;
        while (names.hasNext()) {
            String name = names.next();
            if (!PATCH_FIELDS.contains(name)) {
                throw BusinessException.badRequest(FIELD_NOT_EDITABLE);
            }
            any = true;
        }
        if (!any) {
            throw new IllegalArgumentException("photo update is empty");
        }
        UpdateProjectPhotoRequest request = new UpdateProjectPhotoRequest();
        if (object.has("note")) {
            request.setNotePresent(true);
            request.setNote(text(object.get("note")));
        }
        if (object.has("watermarkPosition")) {
            request.setWatermarkPositionPresent(true);
            request.setWatermarkPosition(text(object.get("watermarkPosition")));
        }
        if (object.has("watermarkVisible")) {
            request.setWatermarkVisiblePresent(true);
            request.setWatermarkVisible(bool(object.get("watermarkVisible")));
        }
        return request;
    }

    private static void rejectUnknown(JsonNode object, Set<String> allowed, Set<String> snapshots) {
        Iterator<String> names = object.fieldNames();
        while (names.hasNext()) {
            String name = names.next();
            if (snapshots.contains(name)) {
                throw BusinessException.badRequest(CLIENT_SNAPSHOT);
            }
            if (!allowed.contains(name)) {
                throw BusinessException.badRequest(FIELD_NOT_EDITABLE);
            }
        }
    }

    private static JsonNode object(JsonNode body) {
        if (body == null || !body.isObject()) {
            throw new IllegalArgumentException("photo body is required");
        }
        return body;
    }

    private static String text(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (!node.isTextual()) {
            throw new IllegalArgumentException("photo field must be text");
        }
        return node.asText();
    }

    private static Long longValue(JsonNode node, String message) {
        if (node == null || node.isNull() || !node.isNumber()) {
            throw new IllegalArgumentException(message);
        }
        return node.longValue();
    }

    private static BigDecimal decimal(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (!node.isNumber()) {
            throw new IllegalArgumentException("gps payload invalid");
        }
        return node.decimalValue();
    }

    private static Integer integer(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (!node.isNumber()) {
            throw new IllegalArgumentException("photo size is invalid");
        }
        return node.intValue();
    }

    private static Boolean bool(JsonNode node) {
        if (node == null || node.isNull() || !node.isBoolean()) {
            throw new IllegalArgumentException("watermarkVisible is invalid");
        }
        return node.booleanValue();
    }

    private static LocalDateTime time(JsonNode node, String message) {
        String value = text(node);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        try {
            return OffsetDateTime.parse(value).toInstant().atOffset(java.time.ZoneOffset.UTC).toLocalDateTime();
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException(message);
        }
    }
}
