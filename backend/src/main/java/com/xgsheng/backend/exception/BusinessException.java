package com.xgsheng.backend.exception;

import org.springframework.http.HttpStatus;

/**
 * 业务规则失败。由 GlobalExceptionHandler 按显式 HTTP 状态码返回 {code, message}。
 * 不继承 IllegalStateException，避免被当成 403 或未登录。
 */
public class BusinessException extends RuntimeException {

    private final int status;

    public BusinessException(HttpStatus status, String message) {
        this(status.value(), message);
    }

    public BusinessException(int status, String message) {
        super(requireMessage(message));
        if (status < 400 || status > 599) {
            throw new IllegalArgumentException("business status must be an HTTP error status");
        }
        this.status = status;
    }

    public int getStatus() {
        return status;
    }

    public static BusinessException badRequest(String message) {
        return new BusinessException(400, message);
    }

    public static BusinessException forbidden(String message) {
        return new BusinessException(403, message);
    }

    public static BusinessException notFound(String message) {
        return new BusinessException(404, message);
    }

    public static BusinessException conflict(String message) {
        return new BusinessException(409, message);
    }

    public static BusinessException unavailable(String message) {
        return new BusinessException(503, message);
    }

    private static String requireMessage(String message) {
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("business message is required");
        }
        return message;
    }
}
