package com.xgsheng.backend.controller;

import com.xgsheng.backend.service.ImageService;
import com.xgsheng.backend.auth.AuthContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import java.util.List;


@RestController
@RequestMapping("/api/image")
public class ImageController {

    private static final Logger log = LoggerFactory.getLogger(ImageController.class);

    @Resource
    private ImageService imageService;

    @PostMapping("/saveByUrl")
    public Object saveByUrl(@RequestBody Map<String, Object> params) {
        Long userId = AuthContext.requireUserId();
        String url = (String) params.get("url");
        String tags = (String) params.get("tags");
        String referer = (String) params.get("referer");
        if (tags == null) {
            tags = (String) params.get("tag");
        }
        return imageService.saveByUrl(userId, url, tags, referer);
    }

    @PostMapping("/upload")
    public Object upload(@RequestParam("file") MultipartFile file,
                         @RequestParam(value = "tags", required = false) String tags,
                         @RequestParam(value = "tag", required = false) String tag) {
        Long userId = AuthContext.requireUserId();
        String resolvedTags = tags != null ? tags : tag;
        log.info("Uploading image for userId={}, originalFilename={}, size={}, tags={}",
                userId,
                file.getOriginalFilename(),
                file.getSize(),
                resolvedTags);
        return imageService.upload(userId, file, resolvedTags);
    }

    @GetMapping("/list")
    public Object list(@RequestParam(value = "tag", required = false) String tag) {
        Long userId = AuthContext.requireUserId();
        return imageService.list(userId, tag);
    }

    @PostMapping("/export/manifest")
    public Object exportManifest(@RequestBody Map<String, Object> params) {
        Long userId = AuthContext.requireUserId();
        Object tagValue = params.get("tags");
        List<String> tags;
        if (tagValue instanceof List<?> rawTags) {
            tags = rawTags.stream()
                    .filter(item -> item != null)
                    .map(String::valueOf)
                    .toList();
        } else {
            tags = List.of();
        }
        return imageService.exportManifest(userId, tags);
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

    @PostMapping("/tags/delete")
    public boolean deleteTag(@RequestBody Map<String, Object> params) {
        Long userId = AuthContext.requireUserId();
        String tagName = (String) params.get("tag");
        return imageService.deleteTag(userId, tagName);
    }

    @PostMapping("/tags/rename")
    public boolean renameTag(@RequestBody Map<String, Object> params) {
        Long userId = AuthContext.requireUserId();
        String oldTagName = (String) params.get("oldTag");
        String newTagName = (String) params.get("newTag");
        return imageService.renameTag(userId, oldTagName, newTagName);
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
