package com.xgsheng.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xgsheng.backend.config.WechatMiniProgramProperties;
import com.xgsheng.backend.exception.BusinessException;
import com.xgsheng.backend.project.InviteToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@Service
public class WechatMiniProgramService {

    private static final Logger log = LoggerFactory.getLogger(WechatMiniProgramService.class);
    private static final Set<Integer> INVALID_ACCESS_TOKEN = Set.of(40001, 40014, 42001);
    private static final String MINI_CODE_FAILED = "wechat mini code failed";

    private final WechatMiniProgramProperties properties;
    private final ObjectMapper objectMapper;
    private final WechatTransport transport;

    private volatile String cachedAccessToken;
    private volatile Instant accessTokenExpiresAt;

    @Autowired
    public WechatMiniProgramService(WechatMiniProgramProperties properties) {
        this(properties, new JdkWechatTransport());
    }

    WechatMiniProgramService(WechatMiniProgramProperties properties, WechatTransport transport) {
        this.properties = properties;
        this.objectMapper = new ObjectMapper();
        this.transport = transport;
    }

    public boolean isConfigured() {
        return properties.isConfigured();
    }

    public MiniappSessionInfo exchangeCodeForSession(String code) throws IOException, InterruptedException {
        if (!isConfigured()) {
            throw new IllegalStateException("wechat miniapp config missing");
        }
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("code is required");
        }

        String url = "https://api.weixin.qq.com/sns/jscode2session"
                + "?appid=" + urlEncode(properties.getAppId())
                + "&secret=" + urlEncode(properties.getAppSecret())
                + "&js_code=" + urlEncode(code)
                + "&grant_type=authorization_code";

        JsonNode node = objectMapper.readTree(transport.get(URI.create(url)));

        if (!node.path("errcode").isMissingNode() && node.path("errcode").asInt() != 0) {
            throw new IllegalStateException("jscode2session failed: " + node.path("errmsg").asText("unknown error"));
        }

        String openId = node.path("openid").asText();
        if (openId == null || openId.isBlank()) {
            throw new IllegalStateException("openid missing");
        }

        MiniappSessionInfo info = new MiniappSessionInfo();
        info.setOpenId(openId);
        info.setSessionKey(node.path("session_key").asText(""));
        return info;
    }

    public byte[] createPluginLoginCode(String token) throws IOException, InterruptedException {
        try {
            return requestUnlimitedCode(token, true);
        } catch (WechatMiniCodeException ex) {
            throw new IllegalStateException("wechat mini code failed: " + ex.errmsg);
        }
    }

    public byte[] createInviteMiniCode(String scene) {
        if (!InviteToken.isScene(scene) || !isConfigured()) {
            throw BusinessException.unavailable(MINI_CODE_FAILED);
        }
        try {
            return requestUnlimitedCode(scene, true);
        } catch (WechatMiniCodeException ex) {
            log.warn("Invite mini code rejected, errcode={}", ex.errcode);
            throw BusinessException.unavailable(MINI_CODE_FAILED);
        } catch (IOException | InterruptedException | RuntimeException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.warn("Invite mini code request failed");
            throw BusinessException.unavailable(MINI_CODE_FAILED);
        }
    }

    private byte[] requestUnlimitedCode(String scene, boolean retryOnInvalidToken)
            throws IOException, InterruptedException, WechatMiniCodeException {
        String accessToken = getAccessToken();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("scene", scene);
        payload.put("page", "pages/index/index");
        payload.put("check_path", false);
        payload.put("width", 430);
        payload.put("env_version", properties.getCodeEnvVersion());
        byte[] body = transport.post(
                URI.create("https://api.weixin.qq.com/wxa/getwxacodeunlimit?access_token=" + accessToken),
                objectMapper.writeValueAsString(payload));
        if (!isWechatJsonError(body)) {
            return body;
        }
        JsonNode node = objectMapper.readTree(body);
        int errcode = node.path("errcode").asInt(-1);
        if (retryOnInvalidToken && INVALID_ACCESS_TOKEN.contains(errcode)) {
            invalidateAccessToken();
            return requestUnlimitedCode(scene, false);
        }
        throw new WechatMiniCodeException(errcode, node.path("errmsg").asText("unknown error"));
    }

    private synchronized String getAccessToken() throws IOException, InterruptedException {
        Instant now = Instant.now();
        if (cachedAccessToken != null && accessTokenExpiresAt != null && now.isBefore(accessTokenExpiresAt)) {
            return cachedAccessToken;
        }

        String url = "https://api.weixin.qq.com/cgi-bin/token?grant_type=client_credential"
                + "&appid=" + urlEncode(properties.getAppId())
                + "&secret=" + urlEncode(properties.getAppSecret());
        JsonNode node = objectMapper.readTree(transport.get(URI.create(url)));
        if (!node.path("errcode").isMissingNode() && node.path("errcode").asInt() != 0) {
            throw new IllegalStateException("get access token failed: " + node.path("errmsg").asText("unknown error"));
        }

        cachedAccessToken = node.path("access_token").asText();
        int expiresIn = node.path("expires_in").asInt(7200);
        accessTokenExpiresAt = now.plusSeconds(Math.max(300, expiresIn - 300L));
        return cachedAccessToken;
    }

    private synchronized void invalidateAccessToken() {
        cachedAccessToken = null;
        accessTokenExpiresAt = null;
    }

    private boolean isWechatJsonError(byte[] body) {
        if (body == null || body.length == 0) {
            return true;
        }
        return body[0] == '{';
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private static final class WechatMiniCodeException extends Exception {
        private final int errcode;
        private final String errmsg;

        private WechatMiniCodeException(int errcode, String errmsg) {
            this.errcode = errcode;
            this.errmsg = errmsg;
        }
    }

    private static final class JdkWechatTransport implements WechatTransport {
        private final HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        @Override
        public String get(URI uri) throws IOException, InterruptedException {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(uri)
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();
            return httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)).body();
        }

        @Override
        public byte[] post(URI uri, String json) throws IOException, InterruptedException {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(uri)
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(15))
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();
            return httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray()).body();
        }
    }

    public static class MiniappSessionInfo {
        private String openId;
        private String sessionKey;

        public String getOpenId() {
            return openId;
        }

        public void setOpenId(String openId) {
            this.openId = openId;
        }

        public String getSessionKey() {
            return sessionKey;
        }

        public void setSessionKey(String sessionKey) {
            this.sessionKey = sessionKey;
        }
    }
}
