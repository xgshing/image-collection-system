package com.xgsheng.backend.service;

import com.xgsheng.backend.entity.Image;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface ImageService {

    String saveByUrl(String url, String tags);

    String upload(MultipartFile file, String tags);

    List<?> list(String tag);

    Image detail(Long id);

    List<String> listTags();

    boolean updateTags(Long id, String tags);

    boolean delete(Long id);
}
