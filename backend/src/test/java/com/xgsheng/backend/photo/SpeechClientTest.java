package com.xgsheng.backend.photo;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xgsheng.backend.config.ExternalServiceProperties;
import org.junit.jupiter.api.Test;

import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpeechClientTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void parseReturnsTextAndTreatsProviderErrorAsFailure() throws Exception {
        assertEquals("今天基坑验收", TencentSpeechClient.parse(mapper.readTree(
                "{\"Response\":{\"Result\":\"今天基坑验收\",\"RequestId\":\"abc\"}}")));
        assertEquals("", TencentSpeechClient.parse(mapper.readTree("{\"Response\":{\"Result\":\"\"}}")));
        assertNull(TencentSpeechClient.parse(mapper.readTree(
                "{\"Response\":{\"Error\":{\"Code\":\"FailedOperation.ErrorRecognize\",\"Message\":\"失败\"}}}")));
    }

    @Test
    void signedRequestDoesNotPutTheSecretInTheUrlOrBody() throws Exception {
        ExternalServiceProperties properties = new ExternalServiceProperties();
        properties.setTencentSpeechSecretId("speech-id");
        properties.setTencentSpeechSecretKey("speech-secret-value");
        TencentSpeechClient client = new TencentSpeechClient(properties, null, mapper);

        HttpRequest request = client.signedRequest("hello".getBytes(StandardCharsets.UTF_8), "wav",
                Instant.parse("2026-11-27T05:12:00Z"));
        String authorization = request.headers().firstValue("Authorization").orElse("");

        assertEquals("https://asr.tencentcloudapi.com/", request.uri().toString());
        assertTrue(authorization.startsWith("TC3-HMAC-SHA256 Credential=speech-id/"));
        assertFalse(authorization.contains("speech-secret-value"));
        assertFalse(request.uri().toString().contains("speech-secret-value"));
        assertTrue(request.headers().firstValue("X-TC-Action").orElse("").equals("SentenceRecognition"));
        assertFalse(request.headers().map().containsKey("X-TC-Region"));
    }

    @Test
    void oneMp3FrameHasAShortDuration() {
        byte[] frame = new byte[417];
        frame[0] = (byte) 0xFF;
        frame[1] = (byte) 0xFB;
        frame[2] = (byte) 0x90;
        assertEquals(26L, SpeechAudio.durationMillis("mp3", frame));
        assertTrue(SpeechAudio.durationMillis("mp3", frame) <= SpeechAudio.MAX_DURATION_MILLIS);
    }
}
