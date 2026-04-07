package com.xgsheng.backend.service.impl;

import com.xgsheng.backend.config.StorageProperties;
import com.xgsheng.backend.service.StorageService;
import com.xgsheng.backend.util.CosStorageClient;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

@Service
public class StorageServiceImpl implements StorageService {

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
}
