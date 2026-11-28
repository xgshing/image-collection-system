package com.xgsheng.backend.project;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InviteTokenTest {

    @Test
    void tokenFitsSceneAndHashIsNotThePlainValue() {
        Set<String> tokens = new HashSet<>();
        for (int i = 0; i < 20; i++) {
            String token = InviteToken.generate();
            String scene = InviteToken.scene(token);
            String hash = InviteToken.sha256(token);
            assertEquals(InviteToken.TOKEN_LENGTH, token.length());
            assertTrue(scene.startsWith("pinv_"));
            assertEquals(32, scene.length());
            assertEquals(64, hash.length());
            assertNotEquals(token, hash);
            assertFalse(hash.contains(token));
            assertTrue(tokens.add(token));
        }
    }

    @Test
    void sceneRejectsPluginLoginShape() {
        assertFalse(InviteToken.isScene("00112233445566778899aabbccddeeff"));
        assertFalse(InviteToken.isScene("pinv_short"));
    }
}
