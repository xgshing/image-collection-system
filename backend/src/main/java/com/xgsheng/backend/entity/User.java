package com.xgsheng.backend.entity;

import lombok.Data;

import java.util.Date;

@Data
public class User {
    private Long id;
    private String openId;
    private String nickName;
    private String avatarUrl;
    private Date createTime;
    private Date updateTime;
}