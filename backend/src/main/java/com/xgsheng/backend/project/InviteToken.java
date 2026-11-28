package com.xgsheng.backend.project;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

/**
 * 邀请凭证。明文只存在于创建响应和当次请求，库中只保存 SHA-256 十六进制。
 * scene 是 pinv_ 加 token，总长不超过微信小程序的 32 个可见字符。
 */
public final class InviteToken {

    public static final String PREFIX = "pinv_";
    public static final int SCENE_MAX_LENGTH = 32;
    public static final int TOKEN_LENGTH = SCENE_MAX_LENGTH - PREFIX.length();

    private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private InviteToken() {
    }

    public static String generate() {
        StringBuilder token = new StringBuilder(TOKEN_LENGTH);
        for (int i = 0; i < TOKEN_LENGTH; i++) {
            token.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return token.toString();
    }

    public static String scene(String token) {
        if (!isToken(token)) {
            throw new IllegalArgumentException("invite scene is invalid");
        }
        return PREFIX + token;
    }

    public static boolean isToken(String token) {
        if (token == null || token.length() != TOKEN_LENGTH) {
            return false;
        }
        for (int i = 0; i < token.length(); i++) {
            if (ALPHABET.indexOf(token.charAt(i)) < 0) {
                return false;
            }
        }
        return true;
    }

    public static boolean isScene(String scene) {
        if (scene == null || !scene.startsWith(PREFIX) || scene.length() != SCENE_MAX_LENGTH) {
            return false;
        }
        return isToken(scene.substring(PREFIX.length()));
    }

    public static String sha256(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte value : digest) {
                hex.append(Character.forDigit((value >> 4) & 0xF, 16));
                hex.append(Character.forDigit(value & 0xF, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable");
        }
    }
}
