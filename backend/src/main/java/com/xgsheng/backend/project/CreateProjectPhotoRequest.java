package com.xgsheng.backend.project;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 创建项目照片时客户端可以提交的字段。地点名称和天气不在这里。
 */
public class CreateProjectPhotoRequest {

    private Long imageId;
    private String clientRequestId;
    private LocalDateTime takenAt;
    private String timezone;
    private String note;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private String coordinateSystem;
    private BigDecimal locationAccuracyMeters;
    private LocalDateTime locationCapturedAt;
    private String gpsStatus;
    private String watermarkPosition;
    private Boolean watermarkVisible;
    private Integer widthPx;
    private Integer heightPx;

    public Long getImageId() {
        return imageId;
    }

    public void setImageId(Long imageId) {
        this.imageId = imageId;
    }

    public String getClientRequestId() {
        return clientRequestId;
    }

    public void setClientRequestId(String clientRequestId) {
        this.clientRequestId = clientRequestId;
    }

    public LocalDateTime getTakenAt() {
        return takenAt;
    }

    public void setTakenAt(LocalDateTime takenAt) {
        this.takenAt = takenAt;
    }

    public String getTimezone() {
        return timezone;
    }

    public void setTimezone(String timezone) {
        this.timezone = timezone;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public BigDecimal getLatitude() {
        return latitude;
    }

    public void setLatitude(BigDecimal latitude) {
        this.latitude = latitude;
    }

    public BigDecimal getLongitude() {
        return longitude;
    }

    public void setLongitude(BigDecimal longitude) {
        this.longitude = longitude;
    }

    public String getCoordinateSystem() {
        return coordinateSystem;
    }

    public void setCoordinateSystem(String coordinateSystem) {
        this.coordinateSystem = coordinateSystem;
    }

    public BigDecimal getLocationAccuracyMeters() {
        return locationAccuracyMeters;
    }

    public void setLocationAccuracyMeters(BigDecimal locationAccuracyMeters) {
        this.locationAccuracyMeters = locationAccuracyMeters;
    }

    public LocalDateTime getLocationCapturedAt() {
        return locationCapturedAt;
    }

    public void setLocationCapturedAt(LocalDateTime locationCapturedAt) {
        this.locationCapturedAt = locationCapturedAt;
    }

    public String getGpsStatus() {
        return gpsStatus;
    }

    public void setGpsStatus(String gpsStatus) {
        this.gpsStatus = gpsStatus;
    }

    public String getWatermarkPosition() {
        return watermarkPosition;
    }

    public void setWatermarkPosition(String watermarkPosition) {
        this.watermarkPosition = watermarkPosition;
    }

    public Boolean getWatermarkVisible() {
        return watermarkVisible;
    }

    public void setWatermarkVisible(Boolean watermarkVisible) {
        this.watermarkVisible = watermarkVisible;
    }

    public Integer getWidthPx() {
        return widthPx;
    }

    public void setWidthPx(Integer widthPx) {
        this.widthPx = widthPx;
    }

    public Integer getHeightPx() {
        return heightPx;
    }

    public void setHeightPx(Integer heightPx) {
        this.heightPx = heightPx;
    }
}
