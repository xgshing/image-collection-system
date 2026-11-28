package com.xgsheng.backend.entity;

import lombok.Data;
import java.util.Date;
import java.util.List;

@Data
public class Image {
    /** 收藏图片，出现在收藏列表。 */
    public static final String ORIGIN_COLLECT = "collect";
    /** 水印相机原图，只属于项目照片，不进入收藏列表。 */
    public static final String ORIGIN_PROJECT = "project";

    private Long id;
    private Long userId;
    private String tag;
    private String url;
    private String localUrl;
    private String md5;
    private String origin;
    private Integer width;
    private Integer height;
    private Date createTime;
    private List<String> tags;
}
