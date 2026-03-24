package com.xgsheng.backend.util;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class DownloadUtil {

    public static byte[] download(String urlStr) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();

        conn.setRequestMethod("GET");
        conn.setConnectTimeout(5000);

        // 防盗链（关键）
        conn.setRequestProperty("User-Agent", "Mozilla/5.0");

        InputStream in = conn.getInputStream();
        return in.readAllBytes();
    }
}