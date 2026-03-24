package com.xgsheng.backend.service.impl;

import com.xgsheng.backend.config.StorageProperties;
import com.xgsheng.backend.entity.Image;
import com.xgsheng.backend.mapper.ImageMapper;
import com.xgsheng.backend.service.ImageService;
import com.xgsheng.backend.util.CosStorageClient;
import com.xgsheng.backend.util.DownloadUtil;
import com.xgsheng.backend.util.Md5Util;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ImageServiceImpl implements ImageService {

    @Resource
    private ImageMapper imageMapper;

    @Resource
    private StorageProperties storageProperties;

    @Resource
    private CosStorageClient cosStorageClient;

    @Override
    public String saveByUrl(String url, String tags) {
        try {
            byte[] bytes = DownloadUtil.download(url);
            String md5 = Md5Util.getMD5(bytes);

            Image exist = imageMapper.findByMd5(md5);
            if (exist != null) {
                return exist.getLocalUrl();
            }

            String savedUrl = saveBytes(bytes);
            Image image = buildImage(url, savedUrl, md5);
            imageMapper.insert(image);
            bindTags(image.getId(), tags);

            return image.getLocalUrl();
        } catch (Exception e) {
            return "error";
        }
    }

    @Override
    public String upload(MultipartFile file, String tags) {
        try {
            byte[] bytes = file.getBytes();
            String md5 = Md5Util.getMD5(bytes);

            Image exist = imageMapper.findByMd5(md5);
            if (exist != null) {
                return exist.getLocalUrl();
            }

            String savedUrl = saveBytes(bytes);
            Image image = buildImage(null, savedUrl, md5);
            imageMapper.insert(image);
            bindTags(image.getId(), tags);

            return image.getLocalUrl();
        } catch (Exception e) {
            return "error";
        }
    }

    @Override
    public List<Image> list(String tag) {
        List<Image> images = (tag == null || tag.isBlank())
                ? imageMapper.findAll()
                : imageMapper.findByTag(tag);
        images.forEach(this::fillTags);
        return images;
    }

    @Override
    public Image detail(Long id) {
        Image image = imageMapper.findById(id);
        if (image != null) {
            fillTags(image);
        }
        return image;
    }

    @Override
    public List<String> listTags() {
        return imageMapper.findAllTagNames();
    }

    @Override
    public boolean updateTags(Long id, String tags) {
        Image image = imageMapper.findById(id);
        if (image == null) {
            return false;
        }

        imageMapper.deleteImageTagByImageId(id);
        bindTags(id, tags);
        return true;
    }

    @Override
    public boolean delete(Long id) {
        Image image = imageMapper.findById(id);
        if (image == null) {
            return false;
        }

        imageMapper.deleteImageTagByImageId(id);
        imageMapper.deleteById(id);
        deleteStoredFile(image.getLocalUrl());
        return true;
    }

    private Image buildImage(String url, String localUrl, String md5) {
        Image image = new Image();
        image.setUserId(null);
        image.setUrl(url);
        image.setLocalUrl(localUrl);
        image.setMd5(md5);
        image.setWidth(null);
        image.setHeight(null);
        image.setCreateTime(new Date());
        return image;
    }

    private String saveBytes(byte[] bytes) throws IOException {
        if (useCosStorage()) {
            String objectKey = cosStorageClient.buildObjectKey(storageProperties.getCos(), System.currentTimeMillis());
            cosStorageClient.upload(storageProperties.getCos(), objectKey, bytes);
            return cosStorageClient.toPublicUrl(storageProperties.getCos(), objectKey);
        }

        File targetFile = createLocalTargetFile();
        java.nio.file.Files.write(targetFile.toPath(), bytes);
        return buildLocalUrl(targetFile.getName());
    }

    private File createLocalTargetFile() throws IOException {
        String configuredDir = storageProperties.getLocalDir();
        String directory = (configuredDir == null || configuredDir.isBlank()) ? "uploads" : configuredDir.trim();

        File uploadDir = new File(System.getProperty("user.dir"), directory);
        if (!uploadDir.exists() && !uploadDir.mkdirs()) {
            throw new IOException("failed to create upload directory");
        }

        return new File(uploadDir, System.currentTimeMillis() + ".jpg");
    }

    private String buildLocalUrl(String fileName) {
        String basePath = storageProperties.getLocalBasePath();
        if (basePath == null || basePath.isBlank()) {
            basePath = "/uploads";
        }
        if (!basePath.startsWith("/")) {
            basePath = "/" + basePath;
        }
        while (basePath.endsWith("/") && basePath.length() > 1) {
            basePath = basePath.substring(0, basePath.length() - 1);
        }
        return basePath + "/" + fileName;
    }

    private void bindTags(Long imageId, String tags) {
        if (imageId == null || tags == null || tags.isBlank()) {
            return;
        }

        List<String> tagNames = Arrays.stream(tags.split(","))
                .map(String::trim)
                .filter(tag -> !tag.isBlank())
                .distinct()
                .collect(Collectors.toList());

        for (String tag : tagNames) {
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

    private void fillTags(Image image) {
        image.setTags(imageMapper.findTagNamesByImageId(image.getId()));
    }

    private void deleteStoredFile(String localUrl) {
        if (useCosStorage()) {
            String key = cosStorageClient.extractKeyFromUrl(storageProperties.getCos(), localUrl);
            cosStorageClient.delete(storageProperties.getCos(), key);
            return;
        }

        if (localUrl == null || localUrl.isBlank()) {
            return;
        }

        String basePath = storageProperties.getLocalBasePath();
        if (basePath == null || basePath.isBlank()) {
            basePath = "/uploads";
        }
        String relativePath = localUrl;
        if (relativePath.startsWith(basePath)) {
            relativePath = relativePath.substring(basePath.length());
        }
        if (relativePath.startsWith("/")) {
            relativePath = relativePath.substring(1);
        }

        String configuredDir = storageProperties.getLocalDir();
        String directory = (configuredDir == null || configuredDir.isBlank()) ? "uploads" : configuredDir.trim();

        String fullRelativePath = directory + File.separator + relativePath.replace("/", File.separator);
        File file = new File(System.getProperty("user.dir"), fullRelativePath);
        if (file.exists()) {
            file.delete();
        }
    }

    private boolean useCosStorage() {
        if (!"cos".equalsIgnoreCase(storageProperties.getType())) {
            return false;
        }
        return cosStorageClient.isConfigured(storageProperties.getCos());
    }
}
