package com.xgsheng.backend.entity;

import lombok.Data;

import java.util.Date;

@Data
public class UserSession {
    private Long id;
    private Long userId;
    private String sessionToken;
    private Date expiresAt;
    private Date createTime;
}