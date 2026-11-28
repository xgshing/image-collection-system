package com.xgsheng.backend.service;

import com.xgsheng.backend.entity.Image;
import com.xgsheng.backend.entity.Project;
import com.xgsheng.backend.entity.ProjectMemberRow;
import com.xgsheng.backend.entity.ProjectPhoto;
import com.xgsheng.backend.exception.BusinessException;
import com.xgsheng.backend.mapper.ImageMapper;
import com.xgsheng.backend.mapper.ProjectMapper;
import com.xgsheng.backend.mapper.ProjectMemberMapper;
import com.xgsheng.backend.mapper.ProjectPhotoMapper;
import com.xgsheng.backend.photo.GeocodeResult;
import com.xgsheng.backend.photo.WeatherResult;
import com.xgsheng.backend.project.ProjectPhotoRules;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 只负责持锁后的照片写入。外部逆地理和天气在进入本类之前完成，避免事务里等待网络。
 * 插入会锁定 image 行，和旧图片删除使用同一行锁。
 */
@Service
public class ProjectPhotoWriter {

    private static final Logger log = LoggerFactory.getLogger(ProjectPhotoWriter.class);

    private final ProjectMapper projectMapper;
    private final ProjectMemberMapper projectMemberMapper;
    private final ProjectPhotoMapper projectPhotoMapper;
    private final ImageMapper imageMapper;

    public ProjectPhotoWriter(ProjectMapper projectMapper,
                              ProjectMemberMapper projectMemberMapper,
                              ProjectPhotoMapper projectPhotoMapper,
                              ImageMapper imageMapper) {
        this.projectMapper = projectMapper;
        this.projectMemberMapper = projectMemberMapper;
        this.projectPhotoMapper = projectPhotoMapper;
        this.imageMapper = imageMapper;
    }

    @Transactional
    public Map<String, Object> insert(long projectId,
                                      long userId,
                                      ProjectPhoto draft,
                                      GeocodeResult geocode,
                                      WeatherResult weather,
                                      LocalDateTime now) {
        Project project = lockedActiveProject(projectId);
        ProjectMemberRow member = projectMemberMapper.lockByUserId(projectId, userId);
        if (member == null || !Long.valueOf(userId).equals(member.getUserId()) || !"active".equals(member.getStatus())) {
            throw BusinessException.forbidden(ProjectAccessService.NOT_A_MEMBER);
        }
        Image image = imageMapper.lockByOwner(userId, draft.getImageId());
        if (image == null) {
            throw BusinessException.badRequest(ProjectPhotoRules.IMAGE_NOT_FOUND);
        }
        ProjectPhoto byRequest = projectPhotoMapper.findByPhotographerAndRequest(userId, draft.getClientRequestId());
        if (byRequest != null) {
            return ProjectPhotoResponses.created(byRequest, false);
        }
        ProjectPhoto byImage = projectPhotoMapper.findByImageId(draft.getImageId());
        if (byImage != null) {
            return existingImage(projectId, byImage);
        }
        applySnapshot(draft, project.getName(), geocode, weather, now);
        try {
            int rows = projectPhotoMapper.insert(draft);
            if (rows != 1 || draft.getId() == null) {
                log.error("Project photo insert affected {} rows, projectId={}", rows, projectId);
                throw new RuntimeException("project photo insert row count mismatch");
            }
            return ProjectPhotoResponses.created(draft, true);
        } catch (DuplicateKeyException ex) {
            return resolveConflict(projectId, userId, draft);
        }
    }

    @Transactional
    public void update(long projectId, long userId, ProjectPhoto current, LocalDateTime now) {
        lockedActiveProject(projectId);
        int rows = projectPhotoMapper.updateEditable(
                current.getId(),
                projectId,
                userId,
                current.getNote(),
                current.getWatermarkPosition(),
                current.getWatermarkVisible(),
                now);
        if (rows != 1) {
            log.error("Project photo update affected {} rows, projectId={}, photoId={}",
                    rows, projectId, current.getId());
            throw new RuntimeException("project photo update row count mismatch");
        }
    }

    /**
     * 删除项目照片。水印相机原图（origin=project）只属于这张项目照片，
     * 同一事务里一并删除，避免它重新出现在收藏列表里。收藏来源的图片保留。
     */
    @Transactional
    public void delete(long projectId, long photoId) {
        lockedActiveProject(projectId);
        ProjectPhoto current = projectPhotoMapper.findByIdInProject(projectId, photoId);
        if (current == null) {
            throw BusinessException.notFound(ProjectPhotoRules.PHOTO_NOT_FOUND);
        }
        int rows = projectPhotoMapper.deleteByIdInProject(projectId, photoId);
        if (rows != 1) {
            throw BusinessException.notFound(ProjectPhotoRules.PHOTO_NOT_FOUND);
        }
        Long imageId = current.getImageId();
        if (imageId != null && imageMapper.lockProjectOriginById(imageId) != null) {
            imageMapper.deleteImageTagByImageId(imageId);
            imageMapper.deleteProjectOriginImageById(imageId);
        }
    }

    private Map<String, Object> resolveConflict(long projectId, long userId, ProjectPhoto draft) {
        ProjectPhoto byRequest = projectPhotoMapper.findByPhotographerAndRequest(userId, draft.getClientRequestId());
        if (byRequest != null) {
            return ProjectPhotoResponses.created(byRequest, false);
        }
        ProjectPhoto byImage = projectPhotoMapper.findByImageId(draft.getImageId());
        if (byImage != null) {
            return existingImage(projectId, byImage);
        }
        log.error("Project photo unique conflict could not be reread, projectId={}", projectId);
        throw new RuntimeException("project photo missing after unique conflict");
    }

    private static Map<String, Object> existingImage(long projectId, ProjectPhoto existing) {
        if (Long.valueOf(projectId).equals(existing.getProjectId())) {
            return ProjectPhotoResponses.created(existing, false);
        }
        throw BusinessException.conflict(ProjectPhotoRules.IMAGE_USED);
    }

    private Project lockedActiveProject(long projectId) {
        Project project = projectMapper.lockById(projectId);
        if (project == null) {
            log.error("Project row missing while writing photo, projectId={}", projectId);
            throw new RuntimeException("project row missing");
        }
        if ("archived".equals(project.getStatus())) {
            throw BusinessException.badRequest(ProjectPhotoRules.ARCHIVED_READ_ONLY);
        }
        if (!"active".equals(project.getStatus())) {
            throw new RuntimeException("project status is inconsistent");
        }
        return project;
    }

    private static void applySnapshot(ProjectPhoto draft,
                                      String projectName,
                                      GeocodeResult geocode,
                                      WeatherResult weather,
                                      LocalDateTime now) {
        boolean located = ProjectPhotoRules.isLocated(draft.getGpsStatus());
        if (!located) {
            draft.setLatitude(null);
            draft.setLongitude(null);
            draft.setCoordinateSystem(null);
            draft.setLocationText(null);
            draft.setGeocodeStatus("skipped");
            clearWeather(draft, "skipped");
        } else {
            draft.setCoordinateSystem("gcj02");
            if (geocode != null && geocode.ok()) {
                draft.setLocationText(geocode.locationText());
                draft.setGeocodeStatus("ok");
            } else {
                draft.setLocationText(null);
                draft.setGeocodeStatus("failed");
            }
            if (weather != null && weather.complete()) {
                draft.setWeatherText(weather.weatherText());
                draft.setTemperatureC(weather.temperatureC());
                draft.setHumidityPercent(weather.humidityPercent());
                draft.setWeatherCode(weather.weatherCode());
                draft.setWeatherProvider("qweather");
                draft.setWeatherObservedAt(weather.observedAt());
                draft.setWeatherStatus("ok");
            } else if (weather != null && "rate_limited".equals(weather.status())) {
                clearWeather(draft, "rate_limited");
            } else {
                clearWeather(draft, "failed");
            }
        }
        draft.setProjectNameSnapshot(projectName == null ? "" : projectName);
        draft.setCreatedAt(now);
        draft.setUpdatedAt(now);
    }

    private static void clearWeather(ProjectPhoto draft, String status) {
        draft.setWeatherText(null);
        draft.setTemperatureC(null);
        draft.setHumidityPercent(null);
        draft.setWeatherCode(null);
        draft.setWeatherProvider(null);
        draft.setWeatherObservedAt(null);
        draft.setWeatherStatus(status);
    }
}
