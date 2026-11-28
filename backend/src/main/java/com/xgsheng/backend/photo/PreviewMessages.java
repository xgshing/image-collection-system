package com.xgsheng.backend.photo;

/**
 * 预览和识别接口使用的固定文案。和照片创建时的降级字段不是同一层响应。
 */
public final class PreviewMessages {

    public static final String GEOCODE_FAILED = "geocode failed";
    public static final String WEATHER_FAILED = "weather failed";
    public static final String RATE_LIMITED = "rate limited";
    public static final String SPEECH_FAILED = "speech failed";
    public static final String SPEECH_NOT_CONFIGURED = "tencent speech is not configured";
    public static final String AUDIO_TOO_LARGE = "audio too large";

    private PreviewMessages() {
    }
}
