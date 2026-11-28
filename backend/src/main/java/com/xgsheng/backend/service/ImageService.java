package com.xgsheng.backend.service;

import com.xgsheng.backend.entity.Image;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

public interface ImageService {

    Map<String, Object> saveByUrl(Long userId, String url, String tags, String referer);

    /**
     * @param origin {@link Image#ORIGIN_PROJECT} 表示水印相机原图；其它值按收藏图片处理。
     */
    Map<String, Object> upload(Long userId, MultipartFile file, String tags, String origin);

    List<Image> list(Long userId, String tag);

    Map<String, Object> exportManifest(Long userId, List<String> tags);

    Image detail(Long userId, Long id);

    List<String> listTags(Long userId);

    List<String> listRecentTags(Long userId);

    boolean deleteTag(Long userId, String tagName);

    boolean renameTag(Long userId, String oldTagName, String newTagName);

    boolean updateTags(Long userId, Long id, String tags);

    boolean delete(Long userId, Long id);
}
