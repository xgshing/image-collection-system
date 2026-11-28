package com.xgsheng.backend.exception;

import com.xgsheng.backend.controller.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.web.method.annotation.ExceptionHandlerMethodResolver;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.lang.reflect.Method;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class BusinessExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
    private final ExceptionHandlerMethodResolver resolver =
            new ExceptionHandlerMethodResolver(GlobalExceptionHandler.class);

    @Test
    void businessStatusesUseExplicitCodeAndMessage() {
        assertBusiness(400, "bad request");
        assertBusiness(403, "not a project member");
        assertBusiness(404, "project not found");
        assertBusiness(409, "image already belongs to another project");
        assertBusiness(503, "tencent lbs is not configured");
    }

    @Test
    void existingHandlersStayOnTheirOwnMethods() {
        assertResolved(new IllegalArgumentException("bad id"), "handleIllegalArgument");
        assertResolved(new IllegalStateException("unauthorized"), "handleIllegalState");
        assertResolved(new IllegalStateException("save image failed"), "handleIllegalState");
        assertResolved(new MaxUploadSizeExceededException(20L * 1024 * 1024), "handleMaxUploadSize");
        assertResolved(new RuntimeException("boom"), "handleException");
        assertResolved(BusinessException.forbidden("not a project member"), "handleBusiness");
        assertFalse(IllegalStateException.class.isAssignableFrom(BusinessException.class));
    }

    @Test
    void existingStatusBodiesStayUnchanged() {
        ResponseEntity<Map<String, Object>> badRequest = handler.handleIllegalArgument(
                new IllegalArgumentException("bad id"));
        assertEquals(400, badRequest.getStatusCode().value());
        assertEquals(400, badRequest.getBody().get("code"));
        assertEquals("bad id", badRequest.getBody().get("message"));

        ResponseEntity<Map<String, Object>> unauthorized = handler.handleIllegalState(
                new IllegalStateException("unauthorized"));
        assertEquals(401, unauthorized.getStatusCode().value());
        assertEquals("unauthorized", unauthorized.getBody().get("message"));

        ResponseEntity<Map<String, Object>> otherState = handler.handleIllegalState(
                new IllegalStateException("save image failed"));
        assertEquals(400, otherState.getStatusCode().value());
        assertEquals("save image failed", otherState.getBody().get("message"));

        ResponseEntity<Map<String, Object>> upload = handler.handleMaxUploadSize(
                new MaxUploadSizeExceededException(20L * 1024 * 1024));
        assertEquals(400, upload.getStatusCode().value());
        assertEquals("上传文件过大，单个文件最大允许 20MB", upload.getBody().get("message"));

        ResponseEntity<Map<String, Object>> unknown = handler.handleException(new RuntimeException("boom"));
        assertEquals(500, unknown.getStatusCode().value());
        assertEquals("internal server error", unknown.getBody().get("message"));
    }

    private void assertBusiness(int status, String message) {
        BusinessException ex = new BusinessException(status, message);
        assertResolved(ex, "handleBusiness");
        ResponseEntity<Map<String, Object>> response = handler.handleBusiness(ex);
        assertEquals(status, response.getStatusCode().value());
        assertEquals(status, response.getBody().get("code"));
        assertEquals(message, response.getBody().get("message"));
        assertInstanceOf(RuntimeException.class, ex);
    }

    private void assertResolved(Exception ex, String methodName) {
        Method method = resolver.resolveMethodByThrowable(ex);
        assertEquals(methodName, method.getName());
    }
}
