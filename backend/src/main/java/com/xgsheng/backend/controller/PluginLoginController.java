package com.xgsheng.backend.controller;

import com.xgsheng.backend.service.WechatMiniProgramService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api/plugin/login")
public class PluginLoginController {

    private static final Logger log = LoggerFactory.getLogger(PluginLoginController.class);
    private static final long SESSION_TTL_MILLIS = 5 * 60 * 1000L;
    private static final ConcurrentHashMap<String, LoginSession> SESSIONS = new ConcurrentHashMap<>();
    private final WechatMiniProgramService wechatMiniProgramService;

    public PluginLoginController(WechatMiniProgramService wechatMiniProgramService) {
        this.wechatMiniProgramService = wechatMiniProgramService;
    }

    @PostMapping("/session")
    public Map<String, Object> createSession() {
        cleanupExpiredSessions();

        String token = UUID.randomUUID().toString().replace("-", "");
        long expiresAt = System.currentTimeMillis() + SESSION_TTL_MILLIS;

        LoginSession session = new LoginSession();
        session.expiresAt = expiresAt;
        SESSIONS.put(token, session);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("token", token);
        result.put("qrContent", "ics-plugin-login:" + token);
        result.put("expiresAt", expiresAt);
        return result;
    }

    @GetMapping("/session/{token}/minicode")
    public ResponseEntity<byte[]> getMiniCode(@PathVariable String token) {
        cleanupExpiredSessions();

        LoginSession session = SESSIONS.get(token);
        if (session == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        if (!wechatMiniProgramService.isConfigured()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .contentType(MediaType.TEXT_PLAIN)
                    .body("wechat miniapp config missing".getBytes());
        }

        try {
            byte[] image = wechatMiniProgramService.createPluginLoginCode(token);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CACHE_CONTROL, "no-store")
                    .contentType(MediaType.IMAGE_PNG)
                    .body(image);
        } catch (Exception ex) {
            log.error("Failed to generate mini code for token {}", token, ex);
            String message = ex.getMessage() == null ? "mini code generation failed" : ex.getMessage();
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .contentType(MediaType.TEXT_PLAIN)
                    .body(message.getBytes());
        }
    }

    @GetMapping("/session/{token}")
    public Map<String, Object> getSession(@PathVariable String token) {
        cleanupExpiredSessions();

        LoginSession session = SESSIONS.get(token);
        if (session == null) {
            return buildStatus("expired", null, null);
        }

        if (session.confirmedAt != null) {
            return buildStatus("confirmed", session.profile, session.confirmedAt);
        }

        return buildStatus("pending", null, null);
    }

    @PostMapping("/confirm")
    public Map<String, Object> confirmLogin(@RequestBody Map<String, Object> params) {
        cleanupExpiredSessions();

        String token = stringValue(params.get("token"));
        LoginSession session = SESSIONS.get(token);
        if (session == null) {
            return buildStatus("expired", null, null);
        }

        String nickName = stringValue(params.get("nickName"));
        String avatarUrl = stringValue(params.get("avatarUrl"));
        if (nickName.isBlank() || avatarUrl.isBlank()) {
            return buildStatus("invalid_profile", null, null);
        }

        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("nickName", nickName);
        profile.put("avatarUrl", avatarUrl);

        session.profile = profile;
        session.confirmedAt = Instant.now().toString();

        return buildStatus("confirmed", profile, session.confirmedAt);
    }

    private static Map<String, Object> buildStatus(String status, Map<String, Object> profile, String confirmedAt) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", status);
        if (profile != null) {
            result.put("profile", profile);
        }
        if (confirmedAt != null) {
            result.put("confirmedAt", confirmedAt);
        }
        return result;
    }

    private static String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static void cleanupExpiredSessions() {
        long now = System.currentTimeMillis();
        SESSIONS.entrySet().removeIf(entry -> entry.getValue().expiresAt < now);
    }

    private static class LoginSession {
        private long expiresAt;
        private Map<String, Object> profile;
        private String confirmedAt;
    }
}
