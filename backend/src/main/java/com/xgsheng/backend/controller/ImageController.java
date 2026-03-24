package com.xgsheng.backend.controller;

import com.xgsheng.backend.service.ImageService;
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
        String url = (String) params.get("url");
        String tags = (String) params.get("tags");
        if (tags == null) {
            tags = (String) params.get("tag");
        }
        return imageService.saveByUrl(url, tags);
    }

    @PostMapping("/upload")
    public String upload(@RequestParam("file") MultipartFile file,
                         @RequestParam(value = "tags", required = false) String tags,
                         @RequestParam(value = "tag", required = false) String tag) {
        return imageService.upload(file, tags != null ? tags : tag);
    }

    @GetMapping("/list")
    public Object list(@RequestParam(value = "tag", required = false) String tag) {
        return imageService.list(tag);
    }

    @GetMapping("/{id}")
    public Object detail(@PathVariable("id") Long id) {
        return imageService.detail(id);
    }

    @GetMapping("/tags")
    public Object tags() {
        return imageService.listTags();
    }

    @PostMapping("/{id}/tags")
    public boolean updateTags(@PathVariable("id") Long id,
                              @RequestBody Map<String, Object> params) {
        String tags = (String) params.get("tags");
        return imageService.updateTags(id, tags);
    }

    @DeleteMapping("/{id}")
    public boolean delete(@PathVariable("id") Long id) {
        return imageService.delete(id);
    }

    @PostMapping("/delete/{id}")
    public boolean deleteByPost(@PathVariable("id") Long id) {
        return imageService.delete(id);
    }


}
