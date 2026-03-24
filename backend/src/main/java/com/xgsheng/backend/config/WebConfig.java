package com.xgsheng.backend.config;

import jakarta.annotation.Resource;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Resource
    private StorageProperties storageProperties;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOrigins("*")
                .allowedMethods("*");
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String basePath = normalizeBasePath(storageProperties.getLocalBasePath());
        String localDir = storageProperties.getLocalDir();
        if (localDir == null || localDir.isBlank()) {
            localDir = "uploads";
        }
        if (!localDir.endsWith("/")) {
            localDir = localDir + "/";
        }

        registry.addResourceHandler(basePath + "/**")
                .addResourceLocations("file:" + localDir);
    }

    private String normalizeBasePath(String basePath) {
        String path = (basePath == null || basePath.isBlank()) ? "/uploads" : basePath.trim();
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        while (path.endsWith("/") && path.length() > 1) {
            path = path.substring(0, path.length() - 1);
        }
        return path;
    }
}
