package com.xgsheng.backend.service;

import com.xgsheng.backend.entity.Image;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface ImageService {

    String saveByUrl(Long userId, String url, String tags);

    String upload(Long userId, MultipartFile file, String tags);

    List<?> list(Long userId, String tag);

    Image detail(Long userId, Long id);

    List<String> listTags(Long userId);

    boolean updateTags(Long userId, Long id, String tags);

    boolean delete(Long userId, Long id);
}
