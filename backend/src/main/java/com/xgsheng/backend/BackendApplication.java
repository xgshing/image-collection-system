package com.xgsheng.backend;

import com.xgsheng.backend.config.ExternalServiceProperties;
import com.xgsheng.backend.config.WechatMiniProgramProperties;
import com.xgsheng.backend.config.StorageProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({
        WechatMiniProgramProperties.class,
        StorageProperties.class,
        ExternalServiceProperties.class
})
public class BackendApplication {
    public static void main(String[] args) {
        SpringApplication.run(BackendApplication.class, args);
    }
}
