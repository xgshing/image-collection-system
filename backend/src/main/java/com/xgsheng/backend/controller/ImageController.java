package com.xgsheng.backend.controller;

import com.xgsheng.backend.service.ImageService;
import com.xgsheng.backend.auth.AuthContext;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;


@RestController
@RequestMapping("/api/image")
public class ImageController {

    @Resource
    private ImageService imageService;

    @PostMapping("/saveByUrl")
    public String saveByUrl(@RequestBody Map<String, Object> params) {
        Long userId = AuthContext.requireUserId();
        String url = (String) params.get("url");
        String tags = (String) params.get("tags");
        if (tags == null) {
            tags = (String) params.get("tag");
        }
        return imageService.saveByUrl(userId, url, tags);
    }

    @PostMapping("/upload")
    public String upload(@RequestParam("file") MultipartFile file,
                         @RequestParam(value = "tags", required = false) String tags,
                         @RequestParam(value = "tag", required = false) String tag) {
        Long userId = AuthContext.requireUserId();
        return imageService.upload(userId, file, tags != null ? tags : tag);
    }

    @GetMapping("/list")
    public Object list(@RequestParam(value = "tag", required = false) String tag) {
        Long userId = AuthContext.requireUserId();
        return imageService.list(userId, tag);
    }

    @GetMapping("/{id}")
    public Object detail(@PathVariable("id") Long id) {
        Long userId = AuthContext.requireUserId();
        return imageService.detail(userId, id);
    }

    @GetMapping("/tags")
    public Object tags() {
        Long userId = AuthContext.requireUserId();
        return imageService.listTags(userId);
    }

    @PostMapping("/{id}/tags")
    public boolean updateTags(@PathVariable("id") Long id,
                              @RequestBody Map<String, Object> params) {
        Long userId = AuthContext.requireUserId();
        String tags = (String) params.get("tags");
        return imageService.updateTags(userId, id, tags);
    }

    @DeleteMapping("/{id}")
    public boolean delete(@PathVariable("id") Long id) {
        Long userId = AuthContext.requireUserId();
        return imageService.delete(userId, id);
    }

    @PostMapping("/delete/{id}")
    public boolean deleteByPost(@PathVariable("id") Long id) {
        Long userId = AuthContext.requireUserId();
        return imageService.delete(userId, id);
    }


}
