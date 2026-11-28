package com.xgsheng.backend.service.impl;

import com.xgsheng.backend.config.StorageProperties;
import com.xgsheng.backend.service.StorageService;
import com.xgsheng.backend.util.CosStorageClient;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Date;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class StorageServiceImpl implements StorageService {

    private static final long READ_URL_TTL_MILLIS = 60L * 60 * 1000;
    private static final long READ_URL_GRID_MILLIS = 10L * 60 * 1000;

    private final Map<String, SignedReadUrl> readUrlCache = new ConcurrentHashMap<>();

    @Resource
    private StorageProperties storageProperties;

    @Resource
    private CosStorageClient cosStorageClient;

    @Override
    public String storeBytes(byte[] bytes, String originalFilename, String contentType, String category) {
        if (bytes == null || bytes.length == 0) {
            throw new IllegalArgumentException("file bytes are required");
        }

        String extension = normalizeExtension(originalFilename);
        String safeCategory = normalizeCategory(category);
        String filename = System.currentTimeMillis() + "-" + UUID.randomUUID().toString().replace("-", "") + extension;

        try {
            if (useCosStorage()) {
                String key = buildCosKey(safeCategory, filename);
                cosStorageClient.upload(
                        storageProperties.getCos(),
                        key,
                        bytes,
                        normalizeContentType(contentType, extension)
                );
                return cosStorageClient.toPublicUrl(storageProperties.getCos(), key);
            }

            Path root = resolveLocalRoot(safeCategory);
            Files.createDirectories(root);
            Path target = root.resolve(filename);
            Files.copy(new ByteArrayInputStream(bytes), target, StandardCopyOption.REPLACE_EXISTING);
            return buildLocalPublicUrl(safeCategory, filename);
        } catch (IOException ex) {
            throw new IllegalStateException("store file failed", ex);
        }
    }

    @Override
    public String canonicalUrl(String url) {
        String value = url == null ? "" : url.trim();
        if (value.isBlank() || !useCosStorage()) {
            return value;
        }
        String key = cosStorageClient.extractKeyFromUrl(storageProperties.getCos(), value);
        if (key.isBlank()) {
            return value;
        }
        return cosStorageClient.toPublicUrl(storageProperties.getCos(), key);
    }

    @Override
    public String readableUrl(String storedUrl) {
        String value = storedUrl == null ? "" : storedUrl.trim();
        if (value.isBlank() || !useCosStorage()) {
            return value;
        }
        String key = cosStorageClient.extractKeyFromUrl(storageProperties.getCos(), value);
        if (key.isBlank()) {
            return value;
        }
        return cachedReadUrl(key);
    }

    private String cachedReadUrl(String key) {
        long now = System.currentTimeMillis();
        SignedReadUrl cached = readUrlCache.get(key);
        if (cached != null && cached.expireAtMillis - now > READ_URL_GRID_MILLIS) {
            return cached.url;
        }
        Date expiration = stableReadExpiration();
        String url = cosStorageClient.presignGet(storageProperties.getCos(), key, expiration);
        readUrlCache.put(key, new SignedReadUrl(url, expiration.getTime()));
        return url;
    }

    @Override
    public int signedReadTtlSeconds() {
        return (int) (READ_URL_TTL_MILLIS / 1000);
    }

    @Override
    public byte[] readStoredBytes(String storedUrl) {
        String value = storedUrl == null ? "" : storedUrl.trim();
        if (value.isBlank()) {
            throw new IllegalStateException("stored image is missing");
        }
        try {
            if (useCosStorage()) {
                String key = cosStorageClient.extractKeyFromUrl(storageProperties.getCos(), value);
                if (key.isBlank()) {
                    throw new IllegalStateException("stored image key is missing");
                }
                return cosStorageClient.read(storageProperties.getCos(), key);
            }
            return Files.readAllBytes(resolveLocalFile(value));
        } catch (RuntimeException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("read stored image failed", ex);
        }
    }

    private Path resolveLocalFile(String storedUrl) {
        String basePath = normalizeBasePath(storageProperties.getLocalBasePath());
        String path = storedUrl;
        int scheme = path.indexOf("://");
        if (scheme >= 0) {
            int slash = path.indexOf('/', scheme + 3);
            path = slash >= 0 ? path.substring(slash) : "";
        }
        int query = path.indexOf('?');
        if (query >= 0) {
            path = path.substring(0, query);
        }
        if (!path.startsWith(basePath + "/")) {
            throw new IllegalStateException("stored image path is outside local storage");
        }
        String relative = path.substring(basePath.length() + 1);
        String localDir = storageProperties.getLocalDir();
        if (localDir == null || localDir.isBlank()) {
            localDir = "uploads";
        }
        Path root = Paths.get(localDir).toAbsolutePath().normalize();
        Path file = root.resolve(relative).normalize();
        if (!file.startsWith(root)) {
            throw new IllegalStateException("stored image path is outside local storage");
        }
        return file;
    }

    private Date stableReadExpiration() {
        long now = System.currentTimeMillis();
        long expireAt = ((now + READ_URL_TTL_MILLIS) / READ_URL_GRID_MILLIS) * READ_URL_GRID_MILLIS;
        return new Date(expireAt);
    }

    private boolean useCosStorage() {
        return "cos".equalsIgnoreCase(storageProperties.getType())
                && cosStorageClient.isConfigured(storageProperties.getCos());
    }

    private Path resolveLocalRoot(String category) {
        String localDir = storageProperties.getLocalDir();
        if (localDir == null || localDir.isBlank()) {
            localDir = "uploads";
        }
        return Paths.get(localDir, category).toAbsolutePath().normalize();
    }

    private String buildLocalPublicUrl(String category, String filename) {
        String basePath = normalizeBasePath(storageProperties.getLocalBasePath());
        String publicBaseUrl = trimTrailingSlash(storageProperties.getPublicBaseUrl());
        String relativePath = basePath + "/" + category + "/" + filename;
        if (publicBaseUrl.isBlank()) {
            return relativePath;
        }
        return publicBaseUrl + relativePath;
    }

    private String buildCosKey(String category, String filename) {
        String prefix = storageProperties.getCos().getKeyPrefix();
        String normalizedPrefix = prefix == null ? "" : prefix.trim();
        if (!normalizedPrefix.isEmpty() && !normalizedPrefix.endsWith("/")) {
            normalizedPrefix += "/";
        }
        return normalizedPrefix + category + "/" + filename;
    }

    private String normalizeCategory(String category) {
        String value = category == null ? "" : category.trim().toLowerCase(Locale.ROOT);
        return value.isBlank() ? "common" : value.replaceAll("[^a-z0-9/_-]", "");
    }

    private String normalizeExtension(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) {
            return ".jpg";
        }
        int index = originalFilename.lastIndexOf('.');
        String value = index >= 0 ? originalFilename.substring(index).toLowerCase(Locale.ROOT) : ".jpg";
        return switch (value) {
            case ".png", ".jpg", ".jpeg", ".gif", ".webp", ".bmp" -> value;
            default -> ".jpg";
        };
    }

    private String normalizeContentType(String contentType, String extension) {
        if (contentType != null && !contentType.isBlank()) {
            return contentType;
        }
        return switch (Objects.requireNonNullElse(extension, ".jpg")) {
            case ".png" -> "image/png";
            case ".gif" -> "image/gif";
            case ".webp" -> "image/webp";
            case ".bmp" -> "image/bmp";
            default -> "image/jpeg";
        };
    }

    private String normalizeBasePath(String value) {
        String path = (value == null || value.isBlank()) ? "/uploads" : value.trim();
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        while (path.endsWith("/") && path.length() > 1) {
            path = path.substring(0, path.length() - 1);
        }
        return path;
    }

    private String trimTrailingSlash(String value) {
        String result = value == null ? "" : value.trim();
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    private static final class SignedReadUrl {
        private final String url;
        private final long expireAtMillis;

        private SignedReadUrl(String url, long expireAtMillis) {
            this.url = url;
            this.expireAtMillis = expireAtMillis;
        }
    }
}
