package com.xgsheng.backend.service;

import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 预览接口的坐标。缓存键使用指定小数位，真正请求仍使用客户端提交的原始坐标。
 */
final class PreviewCoordinates {

    private final BigDecimal submittedLatitude;
    private final BigDecimal submittedLongitude;
    private final BigDecimal latitude;
    private final BigDecimal longitude;

    private PreviewCoordinates(BigDecimal submittedLatitude,
                               BigDecimal submittedLongitude,
                               BigDecimal latitude,
                               BigDecimal longitude) {
        this.submittedLatitude = submittedLatitude;
        this.submittedLongitude = submittedLongitude;
        this.latitude = latitude;
        this.longitude = longitude;
    }

    static PreviewCoordinates require(JsonNode body, int scale) {
        if (body == null || !body.isObject()) {
            throw new IllegalArgumentException("coordinate body is required");
        }
        BigDecimal latitude = decimal(body.get("latitude"), "latitude is invalid");
        BigDecimal longitude = decimal(body.get("longitude"), "longitude is invalid");
        if (latitude.compareTo(BigDecimal.valueOf(-90)) < 0 || latitude.compareTo(BigDecimal.valueOf(90)) > 0) {
            throw new IllegalArgumentException("latitude is invalid");
        }
        if (longitude.compareTo(BigDecimal.valueOf(-180)) < 0 || longitude.compareTo(BigDecimal.valueOf(180)) > 0) {
            throw new IllegalArgumentException("longitude is invalid");
        }
        JsonNode capturedAt = body.get("capturedAt");
        if (capturedAt != null && !capturedAt.isNull() && !capturedAt.isTextual()) {
            throw new IllegalArgumentException("capturedAt is invalid");
        }
        return new PreviewCoordinates(
                latitude,
                longitude,
                latitude.setScale(scale, RoundingMode.HALF_UP),
                longitude.setScale(scale, RoundingMode.HALF_UP));
    }

    BigDecimal submittedLatitude() {
        return submittedLatitude;
    }

    BigDecimal submittedLongitude() {
        return submittedLongitude;
    }

    BigDecimal latitude() {
        return latitude;
    }

    BigDecimal longitude() {
        return longitude;
    }

    private static BigDecimal decimal(JsonNode node, String message) {
        if (node == null || node.isNull() || !node.isNumber()) {
            throw new IllegalArgumentException(message);
        }
        return node.decimalValue();
    }
}
