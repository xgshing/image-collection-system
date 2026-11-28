package com.xgsheng.backend.config;

/**
 * 外部服务是否已配置。只包含布尔状态，不包含密钥。
 */
public class ExternalServiceStatus {

    private final boolean lbsConfigured;
    private final boolean weatherConfigured;
    private final boolean speechConfigured;

    public ExternalServiceStatus(boolean lbsConfigured, boolean weatherConfigured, boolean speechConfigured) {
        this.lbsConfigured = lbsConfigured;
        this.weatherConfigured = weatherConfigured;
        this.speechConfigured = speechConfigured;
    }

    public boolean isLbsConfigured() {
        return lbsConfigured;
    }

    public boolean isWeatherConfigured() {
        return weatherConfigured;
    }

    public boolean isSpeechConfigured() {
        return speechConfigured;
    }

    @Override
    public String toString() {
        return "ExternalServiceStatus{lbsConfigured=" + lbsConfigured
                + ", weatherConfigured=" + weatherConfigured
                + ", speechConfigured=" + speechConfigured
                + "}";
    }
}
