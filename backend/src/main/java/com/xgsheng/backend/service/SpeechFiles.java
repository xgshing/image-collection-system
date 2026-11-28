package com.xgsheng.backend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * 语音临时文件只放在后端 tmp/speech。这个目录不通过静态资源对外提供。
 */
final class SpeechFiles {

    private static final Logger log = LoggerFactory.getLogger(SpeechFiles.class);

    private SpeechFiles() {
    }

    static Path directory() {
        return Path.of("tmp", "speech");
    }

    static Path write(Path directory, String format, byte[] bytes) {
        try {
            Files.createDirectories(directory);
            Path file = directory.resolve(UUID.randomUUID() + "." + format).normalize();
            if (!file.startsWith(directory.normalize())) {
                throw new IOException("speech temp path escaped");
            }
            Files.write(file, bytes);
            return file;
        } catch (IOException ex) {
            log.warn("Speech temp file could not be created");
            throw new IllegalStateException("speech temp file failed");
        }
    }

    static void delete(Path file) {
        if (file == null) {
            return;
        }
        try {
            Files.deleteIfExists(file);
        } catch (IOException ex) {
            log.warn("Speech temp file could not be deleted");
        }
    }

    static void deleteExpired(Path directory, Duration age, Clock clock) {
        if (directory == null || !Files.isDirectory(directory)) {
            return;
        }
        Instant cutoff = clock.instant().minus(age);
        try (Stream<Path> files = Files.list(directory)) {
            files.filter(Files::isRegularFile).forEach(file -> deleteIfExpired(file, cutoff));
        } catch (IOException ex) {
            log.warn("Speech temp cleanup failed");
        }
    }

    private static void deleteIfExpired(Path file, Instant cutoff) {
        try {
            if (Files.getLastModifiedTime(file).toInstant().isBefore(cutoff)) {
                Files.deleteIfExists(file);
            }
        } catch (IOException ex) {
            log.warn("Speech temp cleanup failed");
        }
    }
}
