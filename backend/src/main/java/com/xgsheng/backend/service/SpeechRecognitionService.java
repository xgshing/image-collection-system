package com.xgsheng.backend.service;

import com.xgsheng.backend.auth.AuthContext;
import com.xgsheng.backend.config.ExternalServiceProperties;
import com.xgsheng.backend.exception.BusinessException;
import com.xgsheng.backend.photo.PreviewMessages;
import com.xgsheng.backend.photo.SpeechAudio;
import com.xgsheng.backend.photo.SpeechClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayDeque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 语音只返回文字。不写项目照片，不上传 COS。临时文件在成功、失败和异常时都会删除。
 */
@Service
public class SpeechRecognitionService {

    private static final Logger log = LoggerFactory.getLogger(SpeechRecognitionService.class);
    private static final int LIMIT_PER_MINUTE = 10;

    private final ExternalServiceProperties properties;
    private final SpeechClient speechClient;
    private final Path directory;
    private final Clock clock;
    private final ConcurrentHashMap<Long, ArrayDeque<Long>> calls = new ConcurrentHashMap<>();

    @Autowired
    public SpeechRecognitionService(ExternalServiceProperties properties, SpeechClient speechClient) {
        this(properties, speechClient, SpeechFiles.directory(), Clock.systemUTC());
    }

    SpeechRecognitionService(ExternalServiceProperties properties,
                             SpeechClient speechClient,
                             Path directory,
                             Clock clock) {
        this.properties = properties;
        this.speechClient = speechClient;
        this.directory = directory;
        this.clock = clock;
    }

    public Map<String, Object> recognize(MultipartFile file) {
        long userId = AuthContext.requireUserId();
        byte[] bytes = read(file);
        String format = SpeechAudio.format(file == null ? null : file.getOriginalFilename(),
                file == null ? null : file.getContentType(), bytes);
        if (!allow(userId)) {
            throw new BusinessException(429, PreviewMessages.RATE_LIMITED);
        }
        if (!properties.isSpeechConfigured()) {
            throw BusinessException.unavailable(PreviewMessages.SPEECH_NOT_CONFIGURED);
        }
        Path temporary = null;
        try {
            temporary = SpeechFiles.write(directory, format, bytes);
            String text = speechClient.recognize(bytes, format);
            return Map.of("text", text == null ? "" : text);
        } catch (BusinessException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            log.warn("Speech recognition failed");
            throw BusinessException.unavailable(PreviewMessages.SPEECH_FAILED);
        } finally {
            SpeechFiles.delete(temporary);
        }
    }

    private boolean allow(long userId) {
        long now = clock.millis();
        ArrayDeque<Long> history = calls.computeIfAbsent(userId, id -> new ArrayDeque<>());
        synchronized (history) {
            while (!history.isEmpty() && now - history.peekFirst() >= 60_000L) {
                history.removeFirst();
            }
            if (history.size() >= LIMIT_PER_MINUTE) {
                return false;
            }
            history.addLast(now);
            return true;
        }
    }

    private static byte[] read(MultipartFile file) {
        if (file == null || file.isEmpty() || file.getSize() > SpeechAudio.MAX_BYTES) {
            throw BusinessException.badRequest(PreviewMessages.AUDIO_TOO_LARGE);
        }
        try {
            return file.getBytes();
        } catch (IOException ex) {
            throw BusinessException.unavailable(PreviewMessages.SPEECH_FAILED);
        }
    }
}
