package com.xgsheng.backend.service;

public interface StorageService {

    String storeBytes(byte[] bytes, String originalFilename, String contentType, String category);

    String canonicalUrl(String url);

    /**
     * 把库里的固定地址换成当前可读地址。COS 签名大约 60 分钟，并且不能在成员退出后立刻作废。
     */
    String readableUrl(String storedUrl);

    /**
     * 签名有效期上限，单位秒。实际过期时间会对齐到 10 分钟网格，可能短于这个上限。
     */
    int signedReadTtlSeconds();

    /**
     * 读取已保存的原图字节，供内存绘制水印。不返回密钥，也不写回存储。
     */
    byte[] readStoredBytes(String storedUrl);
}
