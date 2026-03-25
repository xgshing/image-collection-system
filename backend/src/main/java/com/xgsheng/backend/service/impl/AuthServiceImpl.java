package com.xgsheng.backend.service.impl;

import com.xgsheng.backend.entity.User;
import com.xgsheng.backend.entity.UserSession;
import com.xgsheng.backend.mapper.UserMapper;
import com.xgsheng.backend.mapper.UserSessionMapper;
import com.xgsheng.backend.service.AuthService;
import com.xgsheng.backend.service.WechatMiniProgramService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.Objects;
import java.util.UUID;

@Service
public class AuthServiceImpl implements AuthService {

    private static final long SESSION_TTL_MILLIS = 7L * 24 * 60 * 60 * 1000;

    @Resource
    private UserMapper userMapper;

    @Resource
    private UserSessionMapper userSessionMapper;

    @Resource
    private WechatMiniProgramService wechatMiniProgramService;

    @Override
    public LoginResult loginByMiniappCode(String code, String nickName, String avatarUrl) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("code is required");
        }

        cleanupExpiredSessions();

        WechatMiniProgramService.MiniappSessionInfo sessionInfo;
        try {
            sessionInfo = wechatMiniProgramService.exchangeCodeForSession(code);
        } catch (Exception e) {
            throw new IllegalStateException("exchange miniapp code failed", e);
        }

        String openId = sessionInfo.getOpenId();
        if (openId == null || openId.isBlank()) {
            throw new IllegalStateException("openid missing");
        }

        User user = userMapper.findByOpenId(openId);
        if (user == null) {
            user = createUser(openId, nickName, avatarUrl);
        } else {
            updateUserProfileIfNeeded(user, nickName, avatarUrl);
            user = userMapper.findById(user.getId());
        }

        String sessionToken = createSessionToken(user.getId());

        LoginResult result = new LoginResult();
        result.setUserId(user.getId());
        result.setSessionToken(sessionToken);
        result.setNickName(user.getNickName());
        result.setAvatarUrl(user.getAvatarUrl());
        return result;
    }

    @Override
    public User getUserBySessionToken(String sessionToken) {
        if (sessionToken == null || sessionToken.isBlank()) {
            return null;
        }

        cleanupExpiredSessions();

        UserSession userSession = userSessionMapper.findBySessionToken(sessionToken);
        if (userSession == null) {
            return null;
        }

        Date now = new Date();
        if (userSession.getExpiresAt() == null || userSession.getExpiresAt().before(now)) {
            userSessionMapper.deleteBySessionToken(sessionToken);
            return null;
        }

        return userMapper.findById(userSession.getUserId());
    }

    @Override
    public String createSessionToken(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("userId is required");
        }

        String token = UUID.randomUUID().toString().replace("-", "");
        Date expiresAt = new Date(System.currentTimeMillis() + SESSION_TTL_MILLIS);

        UserSession session = new UserSession();
        session.setUserId(userId);
        session.setSessionToken(token);
        session.setExpiresAt(expiresAt);
        userSessionMapper.insert(session);

        return token;
    }

    @Override
    public void cleanupExpiredSessions() {
        userSessionMapper.deleteExpired(new Date());
    }

    private User createUser(String openId, String nickName, String avatarUrl) {
        User user = new User();
        user.setOpenId(openId);
        user.setNickName(normalizeNickName(nickName));
        user.setAvatarUrl(normalizeAvatarUrl(avatarUrl));
        userMapper.insert(user);
        return userMapper.findById(user.getId());
    }

    private void updateUserProfileIfNeeded(User user, String nickName, String avatarUrl) {
        String normalizedNickName = normalizeNickName(nickName);
        String normalizedAvatarUrl = normalizeAvatarUrl(avatarUrl);

        boolean changed = !Objects.equals(user.getNickName(), normalizedNickName)
                || !Objects.equals(user.getAvatarUrl(), normalizedAvatarUrl);

        if (changed) {
            userMapper.updateProfileById(user.getId(), normalizedNickName, normalizedAvatarUrl);
        }
    }

    private String normalizeNickName(String nickName) {
        String value = nickName == null ? "" : nickName.trim();
        return value.isBlank() ? "微信用户" : value;
    }

    private String normalizeAvatarUrl(String avatarUrl) {
        String value = avatarUrl == null ? "" : avatarUrl.trim();
        return value;
    }
}