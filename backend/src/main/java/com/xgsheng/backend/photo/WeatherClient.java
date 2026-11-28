package com.xgsheng.backend.photo;

import java.math.BigDecimal;

public interface WeatherClient {

    /**
     * 未配置、失败或字段不齐时返回 failed。限流返回 rate_limited。不返回假天气。
     */
    WeatherResult current(BigDecimal latitude, BigDecimal longitude);
}
