package com.xgsheng.backend.controller;

import com.xgsheng.backend.project.ProjectPhotoRequests;
import com.xgsheng.backend.service.ProjectPhotoCommandService;
import com.xgsheng.backend.service.ProjectPhotoDownloadService;
import com.xgsheng.backend.service.ProjectPhotoQueryService;
import com.xgsheng.backend.util.JsonBodies;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 项目照片接口。权限在 Service 中通过 ProjectAccessService 判断，这里不重复写成员状态。
 */
@RestController
public class ProjectPhotoController {

    private final ProjectPhotoCommandService projectPhotoCommandService;
    private final ProjectPhotoQueryService projectPhotoQueryService;
    private final ProjectPhotoDownloadService projectPhotoDownloadService;

    public ProjectPhotoController(ProjectPhotoCommandService projectPhotoCommandService,
                                  ProjectPhotoQueryService projectPhotoQueryService,
                                  ProjectPhotoDownloadService projectPhotoDownloadService) {
        this.projectPhotoCommandService = projectPhotoCommandService;
        this.projectPhotoQueryService = projectPhotoQueryService;
        this.projectPhotoDownloadService = projectPhotoDownloadService;
    }

    @PostMapping("/api/project/{projectId:\\d+}/photos")
    public Map<String, Object> create(@PathVariable("projectId") long projectId,
                                      @RequestBody Map<String, Object> body) {
        return projectPhotoCommandService.create(projectId, ProjectPhotoRequests.parseCreate(JsonBodies.of(body)));
    }

    @GetMapping("/api/project/{projectId:\\d+}/photos")
    public Map<String, Object> list(@PathVariable("projectId") long projectId,
                                    @RequestParam(value = "page", required = false) Integer page,
                                    @RequestParam(value = "pageSize", required = false) Integer pageSize) {
        return projectPhotoQueryService.list(projectId, page, pageSize);
    }

    @GetMapping("/api/project/{projectId:\\d+}/photos/{photoId:\\d+}")
    public Map<String, Object> detail(@PathVariable("projectId") long projectId,
                                      @PathVariable("photoId") long photoId) {
        return projectPhotoQueryService.detail(projectId, photoId);
    }

    @PatchMapping("/api/project/{projectId:\\d+}/photos/{photoId:\\d+}")
    public Map<String, Object> update(@PathVariable("projectId") long projectId,
                                      @PathVariable("photoId") long photoId,
                                      @RequestBody Map<String, Object> body) {
        return projectPhotoCommandService.update(projectId, photoId, ProjectPhotoRequests.parseUpdate(JsonBodies.of(body)));
    }

    @DeleteMapping("/api/project/{projectId:\\d+}/photos/{photoId:\\d+}")
    public Map<String, Object> delete(@PathVariable("projectId") long projectId,
                                      @PathVariable("photoId") long photoId) {
        return projectPhotoCommandService.delete(projectId, photoId);
    }

    @GetMapping("/api/project/{projectId:\\d+}/photos/{photoId:\\d+}/original")
    public Map<String, Object> original(@PathVariable("projectId") long projectId,
                                        @PathVariable("photoId") long photoId) {
        return projectPhotoQueryService.original(projectId, photoId);
    }

    @GetMapping("/api/project/{projectId:\\d+}/photos/{photoId:\\d+}/watermarked")
    public ResponseEntity<byte[]> watermarked(@PathVariable("projectId") long projectId,
                                              @PathVariable("photoId") long photoId) {
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_JPEG)
                .cacheControl(CacheControl.noStore())
                .body(projectPhotoDownloadService.watermarked(projectId, photoId));
    }
}
