package com.xgsheng.backend.controller;

import com.xgsheng.backend.service.ProjectInviteCommandService;
import com.xgsheng.backend.service.ProjectInviteQueryService;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class ProjectInviteController {

    private final ProjectInviteCommandService projectInviteCommandService;
    private final ProjectInviteQueryService projectInviteQueryService;

    public ProjectInviteController(ProjectInviteCommandService projectInviteCommandService,
                                   ProjectInviteQueryService projectInviteQueryService) {
        this.projectInviteCommandService = projectInviteCommandService;
        this.projectInviteQueryService = projectInviteQueryService;
    }

    @PostMapping("/api/project/{projectId:\\d+}/invites")
    public Map<String, Object> create(@PathVariable("projectId") long projectId) {
        return projectInviteCommandService.create(projectId);
    }

    @GetMapping("/api/project/invites/{token}")
    public Map<String, Object> detail(@PathVariable("token") String token) {
        return projectInviteQueryService.detail(token);
    }

    @PostMapping("/api/project/invites/{token}/accept")
    public Map<String, Object> accept(@PathVariable("token") String token) {
        return projectInviteCommandService.accept(token);
    }

    @PostMapping("/api/project/invites/{token}/reject")
    public Map<String, Object> reject(@PathVariable("token") String token) {
        return projectInviteCommandService.reject(token);
    }

    @PostMapping("/api/project/invites/{token}/message")
    public Map<String, Object> message(@PathVariable("token") String token) {
        return projectInviteCommandService.createMessage(token);
    }

    @PostMapping("/api/project/invites/{token}/qrcode")
    public ResponseEntity<byte[]> qrcode(@PathVariable("token") String token) {
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .cacheControl(CacheControl.noStore())
                .body(projectInviteQueryService.qrcode(token));
    }
}
