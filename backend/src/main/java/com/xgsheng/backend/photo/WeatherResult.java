package com.xgsheng.backend.photo;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 天气快照。status 为 ok、failed 或 rate_limited。ok 必须带齐文字、温度、代码和观测时间。
 * failed 时可用 weatherText 携带上游原因，供预览接口透出。
 */
public record WeatherResult(String status,
                            String weatherText,
                            BigDecimal temperatureC,
                            BigDecimal humidityPercent,
                            String weatherCode,
                            LocalDateTime observedAt) {

    public static WeatherResult failed() {
        return failed(null);
    }

    public static WeatherResult failed(String detail) {
        return new WeatherResult("failed", detail, null, null, null, null);
    }

    public static WeatherResult rateLimited() {
        return new WeatherResult("rate_limited", null, null, null, null, null);
    }

    public boolean complete() {
        return "ok".equals(status)
                && weatherText != null && !weatherText.isBlank()
                && temperatureC != null
                && weatherCode != null && !weatherCode.isBlank()
                && observedAt != null;
    }
}
