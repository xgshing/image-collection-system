package com.xgsheng.backend.photo;

import java.math.BigDecimal;

public interface GeocodeClient {

    /**
     * 未配置或调用失败时返回空地点，不抛给创建照片的主流程。
     */
    GeocodeResult reverse(BigDecimal latitude, BigDecimal longitude);
}
