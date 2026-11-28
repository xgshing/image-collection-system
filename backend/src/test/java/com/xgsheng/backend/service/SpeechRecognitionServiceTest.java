package com.xgsheng.backend.service;

import com.xgsheng.backend.auth.AuthContext;
import com.xgsheng.backend.config.ExternalServiceProperties;
import com.xgsheng.backend.entity.User;
import com.xgsheng.backend.exception.BusinessException;
import com.xgsheng.backend.photo.PreviewMessages;
import com.xgsheng.backend.photo.SpeechClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpeechRecognitionServiceTest {

    private final Clock clock = Clock.fixed(Instant.parse("2026-11-27T05:12:00Z"), ZoneOffset.UTC);

    @TempDir
    Path directory;

    @AfterEach
    void clearAuth() {
        AuthContext.clear();
    }

    @Test
    void successReturnsTextAndDeletesTheTempFile() throws Exception {
        login(7L);
        CountingSpeech speech = new CountingSpeech("今天基坑验收");
        SpeechRecognitionService service = service(configured(), speech);

        Map<String, Object> body = service.recognize(audio("note.wav", wav(1)));

        assertEquals("今天基坑验收", body.get("text"));
        assertEquals(1, speech.calls.get());
        assertTrue(Files.list(directory).findAny().isEmpty());
    }

    @Test
    void emptyRecognitionIsNotAnError() {
        login(7L);
        SpeechRecognitionService service = service(configured(), new CountingSpeech(""));

        assertEquals("", service.recognize(audio("note.wav", wav(1))).get("text"));
    }

    @Test
    void missingSpeechConfigDoesNotCreateAFileOrFakeText() throws Exception {
        login(7L);
        CountingSpeech speech = new CountingSpeech("假文字");
        SpeechRecognitionService service = service(new ExternalServiceProperties(), speech);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.recognize(audio("note.wav", wav(1))));
        assertEquals(503, ex.getStatus());
        assertEquals(PreviewMessages.SPEECH_NOT_CONFIGURED, ex.getMessage());
        assertEquals(0, speech.calls.get());
        assertTrue(Files.list(directory).findAny().isEmpty());
    }

    @Test
    void oversizedOrUnsupportedAudioIsRejected() {
        login(7L);
        SpeechRecognitionService service = service(configured(), new CountingSpeech("不会出现"));

        BusinessException large = assertThrows(BusinessException.class,
                () -> service.recognize(audio("note.wav", new byte[SpeechAudioSize.OVER])));
        assertEquals(PreviewMessages.AUDIO_TOO_LARGE, large.getMessage());
        BusinessException format = assertThrows(BusinessException.class,
                () -> service.recognize(audio("note.exe", wav(1))));
        assertEquals(PreviewMessages.AUDIO_TOO_LARGE, format.getMessage());
    }

    @Test
    void audioLongerThanSixtySecondsIsRejected() {
        login(7L);
        SpeechRecognitionService service = service(configured(), new CountingSpeech("不会出现"));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.recognize(audio("long.wav", wav(61))));
        assertEquals(400, ex.getStatus());
        assertEquals(PreviewMessages.AUDIO_TOO_LARGE, ex.getMessage());
    }

    @Test
    void providerFailureDeletesTheTempFile() throws Exception {
        login(7L);
        SpeechRecognitionService service = service(configured(), new CountingSpeech(null));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.recognize(audio("note.wav", wav(1))));
        assertEquals(PreviewMessages.SPEECH_FAILED, ex.getMessage());
        assertTrue(Files.list(directory).findAny().isEmpty());
    }

    @Test
    void eleventhCallInTheSameMinuteIsLimited() {
        login(7L);
        CountingSpeech speech = new CountingSpeech("文字");
        SpeechRecognitionService service = service(configured(), speech);
        for (int i = 0; i < 10; i++) {
            service.recognize(audio("note.wav", wav(1)));
        }

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.recognize(audio("note.wav", wav(1))));
        assertEquals(429, ex.getStatus());
        assertEquals(PreviewMessages.RATE_LIMITED, ex.getMessage());
        assertEquals(10, speech.calls.get());
    }

    @Test
    void startupCleanupRemovesOnlyExpiredFiles() throws Exception {
        Path fresh = directory.resolve("fresh.wav");
        Path expired = directory.resolve("expired.wav");
        Files.write(fresh, new byte[]{1});
        Files.write(expired, new byte[]{1});
        Files.setLastModifiedTime(expired, java.nio.file.attribute.FileTime.from(clock.instant().minus(Duration.ofHours(2))));

        SpeechFiles.deleteExpired(directory, Duration.ofHours(1), clock);

        assertTrue(Files.exists(fresh));
        assertFalse(Files.exists(expired));
    }

    private SpeechRecognitionService service(ExternalServiceProperties properties, SpeechClient speech) {
        return new SpeechRecognitionService(properties, speech, directory, clock);
    }

    private static ExternalServiceProperties configured() {
        ExternalServiceProperties properties = new ExternalServiceProperties();
        properties.setTencentSpeechSecretId("speech-id");
        properties.setTencentSpeechSecretKey("speech-secret-value");
        return properties;
    }

    private static void login(long userId) {
        User user = new User();
        user.setId(userId);
        AuthContext.setUser(user);
    }

    private static byte[] wav(int seconds) {
        int dataSize = 8000 * seconds * 2;
        byte[] bytes = new byte[44 + dataSize];
        write(bytes, 0, "RIFF");
        writeInt(bytes, 4, 36 + dataSize);
        write(bytes, 8, "WAVE");
        write(bytes, 12, "fmt ");
        writeInt(bytes, 16, 16);
        writeShort(bytes, 20, 1);
        writeShort(bytes, 22, 1);
        writeInt(bytes, 24, 8000);
        writeInt(bytes, 28, 16000);
        writeShort(bytes, 32, 2);
        writeShort(bytes, 34, 16);
        write(bytes, 36, "data");
        writeInt(bytes, 40, dataSize);
        return bytes;
    }

    private static void write(byte[] bytes, int offset, String value) {
        for (int i = 0; i < value.length(); i++) {
            bytes[offset + i] = (byte) value.charAt(i);
        }
    }

    private static void writeInt(byte[] bytes, int offset, int value) {
        bytes[offset] = (byte) value;
        bytes[offset + 1] = (byte) (value >> 8);
        bytes[offset + 2] = (byte) (value >> 16);
        bytes[offset + 3] = (byte) (value >> 24);
    }

    private static void writeShort(byte[] bytes, int offset, int value) {
        bytes[offset] = (byte) value;
        bytes[offset + 1] = (byte) (value >> 8);
    }

    private static MultipartFile audio(String name, byte[] bytes) {
        return new MultipartFile() {
            @Override
            public String getName() {
                return "file";
            }

            @Override
            public String getOriginalFilename() {
                return name;
            }

            @Override
            public String getContentType() {
                return "audio/wav";
            }

            @Override
            public boolean isEmpty() {
                return bytes.length == 0;
            }

            @Override
            public long getSize() {
                return bytes.length;
            }

            @Override
            public byte[] getBytes() {
                return bytes;
            }

            @Override
            public InputStream getInputStream() {
                return new ByteArrayInputStream(bytes);
            }

            @Override
            public void transferTo(File dest) throws IOException {
                Files.write(dest.toPath(), bytes);
            }
        };
    }

    private static final class SpeechAudioSize {
        private static final int OVER = 3 * 1024 * 1024 + 1;
    }

    private static final class CountingSpeech implements SpeechClient {
        private final String text;
        private final AtomicInteger calls = new AtomicInteger();

        private CountingSpeech(String text) {
            this.text = text;
        }

        @Override
        public String recognize(byte[] audio, String format) {
            calls.incrementAndGet();
            if (text == null) {
                throw new IllegalStateException("provider down");
            }
            return text;
        }
    }
}
