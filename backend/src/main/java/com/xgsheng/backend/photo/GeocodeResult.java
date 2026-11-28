package com.xgsheng.backend.photo;

/**
 * 逆地理结果。失败时地点为空，不携带编造的地址；errorDetail 仅用于预览页展示原因。
 */
public record GeocodeResult(String locationText, String errorDetail) {

    public GeocodeResult(String locationText) {
        this(locationText, null);
    }

    public boolean ok() {
        return locationText != null && !locationText.isBlank();
    }
}
