package com.xgsheng.backend.service;

import com.xgsheng.backend.entity.ProjectPhoto;
import com.xgsheng.backend.exception.BusinessException;
import com.xgsheng.backend.photo.ProjectPhotoWatermarkRenderer;
import com.xgsheng.backend.project.ProjectPhotoRules;
import org.springframework.stereotype.Service;

/**
 * 带水印下载只在内存生成 JPEG。不上传 COS，不改原图地址。
 * 已签发的临时 URL 不能因成员退出而立刻失效，本方法不负责撤销旧链接。
 */
@Service
public class ProjectPhotoDownloadService {

    private final ProjectPhotoQueryService projectPhotoQueryService;
    private final StorageService storageService;

    public ProjectPhotoDownloadService(ProjectPhotoQueryService projectPhotoQueryService,
                                       StorageService storageService) {
        this.projectPhotoQueryService = projectPhotoQueryService;
        this.storageService = storageService;
    }

    public byte[] watermarked(long projectId, long photoId) {
        ProjectPhoto photo = projectPhotoQueryService.requirePhoto(projectId, photoId);
        try {
            byte[] original = storageService.readStoredBytes(photo.getStoredUrl());
            return ProjectPhotoWatermarkRenderer.render(original, photo);
        } catch (BusinessException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw BusinessException.badRequest(ProjectPhotoRules.RENDER_FAILED);
        }
    }
}
