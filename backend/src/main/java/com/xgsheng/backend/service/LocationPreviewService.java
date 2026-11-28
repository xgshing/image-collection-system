package com.xgsheng.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.xgsheng.backend.auth.AuthContext;
import com.xgsheng.backend.exception.BusinessException;
import com.xgsheng.backend.photo.GeocodeClient;
import com.xgsheng.backend.photo.GeocodeResult;
import com.xgsheng.backend.photo.PreviewMessages;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 地点预览。调用和照片创建相同的逆地理客户端，只缓存成功结果，不写数据库。
 */
@Service
public class LocationPreviewService {

    private static final Duration CACHE_TTL = Duration.ofMinutes(10);

    private final GeocodeClient geocodeClient;
    private final Clock clock;
    private final ConcurrentHashMap<String, CachedText> cache = new ConcurrentHashMap<>();

    @Autowired
    public LocationPreviewService(GeocodeClient geocodeClient) {
        this(geocodeClient, Clock.systemUTC());
    }

    LocationPreviewService(GeocodeClient geocodeClient, Clock clock) {
        this.geocodeClient = geocodeClient;
        this.clock = clock;
    }

    public Map<String, Object> reverse(JsonNode body) {
        AuthContext.requireUserId();
        PreviewCoordinates coordinates = PreviewCoordinates.require(body, 4);
        rejectForeignCoordinate(body);
        String key = coordinates.latitude().toPlainString() + "," + coordinates.longitude().toPlainString();
        String cached = readCache(key);
        if (cached == null) {
            GeocodeResult result = geocodeClient.reverse(coordinates.submittedLatitude(), coordinates.submittedLongitude());
            if (result == null || !result.ok()) {
                String detail = result != null && result.errorDetail() != null && !result.errorDetail().isBlank()
                        ? result.errorDetail()
                        : PreviewMessages.GEOCODE_FAILED;
                throw BusinessException.unavailable(detail);
            }
            cached = result.locationText();
            cache.put(key, new CachedText(cached, clock.instant().plus(CACHE_TTL)));
        }
        return Map.of(
                "locationText", cached,
                "provider", "tencent-lbs",
                "coordinateSystem", "gcj02");
    }

    private String readCache(String key) {
        CachedText cached = cache.get(key);
        if (cached == null) {
            return null;
        }
        if (clock.instant().isAfter(cached.expiresAt())) {
            cache.remove(key, cached);
            return null;
        }
        return cached.text();
    }

    private static void rejectForeignCoordinate(JsonNode body) {
        JsonNode system = body.get("coordinateSystem");
        if (system == null || system.isNull()) {
            return;
        }
        if (!system.isTextual() || !"gcj02".equals(system.asText())) {
            throw new IllegalArgumentException("coordinate system must be gcj02");
        }
    }

    private record CachedText(String text, Instant expiresAt) {
    }
}
