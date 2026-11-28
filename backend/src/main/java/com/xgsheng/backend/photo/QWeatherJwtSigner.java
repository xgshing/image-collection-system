package com.xgsheng.backend.photo;

import com.xgsheng.backend.config.ExternalServiceProperties;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 和风正式认证：用本地 Ed25519 私钥签发短时 JWT，不依赖 jwt.qweather.com 临时 Token。
 */
@Component
public class QWeatherJwtSigner {

    /** 服务端签发有效期 1 小时；最长允许 24h。 */
    private static final long TOKEN_TTL_SECONDS = 3600L;
    private static final long REFRESH_BEFORE_SECONDS = 300L;

    private final ExternalServiceProperties properties;
    private final AtomicReference<CachedToken> cache = new AtomicReference<>();

    public QWeatherJwtSigner(ExternalServiceProperties properties) {
        this.properties = properties;
    }

    /** 优先私钥动态签发；未配置私钥时回退静态 JWT（仅兼容旧调试）。 */
    public String resolveBearerToken() {
        if (properties.isQweatherJwtSigningConfigured()) {
            return signedToken();
        }
        String staticJwt = QWeatherClient.sanitizeCredential(properties.getQweatherJwt());
        if (staticJwt.isEmpty()) {
            throw new IllegalStateException("和风未配置私钥签发或静态JWT");
        }
        String error = QWeatherClient.validateCredentialChars(staticJwt);
        if (error != null) {
            throw new IllegalStateException(error);
        }
        return staticJwt;
    }

    private String signedToken() {
        CachedToken existing = cache.get();
        long now = Instant.now().getEpochSecond();
        if (existing != null && existing.expiresAtEpochSec - REFRESH_BEFORE_SECONDS > now) {
            return existing.token;
        }
        synchronized (this) {
            existing = cache.get();
            now = Instant.now().getEpochSecond();
            if (existing != null && existing.expiresAtEpochSec - REFRESH_BEFORE_SECONDS > now) {
                return existing.token;
            }
            long iat = now - 30;
            long exp = iat + TOKEN_TTL_SECONDS;
            String token = sign(properties.getQweatherKid().trim(),
                    properties.getQweatherDevId().trim(),
                    properties.getQweatherProjectId().trim(),
                    loadPrivateKeyPem(),
                    iat,
                    exp);
            cache.set(new CachedToken(token, exp));
            return token;
        }
    }

    static String sign(String kid, String iss, String sub, String privateKeyPem, long iat, long exp) {
        try {
            PrivateKey privateKey = parsePrivateKey(privateKeyPem);
            String headerJson = "{\"alg\":\"EdDSA\",\"kid\":\"" + escapeJson(kid) + "\"}";
            String payloadJson = "{\"iss\":\"" + escapeJson(iss) + "\",\"sub\":\"" + escapeJson(sub)
                    + "\",\"iat\":" + iat + ",\"exp\":" + exp + "}";
            String header = base64Url(headerJson.getBytes(StandardCharsets.UTF_8));
            String payload = base64Url(payloadJson.getBytes(StandardCharsets.UTF_8));
            String data = header + "." + payload;
            Signature signer = Signature.getInstance("EdDSA");
            signer.initSign(privateKey);
            signer.update(data.getBytes(StandardCharsets.UTF_8));
            return data + "." + base64Url(signer.sign());
        } catch (Exception ex) {
            throw new IllegalStateException("和风JWT签发失败:" + ex.getMessage(), ex);
        }
    }

    private String loadPrivateKeyPem() {
        String path = properties.getQweatherPrivateKeyPath();
        if (path != null && !path.isBlank()) {
            try {
                return Files.readString(Path.of(path.trim()), StandardCharsets.UTF_8);
            } catch (Exception ex) {
                throw new IllegalStateException("读取和风私钥文件失败:" + path, ex);
            }
        }
        String pem = properties.getQweatherPrivateKey();
        if (pem == null || pem.isBlank()) {
            throw new IllegalStateException("和风私钥为空");
        }
        // PowerShell 单行环境变量常用字面 \\n
        if (pem.contains("\\n") && !pem.contains("\n")) {
            pem = pem.replace("\\n", "\n");
        }
        return pem;
    }

    static PrivateKey parsePrivateKey(String pem) throws Exception {
        String normalized = pem
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replace("-----BEGIN ED25519 PRIVATE KEY-----", "")
                .replace("-----END ED25519 PRIVATE KEY-----", "")
                .replaceAll("\\s+", "");
        byte[] decoded = Base64.getDecoder().decode(normalized);
        KeyFactory keyFactory = KeyFactory.getInstance("EdDSA");
        return keyFactory.generatePrivate(new PKCS8EncodedKeySpec(decoded));
    }

    private static String base64Url(byte[] raw) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
    }

    private static String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private record CachedToken(String token, long expiresAtEpochSec) {
    }
}
