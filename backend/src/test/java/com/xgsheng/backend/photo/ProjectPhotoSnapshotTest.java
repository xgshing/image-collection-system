package com.xgsheng.backend.photo;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xgsheng.backend.entity.ProjectPhoto;
import com.xgsheng.backend.exception.BusinessException;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPOutputStream;
import java.math.BigDecimal;

import java.time.LocalDateTime;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProjectPhotoSnapshotTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void geocodePrefersRecommend() throws Exception {
        GeocodeResult result = TencentGeocodeClient.parse(mapper.readTree(
                "{\"status\":0,\"result\":{\"address\":\"详细地址\",\"formatted_addresses\":{\"recommend\":\"推荐地址\"}}}"));
        assertEquals("推荐地址", result.locationText());
    }

    @Test
    void geocodeMapsDailyQuotaExceeded() throws Exception {
        GeocodeResult result = TencentGeocodeClient.parse(mapper.readTree(
                "{\"status\":121,\"message\":\"此key每日调用量已达到上限\"}"));
        assertNull(result.locationText());
        assertTrue(result.errorDetail().contains("日配额"));
    }

    @Test
    void qweatherRejectsIllegalCredentialChars() {
        String jwt = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIn0.signaturepart";
        assertNull(QWeatherClient.validateCredentialChars(jwt));
        assertNotNull(QWeatherClient.validateCredentialChars("aaa bbb"));
        assertNotNull(QWeatherClient.validateCredentialChars("short.jwt.x"));
        assertEquals("abcd", QWeatherClient.sanitizeCredential(" \"ab cd\" "));
        assertEquals("abcd", QWeatherClient.sanitizeCredential("ab\u200Bcd"));
    }

    @Test
    void weatherGzipBodyDecodesBeforeParse() throws Exception {
        byte[] json = "{\"code\":\"200\"}".getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream raw = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(raw)) {
            gzip.write(json);
        }
        String text = QWeatherClient.ungzip(raw.toByteArray());
        assertEquals("{\"code\":\"200\"}", text);
    }

    @Test
    void weatherHostMustBeAccountHost() {
        assertTrue(QWeatherClient.hostError("devapi.qweather.com").contains("未配置"));
        assertTrue(QWeatherClient.hostError("https://abc.qweatherapi.com").contains("主机名"));
        assertNull(QWeatherClient.hostError("abc123.qweatherapi.com"));
    }

    @Test
    void weatherAuthHintUsesErrorDetail() {
        String hint = QWeatherClient.authHint("{\"error\":{\"title\":\"Invalid JWT\",\"detail\":\"signature mismatch\"}}");
        assertEquals("signature mismatch", hint);
        assertTrue(QWeatherClient.authHint("").contains("API Host"));
    }

    @Test
    void weatherRequiresObservedTime() throws Exception {
        WeatherResult result = QWeatherClient.parse(mapper.readTree(
                "{\"code\":\"200\",\"now\":{\"text\":\"多云\",\"temp\":\"26.5\",\"humidity\":\"0.72\",\"icon\":\"101\"}}"));
        assertEquals("failed", result.status());
        assertEquals("和风字段不全:无obsTime", result.weatherText());
    }

    @Test
    void weatherConvertsFractionHumidity() throws Exception {
        WeatherResult result = QWeatherClient.parse(mapper.readTree(
                "{\"code\":\"200\",\"now\":{\"text\":\"多云\",\"temp\":\"26.5\",\"humidity\":\"0.72\",\"icon\":\"101\",\"obsTime\":\"2026-11-27T13:10:00+08:00\"}}"));
        assertEquals("ok", result.status());
        assertEquals(0, new BigDecimal("72.00").compareTo(result.humidityPercent()));
        assertEquals("101", result.weatherCode());
    }

    @Test
    void hiddenWatermarkReturnsOriginalBytes() {
        ProjectPhoto photo = new ProjectPhoto();
        photo.setWatermarkVisible(0);
        byte[] original = new byte[]{1, 2, 3};
        assertArrayEquals(original, ProjectPhotoWatermarkRenderer.render(original, photo));
    }

    @Test
    void visibleWatermarkWithoutFontFailsAndDoesNotClaimADrawing() {
        ProjectPhoto photo = new ProjectPhoto();
        photo.setWatermarkVisible(1);
        photo.setProjectNameSnapshot("项目");
        photo.setNote("");
        photo.setGpsStatus("empty");
        photo.setWeatherStatus("skipped");
        BusinessException ex = assertThrows(BusinessException.class,
                () -> ProjectPhotoWatermarkRenderer.render(new byte[]{(byte) 0xFF, (byte) 0xD8}, photo));
        assertEquals("watermark render failed", ex.getMessage());
    }

    @Test
    void visibleWatermarkDrawsChineseEnglishDigitsAndPunctuationWithoutReplacingOriginal() throws Exception {
        assertNotNull(ProjectPhotoWatermarkRenderer.class.getResource("/fonts/NotoSansSC-Regular.otf"));
        assertNotNull(ProjectPhotoWatermarkRenderer.class.getResource("/fonts/NotoSansSC-Bold.otf"));
        BufferedImage canvas = new BufferedImage(320, 240, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        assertTrue(ImageIO.write(canvas, "jpeg", output));
        byte[] original = output.toByteArray();
        byte[] untouched = original.clone();
        ProjectPhoto photo = new ProjectPhoto();
        photo.setWatermarkVisible(1);
        photo.setWatermarkPosition("bottom_left");
        photo.setProjectNameSnapshot("项目 Project");
        photo.setNote("验收，12。");
        photo.setTakenAt(LocalDateTime.of(2026, 11, 27, 5, 12));
        photo.setTimezone("Asia/Shanghai");
        photo.setLocationText("南山区");
        photo.setWeatherStatus("ok");
        photo.setWeatherText("多云");
        photo.setTemperatureC(new BigDecimal("26.5"));
        byte[] rendered = ProjectPhotoWatermarkRenderer.render(original, photo);
        assertArrayEquals(untouched, original);
        assertFalse(Arrays.equals(original, rendered));
        assertEquals(0xFF, rendered[0] & 0xFF);
        assertEquals(0xD8, rendered[1] & 0xFF);
        assertNotNull(ImageIO.read(new java.io.ByteArrayInputStream(rendered)));
    }

    @Test
    void displayUsesConfirmedFallbackWords() {
        ProjectPhoto photo = new ProjectPhoto();
        photo.setProjectNameSnapshot("项目");
        photo.setNote("");
        photo.setLocationText(null);
        photo.setWeatherStatus("failed");
        var lines = ProjectPhotoWatermarkRenderer.displayLines(photo);
        assertTrue(lines.stream().anyMatch(line -> line.contains("未获取到地点") && line.contains("点：")));
        assertTrue(lines.stream().anyMatch(line -> line.contains("未获取到天气") && line.contains("气：")));
        assertTrue(lines.contains("备注内容：（空记录）"));
    }
}
