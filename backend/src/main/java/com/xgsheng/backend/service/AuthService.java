package com.xgsheng.backend.service;

import com.xgsheng.backend.entity.User;

public interface AuthService {

    LoginResult loginByMiniappCode(String code, String nickName, String avatarUrl);

    User getUserBySessionToken(String sessionToken);

    String createSessionToken(Long userId);

    void cleanupExpiredSessions();

    class LoginResult {
        private Long userId;
        private String sessionToken;
        private String nickName;
        private String avatarUrl;

        public Long getUserId() {
            return userId;
        }

        public void setUserId(Long userId) {
            this.userId = userId;
        }

        public String getSessionToken() {
            return sessionToken;
        }

        public void setSessionToken(String sessionToken) {
            this.sessionToken = sessionToken;
        }

        public String getNickName() {
            return nickName;
        }

        public void setNickName(String nickName) {
            this.nickName = nickName;
        }

        public String getAvatarUrl() {
            return avatarUrl;
        }

        public void setAvatarUrl(String avatarUrl) {
            this.avatarUrl = avatarUrl;
        }
    }
}