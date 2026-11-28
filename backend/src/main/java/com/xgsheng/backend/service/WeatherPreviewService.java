package com.xgsheng.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.xgsheng.backend.auth.AuthContext;
import com.xgsheng.backend.exception.BusinessException;
import com.xgsheng.backend.photo.PreviewMessages;
import com.xgsheng.backend.photo.WeatherClient;
import com.xgsheng.backend.photo.WeatherResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 天气预览。和照片创建使用同一个天气客户端。缓存只保存成功结果，观测时间仍来自服务商。
 */
@Service
public class WeatherPreviewService {

    private static final Duration CACHE_TTL = Duration.ofMinutes(10);

    private final WeatherClient weatherClient;
    private final Clock clock;
    private final ConcurrentHashMap<String, CachedWeather> cache = new ConcurrentHashMap<>();

    @Autowired
    public WeatherPreviewService(WeatherClient weatherClient) {
        this(weatherClient, Clock.systemUTC());
    }

    WeatherPreviewService(WeatherClient weatherClient, Clock clock) {
        this.weatherClient = weatherClient;
        this.clock = clock;
    }

    public Map<String, Object> current(JsonNode body) {
        long userId = AuthContext.requireUserId();
        PreviewCoordinates coordinates = PreviewCoordinates.require(body, 2);
        String key = userId + ":" + coordinates.latitude().toPlainString() + "," + coordinates.longitude().toPlainString();
        WeatherResult result = readCache(key);
        if (result == null) {
            result = weatherClient.current(coordinates.submittedLatitude(), coordinates.submittedLongitude());
            if (result != null && "rate_limited".equals(result.status())) {
                throw new BusinessException(429, PreviewMessages.RATE_LIMITED);
            }
            if (result == null || !result.complete()) {
                String detail = result != null && result.weatherText() != null && !result.weatherText().isBlank()
                        ? result.weatherText()
                        : PreviewMessages.WEATHER_FAILED;
                throw BusinessException.unavailable(detail);
            }
            cache.put(key, new CachedWeather(result, clock.instant().plus(CACHE_TTL)));
        }
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("weatherText", result.weatherText());
        response.put("temperatureC", result.temperatureC());
        response.put("humidityPercent", result.humidityPercent());
        response.put("weatherCode", result.weatherCode());
        response.put("provider", "qweather");
        response.put("observedAt", ProjectResponses.formatTime(result.observedAt()));
        return response;
    }

    private WeatherResult readCache(String key) {
        CachedWeather cached = cache.get(key);
        if (cached == null) {
            return null;
        }
        if (clock.instant().isAfter(cached.expiresAt())) {
            cache.remove(key, cached);
            return null;
        }
        return cached.result();
    }

    private record CachedWeather(WeatherResult result, Instant expiresAt) {
    }
}
