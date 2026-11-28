package com.xgsheng.backend.photo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.xgsheng.backend.config.ExternalServiceProperties;
import com.xgsheng.backend.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.HexFormat;

/**
 * 腾讯云一句话识别的 HTTP 调用。项目里的 COS SDK 不包含这个接口，因此直接按官方签名调用，不再引入第二套 SDK。
 * 日志不记录密钥、音频和识别文本。
 */
@Component
public class TencentSpeechClient implements SpeechClient {

    private static final Logger log = LoggerFactory.getLogger(TencentSpeechClient.class);
    private static final String HOST = "asr.tencentcloudapi.com";
    private static final String SERVICE = "asr";
    private static final String ACTION = "SentenceRecognition";
    private static final String VERSION = "2019-06-14";
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneOffset.UTC);

    private final ExternalServiceProperties properties;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    @Autowired
    public TencentSpeechClient(ExternalServiceProperties properties) {
        this(properties, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build(), new ObjectMapper());
    }

    TencentSpeechClient(ExternalServiceProperties properties, HttpClient httpClient, ObjectMapper objectMapper) {
        this.properties = properties;
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public String recognize(byte[] audio, String format) {
        if (!properties.isSpeechConfigured() || audio == null || audio.length == 0) {
            throw BusinessException.unavailable(PreviewMessages.SPEECH_FAILED);
        }
        try {
            HttpRequest request = signedRequest(audio, format, Instant.now());
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.warn("Speech request failed, status={}", response.statusCode());
                throw BusinessException.unavailable(PreviewMessages.SPEECH_FAILED);
            }
            String text = parse(objectMapper.readTree(response.body()));
            if (text == null) {
                throw BusinessException.unavailable(PreviewMessages.SPEECH_FAILED);
            }
            return text;
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("Speech request failed");
            throw BusinessException.unavailable(PreviewMessages.SPEECH_FAILED);
        }
    }

    HttpRequest signedRequest(byte[] audio, String format, Instant now) throws Exception {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("EngSerViceType", "16k_zh");
        body.put("SourceType", 1);
        body.put("VoiceFormat", format);
        body.put("Data", Base64.getEncoder().encodeToString(audio));
        body.put("DataLen", audio.length);
        String payload = objectMapper.writeValueAsString(body);
        long timestamp = now.getEpochSecond();
        String date = DATE.format(now);
        String authorization = authorization(payload, timestamp, date);
        return HttpRequest.newBuilder(URI.create("https://" + HOST + "/"))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/json; charset=utf-8")
                .header("X-TC-Action", ACTION)
                .header("X-TC-Version", VERSION)
                .header("X-TC-Timestamp", Long.toString(timestamp))
                .header("Authorization", authorization)
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build();
    }

    /**
     * 有识别结果时返回文本，空结果返回空字符串。响应带错误时返回 null。
     */
    static String parse(JsonNode root) {
        if (root == null) {
            return null;
        }
        JsonNode response = root.path("Response");
        if (response.hasNonNull("Error")) {
            return null;
        }
        JsonNode result = response.get("Result");
        if (result == null || result.isNull()) {
            return "";
        }
        return result.asText("");
    }

    private String authorization(String payload, long timestamp, String date) throws Exception {
        String canonicalHeaders = "content-type:application/json; charset=utf-8\nhost:" + HOST + "\n";
        String signedHeaders = "content-type;host";
        String canonicalRequest = "POST\n/\n\n" + canonicalHeaders + "\n" + signedHeaders + "\n" + sha256(payload);
        String scope = date + "/" + SERVICE + "/tc3_request";
        String stringToSign = "TC3-HMAC-SHA256\n" + timestamp + "\n" + scope + "\n" + sha256(canonicalRequest);
        byte[] secretDate = hmac(("TC3" + properties.getTencentSpeechSecretKey()).getBytes(StandardCharsets.UTF_8), date);
        byte[] secretService = hmac(secretDate, SERVICE);
        byte[] secretSigning = hmac(secretService, "tc3_request");
        String signature = HexFormat.of().formatHex(hmac(secretSigning, stringToSign));
        return "TC3-HMAC-SHA256 Credential=" + properties.getTencentSpeechSecretId() + "/" + scope
                + ", SignedHeaders=" + signedHeaders + ", Signature=" + signature;
    }

    private static byte[] hmac(byte[] key, String value) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String sha256(String value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8)));
    }
}
