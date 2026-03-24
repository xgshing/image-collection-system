package com.xgsheng.backend.util;

import com.qcloud.cos.COSClient;
import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.auth.BasicCOSCredentials;
import com.qcloud.cos.auth.COSCredentials;
import com.qcloud.cos.model.ObjectMetadata;
import com.qcloud.cos.model.PutObjectRequest;
import com.qcloud.cos.region.Region;
import com.xgsheng.backend.config.StorageProperties;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.util.Locale;

@Component
public class CosStorageClient {

    public void upload(StorageProperties.Cos cos, String key, byte[] bytes) {
        COSClient client = buildClient(cos);
        try {
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentLength(bytes.length);
            metadata.setContentType("image/jpeg");

            PutObjectRequest request = new PutObjectRequest(
                    cos.getBucket(),
                    key,
                    new ByteArrayInputStream(bytes),
                    metadata
            );
            client.putObject(request);
        } finally {
            client.shutdown();
        }
    }

    public void delete(StorageProperties.Cos cos, String key) {
        if (key == null || key.isBlank()) {
            return;
        }

        COSClient client = buildClient(cos);
        try {
            client.deleteObject(cos.getBucket(), key);
        } finally {
            client.shutdown();
        }
    }

    private COSClient buildClient(StorageProperties.Cos cos) {
        COSCredentials credentials = new BasicCOSCredentials(cos.getSecretId(), cos.getSecretKey());
        ClientConfig config = new ClientConfig(new Region(cos.getRegion()));
        return new COSClient(credentials, config);
    }

    public boolean isConfigured(StorageProperties.Cos cos) {
        return notBlank(cos.getSecretId())
                && notBlank(cos.getSecretKey())
                && notBlank(cos.getRegion())
                && notBlank(cos.getBucket())
                && notBlank(cos.getPublicUrlPrefix());
    }

    private boolean notBlank(String value) {
        return value != null && !value.trim().isEmpty();
    }

    public String toPublicUrl(StorageProperties.Cos cos, String key) {
        String prefix = trimTrailingSlash(cos.getPublicUrlPrefix());
        return prefix + "/" + key;
    }

    private String trimTrailingSlash(String value) {
        if (value == null) {
            return "";
        }

        String result = value.trim();
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    public String buildObjectKey(StorageProperties.Cos cos, long timestampMillis) {
        String normalizedPrefix = cos.getKeyPrefix() == null ? "" : cos.getKeyPrefix().trim();
        if (!normalizedPrefix.isEmpty() && !normalizedPrefix.endsWith("/")) {
            normalizedPrefix = normalizedPrefix + "/";
        }
        return normalizedPrefix + timestampMillis + ".jpg";
    }

    public String extractKeyFromUrl(StorageProperties.Cos cos, String url) {
        if (url == null || url.isBlank()) {
            return "";
        }
        String normalizedPrefix = trimTrailingSlash(cos.getPublicUrlPrefix()) + "/";
        String normalizedUrl = url.trim();
        if (normalizedUrl.toLowerCase(Locale.ROOT).startsWith(normalizedPrefix.toLowerCase(Locale.ROOT))) {
            return normalizedUrl.substring(normalizedPrefix.length());
        }
        return "";
    }
}
