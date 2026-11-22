package com.xgsheng.backend.service;

public interface StorageService {

    String storeBytes(byte[] bytes, String originalFilename, String contentType, String category);

    String canonicalUrl(String url);

    String readableUrl(String storedUrl);
}
