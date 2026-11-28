package com.xgsheng.backend.util;

import com.qcloud.cos.COSClient;
import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.auth.BasicCOSCredentials;
import com.qcloud.cos.auth.COSCredentials;
import com.qcloud.cos.http.HttpMethodName;
import com.qcloud.cos.http.HttpProtocol;
import com.qcloud.cos.model.COSObject;
import com.qcloud.cos.model.GeneratePresignedUrlRequest;
import com.qcloud.cos.model.ObjectMetadata;
import com.qcloud.cos.model.PutObjectRequest;
import com.qcloud.cos.region.Region;
import com.xgsheng.backend.config.StorageProperties;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class CosStorageClient {

    private final Map<String, COSClient> presignClients = new ConcurrentHashMap<>();

    public void upload(StorageProperties.Cos cos, String key, byte[] bytes, String contentType) {
        COSClient client = buildClient(cos);
        try {
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentLength(bytes.length);
            metadata.setContentType((contentType == null || contentType.isBlank()) ? "image/jpeg" : contentType);

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

    public byte[] read(StorageProperties.Cos cos, String key) {
        COSClient client = buildClient(cos);
        try (COSObject object = client.getObject(cos.getBucket(), key)) {
            return object.getObjectContent().readAllBytes();
        } catch (Exception ex) {
            throw new IllegalStateException("read cos object failed", ex);
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

    public String presignGet(StorageProperties.Cos cos, String key, Date expiration) {
        COSClient client = presignClient(cos);
        GeneratePresignedUrlRequest request = new GeneratePresignedUrlRequest(cos.getBucket(), key, HttpMethodName.GET);
        request.setExpiration(expiration);
        return client.generatePresignedUrl(request).toString();
    }

    private COSClient presignClient(StorageProperties.Cos cos) {
        String cacheKey = cos.getRegion() + "|" + cos.getBucket() + "|" + cos.getSecretId();
        return presignClients.computeIfAbsent(cacheKey, ignored -> buildClient(cos));
    }

    private COSClient buildClient(StorageProperties.Cos cos) {
        COSCredentials credentials = new BasicCOSCredentials(cos.getSecretId(), cos.getSecretKey());
        ClientConfig config = new ClientConfig(new Region(cos.getRegion()));
        config.setHttpProtocol(HttpProtocol.https);
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
        if (url == null || url.isBlank() || cos == null) {
            return "";
        }
        String normalizedUrl = stripQueryAndFragment(url.trim());
        String fromPrefix = keyAfterPrefix(cos.getPublicUrlPrefix(), normalizedUrl);
        if (!fromPrefix.isBlank()) {
            return fromPrefix;
        }
        if (notBlank(cos.getBucket()) && notBlank(cos.getRegion())) {
            String defaultPrefix = "https://" + cos.getBucket().trim() + ".cos." + cos.getRegion().trim() + ".myqcloud.com";
            return keyAfterPrefix(defaultPrefix, normalizedUrl);
        }
        return "";
    }

    private String keyAfterPrefix(String prefix, String url) {
        String normalizedPrefix = trimTrailingSlash(prefix);
        if (normalizedPrefix.isBlank()) {
            return "";
        }
        String withSlash = normalizedPrefix + "/";
        if (url.toLowerCase(Locale.ROOT).startsWith(withSlash.toLowerCase(Locale.ROOT))) {
            return url.substring(withSlash.length());
        }
        return "";
    }

    private String stripQueryAndFragment(String value) {
        int query = value.indexOf('?');
        int fragment = value.indexOf('#');
        int cut = -1;
        if (query >= 0) {
            cut = query;
        }
        if (fragment >= 0 && (cut < 0 || fragment < cut)) {
            cut = fragment;
        }
        return cut >= 0 ? value.substring(0, cut) : value;
    }
}
