package com.xgsheng.backend.service;

import com.xgsheng.backend.auth.AuthContext;
import com.xgsheng.backend.entity.Project;
import com.xgsheng.backend.exception.BusinessException;
import com.xgsheng.backend.exception.PendingProductRuleException;
import com.xgsheng.backend.mapper.ProjectMapper;
import com.xgsheng.backend.mapper.ProjectMemberMapper;
import com.xgsheng.backend.project.CreateProjectRequest;
import com.xgsheng.backend.project.ProjectRules;
import com.xgsheng.backend.project.UpdateProjectRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class ProjectCommandService {

    private static final Logger log = LoggerFactory.getLogger(ProjectCommandService.class);

    private final ProjectMapper projectMapper;
    private final ProjectMemberMapper projectMemberMapper;
    private final ProjectAccessService projectAccessService;
    private final Clock clock;

    @Autowired
    public ProjectCommandService(ProjectMapper projectMapper,
                                 ProjectMemberMapper projectMemberMapper,
                                 ProjectAccessService projectAccessService) {
        this(projectMapper, projectMemberMapper, projectAccessService, Clock.systemUTC());
    }

    ProjectCommandService(ProjectMapper projectMapper,
                                 ProjectMemberMapper projectMemberMapper,
                                 ProjectAccessService projectAccessService,
                                 Clock clock) {
        this.projectMapper = projectMapper;
        this.projectMemberMapper = projectMemberMapper;
        this.projectAccessService = projectAccessService;
        this.clock = clock;
    }

    @Transactional
    public Map<String, Object> create(CreateProjectRequest request) {
        long userId = AuthContext.requireUserId();
        String name = ProjectRules.normalizeName(request == null ? null : request.getName());
        boolean watermarkEnabled = request.getWatermarkEnabled() == null || request.getWatermarkEnabled();
        String position = ProjectRules.normalizePosition(
                request.getDefaultWatermarkPosition(), request.getDefaultWatermarkPosition() == null);
        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);

        Project project = new Project();
        project.setName(name);
        project.setOwnerUserId(userId);
        project.setWatermarkEnabled(watermarkEnabled ? 1 : 0);
        project.setDefaultWatermarkPosition(position);
        project.setStatus("active");
        project.setCreatedAt(now);
        project.setUpdatedAt(now);
        int projectRows = projectMapper.insert(project);
        if (projectRows != 1 || project.getId() == null) {
            log.error("Project insert did not create one row");
            throw new RuntimeException("project insert row count mismatch");
        }
        int memberRows = projectMemberMapper.insertOwner(project.getId(), userId, now);
        if (memberRows != 1) {
            log.error("Owner membership insert affected {} rows for projectId={}", memberRows, project.getId());
            throw new RuntimeException("project owner membership row count mismatch");
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", project.getId());
        body.put("name", project.getName());
        body.put("role", "owner");
        body.put("status", "active");
        body.put("watermarkEnabled", watermarkEnabled);
        body.put("defaultWatermarkPosition", position);
        body.put("createdAt", ProjectResponses.formatTime(now));
        return body;
    }

    @Transactional
    public void updateActive(long projectId, UpdateProjectRequest request) {
        long userId = AuthContext.requireUserId();
        projectAccessService.requireOwner(projectId, "only owner can update project");
        if (request == null || !request.hasAnyField()) {
            throw new IllegalArgumentException("project update is empty");
        }
        Project current = projectMapper.findById(projectId);
        if (current == null) {
            log.error("Project disappeared after owner check, projectId={}", projectId);
            throw new RuntimeException("project row missing after owner check");
        }
        if (!"active".equals(current.getStatus())) {
            throw new PendingProductRuleException("archived project update is not confirmed, projectId=" + projectId);
        }
        if (!Long.valueOf(userId).equals(current.getOwnerUserId())) {
            log.error("owner_user_id does not match active owner, projectId={}", projectId);
            throw new RuntimeException("project owner row is inconsistent");
        }

        String name = request.getName() == null ? current.getName() : ProjectRules.normalizeName(request.getName());
        Integer currentWatermark = current.getWatermarkEnabled();
        if (request.getWatermarkEnabled() == null && currentWatermark == null) {
            log.error("Project watermark flag is missing, projectId={}", projectId);
            throw new RuntimeException("project watermark flag is missing");
        }
        if (request.getDefaultWatermarkPosition() == null && current.getDefaultWatermarkPosition() == null) {
            log.error("Project watermark position is missing, projectId={}", projectId);
            throw new RuntimeException("project watermark position is missing");
        }
        int watermark = request.getWatermarkEnabled() == null
                ? currentWatermark
                : (request.getWatermarkEnabled() ? 1 : 0);
        String position = request.getDefaultWatermarkPosition() == null
                ? current.getDefaultWatermarkPosition()
                : ProjectRules.normalizePosition(request.getDefaultWatermarkPosition(), false);
        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);

        Project update = new Project();
        update.setId(projectId);
        update.setOwnerUserId(userId);
        update.setName(name);
        update.setWatermarkEnabled(watermark);
        update.setDefaultWatermarkPosition(position);
        update.setUpdatedAt(now);
        int rows = projectMapper.updateActive(update);
        if (rows != 1) {
            log.error("Active project update affected {} rows for projectId={}", rows, projectId);
            throw new RuntimeException("project update row count mismatch");
        }
    }

    @Transactional
    public void archive(long projectId) {
        long userId = AuthContext.requireUserId();
        projectAccessService.requireOwner(projectId, "only owner can delete project");
        Project current = projectMapper.lockById(projectId);
        if (current == null) {
            log.error("Project disappeared after owner check, projectId={}", projectId);
            throw new RuntimeException("project row missing after owner check");
        }
        if (!"active".equals(current.getStatus())) {
            throw BusinessException.badRequest("archived project is read only");
        }
        if (!Long.valueOf(userId).equals(current.getOwnerUserId())) {
            log.error("owner_user_id does not match active owner, projectId={}", projectId);
            throw new RuntimeException("project owner row is inconsistent");
        }
        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        int rows = projectMapper.archiveActive(projectId, userId, now);
        if (rows != 1) {
            log.error("Project archive affected {} rows for projectId={}", rows, projectId);
            throw new RuntimeException("project archive row count mismatch");
        }
    }

    @Transactional
    public void purge(long projectId) {
        long userId = AuthContext.requireUserId();
        projectAccessService.requireOwner(projectId, "only owner can delete project");
        Project current = projectMapper.lockById(projectId);
        if (current == null) {
            log.error("Project disappeared after owner check, projectId={}", projectId);
            throw new RuntimeException("project row missing after owner check");
        }
        if (!"archived".equals(current.getStatus())) {
            throw BusinessException.badRequest("only archived project can be purged");
        }
        if (!Long.valueOf(userId).equals(current.getOwnerUserId())) {
            log.error("owner_user_id does not match active owner, projectId={}", projectId);
            throw new RuntimeException("project owner row is inconsistent");
        }
        projectMapper.deleteMessagesForProject(projectId);
        projectMapper.deleteAcceptancesForProject(projectId);
        projectMapper.deleteInvitesForProject(projectId);
        projectMapper.deletePhotosForProject(projectId);
        projectMapper.deleteMembersForProject(projectId);
        int rows = projectMapper.deleteArchived(projectId, userId);
        if (rows != 1) {
            log.error("Archived project delete affected {} rows for projectId={}", rows, projectId);
            throw new RuntimeException("project purge row count mismatch");
        }
    }
}
