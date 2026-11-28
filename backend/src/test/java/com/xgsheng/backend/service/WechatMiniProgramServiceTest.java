package com.xgsheng.backend.service;

import com.xgsheng.backend.config.WechatMiniProgramProperties;
import com.xgsheng.backend.exception.BusinessException;
import com.xgsheng.backend.project.InviteToken;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WechatMiniProgramServiceTest {

    @Test
    void inviteCodeRetriesOnceWhenTheAccessTokenIsRejected() throws Exception {
        Script script = new Script();
        script.invalidPosts = 1;
        WechatMiniProgramService service = service(script);
        String scene = InviteToken.scene(InviteToken.generate());
        byte[] body = service.createInviteMiniCode(scene);
        assertEquals(0x89, body[0] & 0xFF);
        assertEquals(2, script.gets.get());
        assertEquals(2, script.posts.get());
        assertTrue(script.lastJson.contains("\"scene\":\"" + scene + "\""));
        assertTrue(script.lastJson.contains("\"page\":\"pages/index/index\""));
        assertTrue(script.lastJson.contains("\"check_path\":false"));
        assertTrue(script.lastJson.contains("\"width\":430"));
        assertFalse(script.lastJson.contains("projectId"));
    }

    @Test
    void inviteCodeFailureDoesNotExposeTheTokenOrSecret() {
        Script script = new Script();
        script.postFailure = new IOException("https://api.weixin.qq.com/wxa/getwxacodeunlimit?access_token=sekret&secret=test-secret");
        WechatMiniProgramService service = service(script);
        String scene = InviteToken.scene(InviteToken.generate());
        BusinessException ex = assertThrows(BusinessException.class, () -> service.createInviteMiniCode(scene));
        assertEquals(503, ex.getStatus());
        assertEquals("wechat mini code failed", ex.getMessage());
        assertFalse(ex.getMessage().contains("sekret"));
        assertFalse(ex.getMessage().contains(scene));
        assertFalse(ex.getMessage().contains("test-secret"));
    }

    @Test
    void pluginLoginCodeStaysSeparateFromTheInvitePrefix() throws Exception {
        Script script = new Script();
        byte[] body = service(script).createPluginLoginCode("00112233445566778899aabbccddeeff");
        assertEquals(0x89, body[0] & 0xFF);
        assertTrue(script.lastJson.contains("\"scene\":\"00112233445566778899aabbccddeeff\""));
        assertFalse(script.lastJson.contains("pinv_"));
        assertEquals(1, script.posts.get());
    }

    @Test
    void otherWechatErrorsDoNotRetry() {
        Script script = new Script();
        script.errorCode = 41030;
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service(script).createInviteMiniCode(InviteToken.scene(InviteToken.generate())));
        assertEquals("wechat mini code failed", ex.getMessage());
        assertEquals(1, script.posts.get());
    }

    @Test
    void invalidInviteSceneDoesNotCallWechat() {
        Script script = new Script();
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service(script).createInviteMiniCode("00112233445566778899aabbccddeeff"));
        assertEquals(503, ex.getStatus());
        assertEquals(0, script.posts.get());
        assertEquals(0, script.gets.get());
    }

    private static WechatMiniProgramService service(Script script) {
        WechatMiniProgramProperties properties = new WechatMiniProgramProperties();
        properties.setAppId("test-app");
        properties.setAppSecret("test-secret");
        return new WechatMiniProgramService(properties, script);
    }

    private static final class Script implements WechatTransport {
        private final AtomicInteger gets = new AtomicInteger();
        private final AtomicInteger posts = new AtomicInteger();
        private int invalidPosts;
        private int errorCode;
        private IOException postFailure;
        private String lastJson;

        @Override
        public String get(URI uri) {
            gets.incrementAndGet();
            return "{\"access_token\":\"cached-token\",\"expires_in\":7200}";
        }

        @Override
        public byte[] post(URI uri, String json) throws IOException {
            posts.incrementAndGet();
            lastJson = json;
            if (postFailure != null) {
                throw postFailure;
            }
            if (invalidPosts > 0) {
                invalidPosts--;
                return "{\"errcode\":40001,\"errmsg\":\"invalid credential\"}".getBytes(StandardCharsets.UTF_8);
            }
            if (errorCode != 0) {
                return ("{\"errcode\":" + errorCode + ",\"errmsg\":\"bad scene\"}").getBytes(StandardCharsets.UTF_8);
            }
            return new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47};
        }
    }
}
