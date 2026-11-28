package com.xgsheng.backend.controller;

import com.xgsheng.backend.project.CreateProjectRequest;
import com.xgsheng.backend.project.TransferOwnerRequest;
import com.xgsheng.backend.project.UpdateProjectRequest;
import com.xgsheng.backend.service.ProjectCommandService;
import com.xgsheng.backend.service.ProjectMemberCommandService;
import com.xgsheng.backend.service.ProjectMemberQueryService;
import com.xgsheng.backend.service.ProjectQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/project")
public class ProjectController {

    private final ProjectCommandService projectCommandService;
    private final ProjectQueryService projectQueryService;
    private final ProjectMemberCommandService projectMemberCommandService;
    private final ProjectMemberQueryService projectMemberQueryService;

    public ProjectController(ProjectCommandService projectCommandService,
                             ProjectQueryService projectQueryService,
                             ProjectMemberCommandService projectMemberCommandService,
                             ProjectMemberQueryService projectMemberQueryService) {
        this.projectCommandService = projectCommandService;
        this.projectQueryService = projectQueryService;
        this.projectMemberCommandService = projectMemberCommandService;
        this.projectMemberQueryService = projectMemberQueryService;
    }

    @PostMapping
    public Map<String, Object> create(@RequestBody CreateProjectRequest request) {
        return projectCommandService.create(request);
    }

    @GetMapping
    public Map<String, Object> list(@RequestParam(value = "page", required = false) Integer page,
                                    @RequestParam(value = "pageSize", required = false) Integer pageSize) {
        return projectQueryService.list(page, pageSize);
    }

    @GetMapping("/{projectId:\\d+}")
    public Map<String, Object> detail(@PathVariable("projectId") long projectId) {
        return projectQueryService.detail(projectId);
    }

    @PatchMapping("/{projectId:\\d+}")
    public Map<String, Object> update(@PathVariable("projectId") long projectId,
                                      @RequestBody UpdateProjectRequest request) {
        projectCommandService.updateActive(projectId, request);
        return projectQueryService.detail(projectId);
    }

    @PostMapping("/{projectId:\\d+}/archive")
    public Map<String, Object> archive(@PathVariable("projectId") long projectId) {
        projectCommandService.archive(projectId);
        return projectQueryService.detail(projectId);
    }

    @PostMapping("/{projectId:\\d+}/purge")
    public Map<String, Object> purge(@PathVariable("projectId") long projectId) {
        projectCommandService.purge(projectId);
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("deleted", true);
        return body;
    }

    @GetMapping("/{projectId:\\d+}/members")
    public Map<String, Object> members(@PathVariable("projectId") long projectId) {
        return projectMemberQueryService.listMembers(projectId);
    }

    @PostMapping("/{projectId:\\d+}/owner/transfer")
    public Map<String, Object> transfer(@PathVariable("projectId") long projectId,
                                        @RequestBody TransferOwnerRequest request) {
        return projectMemberCommandService.transfer(projectId, request);
    }

    @PatchMapping("/{projectId:\\d+}/members/{userId:\\d+}/name")
    public Map<String, Object> rename(@PathVariable("projectId") long projectId,
                                      @PathVariable("userId") long targetUserId,
                                      @RequestBody com.xgsheng.backend.project.RenameMemberRequest request) {
        return projectMemberCommandService.rename(projectId, targetUserId, request == null ? null : request.getNickName());
    }

    @PostMapping("/{projectId:\\d+}/members/{userId:\\d+}/remove")
    public Map<String, Object> remove(@PathVariable("projectId") long projectId,
                                      @PathVariable("userId") long targetUserId) {
        return projectMemberCommandService.remove(projectId, targetUserId);
    }

    @PostMapping("/{projectId:\\d+}/members/leave")
    public Map<String, Object> leave(@PathVariable("projectId") long projectId) {
        return projectMemberCommandService.leave(projectId);
    }
}
