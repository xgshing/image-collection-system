package com.xgsheng.backend.service.impl;

import com.xgsheng.backend.entity.Image;
import com.xgsheng.backend.mapper.ImageMapper;
import com.xgsheng.backend.service.ImageService;
import com.xgsheng.backend.service.StorageService;
import com.xgsheng.backend.util.DownloadUtil;
import com.xgsheng.backend.util.Md5Util;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class ImageServiceImpl implements ImageService {

    @Resource
    private ImageMapper imageMapper;

    @Resource
    private StorageService storageService;

    @Override
    public Map<String, Object> saveByUrl(Long userId, String url, String tags, String referer) {
        if (userId == null) {
            throw new IllegalArgumentException("userId is required");
        }
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("url is required");
        }

        try {
            byte[] bytes = DownloadUtil.download(url.trim(), referer);
            return saveImage(userId, bytes, tags, url.trim(), extensionFromUrl(url), null, true);
        } catch (Exception ex) {
            throw new IllegalStateException("download image failed", ex);
        }
    }

    @Override
    public Map<String, Object> upload(Long userId, MultipartFile file, String tags) {
        if (userId == null) {
            throw new IllegalArgumentException("userId is required");
        }
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("file is required");
        }

        try {
            return saveImage(
                    userId,
                    file.getBytes(),
                    tags,
                    "",
                    extensionFromFilename(file.getOriginalFilename()),
                    file.getContentType(),
                    false
            );
        } catch (IOException ex) {
            throw new IllegalStateException("read upload failed", ex);
        }
    }

    @Override
    public List<Image> list(Long userId, String tag) {
        List<Image> images = (tag == null || tag.isBlank())
                ? imageMapper.findAll(userId)
                : imageMapper.findByTag(userId, tag.trim());
        images.forEach(this::fillTags);
        return images;
    }

    @Override
    public Map<String, Object> exportManifest(Long userId, List<String> tags) {
        List<String> normalizedTags = normalizeTagList(tags);
        List<Image> images = normalizedTags.isEmpty()
                ? List.of()
                : imageMapper.findByTags(userId, normalizedTags);
        images.forEach(this::fillTags);

        List<Map<String, Object>> items = new ArrayList<>();
        for (Image image : images) {
            List<String> matchedTags = image.getTags() == null
                    ? List.of()
                    : image.getTags().stream()
                    .filter(normalizedTags::contains)
                    .toList();
            if (matchedTags.isEmpty()) {
                continue;
            }

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("imageId", image.getId());
            item.put("localUrl", image.getLocalUrl());
            item.put("sourceUrl", image.getUrl());
            item.put("createTime", image.getCreateTime());
            item.put("tags", image.getTags());
            item.put("matchedTags", matchedTags);
            items.add(item);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("requestedTags", normalizedTags);
        result.put("totalImages", items.size());
        result.put("items", items);
        return result;
    }

    @Override
    public Image detail(Long userId, Long id) {
        Image image = imageMapper.findById(userId, id);
        if (image == null) {
            return null;
        }
        fillTags(image);
        return image;
    }

    @Override
    public List<String> listTags(Long userId) {
        return imageMapper.findAllTagNames(userId);
    }

    @Override
    public boolean deleteTag(Long userId, String tagName) {
        if (userId == null || tagName == null || tagName.isBlank()) {
            return false;
        }

        Long tagId = imageMapper.findTagIdByName(tagName.trim());
        if (tagId == null) {
            return false;
        }

        imageMapper.deleteImageTagByUserIdAndTagId(userId, tagId);
        return true;
    }

    @Override
    public boolean renameTag(Long userId, String oldTagName, String newTagName) {
        if (userId == null || oldTagName == null || newTagName == null) {
            return false;
        }

        String oldValue = oldTagName.trim();
        String newValue = newTagName.trim();
        if (oldValue.isBlank() || newValue.isBlank()) {
            return false;
        }
        if (oldValue.equals(newValue)) {
            return true;
        }

        Long sourceTagId = imageMapper.findTagIdByName(oldValue);
        if (sourceTagId == null) {
            return false;
        }

        Long targetTagId = imageMapper.findTagIdByName(newValue);
        if (targetTagId == null) {
            imageMapper.insertTag(newValue);
            targetTagId = imageMapper.findTagIdByName(newValue);
        }
        if (targetTagId == null) {
            return false;
        }

        imageMapper.insertImageTagForUserImagesByTagId(userId, sourceTagId, targetTagId);
        imageMapper.deleteImageTagByUserIdAndTagId(userId, sourceTagId);
        return true;
    }

    @Override
    public boolean updateTags(Long userId, Long id, String tags) {
        Image image = imageMapper.findById(userId, id);
        if (image == null) {
            return false;
        }

        replaceTags(image.getId(), parseTags(tags));
        return true;
    }

    @Override
    public boolean delete(Long userId, Long id) {
        Image image = imageMapper.findById(userId, id);
        if (image == null) {
            return false;
        }

        imageMapper.deleteImageTagByImageId(id);
        imageMapper.deleteById(userId, id);
        return true;
    }

    private Map<String, Object> saveImage(Long userId,
                                          byte[] bytes,
                                          String tags,
                                          String sourceUrl,
                                          String extension,
                                          String contentType,
                                          boolean updateCreateTimeWhenExists) {
        if (bytes == null || bytes.length == 0) {
            throw new IllegalArgumentException("image content is empty");
        }

        try {
            String md5 = Md5Util.getMD5(bytes);
            Image existing = imageMapper.findByMd5(userId, md5);
            List<String> normalizedTags = parseTags(tags);
            Date now = new Date();

            if (existing != null) {
                if (updateCreateTimeWhenExists) {
                    imageMapper.updateCreateTime(userId, existing.getId(), now);
                    existing.setCreateTime(now);
                }
                mergeTags(existing.getId(), normalizedTags);
                fillTags(existing);
                return buildResult(existing, "exists", "image already exists");
            }

            Image image = new Image();
            image.setUserId(userId);
            image.setUrl(sourceUrl == null ? "" : sourceUrl);
            image.setMd5(md5);
            image.setCreateTime(now);
            image.setLocalUrl(storageService.storeBytes(
                    bytes,
                    "image" + normalizeExtension(extension),
                    contentType,
                    "images"
            ));
            imageMapper.insert(image);
            replaceTags(image.getId(), normalizedTags);
            fillTags(image);
            return buildResult(image, "created", "saved");
        } catch (Exception ex) {
            throw new IllegalStateException("save image failed", ex);
        }
    }

    private void fillTags(Image image) {
        image.setTags(imageMapper.findTagNamesByImageId(image.getId()));
    }

    private void mergeTags(Long imageId, List<String> incomingTags) {
        List<String> mergedTags = new ArrayList<>(imageMapper.findTagNamesByImageId(imageId));
        for (String tag : incomingTags) {
            if (!mergedTags.contains(tag)) {
                mergedTags.add(tag);
            }
        }
        replaceTags(imageId, mergedTags);
    }

    private void replaceTags(Long imageId, List<String> tags) {
        imageMapper.deleteImageTagByImageId(imageId);
        for (String tag : tags) {
            Long tagId = imageMapper.findTagIdByName(tag);
            if (tagId == null) {
                imageMapper.insertTag(tag);
                tagId = imageMapper.findTagIdByName(tag);
            }
            if (tagId != null) {
                imageMapper.insertImageTag(imageId, tagId);
            }
        }
    }

    private List<String> parseTags(String tags) {
        if (tags == null || tags.isBlank()) {
            return List.of();
        }

        return normalizeTagList(Arrays.asList(tags.split("[,\\uFF0C]")));
    }

    private List<String> normalizeTagList(List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return List.of();
        }

        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        tags.stream()
                .map(item -> item == null ? "" : item.trim())
                .filter(item -> !item.isBlank())
                .forEach(normalized::add);
        return new ArrayList<>(normalized);
    }

    private Map<String, Object> buildResult(Image image, String status, String message) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", status);
        result.put("message", message);
        result.put("imageId", image.getId());
        result.put("localUrl", image.getLocalUrl());
        result.put("createTime", image.getCreateTime());
        result.put("tags", image.getTags() == null ? List.of() : image.getTags());
        return result;
    }

    private String extensionFromFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            return ".jpg";
        }
        int index = filename.lastIndexOf('.');
        return index >= 0 ? filename.substring(index) : ".jpg";
    }

    private String extensionFromUrl(String url) {
        try {
            String path = URI.create(url).getPath();
            if (path == null) {
                return ".jpg";
            }
            int index = path.lastIndexOf('.');
            return index >= 0 ? path.substring(index) : ".jpg";
        } catch (Exception ex) {
            return ".jpg";
        }
    }

    private String normalizeExtension(String extension) {
        String value = extension == null ? "" : extension.trim().toLowerCase(Locale.ROOT);
        if (!value.startsWith(".")) {
            value = "." + value;
        }
        return switch (value) {
            case ".png", ".jpg", ".jpeg", ".gif", ".webp", ".bmp" -> value;
            default -> ".jpg";
        };
    }
}
