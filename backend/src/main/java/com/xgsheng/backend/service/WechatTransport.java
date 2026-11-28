package com.xgsheng.backend.service;

import java.io.IOException;
import java.net.URI;

interface WechatTransport {

    String get(URI uri) throws IOException, InterruptedException;

    byte[] post(URI uri, String json) throws IOException, InterruptedException;
}
