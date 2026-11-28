package com.xgsheng.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xgsheng.backend.auth.AuthContext;
import com.xgsheng.backend.entity.User;
import com.xgsheng.backend.exception.BusinessException;
import com.xgsheng.backend.photo.GeocodeClient;
import com.xgsheng.backend.photo.GeocodeResult;
import com.xgsheng.backend.photo.PreviewMessages;
import com.xgsheng.backend.photo.WeatherClient;
import com.xgsheng.backend.photo.WeatherResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CapturePreviewServiceTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-27T05:12:00Z"), ZoneOffset.UTC);

    @AfterEach
    void clearAuth() {
        AuthContext.clear();
    }

    @Test
    void reverseUsesServerTextAndCachesFourDecimals() throws Exception {
        login(7L);
        CountingGeocode geocode = new CountingGeocode("深圳市南山区");
        LocationPreviewService service = new LocationPreviewService(geocode, clock);

        Map<String, Object> first = service.reverse(mapper.readTree(
                "{\"latitude\":22.54306,\"longitude\":114.05786,\"locationText\":\"伪造地点\",\"coordinateSystem\":\"gcj02\"}"));
        Map<String, Object> second = service.reverse(mapper.readTree(
                "{\"latitude\":22.54314,\"longitude\":114.05786}"));

        assertEquals("深圳市南山区", first.get("locationText"));
        assertEquals("tencent-lbs", first.get("provider"));
        assertEquals("gcj02", first.get("coordinateSystem"));
        assertEquals("深圳市南山区", second.get("locationText"));
        assertEquals(1, geocode.calls.get());
    }

    @Test
    void reverseFailureDoesNotInventAPlace() throws Exception {
        login(7L);
        LocationPreviewService service = new LocationPreviewService(new CountingGeocode(null), clock);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.reverse(mapper.readTree(
                "{\"latitude\":22.5,\"longitude\":114.0}")));
        assertEquals(503, ex.getStatus());
        assertEquals(PreviewMessages.GEOCODE_FAILED, ex.getMessage());
    }

    @Test
    void wgs84IsRejectedBeforeTheProviderCall() throws Exception {
        login(7L);
        CountingGeocode geocode = new CountingGeocode("不应调用");
        LocationPreviewService service = new LocationPreviewService(geocode, clock);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> service.reverse(mapper.readTree(
                "{\"latitude\":22.5,\"longitude\":114.0,\"coordinateSystem\":\"wgs84\"}")));
        assertEquals("coordinate system must be gcj02", ex.getMessage());
        assertEquals(0, geocode.calls.get());
    }

    @Test
    void weatherKeepsProviderObservedTimeAndDoesNotShareAnotherUsersCache() throws Exception {
        CountingWeather weather = new CountingWeather(new WeatherResult(
                "ok", "多云", new BigDecimal("26.5"), new BigDecimal("72.0"), "101",
                LocalDateTime.of(2026, 11, 27, 5, 10)));
        WeatherPreviewService service = new WeatherPreviewService(weather, clock);
        login(7L);
        Map<String, Object> body = service.current(mapper.readTree(
                "{\"latitude\":22.543,\"longitude\":114.057,\"capturedAt\":\"2026-11-27T01:00:00Z\"}"));
        service.current(mapper.readTree("{\"latitude\":22.54,\"longitude\":114.06}"));
        login(8L);
        service.current(mapper.readTree("{\"latitude\":22.54,\"longitude\":114.06}"));

        assertEquals("多云", body.get("weatherText"));
        assertEquals("qweather", body.get("provider"));
        assertEquals("2026-11-27T05:10:00Z", body.get("observedAt"));
        assertEquals(2, weather.calls.get());
    }

    @Test
    void weatherFailureAndRateLimitExposeNoForecast() throws Exception {
        login(7L);
        WeatherPreviewService failed = new WeatherPreviewService(new CountingWeather(WeatherResult.failed()), clock);
        BusinessException failure = assertThrows(BusinessException.class,
                () -> failed.current(mapper.readTree("{\"latitude\":22.54,\"longitude\":114.06}")));
        assertEquals(503, failure.getStatus());
        assertEquals(PreviewMessages.WEATHER_FAILED, failure.getMessage());

        WeatherPreviewService limited = new WeatherPreviewService(new CountingWeather(WeatherResult.rateLimited()), clock);
        BusinessException limit = assertThrows(BusinessException.class,
                () -> limited.current(mapper.readTree("{\"latitude\":22.54,\"longitude\":114.06}")));
        assertEquals(429, limit.getStatus());
        assertEquals(PreviewMessages.RATE_LIMITED, limit.getMessage());
    }

    private static void login(long userId) {
        User user = new User();
        user.setId(userId);
        AuthContext.setUser(user);
    }

    private static final class CountingGeocode implements GeocodeClient {
        private final String text;
        private final AtomicInteger calls = new AtomicInteger();

        private CountingGeocode(String text) {
            this.text = text;
        }

        @Override
        public GeocodeResult reverse(BigDecimal latitude, BigDecimal longitude) {
            calls.incrementAndGet();
            return new GeocodeResult(text);
        }
    }

    private static final class CountingWeather implements WeatherClient {
        private final WeatherResult result;
        private final AtomicInteger calls = new AtomicInteger();

        private CountingWeather(WeatherResult result) {
            this.result = result;
        }

        @Override
        public WeatherResult current(BigDecimal latitude, BigDecimal longitude) {
            calls.incrementAndGet();
            return result;
        }
    }
}
