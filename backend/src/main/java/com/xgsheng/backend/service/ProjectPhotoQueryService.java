package com.xgsheng.backend.service;

import com.xgsheng.backend.entity.Project;
import com.xgsheng.backend.entity.ProjectPhoto;
import com.xgsheng.backend.exception.BusinessException;
import com.xgsheng.backend.mapper.ProjectMapper;
import com.xgsheng.backend.mapper.ProjectPhotoMapper;
import com.xgsheng.backend.project.ProjectPage;
import com.xgsheng.backend.project.ProjectPhotoRules;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 项目照片查询。先确认在职成员，再读 project_photo 和签发临时地址。
 */
@Service
public class ProjectPhotoQueryService {

    private final ProjectAccessService projectAccessService;
    private final ProjectMapper projectMapper;
    private final ProjectPhotoMapper projectPhotoMapper;
    private final StorageService storageService;

    public ProjectPhotoQueryService(ProjectAccessService projectAccessService,
                                    ProjectMapper projectMapper,
                                    ProjectPhotoMapper projectPhotoMapper,
                                    StorageService storageService) {
        this.projectAccessService = projectAccessService;
        this.projectMapper = projectMapper;
        this.projectPhotoMapper = projectPhotoMapper;
        this.storageService = storageService;
    }

    public Map<String, Object> list(long projectId, Integer page, Integer pageSize) {
        requireReadableProject(projectId);
        ProjectPage resolved = ProjectPage.resolve(page, pageSize);
        List<Map<String, Object>> items = new ArrayList<>();
        for (ProjectPhoto photo : projectPhotoMapper.listByProject(projectId, resolved.getPageSize(), resolved.offset())) {
            items.add(ProjectPhotoResponses.detail(photo, storageService.readableUrl(photo.getStoredUrl())));
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("items", items);
        body.put("page", resolved.getPage());
        body.put("pageSize", resolved.getPageSize());
        body.put("total", projectPhotoMapper.countByProjectId(projectId));
        return body;
    }

    public Map<String, Object> detail(long projectId, long photoId) {
        ProjectPhoto photo = requirePhoto(projectId, photoId);
        return ProjectPhotoResponses.detail(photo, storageService.readableUrl(photo.getStoredUrl()));
    }

    public Map<String, Object> original(long projectId, long photoId) {
        ProjectPhoto photo = requirePhoto(projectId, photoId);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("url", storageService.readableUrl(photo.getStoredUrl()));
        body.put("expiresInSeconds", storageService.signedReadTtlSeconds());
        return body;
    }

    public ProjectPhoto requirePhoto(long projectId, long photoId) {
        requireReadableProject(projectId);
        ProjectPhoto photo = projectPhotoMapper.findByIdInProject(projectId, photoId);
        if (photo == null) {
            throw BusinessException.notFound(ProjectPhotoRules.PHOTO_NOT_FOUND);
        }
        return photo;
    }

    private void requireReadableProject(long projectId) {
        projectAccessService.requireActiveMember(projectId);
        Project project = projectMapper.findById(projectId);
        if (project == null) {
            throw new RuntimeException("project row missing after membership check");
        }
        if (!"active".equals(project.getStatus()) && !"archived".equals(project.getStatus())) {
            throw new RuntimeException("project status is inconsistent");
        }
    }
}
