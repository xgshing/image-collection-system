package com.xgsheng.backend.photo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xgsheng.backend.config.ExternalServiceProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 腾讯位置服务逆地理。密钥只放在请求里，日志不记录完整地址。
 */
@Component
public class TencentGeocodeClient implements GeocodeClient {

    private static final Logger log = LoggerFactory.getLogger(TencentGeocodeClient.class);
    /** 同一坐标短时复用，避免调试反复进拍照页把日配额打光。 */
    private static final long CACHE_TTL_MS = 10 * 60 * 1000L;
    private static final ConcurrentHashMap<String, CachedGeocode> CACHE = new ConcurrentHashMap<>();

    private final ExternalServiceProperties properties;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    @Autowired
    public TencentGeocodeClient(ExternalServiceProperties properties) {
        this(properties, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build(), new ObjectMapper());
    }

    TencentGeocodeClient(ExternalServiceProperties properties, HttpClient httpClient, ObjectMapper objectMapper) {
        this.properties = properties;
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public GeocodeResult reverse(BigDecimal latitude, BigDecimal longitude) {
        if (!properties.isLbsConfigured() || latitude == null || longitude == null) {
            return new GeocodeResult(null, "tencent lbs is not configured");
        }
        String cacheKey = cacheKey(latitude, longitude);
        CachedGeocode cached = CACHE.get(cacheKey);
        if (cached != null && cached.fresh()) {
            return cached.result;
        }
        try {
            String location = latitude.toPlainString() + "," + longitude.toPlainString();
            String key = URLEncoder.encode(properties.getTencentLbsKey(), StandardCharsets.UTF_8);
            URI uri = URI.create("https://apis.map.qq.com/ws/geocoder/v1/?location="
                    + URLEncoder.encode(location, StandardCharsets.UTF_8) + "&key=" + key);
            HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(3)).GET().build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.warn("Geocode request failed, status={}", response.statusCode());
                return new GeocodeResult(null, "腾讯LBS:HTTP" + response.statusCode());
            }
            GeocodeResult result = parse(objectMapper.readTree(response.body()));
            if (result.locationText() != null && !result.locationText().isBlank()) {
                CACHE.put(cacheKey, new CachedGeocode(result, System.currentTimeMillis()));
            }
            return result;
        } catch (Exception ex) {
            log.warn("Geocode request failed: {}", ex.toString());
            return new GeocodeResult(null, "腾讯LBS调用异常:" + safeMessage(ex));
        }
    }

    static GeocodeResult parse(JsonNode root) {
        if (root == null) {
            return new GeocodeResult(null, "腾讯LBS:空响应");
        }
        int status = root.path("status").asInt(-1);
        if (status != 0) {
            String message = root.path("message").asText("");
            if (status == 199) {
                return new GeocodeResult(null, "腾讯Key未开通逆地理Web服务");
            }
            if (status == 311) {
                return new GeocodeResult(null, "腾讯Key无效或已删除");
            }
            if (status == 110) {
                return new GeocodeResult(null, "腾讯Key请求来源未授权");
            }
            if (status == 112) {
                return new GeocodeResult(null, "腾讯Key未授权此服务器IP");
            }
            if (status == 120) {
                return new GeocodeResult(null, "腾讯逆地理每秒超限,稍后重试");
            }
            if (status == 121) {
                return new GeocodeResult(null, "腾讯逆地理日配额已用尽,请到控制台配额管理分配额度");
            }
            return new GeocodeResult(null, message.isBlank()
                    ? ("腾讯LBS:status=" + status)
                    : ("腾讯LBS:" + status + ":" + message));
        }
        JsonNode result = root.path("result");
        String recommend = result.path("formatted_addresses").path("recommend").asText("");
        String address = result.path("address").asText("");
        String text = !recommend.isBlank() ? recommend : address;
        if (text.isBlank()) {
            return new GeocodeResult(null, "腾讯LBS:地址为空");
        }
        return new GeocodeResult(text);
    }

    private static String safeMessage(Exception ex) {
        String message = ex.getMessage();
        if (message == null || message.isBlank()) {
            return ex.getClass().getSimpleName();
        }
        return message.length() > 80 ? message.substring(0, 80) : message;
    }

    private static String cacheKey(BigDecimal latitude, BigDecimal longitude) {
        return latitude.setScale(4, RoundingMode.HALF_UP).toPlainString()
                + ","
                + longitude.setScale(4, RoundingMode.HALF_UP).toPlainString();
    }

    private record CachedGeocode(GeocodeResult result, long atMs) {
        boolean fresh() {
            return System.currentTimeMillis() - atMs < CACHE_TTL_MS;
        }
    }
}
