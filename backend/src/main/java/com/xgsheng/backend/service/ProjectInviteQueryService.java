package com.xgsheng.backend.service;

import com.xgsheng.backend.auth.AuthContext;
import com.xgsheng.backend.entity.ProjectInvite;
import com.xgsheng.backend.entity.ProjectInviteAcceptance;
import com.xgsheng.backend.entity.UserMessage;
import com.xgsheng.backend.exception.BusinessException;
import com.xgsheng.backend.exception.PendingProductRuleException;
import com.xgsheng.backend.mapper.ProjectInviteAcceptanceMapper;
import com.xgsheng.backend.mapper.ProjectInviteMapper;
import com.xgsheng.backend.mapper.ProjectMemberMapper;
import com.xgsheng.backend.mapper.UserMessageMapper;
import com.xgsheng.backend.project.InviteRules;
import com.xgsheng.backend.project.InviteToken;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ProjectInviteQueryService {

    private static final Duration CODE_CACHE_TTL = Duration.ofMinutes(10);

    private final ProjectInviteMapper projectInviteMapper;
    private final ProjectInviteAcceptanceMapper acceptanceMapper;
    private final ProjectMemberMapper projectMemberMapper;
    private final UserMessageMapper userMessageMapper;
    private final ProjectAccessService projectAccessService;
    private final WechatMiniProgramService wechatMiniProgramService;
    private final Clock clock;
    private final ConcurrentHashMap<String, CachedCode> codeCache = new ConcurrentHashMap<>();

    @Autowired
    public ProjectInviteQueryService(ProjectInviteMapper projectInviteMapper,
                                     ProjectInviteAcceptanceMapper acceptanceMapper,
                                     ProjectMemberMapper projectMemberMapper,
                                     UserMessageMapper userMessageMapper,
                                     ProjectAccessService projectAccessService,
                                     WechatMiniProgramService wechatMiniProgramService) {
        this(projectInviteMapper, acceptanceMapper, projectMemberMapper, userMessageMapper,
                projectAccessService, wechatMiniProgramService, Clock.systemUTC());
    }

    ProjectInviteQueryService(ProjectInviteMapper projectInviteMapper,
                              ProjectInviteAcceptanceMapper acceptanceMapper,
                              ProjectMemberMapper projectMemberMapper,
                              UserMessageMapper userMessageMapper,
                              ProjectAccessService projectAccessService,
                              WechatMiniProgramService wechatMiniProgramService,
                              Clock clock) {
        this.projectInviteMapper = projectInviteMapper;
        this.acceptanceMapper = acceptanceMapper;
        this.projectMemberMapper = projectMemberMapper;
        this.userMessageMapper = userMessageMapper;
        this.projectAccessService = projectAccessService;
        this.wechatMiniProgramService = wechatMiniProgramService;
        this.clock = clock;
    }

    public Map<String, Object> detail(String token) {
        long userId = AuthContext.requireUserId();
        ProjectInvite invite = requireInvite(token);
        rejectArchived(invite);
        LocalDateTime now = now();
        UserMessage message = userMessageMapper.findByUserAndInvite(userId, invite.getId());
        ProjectInviteAcceptance acceptance = acceptanceMapper.findByInviteAndUser(invite.getId(), userId);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("inviteId", invite.getId());
        body.put("projectId", invite.getProjectId());
        body.put("projectName", invite.getProjectName());
        body.put("inviterNickName", invite.getInviterNickName());
        body.put("status", InviteRules.displayStatus(invite.getStatus(), invite.getExpiresAt(), now));
        body.put("expiresAt", ProjectResponses.formatTime(invite.getExpiresAt()));
        body.put("myDecision", acceptance == null ? null : acceptance.getDecision());
        body.put("alreadyMember", activeMember(invite.getProjectId(), userId));
        body.put("messageId", message == null ? null : message.getId());
        return body;
    }

    public byte[] qrcode(String token) {
        ProjectInvite invite = requireInvite(token);
        projectAccessService.requireActiveMember(invite.getProjectId());
        LocalDateTime now = now();
        InviteRules.rejectIfClosed(invite, now);
        rejectArchived(invite);
        String hash = InviteToken.sha256(token);
        Instant current = clock.instant();
        CachedCode cached = codeCache.get(hash);
        if (cached != null && current.isBefore(cached.storedAt.plus(CODE_CACHE_TTL))) {
            return cached.body;
        }
        byte[] body = wechatMiniProgramService.createInviteMiniCode(InviteToken.scene(token));
        codeCache.put(hash, new CachedCode(body, current));
        return body;
    }

    private ProjectInvite requireInvite(String token) {
        if (token == null || token.isBlank()) {
            throw BusinessException.notFound(InviteRules.NOT_FOUND);
        }
        ProjectInvite invite = projectInviteMapper.findDetailByTokenHash(InviteToken.sha256(token));
        if (invite == null || invite.getId() == null || invite.getProjectId() == null) {
            throw BusinessException.notFound(InviteRules.NOT_FOUND);
        }
        return invite;
    }

    private boolean activeMember(long projectId, long userId) {
        var access = projectMemberMapper.findMembership(projectId, userId);
        return access != null && Long.valueOf(userId).equals(access.getUserId()) && "active".equals(access.getStatus());
    }

    private static void rejectArchived(ProjectInvite invite) {
        if ("archived".equals(invite.getProjectStatus())) {
            throw new PendingProductRuleException(InviteRules.ARCHIVED_PREFIX + invite.getProjectId());
        }
        if (!"active".equals(invite.getProjectStatus())) {
            throw new RuntimeException("project status is inconsistent");
        }
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }

    private static final class CachedCode {
        private final byte[] body;
        private final Instant storedAt;

        private CachedCode(byte[] body, Instant storedAt) {
            this.body = body;
            this.storedAt = storedAt;
        }
    }
}
