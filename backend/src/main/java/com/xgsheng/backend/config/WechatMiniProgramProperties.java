package com.xgsheng.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "wechat.miniapp")
public class WechatMiniProgramProperties {

    private String appId;
    private String appSecret;
    private String codeEnvVersion = "release";

    public String getAppId() {
        return appId;
    }

    public void setAppId(String appId) {
        this.appId = appId;
    }

    public String getAppSecret() {
        return appSecret;
    }

    public void setAppSecret(String appSecret) {
        this.appSecret = appSecret;
    }

    public String getCodeEnvVersion() {
        return codeEnvVersion;
    }

    public void setCodeEnvVersion(String codeEnvVersion) {
        if (codeEnvVersion == null || codeEnvVersion.isBlank()) {
            this.codeEnvVersion = "release";
            return;
        }
        this.codeEnvVersion = codeEnvVersion.trim().toLowerCase();
    }

    public boolean isConfigured() {
        return appId != null && !appId.isBlank() && appSecret != null && !appSecret.isBlank();
    }
}
