package com.xgsheng.backend.service;

import com.xgsheng.backend.entity.ProjectPhoto;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 照片响应。previewUrl 和原图地址由调用方在权限通过后填入，本类不签名。
 */
final class ProjectPhotoResponses {

    private ProjectPhotoResponses() {
    }

    static Map<String, Object> created(ProjectPhoto photo, boolean created) {
        Map<String, Object> body = detail(photo, null);
        body.put("created", created);
        return body;
    }

    static Map<String, Object> detail(ProjectPhoto photo, String previewUrl) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", photo.getId());
        body.put("projectId", photo.getProjectId());
        body.put("imageId", photo.getImageId());
        body.put("photographerUserId", photo.getPhotographerUserId());
        body.put("photographerNickName", photo.getPhotographerNickName());
        body.put("takenAt", ProjectResponses.formatTime(photo.getTakenAt()));
        body.put("timezone", photo.getTimezone());
        body.put("note", photo.getNote() == null ? "" : photo.getNote());
        body.put("latitude", photo.getLatitude());
        body.put("longitude", photo.getLongitude());
        body.put("gpsStatus", photo.getGpsStatus());
        body.put("geocodeStatus", photo.getGeocodeStatus());
        body.put("locationText", photo.getLocationText());
        body.put("weatherText", photo.getWeatherText());
        body.put("temperatureC", photo.getTemperatureC());
        body.put("humidityPercent", photo.getHumidityPercent());
        body.put("weatherCode", photo.getWeatherCode());
        body.put("weatherProvider", photo.getWeatherProvider());
        body.put("weatherObservedAt", ProjectResponses.formatTime(photo.getWeatherObservedAt()));
        body.put("weatherStatus", photo.getWeatherStatus());
        body.put("watermarkPosition", photo.getWatermarkPosition());
        body.put("watermarkVisible", photo.getWatermarkVisible() != null && photo.getWatermarkVisible() == 1);
        body.put("projectNameSnapshot", photo.getProjectNameSnapshot());
        body.put("widthPx", photo.getWidthPx());
        body.put("heightPx", photo.getHeightPx());
        if (previewUrl != null) {
            body.put("previewUrl", previewUrl);
        }
        return body;
    }
}
