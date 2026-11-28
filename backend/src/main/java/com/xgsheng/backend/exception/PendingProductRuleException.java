package com.xgsheng.backend.exception;

/**
 * 产品规则尚未确认。不映射成新的业务状态码，由现有未捕获异常分支返回 500。
 */
public class PendingProductRuleException extends RuntimeException {

    public PendingProductRuleException(String message) {
        super(message);
    }
}
