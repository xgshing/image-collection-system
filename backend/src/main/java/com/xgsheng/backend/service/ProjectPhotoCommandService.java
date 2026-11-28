package com.xgsheng.backend.service;

import com.xgsheng.backend.auth.AuthContext;
import com.xgsheng.backend.entity.Image;
import com.xgsheng.backend.entity.Project;
import com.xgsheng.backend.entity.ProjectPhoto;
import com.xgsheng.backend.exception.BusinessException;
import com.xgsheng.backend.mapper.ImageMapper;
import com.xgsheng.backend.mapper.ProjectMapper;
import com.xgsheng.backend.mapper.ProjectPhotoMapper;
import com.xgsheng.backend.photo.GeocodeClient;
import com.xgsheng.backend.photo.GeocodeResult;
import com.xgsheng.backend.photo.WeatherClient;
import com.xgsheng.backend.photo.WeatherResult;
import com.xgsheng.backend.project.CreateProjectPhotoRequest;
import com.xgsheng.backend.project.ProjectPhotoRules;
import com.xgsheng.backend.project.UpdateProjectPhotoRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;

/**
 * 项目照片的创建和修改。创建先做权限和幂等判断，再请求地点与天气，最后交给写入事务。
 */
@Service
public class ProjectPhotoCommandService {

    private final ProjectAccessService projectAccessService;
    private final ProjectMapper projectMapper;
    private final ProjectPhotoMapper projectPhotoMapper;
    private final ImageMapper imageMapper;
    private final ProjectPhotoWriter projectPhotoWriter;
    private final GeocodeClient geocodeClient;
    private final WeatherClient weatherClient;
    private final Clock clock;

    @Autowired
    public ProjectPhotoCommandService(ProjectAccessService projectAccessService,
                                      ProjectMapper projectMapper,
                                      ProjectPhotoMapper projectPhotoMapper,
                                      ImageMapper imageMapper,
                                      ProjectPhotoWriter projectPhotoWriter,
                                      GeocodeClient geocodeClient,
                                      WeatherClient weatherClient) {
        this(projectAccessService, projectMapper, projectPhotoMapper, imageMapper, projectPhotoWriter,
                geocodeClient, weatherClient, Clock.systemUTC());
    }

    ProjectPhotoCommandService(ProjectAccessService projectAccessService,
                               ProjectMapper projectMapper,
                               ProjectPhotoMapper projectPhotoMapper,
                               ImageMapper imageMapper,
                               ProjectPhotoWriter projectPhotoWriter,
                               GeocodeClient geocodeClient,
                               WeatherClient weatherClient,
                               Clock clock) {
        this.projectAccessService = projectAccessService;
        this.projectMapper = projectMapper;
        this.projectPhotoMapper = projectPhotoMapper;
        this.imageMapper = imageMapper;
        this.projectPhotoWriter = projectPhotoWriter;
        this.geocodeClient = geocodeClient;
        this.weatherClient = weatherClient;
        this.clock = clock;
    }

    public Map<String, Object> create(long projectId, CreateProjectPhotoRequest request) {
        long userId = AuthContext.requireUserId();
        projectAccessService.requireActiveMember(projectId);
        ProjectPhotoRules.validateCreate(request);
        Project project = requireKnownProject(projectId);
        rejectArchived(project);
        ProjectPhoto byRequest = projectPhotoMapper.findByPhotographerAndRequest(userId, request.getClientRequestId());
        if (byRequest != null) {
            return ProjectPhotoResponses.created(byRequest, false);
        }
        ProjectPhoto byImage = projectPhotoMapper.findByImageId(request.getImageId());
        if (byImage != null) {
            if (Long.valueOf(projectId).equals(byImage.getProjectId())) {
                return ProjectPhotoResponses.created(byImage, false);
            }
            throw BusinessException.conflict(ProjectPhotoRules.IMAGE_USED);
        }
        if (imageMapper.findById(userId, request.getImageId()) == null) {
            throw BusinessException.badRequest(ProjectPhotoRules.IMAGE_NOT_FOUND);
        }
        GeocodeResult geocode = null;
        WeatherResult weather = null;
        if (ProjectPhotoRules.isLocated(request.getGpsStatus())) {
            geocode = geocodeClient.reverse(request.getLatitude(), request.getLongitude());
            weather = weatherClient.current(request.getLatitude(), request.getLongitude());
        }
        ProjectPhoto draft = draft(projectId, userId, request);
        return projectPhotoWriter.insert(projectId, userId, draft, geocode, weather, now());
    }

    public Map<String, Object> update(long projectId, long photoId, UpdateProjectPhotoRequest request) {
        long userId = AuthContext.requireUserId();
        projectAccessService.requireActiveMember(projectId);
        rejectArchived(requireKnownProject(projectId));
        ProjectPhoto current = projectPhotoMapper.findByIdInProject(projectId, photoId);
        if (current == null) {
            throw BusinessException.notFound(ProjectPhotoRules.PHOTO_NOT_FOUND);
        }
        if (!Long.valueOf(userId).equals(current.getPhotographerUserId())) {
            throw BusinessException.forbidden(ProjectPhotoRules.ONLY_PHOTOGRAPHER);
        }
        if (request.isNotePresent()) {
            current.setNote(ProjectPhotoRules.normalizeNote(request.getNote()));
        }
        if (request.isWatermarkPositionPresent()) {
            current.setWatermarkPosition(com.xgsheng.backend.project.ProjectRules.normalizePosition(
                    request.getWatermarkPosition(), false));
        }
        if (request.isWatermarkVisiblePresent()) {
            current.setWatermarkVisible(Boolean.TRUE.equals(request.getWatermarkVisible()) ? 1 : 0);
        }
        projectPhotoWriter.update(projectId, userId, current, now());
        ProjectPhoto saved = projectPhotoMapper.findByIdInProject(projectId, photoId);
        if (saved == null) {
            throw new RuntimeException("project photo missing after update");
        }
        return ProjectPhotoResponses.detail(saved, null);
    }

    public Map<String, Object> delete(long projectId, long photoId) {
        long userId = AuthContext.requireUserId();
        var member = projectAccessService.requireActiveMember(projectId);
        rejectArchived(requireKnownProject(projectId));
        ProjectPhoto current = projectPhotoMapper.findByIdInProject(projectId, photoId);
        if (current == null) {
            throw BusinessException.notFound(ProjectPhotoRules.PHOTO_NOT_FOUND);
        }
        boolean isOwner = "owner".equals(member.getRole());
        if (!isOwner && !Long.valueOf(userId).equals(current.getPhotographerUserId())) {
            throw BusinessException.forbidden(ProjectPhotoRules.ONLY_PHOTOGRAPHER);
        }
        projectPhotoWriter.delete(projectId, photoId);
        return Map.of("deleted", true, "photoId", photoId);
    }

    private Project requireKnownProject(long projectId) {
        Project project = projectMapper.findById(projectId);
        if (project == null) {
            throw new RuntimeException("project row missing after membership check");
        }
        return project;
    }

    private static void rejectArchived(Project project) {
        if ("archived".equals(project.getStatus())) {
            throw BusinessException.badRequest(ProjectPhotoRules.ARCHIVED_READ_ONLY);
        }
        if (!"active".equals(project.getStatus())) {
            throw new RuntimeException("project status is inconsistent");
        }
    }

    private static ProjectPhoto draft(long projectId, long userId, CreateProjectPhotoRequest request) {
        ProjectPhoto photo = new ProjectPhoto();
        photo.setProjectId(projectId);
        photo.setImageId(request.getImageId());
        photo.setPhotographerUserId(userId);
        photo.setTakenAt(request.getTakenAt());
        photo.setTimezone(request.getTimezone());
        photo.setNote(request.getNote());
        photo.setLatitude(request.getLatitude());
        photo.setLongitude(request.getLongitude());
        photo.setCoordinateSystem(request.getCoordinateSystem());
        photo.setLocationAccuracyM(request.getLocationAccuracyMeters());
        photo.setLocationCapturedAt(request.getLocationCapturedAt());
        photo.setGpsStatus(request.getGpsStatus());
        photo.setWatermarkPosition(request.getWatermarkPosition());
        photo.setWatermarkVisible(Boolean.TRUE.equals(request.getWatermarkVisible()) ? 1 : 0);
        photo.setWidthPx(request.getWidthPx());
        photo.setHeightPx(request.getHeightPx());
        photo.setClientRequestId(request.getClientRequestId());
        return photo;
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }
}
