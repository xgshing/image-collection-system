package com.xgsheng.backend.service;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;

/**
 * 启动时清掉超过 1 小时的语音临时文件。不访问数据库，也不删除未过期文件。
 */
@Component
public class SpeechTempJanitor implements ApplicationRunner {

    @Override
    public void run(ApplicationArguments args) {
        SpeechFiles.deleteExpired(SpeechFiles.directory(), Duration.ofHours(1), Clock.systemUTC());
    }
}
