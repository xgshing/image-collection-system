package com.xgsheng.backend.service;

import com.xgsheng.backend.auth.AuthContext;
import com.xgsheng.backend.entity.ProjectSummaryRow;
import com.xgsheng.backend.mapper.ProjectMapper;
import com.xgsheng.backend.mapper.ProjectPhotoMapper;
import com.xgsheng.backend.project.ProjectPage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ProjectQueryService {

    private static final Logger log = LoggerFactory.getLogger(ProjectQueryService.class);

    private final ProjectAccessService projectAccessService;
    private final ProjectMapper projectMapper;
    private final ProjectPhotoMapper projectPhotoMapper;

    public ProjectQueryService(ProjectAccessService projectAccessService,
                               ProjectMapper projectMapper,
                               ProjectPhotoMapper projectPhotoMapper) {
        this.projectAccessService = projectAccessService;
        this.projectMapper = projectMapper;
        this.projectPhotoMapper = projectPhotoMapper;
    }

    public Map<String, Object> list(Integer page, Integer pageSize) {
        long userId = AuthContext.requireUserId();
        ProjectPage resolved = ProjectPage.resolve(page, pageSize);
        List<ProjectSummaryRow> rows = projectMapper.listActiveForUser(userId, resolved.getPageSize(), resolved.offset());
        long total = projectMapper.countActiveForUser(userId);
        List<Map<String, Object>> items = new ArrayList<>();
        for (ProjectSummaryRow row : rows) {
            items.add(toItem(row, false));
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("items", items);
        body.put("page", resolved.getPage());
        body.put("pageSize", resolved.getPageSize());
        body.put("total", total);
        return body;
    }

    public Map<String, Object> detail(long projectId) {
        projectAccessService.requireActiveMember(projectId);
        long userId = AuthContext.requireUserId();
        ProjectSummaryRow row = projectMapper.findActiveDetail(projectId, userId);
        if (row == null) {
            log.error("Active membership check passed but detail row is missing, projectId={}", projectId);
            throw new RuntimeException("project detail row missing after membership check");
        }
        return toItem(row, true);
    }

    private Map<String, Object> toItem(ProjectSummaryRow row, boolean includeTimestamps) {
        int photoCount = projectPhotoMapper.countByProjectId(row.getId());
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", row.getId());
        item.put("name", row.getName());
        item.put("role", row.getRole());
        item.put("status", row.getStatus());
        item.put("photoCount", photoCount);
        item.put("memberCount", row.getMemberCount());
        item.put("ownerNickName", row.getOwnerNickName());
        item.put("watermarkEnabled", row.getWatermarkEnabled() != null && row.getWatermarkEnabled() == 1);
        item.put("defaultWatermarkPosition", row.getDefaultWatermarkPosition());
        if (includeTimestamps) {
            item.put("createdAt", ProjectResponses.formatTime(row.getCreatedAt()));
            item.put("updatedAt", ProjectResponses.formatTime(row.getUpdatedAt()));
        }
        return item;
    }
}
