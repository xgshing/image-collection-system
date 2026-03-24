package com.xgsheng.backend.entity;

import lombok.Data;
import java.util.Date;
import java.util.List;

@Data
public class Image {
    private Long id;
    private Long userId;
    private String tag;
    private String url;
    private String localUrl;
    private String md5;
    private Integer width;
    private Integer height;
    private Date createTime;
    private List<String> tags;
}
