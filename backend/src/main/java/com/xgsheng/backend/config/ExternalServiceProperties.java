package com.xgsheng.backend.config;

import com.xgsheng.backend.exception.BusinessException;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 地点、天气、语音识别的服务端配置。值只来自环境变量，本类不提供默认成功数据。
 */
@ConfigurationProperties(prefix = "external")
public class ExternalServiceProperties {

    private String tencentLbsKey = "";
    /** 兼容调试：静态 JWT。正式环境请用私钥签发配置。 */
    private String qweatherJwt = "";
    private String qweatherApiHost = "devapi.qweather.com";
    /** Ed25519 私钥 PEM 全文，或与 qweatherPrivateKeyPath 二选一。 */
    private String qweatherPrivateKey = "";
    private String qweatherPrivateKeyPath = "";
    /** 凭据 ID（JWT header.kid） */
    private String qweatherKid = "";
    /** 开发者 ID（JWT payload.iss，控制台设置里 Q 开头） */
    private String qweatherDevId = "";
    /** 项目 ID（JWT payload.sub） */
    private String qweatherProjectId = "";
    private String tencentSpeechSecretId = "";
    private String tencentSpeechSecretKey = "";

    public ExternalServiceStatus status() {
        return new ExternalServiceStatus(isLbsConfigured(), isWeatherConfigured(), isSpeechConfigured());
    }

    public boolean isLbsConfigured() {
        return isPresent(tencentLbsKey);
    }

    public boolean isWeatherConfigured() {
        return isQweatherJwtSigningConfigured() || isPresent(qweatherJwt);
    }

    /** 正式：私钥 + kid + 开发者ID + 项目ID。 */
    public boolean isQweatherJwtSigningConfigured() {
        boolean hasKey = isPresent(qweatherPrivateKey) || isPresent(qweatherPrivateKeyPath);
        return hasKey && isPresent(qweatherKid) && isPresent(qweatherDevId) && isPresent(qweatherProjectId);
    }

    public boolean isSpeechConfigured() {
        return isPresent(tencentSpeechSecretId) && isPresent(tencentSpeechSecretKey);
    }

    public void requireLbsConfigured() {
        if (!isLbsConfigured()) {
            throw BusinessException.unavailable("tencent lbs is not configured");
        }
    }

    public void requireWeatherConfigured() {
        if (!isWeatherConfigured()) {
            throw BusinessException.unavailable("qweather is not configured");
        }
    }

    public void requireSpeechConfigured() {
        if (!isSpeechConfigured()) {
            throw BusinessException.unavailable("tencent speech is not configured");
        }
    }

    public String getTencentLbsKey() {
        return tencentLbsKey;
    }

    public void setTencentLbsKey(String tencentLbsKey) {
        this.tencentLbsKey = tencentLbsKey;
    }

    public String getQweatherJwt() {
        return qweatherJwt;
    }

    public void setQweatherJwt(String qweatherJwt) {
        this.qweatherJwt = qweatherJwt;
    }

    public String getQweatherApiHost() {
        return qweatherApiHost;
    }

    public void setQweatherApiHost(String qweatherApiHost) {
        this.qweatherApiHost = qweatherApiHost;
    }

    public String getQweatherPrivateKey() {
        return qweatherPrivateKey;
    }

    public void setQweatherPrivateKey(String qweatherPrivateKey) {
        this.qweatherPrivateKey = qweatherPrivateKey;
    }

    public String getQweatherPrivateKeyPath() {
        return qweatherPrivateKeyPath;
    }

    public void setQweatherPrivateKeyPath(String qweatherPrivateKeyPath) {
        this.qweatherPrivateKeyPath = qweatherPrivateKeyPath;
    }

    public String getQweatherKid() {
        return qweatherKid;
    }

    public void setQweatherKid(String qweatherKid) {
        this.qweatherKid = qweatherKid;
    }

    public String getQweatherDevId() {
        return qweatherDevId;
    }

    public void setQweatherDevId(String qweatherDevId) {
        this.qweatherDevId = qweatherDevId;
    }

    public String getQweatherProjectId() {
        return qweatherProjectId;
    }

    public void setQweatherProjectId(String qweatherProjectId) {
        this.qweatherProjectId = qweatherProjectId;
    }

    public String getTencentSpeechSecretId() {
        return tencentSpeechSecretId;
    }

    public void setTencentSpeechSecretId(String tencentSpeechSecretId) {
        this.tencentSpeechSecretId = tencentSpeechSecretId;
    }

    public String getTencentSpeechSecretKey() {
        return tencentSpeechSecretKey;
    }

    public void setTencentSpeechSecretKey(String tencentSpeechSecretKey) {
        this.tencentSpeechSecretKey = tencentSpeechSecretKey;
    }

    @Override
    public String toString() {
        return status().toString();
    }

    private static boolean isPresent(String value) {
        return value != null && !value.isBlank();
    }
}
