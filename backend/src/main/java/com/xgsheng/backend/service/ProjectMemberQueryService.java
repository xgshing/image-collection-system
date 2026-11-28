package com.xgsheng.backend.service;

import com.xgsheng.backend.entity.ProjectMemberRow;
import com.xgsheng.backend.mapper.ProjectMemberMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ProjectMemberQueryService {

    private final ProjectAccessService projectAccessService;
    private final ProjectMemberMapper projectMemberMapper;
    private final StorageService storageService;

    public ProjectMemberQueryService(ProjectAccessService projectAccessService,
                                     ProjectMemberMapper projectMemberMapper,
                                     StorageService storageService) {
        this.projectAccessService = projectAccessService;
        this.projectMemberMapper = projectMemberMapper;
        this.storageService = storageService;
    }

    public Map<String, Object> listMembers(long projectId) {
        projectAccessService.requireActiveMember(projectId);
        List<Map<String, Object>> items = new ArrayList<>();
        for (ProjectMemberRow row : projectMemberMapper.listActive(projectId)) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("userId", row.getUserId());
            item.put("nickName", row.getNickName());
            item.put("avatarUrl", storageService.readableUrl(row.getAvatarUrl()));
            item.put("role", row.getRole());
            item.put("status", row.getStatus());
            item.put("joinedAt", ProjectResponses.formatTime(row.getJoinedAt()));
            items.add(item);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("items", items);
        return body;
    }
}
