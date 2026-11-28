package com.xgsheng.backend.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 项目照片。storedUrl 和 photographerNickName 只用于查询结果，不写入 project_photo。
 */
public class ProjectPhoto {

    private Long id;
    private Long projectId;
    private Long imageId;
    private Long photographerUserId;
    private LocalDateTime takenAt;
    private String timezone;
    private String note;
    private String locationText;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private String coordinateSystem;
    private BigDecimal locationAccuracyM;
    private LocalDateTime locationCapturedAt;
    private String gpsStatus;
    private String geocodeStatus;
    private String weatherText;
    private BigDecimal temperatureC;
    private BigDecimal humidityPercent;
    private String weatherCode;
    private String weatherProvider;
    private LocalDateTime weatherObservedAt;
    private String weatherStatus;
    private String watermarkPosition;
    private Integer watermarkVisible;
    private String projectNameSnapshot;
    private Integer widthPx;
    private Integer heightPx;
    private String clientRequestId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String photographerNickName;
    private String storedUrl;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getProjectId() {
        return projectId;
    }

    public void setProjectId(Long projectId) {
        this.projectId = projectId;
    }

    public Long getImageId() {
        return imageId;
    }

    public void setImageId(Long imageId) {
        this.imageId = imageId;
    }

    public Long getPhotographerUserId() {
        return photographerUserId;
    }

    public void setPhotographerUserId(Long photographerUserId) {
        this.photographerUserId = photographerUserId;
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

    public String getLocationText() {
        return locationText;
    }

    public void setLocationText(String locationText) {
        this.locationText = locationText;
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

    public BigDecimal getLocationAccuracyM() {
        return locationAccuracyM;
    }

    public void setLocationAccuracyM(BigDecimal locationAccuracyM) {
        this.locationAccuracyM = locationAccuracyM;
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

    public String getGeocodeStatus() {
        return geocodeStatus;
    }

    public void setGeocodeStatus(String geocodeStatus) {
        this.geocodeStatus = geocodeStatus;
    }

    public String getWeatherText() {
        return weatherText;
    }

    public void setWeatherText(String weatherText) {
        this.weatherText = weatherText;
    }

    public BigDecimal getTemperatureC() {
        return temperatureC;
    }

    public void setTemperatureC(BigDecimal temperatureC) {
        this.temperatureC = temperatureC;
    }

    public BigDecimal getHumidityPercent() {
        return humidityPercent;
    }

    public void setHumidityPercent(BigDecimal humidityPercent) {
        this.humidityPercent = humidityPercent;
    }

    public String getWeatherCode() {
        return weatherCode;
    }

    public void setWeatherCode(String weatherCode) {
        this.weatherCode = weatherCode;
    }

    public String getWeatherProvider() {
        return weatherProvider;
    }

    public void setWeatherProvider(String weatherProvider) {
        this.weatherProvider = weatherProvider;
    }

    public LocalDateTime getWeatherObservedAt() {
        return weatherObservedAt;
    }

    public void setWeatherObservedAt(LocalDateTime weatherObservedAt) {
        this.weatherObservedAt = weatherObservedAt;
    }

    public String getWeatherStatus() {
        return weatherStatus;
    }

    public void setWeatherStatus(String weatherStatus) {
        this.weatherStatus = weatherStatus;
    }

    public String getWatermarkPosition() {
        return watermarkPosition;
    }

    public void setWatermarkPosition(String watermarkPosition) {
        this.watermarkPosition = watermarkPosition;
    }

    public Integer getWatermarkVisible() {
        return watermarkVisible;
    }

    public void setWatermarkVisible(Integer watermarkVisible) {
        this.watermarkVisible = watermarkVisible;
    }

    public String getProjectNameSnapshot() {
        return projectNameSnapshot;
    }

    public void setProjectNameSnapshot(String projectNameSnapshot) {
        this.projectNameSnapshot = projectNameSnapshot;
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

    public String getClientRequestId() {
        return clientRequestId;
    }

    public void setClientRequestId(String clientRequestId) {
        this.clientRequestId = clientRequestId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getPhotographerNickName() {
        return photographerNickName;
    }

    public void setPhotographerNickName(String photographerNickName) {
        this.photographerNickName = photographerNickName;
    }

    public String getStoredUrl() {
        return storedUrl;
    }

    public void setStoredUrl(String storedUrl) {
        this.storedUrl = storedUrl;
    }
}
