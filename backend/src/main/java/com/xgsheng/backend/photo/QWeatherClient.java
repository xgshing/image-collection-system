package com.xgsheng.backend.photo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xgsheng.backend.config.ExternalServiceProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * 和风实时天气。使用官方 v7/weather/now（经度,纬度），Authorization Bearer JWT。
 */
@Component
public class QWeatherClient implements WeatherClient {

    private static final Logger log = LoggerFactory.getLogger(QWeatherClient.class);

    private final ExternalServiceProperties properties;
    private final QWeatherJwtSigner jwtSigner;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    @Autowired
    public QWeatherClient(ExternalServiceProperties properties, QWeatherJwtSigner jwtSigner) {
        this(properties, jwtSigner, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build(), new ObjectMapper());
    }

    QWeatherClient(ExternalServiceProperties properties, QWeatherJwtSigner jwtSigner, HttpClient httpClient, ObjectMapper objectMapper) {
        this.properties = properties;
        this.jwtSigner = jwtSigner;
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public WeatherResult current(BigDecimal latitude, BigDecimal longitude) {
        if (!properties.isWeatherConfigured() || latitude == null || longitude == null) {
            return WeatherResult.failed("qweather is not configured");
        }
        try {
            BigDecimal lat = latitude.setScale(2, RoundingMode.HALF_UP);
            BigDecimal lng = longitude.setScale(2, RoundingMode.HALF_UP);
            // 和风 location 参数是「经度,纬度」
            String location = URLEncoder.encode(lng.toPlainString() + "," + lat.toPlainString(), StandardCharsets.UTF_8);
            if (!properties.isQweatherJwtSigningConfigured()) {
                return WeatherResult.failed("和风仍在用静态JWT。请配置私钥路径、凭据ID、开发者ID、项目ID后重启，不要再填临时JWT");
            }
            String host = normalizeHost(properties.getQweatherApiHost());
            String hostError = hostError(host);
            if (hostError != null) {
                return WeatherResult.failed(hostError);
            }
            URI uri = URI.create("https://" + host + "/v7/weather/now?location=" + location + "&lang=zh");
            String credential = jwtSigner.resolveBearerToken();
            if (credential == null || credential.isBlank()) {
                return WeatherResult.failed("和风凭证为空");
            }
            String credentialError = validateCredentialChars(credential);
            if (credentialError != null) {
                return WeatherResult.failed(credentialError);
            }
            HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofSeconds(3))
                    .header("Accept", "application/json")
                    .header("Accept-Encoding", "gzip")
                    .GET();
            // 正式路径签发的是 JWT；旧静态配置若无点号则按 API KEY
            if (credential.chars().filter(ch -> ch == '.').count() >= 2) {
                builder.header("Authorization", "Bearer " + credential);
            } else {
                builder.header("X-QW-Api-Key", credential);
            }
            HttpRequest request = builder.build();
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            String body = decodeBody(response);
            if (response.statusCode() == 429) {
                return WeatherResult.rateLimited();
            }
            if (response.statusCode() == 401 || response.statusCode() == 403) {
                String hint = authHint(body);
                log.warn("Weather auth failed, status={}, mode={}, host={}, hint={}",
                        response.statusCode(),
                        properties.isQweatherJwtSigningConfigured() ? "sign" : "static",
                        host,
                        hint);
                String mode = properties.isQweatherJwtSigningConfigured() ? "私钥签发" : "静态JWT";
                return WeatherResult.failed("和风认证失败" + response.statusCode() + "(" + mode + "):" + hint);
            }
            if (response.statusCode() != 200) {
                log.warn("Weather request failed, status={}", response.statusCode());
                return WeatherResult.failed("和风HTTP" + response.statusCode());
            }
            return parse(objectMapper.readTree(body));
        } catch (IllegalArgumentException ex) {
            log.warn("Weather request illegal arg: {}", ex.toString());
            String message = ex.getMessage();
            if (message != null && message.startsWith("和风")) {
                return WeatherResult.failed(clip(message));
            }
            return WeatherResult.failed("和风参数非法:" + clip(message == null ? ex.getClass().getSimpleName() : message));
        } catch (IllegalStateException ex) {
            log.warn("Weather credential failed: {}", ex.toString());
            return WeatherResult.failed(safeMessage(ex));
        } catch (Exception ex) {
            log.warn("Weather request failed: {}", ex.toString());
            return WeatherResult.failed("和风调用异常:" + safeMessage(ex));
        }
    }

    static WeatherResult parse(JsonNode root) {
        if (root == null) {
            return WeatherResult.failed("和风:空响应");
        }
        String code = root.path("code").asText("");
        if ("429".equals(code)) {
            return WeatherResult.rateLimited();
        }
        if (!code.isEmpty() && !"200".equals(code)) {
            return WeatherResult.failed("和风code:" + code);
        }
        JsonNode current = root.has("current") ? root.path("current") : root.path("now");
        String text = firstText(current, "text", "weatherText");
        BigDecimal temperature = decimal(current, "temp", "temperature");
        BigDecimal humidity = humidity(current);
        String weatherCode = firstText(current, "code", "icon");
        LocalDateTime observed = observedAt(firstText(current, "obsTime", "time", "observedAt"));
        WeatherResult result = new WeatherResult("ok", text, temperature, humidity, weatherCode, observed);
        if (!result.complete()) {
            String missing = "和风字段不全"
                    + (text == null || text.isBlank() ? ":无text" : "")
                    + (temperature == null ? ":无temp" : "")
                    + (weatherCode == null || weatherCode.isBlank() ? ":无icon" : "")
                    + (observed == null ? ":无obsTime" : "");
            return WeatherResult.failed(missing);
        }
        return result;
    }

    private static BigDecimal humidity(JsonNode current) {
        BigDecimal value = decimal(current, "humidity");
        if (value == null) {
            return null;
        }
        if (value.compareTo(BigDecimal.ONE) <= 0) {
            return value.multiply(BigDecimal.valueOf(100));
        }
        return value;
    }

    private static LocalDateTime observedAt(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(value).withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime();
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private static String firstText(JsonNode node, String... names) {
        for (String name : names) {
            JsonNode child = node.path(name);
            if (child.isTextual() || child.isNumber()) {
                String text = child.asText();
                if (!text.isBlank()) {
                    return text;
                }
            }
        }
        return null;
    }

    private static BigDecimal decimal(JsonNode node, String... names) {
        for (String name : names) {
            JsonNode child = node.path(name);
            if (child.isNumber()) {
                return child.decimalValue();
            }
            if (child.isTextual() && !child.asText().isBlank()) {
                try {
                    return new BigDecimal(child.asText());
                } catch (NumberFormatException ex) {
                    return null;
                }
            }
        }
        return null;
    }

    private static String safeMessage(Exception ex) {
        String message = ex.getMessage();
        if (message == null || message.isBlank()) {
            return ex.getClass().getSimpleName();
        }
        return message.length() > 80 ? message.substring(0, 80) : message;
    }

    private static String normalizeHost(String host) {
        if (host == null || host.isBlank()) {
            return "devapi.qweather.com";
        }
        String value = host.trim().replaceAll("\\s+", "");
        if ((value.startsWith("\"") && value.endsWith("\"")) || (value.startsWith("'") && value.endsWith("'"))) {
            value = value.substring(1, value.length() - 1);
        }
        if (value.startsWith("https://")) {
            value = value.substring("https://".length());
        } else if (value.startsWith("http://")) {
            value = value.substring("http://".length());
        }
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value.isBlank() ? "devapi.qweather.com" : value;
    }

    static String hostError(String host) {
        if (host == null || host.isBlank() || "devapi.qweather.com".equals(host)) {
            return "和风API Host未配置。请填写控制台里的专属主机名，不要带https://";
        }
        if (!host.matches("[A-Za-z0-9.-]+") || host.startsWith(".") || host.endsWith(".") || !host.contains(".")) {
            return "和风API Host只能是主机名，不要带https://、路径、引号或空格";
        }
        return null;
    }

    /** 去掉粘贴 JWT 时常见的引号、换行、空格，避免 HttpClient Illegal character。 */
    static String sanitizeCredential(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String value = raw.trim();
        if ((value.startsWith("\"") && value.endsWith("\"")) || (value.startsWith("'") && value.endsWith("'"))) {
            value = value.substring(1, value.length() - 1).trim();
        }
        // 去掉零宽字符、BOM 等不可见杂质
        value = value.replaceAll("[\\u0000-\\u001F\\u007F\\u00A0\\u200B-\\u200D\\uFEFF]", "");
        return value.replaceAll("\\s+", "");
    }

    /** JWT/API Key 只允许 Base64URL 与点号，否则 HttpClient 会报 Illegal character。 */
    static String validateCredentialChars(String credential) {
        for (int i = 0; i < credential.length(); i++) {
            char ch = credential.charAt(i);
            boolean ok = (ch >= 'A' && ch <= 'Z')
                    || (ch >= 'a' && ch <= 'z')
                    || (ch >= '0' && ch <= '9')
                    || ch == '-' || ch == '_' || ch == '.' || ch == '+' || ch == '/' || ch == '=';
            if (!ok) {
                return "和风JWT含非法字符(位置" + i + "),请重新复制一整行JWT";
            }
        }
        long dots = credential.chars().filter(ch -> ch == '.').count();
        if (dots >= 2 && credential.length() < 40) {
            return "和风JWT过短,请确认粘贴完整";
        }
        return null;
    }

    static String decodeBody(HttpResponse<byte[]> response) throws Exception {
        byte[] raw = response.body() == null ? new byte[0] : response.body();
        String encoding = response.headers().firstValue("Content-Encoding").orElse("");
        boolean gzip = "gzip".equalsIgnoreCase(encoding)
                || (raw.length >= 2 && raw[0] == (byte) 0x1f && raw[1] == (byte) 0x8b);
        if (!gzip) {
            return new String(raw, StandardCharsets.UTF_8);
        }
        return ungzip(raw);
    }

    static String ungzip(byte[] raw) throws Exception {
        try (GZIPInputStream in = new GZIPInputStream(new ByteArrayInputStream(raw));
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            in.transferTo(out);
            return out.toString(StandardCharsets.UTF_8);
        }
    }

    /** 从和风 401/403 正文里抽出短原因，不记录完整 JWT。 */
    static String authHint(String body) {
        if (body == null || body.isBlank()) {
            return "核对kid、开发者ID、项目ID、私钥与API Host";
        }
        String compact = body.replaceAll("\\s+", " ").trim();
        try {
            JsonNode root = new ObjectMapper().readTree(body);
            String title = firstPresent(root, "error", "title");
            String detail = firstPresent(root, "error", "detail");
            String code = root.path("code").asText("");
            String message = firstNonBlank(detail, title, root.path("message").asText(""), code);
            if (!message.isBlank()) {
                return clip(message);
            }
        } catch (Exception ignored) {
            // 非 JSON 时用原文短句
        }
        return clip(compact);
    }

    private static String firstPresent(JsonNode root, String objectName, String field) {
        JsonNode node = root.path(objectName).path(field);
        return node.isMissingNode() ? "" : node.asText("");
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private static String clip(String value) {
        String text = value.replaceAll("[\\r\\n\\t]", " ").trim();
        return text.length() > 48 ? text.substring(0, 48) : text;
    }
}
