package com.xgsheng.backend.util;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;

public class DownloadUtil {

    public static byte[] download(String urlStr, String referer) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();

        conn.setRequestMethod("GET");
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(10000);
        conn.setInstanceFollowRedirects(true);

        conn.setRequestProperty("User-Agent", "Mozilla/5.0");
        conn.setRequestProperty("Accept", "image/avif,image/webp,image/apng,image/*,*/*;q=0.8");
        conn.setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8");
        conn.setRequestProperty("Cache-Control", "no-cache");

        String normalizedReferer = buildReferer(urlStr, referer);
        if (!normalizedReferer.isBlank()) {
            conn.setRequestProperty("Referer", normalizedReferer);
            conn.setRequestProperty("Origin", toOrigin(normalizedReferer));
        }

        int statusCode = conn.getResponseCode();
        if (statusCode < 200 || statusCode >= 300) {
            throw new IllegalStateException("download image failed with status " + statusCode);
        }

        try (InputStream in = conn.getInputStream()) {
            return in.readAllBytes();
        }
    }

    private static String buildReferer(String urlStr, String referer) {
        if (referer != null && !referer.isBlank()) {
            return referer.trim();
        }

        try {
            URI uri = URI.create(urlStr);
            return uri.getScheme() + "://" + uri.getHost() + "/";
        } catch (Exception ex) {
            return "";
        }
    }

    private static String toOrigin(String referer) {
        try {
            URI uri = URI.create(referer);
            return uri.getScheme() + "://" + uri.getHost();
        } catch (Exception ex) {
            return "";
        }
    }
}
