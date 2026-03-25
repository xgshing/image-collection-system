package com.xgsheng.backend.auth;

import com.xgsheng.backend.entity.User;
import com.xgsheng.backend.service.AuthService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AuthInterceptor implements HandlerInterceptor {

    @Resource
    private AuthService authService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String token = extractBearerToken(request.getHeader("Authorization"));
        if (token == null || token.isBlank()) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return false;
        }

        User user = authService.getUserBySessionToken(token);
        if (user == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return false;
        }

        AuthContext.setUser(user);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        AuthContext.clear();
    }

    private String extractBearerToken(String authHeader) {
        if (authHeader == null) {
            return null;
        }
        String prefix = "Bearer ";
        if (!authHeader.startsWith(prefix)) {
            return null;
        }
        return authHeader.substring(prefix.length()).trim();
    }
}