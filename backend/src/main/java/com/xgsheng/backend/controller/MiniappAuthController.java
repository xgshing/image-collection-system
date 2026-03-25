package com.xgsheng.backend.controller;

import com.xgsheng.backend.auth.AuthContext;
import com.xgsheng.backend.entity.User;
import com.xgsheng.backend.service.AuthService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class MiniappAuthController {

    @Resource
    private AuthService authService;

    @PostMapping("/miniapp/login")
    public Map<String, Object> miniappLogin(@RequestBody Map<String, Object> params) {
        String code = stringValue(params.get("code"));
        String nickName = stringValue(params.get("nickName"));
        String avatarUrl = stringValue(params.get("avatarUrl"));

        AuthService.LoginResult result = authService.loginByMiniappCode(code, nickName, avatarUrl);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("userId", result.getUserId());
        data.put("sessionToken", result.getSessionToken());
        data.put("nickName", result.getNickName());
        data.put("avatarUrl", result.getAvatarUrl());
        return data;
    }

    @GetMapping("/me")
    public Map<String, Object> me() {
        User user = AuthContext.requireUser();

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("userId", user.getId());
        data.put("nickName", user.getNickName());
        data.put("avatarUrl", user.getAvatarUrl());
        return data;
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}