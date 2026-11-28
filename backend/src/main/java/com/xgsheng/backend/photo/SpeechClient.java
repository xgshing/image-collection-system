package com.xgsheng.backend.photo;

/**
 * 识别一段音频。失败时抛出业务异常，不返回编造的文字。
 */
public interface SpeechClient {

    String recognize(byte[] audio, String format);
}
