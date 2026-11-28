package com.xgsheng.backend.project;

/**
 * 照片修改只携带记录、水印位置和水印开关。未出现的字段保持原值。
 */
public class UpdateProjectPhotoRequest {

    private boolean notePresent;
    private String note;
    private boolean watermarkPositionPresent;
    private String watermarkPosition;
    private boolean watermarkVisiblePresent;
    private Boolean watermarkVisible;

    public boolean isNotePresent() {
        return notePresent;
    }

    public void setNotePresent(boolean notePresent) {
        this.notePresent = notePresent;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public boolean isWatermarkPositionPresent() {
        return watermarkPositionPresent;
    }

    public void setWatermarkPositionPresent(boolean watermarkPositionPresent) {
        this.watermarkPositionPresent = watermarkPositionPresent;
    }

    public String getWatermarkPosition() {
        return watermarkPosition;
    }

    public void setWatermarkPosition(String watermarkPosition) {
        this.watermarkPosition = watermarkPosition;
    }

    public boolean isWatermarkVisiblePresent() {
        return watermarkVisiblePresent;
    }

    public void setWatermarkVisiblePresent(boolean watermarkVisiblePresent) {
        this.watermarkVisiblePresent = watermarkVisiblePresent;
    }

    public Boolean getWatermarkVisible() {
        return watermarkVisible;
    }

    public void setWatermarkVisible(Boolean watermarkVisible) {
        this.watermarkVisible = watermarkVisible;
    }
}
