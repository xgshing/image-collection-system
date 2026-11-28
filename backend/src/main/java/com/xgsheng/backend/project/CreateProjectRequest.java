package com.xgsheng.backend.project;

public class CreateProjectRequest {

    private String name;
    private Boolean watermarkEnabled;
    private String defaultWatermarkPosition;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Boolean getWatermarkEnabled() {
        return watermarkEnabled;
    }

    public void setWatermarkEnabled(Boolean watermarkEnabled) {
        this.watermarkEnabled = watermarkEnabled;
    }

    public String getDefaultWatermarkPosition() {
        return defaultWatermarkPosition;
    }

    public void setDefaultWatermarkPosition(String defaultWatermarkPosition) {
        this.defaultWatermarkPosition = defaultWatermarkPosition;
    }
}
