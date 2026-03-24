package com.xgsheng.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xgsheng.backend.config.WechatMiniProgramProperties;
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

@Service
public class WechatMiniProgramService {

    private final WechatMiniProgramProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    private volatile String cachedAccessToken;
    private volatile Instant accessTokenExpiresAt;

    public WechatMiniProgramService(WechatMiniProgramProperties properties) {
        this.properties = properties;
        this.objectMapper = new ObjectMapper();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public boolean isConfigured() {
        return properties.isConfigured();
    }

    public byte[] createPluginLoginCode(String token) throws IOException, InterruptedException {
        String accessToken = getAccessToken();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("scene", token);
        payload.put("page", "pages/index/index");
        payload.put("check_path", false);
        payload.put("width", 430);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.weixin.qq.com/wxa/getwxacodeunlimit?access_token=" + accessToken))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(15))
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                .build();

        HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
        byte[] body = response.body();

        if (isWechatJsonError(body)) {
            JsonNode node = objectMapper.readTree(body);
            throw new IllegalStateException("wechat mini code failed: " + node.path("errmsg").asText("unknown error"));
        }

        return body;
    }

    private synchronized String getAccessToken() throws IOException, InterruptedException {
        Instant now = Instant.now();
        if (cachedAccessToken != null && accessTokenExpiresAt != null && now.isBefore(accessTokenExpiresAt)) {
            return cachedAccessToken;
        }

        String url = "https://api.weixin.qq.com/cgi-bin/token?grant_type=client_credential"
                + "&appid=" + urlEncode(properties.getAppId())
                + "&secret=" + urlEncode(properties.getAppSecret());

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        JsonNode node = objectMapper.readTree(response.body());
        if (!node.path("errcode").isMissingNode() && node.path("errcode").asInt() != 0) {
            throw new IllegalStateException("get access token failed: " + node.path("errmsg").asText("unknown error"));
        }

        cachedAccessToken = node.path("access_token").asText();
        int expiresIn = node.path("expires_in").asInt(7200);
        accessTokenExpiresAt = now.plusSeconds(Math.max(300, expiresIn - 300L));
        return cachedAccessToken;
    }

    private boolean isWechatJsonError(byte[] body) {
        if (body == null || body.length == 0) {
            return true;
        }
        byte firstByte = body[0];
        return firstByte == '{';
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }
}
