package com.xgsheng.backend.service;

import com.xgsheng.backend.auth.AuthContext;
import com.xgsheng.backend.entity.Project;
import com.xgsheng.backend.entity.ProjectInvite;
import com.xgsheng.backend.entity.ProjectInviteAcceptance;
import com.xgsheng.backend.entity.ProjectMemberRow;
import com.xgsheng.backend.entity.UserMessage;
import com.xgsheng.backend.exception.BusinessException;
import com.xgsheng.backend.exception.PendingProductRuleException;
import com.xgsheng.backend.mapper.ProjectInviteAcceptanceMapper;
import com.xgsheng.backend.mapper.ProjectInviteMapper;
import com.xgsheng.backend.mapper.ProjectMapper;
import com.xgsheng.backend.mapper.ProjectMemberMapper;
import com.xgsheng.backend.mapper.UserMessageMapper;
import com.xgsheng.backend.project.InviteRules;
import com.xgsheng.backend.project.InviteToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class ProjectInviteCommandService {

    private static final Logger log = LoggerFactory.getLogger(ProjectInviteCommandService.class);
    private static final int TOKEN_ATTEMPTS = 3;

    private final ProjectMapper projectMapper;
    private final ProjectMemberMapper projectMemberMapper;
    private final ProjectInviteMapper projectInviteMapper;
    private final ProjectInviteAcceptanceMapper acceptanceMapper;
    private final UserMessageMapper userMessageMapper;
    private final ProjectAccessService projectAccessService;
    private final Clock clock;

    @Autowired
    public ProjectInviteCommandService(ProjectMapper projectMapper,
                                       ProjectMemberMapper projectMemberMapper,
                                       ProjectInviteMapper projectInviteMapper,
                                       ProjectInviteAcceptanceMapper acceptanceMapper,
                                       UserMessageMapper userMessageMapper,
                                       ProjectAccessService projectAccessService) {
        this(projectMapper, projectMemberMapper, projectInviteMapper, acceptanceMapper,
                userMessageMapper, projectAccessService, Clock.systemUTC());
    }

    ProjectInviteCommandService(ProjectMapper projectMapper,
                                ProjectMemberMapper projectMemberMapper,
                                ProjectInviteMapper projectInviteMapper,
                                ProjectInviteAcceptanceMapper acceptanceMapper,
                                UserMessageMapper userMessageMapper,
                                ProjectAccessService projectAccessService,
                                Clock clock) {
        this.projectMapper = projectMapper;
        this.projectMemberMapper = projectMemberMapper;
        this.projectInviteMapper = projectInviteMapper;
        this.acceptanceMapper = acceptanceMapper;
        this.userMessageMapper = userMessageMapper;
        this.projectAccessService = projectAccessService;
        this.clock = clock;
    }

    @Transactional
    public Map<String, Object> create(long projectId) {
        long userId = AuthContext.requireUserId();
        projectAccessService.requireActiveMember(projectId);
        Project project = lockedProject(projectId);
        rejectArchived(project);
        ProjectMemberRow member = projectMemberMapper.lockByUserId(projectId, userId);
        if (!isActiveMember(member, userId)) {
            throw BusinessException.forbidden(ProjectAccessService.NOT_A_MEMBER);
        }
        LocalDateTime now = now();
        CreatedInvite created = insertInvite(projectId, userId, now);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("inviteId", created.invite.getId());
        body.put("token", created.plainToken);
        body.put("scene", InviteToken.scene(created.plainToken));
        body.put("expiresAt", ProjectResponses.formatTime(created.invite.getExpiresAt()));
        body.put("status", "active");
        return body;
    }

    @Transactional
    public Map<String, Object> accept(String token) {
        long userId = AuthContext.requireUserId();
        String hash = hashOf(token);
        ProjectInvite preview = requirePreview(hash);
        Project project = lockedProject(preview.getProjectId());
        LocalDateTime now = now();
        ProjectInvite invite = lockedInvite(hash);
        InviteRules.rejectIfClosed(invite, now);
        rejectArchived(project);
        ensureAccepted(invite.getId(), userId, now);
        MemberOutcome membership = ensureActiveMember(project.getId(), userId, now);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("projectId", project.getId());
        body.put("role", membership.role);
        body.put("decision", "accepted");
        body.put("alreadyMember", membership.alreadyActive);
        return body;
    }

    @Transactional
    public Map<String, Object> reject(String token) {
        long userId = AuthContext.requireUserId();
        String hash = hashOf(token);
        ProjectInvite preview = requirePreview(hash);
        Project project = lockedProject(preview.getProjectId());
        LocalDateTime now = now();
        ProjectInvite invite = lockedInvite(hash);
        InviteRules.rejectIfClosed(invite, now);
        rejectArchived(project);
        ProjectInviteAcceptance existing = acceptanceMapper.findByInviteAndUser(invite.getId(), userId);
        if (existing == null) {
            try {
                int rows = acceptanceMapper.insert(invite.getId(), userId, "rejected", now);
                if (rows != 1) {
                    log.error("Invite rejection insert affected {} rows, projectId={}, inviteId={}",
                            rows, project.getId(), invite.getId());
                    throw new RuntimeException("invite acceptance insert row count mismatch");
                }
            } catch (DuplicateKeyException ex) {
                existing = acceptanceMapper.findByInviteAndUser(invite.getId(), userId);
                if (existing == null) {
                    log.error("Invite rejection missing after unique conflict, projectId={}, inviteId={}",
                            project.getId(), invite.getId());
                    throw new RuntimeException("invite acceptance missing after unique conflict");
                }
            }
        }
        if (existing != null && "accepted".equals(existing.getDecision())) {
            throw BusinessException.badRequest(InviteRules.ALREADY_ACCEPTED);
        }
        if (existing != null && !"rejected".equals(existing.getDecision())) {
            log.error("Invite decision is inconsistent, projectId={}, inviteId={}", project.getId(), invite.getId());
            throw new RuntimeException("invite acceptance data is inconsistent");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("decision", "rejected");
        return body;
    }

    @Transactional
    public Map<String, Object> createMessage(String token) {
        long userId = AuthContext.requireUserId();
        String hash = hashOf(token);
        ProjectInvite preview = requirePreview(hash);
        Project project = lockedProject(preview.getProjectId());
        rejectArchived(project);
        ProjectInvite invite = lockedInvite(hash);
        LocalDateTime now = now();
        UserMessage existing = userMessageMapper.findByUserAndInvite(userId, invite.getId());
        boolean created = false;
        if (existing == null) {
            UserMessage message = new UserMessage();
            message.setUserId(userId);
            message.setTitle(InviteRules.inviteTitle(preview.getInviterNickName()));
            message.setBody(project.getName() == null ? "" : project.getName());
            message.setProjectId(project.getId());
            message.setInviteId(invite.getId());
            message.setCreatedAt(now);
            try {
                int rows = userMessageMapper.insertInvite(message);
                if (rows != 1 || message.getId() == null) {
                    log.error("Invite message insert affected {} rows, projectId={}, inviteId={}",
                            rows, project.getId(), invite.getId());
                    throw new RuntimeException("invite message insert row count mismatch");
                }
                existing = message;
                created = true;
            } catch (DuplicateKeyException ex) {
                existing = userMessageMapper.findByUserAndInvite(userId, invite.getId());
                if (existing == null || existing.getId() == null) {
                    log.error("Invite message missing after unique conflict, projectId={}, inviteId={}",
                            project.getId(), invite.getId());
                    throw new RuntimeException("invite message missing after unique conflict");
                }
            }
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("messageId", existing.getId());
        body.put("read", existing.getReadAt() != null);
        body.put("created", created);
        body.put("alreadyMember", activeMember(project.getId(), userId));
        body.put("myDecision", decisionOf(invite.getId(), userId));
        body.put("status", InviteRules.displayStatus(invite.getStatus(), invite.getExpiresAt(), now));
        return body;
    }

    private boolean activeMember(long projectId, long userId) {
        var access = projectMemberMapper.findMembership(projectId, userId);
        return access != null && Long.valueOf(userId).equals(access.getUserId()) && "active".equals(access.getStatus());
    }

    private CreatedInvite insertInvite(long projectId, long userId, LocalDateTime now) {
        for (int attempt = 0; attempt < TOKEN_ATTEMPTS; attempt++) {
            String plain = InviteToken.generate();
            ProjectInvite invite = new ProjectInvite();
            invite.setProjectId(projectId);
            invite.setInviterUserId(userId);
            invite.setTokenHash(InviteToken.sha256(plain));
            invite.setStatus("active");
            invite.setExpiresAt(now.plusDays(7));
            invite.setCreatedAt(now);
            try {
                int rows = projectInviteMapper.insert(invite);
                if (rows != 1 || invite.getId() == null) {
                    log.error("Invite insert affected {} rows, projectId={}", rows, projectId);
                    throw new RuntimeException("invite insert row count mismatch");
                }
                return new CreatedInvite(invite, plain);
            } catch (DuplicateKeyException ex) {
                log.warn("Invite token hash collision, projectId={}", projectId);
            }
        }
        log.error("Invite token hash still collides, projectId={}", projectId);
        throw new RuntimeException("invite token hash collision");
    }

    private void ensureAccepted(long inviteId, long userId, LocalDateTime now) {
        ProjectInviteAcceptance existing = acceptanceMapper.findByInviteAndUser(inviteId, userId);
        if (existing == null) {
            try {
                int rows = acceptanceMapper.insert(inviteId, userId, "accepted", now);
                if (rows != 1) {
                    log.error("Invite acceptance insert affected {} rows, inviteId={}", rows, inviteId);
                    throw new RuntimeException("invite acceptance insert row count mismatch");
                }
                return;
            } catch (DuplicateKeyException ex) {
                existing = acceptanceMapper.findByInviteAndUser(inviteId, userId);
                if (existing == null) {
                    log.error("Invite acceptance missing after unique conflict, inviteId={}", inviteId);
                    throw new RuntimeException("invite acceptance missing after unique conflict");
                }
            }
        }
        if ("accepted".equals(existing.getDecision())) {
            return;
        }
        if (!"rejected".equals(existing.getDecision())) {
            log.error("Invite decision is inconsistent, inviteId={}", inviteId);
            throw new RuntimeException("invite acceptance data is inconsistent");
        }
        int rows = acceptanceMapper.updateRejectedToAccepted(existing.getId(), inviteId, userId, now);
        if (rows == 1) {
            return;
        }
        ProjectInviteAcceptance again = acceptanceMapper.findByInviteAndUser(inviteId, userId);
        if (again != null && "accepted".equals(again.getDecision())) {
            return;
        }
        log.error("Rejected invite was not updated to accepted, inviteId={}", inviteId);
        throw new RuntimeException("invite acceptance update row count mismatch");
    }

    private MemberOutcome ensureActiveMember(long projectId, long userId, LocalDateTime now) {
        ProjectMemberRow member = projectMemberMapper.lockByUserId(projectId, userId);
        if (member == null) {
            try {
                int rows = projectMemberMapper.insertMember(projectId, userId, now);
                if (rows != 1) {
                    log.error("Member insert affected {} rows, projectId={}", rows, projectId);
                    throw new RuntimeException("project member insert row count mismatch");
                }
                return new MemberOutcome("member", false);
            } catch (DuplicateKeyException ex) {
                member = projectMemberMapper.lockByUserId(projectId, userId);
                if (member == null) {
                    log.error("Member missing after unique conflict, projectId={}", projectId);
                    throw new RuntimeException("project member missing after unique conflict");
                }
            }
        }
        if ("active".equals(member.getStatus())) {
            if (member.getRole() == null) {
                log.error("Active member role is missing, projectId={}", projectId);
                throw new RuntimeException("project member data is inconsistent");
            }
            return new MemberOutcome(member.getRole(), true);
        }
        if (!"left".equals(member.getStatus()) && !"removed".equals(member.getStatus())) {
            log.error("Member status is inconsistent, projectId={}", projectId);
            throw new RuntimeException("project member data is inconsistent");
        }
        if (!"member".equals(member.getRole())) {
            log.error("Inactive member role is not member, projectId={}", projectId);
            throw new RuntimeException("project member data is inconsistent");
        }
        int rows = projectMemberMapper.reactivateMember(member.getId(), userId, now);
        if (rows != 1) {
            log.error("Member reactivate affected {} rows, projectId={}", rows, projectId);
            throw new RuntimeException("project member data is inconsistent");
        }
        return new MemberOutcome("member", false);
    }

    private ProjectInvite requirePreview(String hash) {
        ProjectInvite preview = projectInviteMapper.findDetailByTokenHash(hash);
        if (preview == null || preview.getProjectId() == null) {
            throw BusinessException.notFound(InviteRules.NOT_FOUND);
        }
        return preview;
    }

    private Project lockedProject(long projectId) {
        Project project = projectMapper.lockById(projectId);
        if (project == null) {
            log.error("Project row missing, projectId={}", projectId);
            throw new RuntimeException("project row missing");
        }
        return project;
    }

    private ProjectInvite lockedInvite(String hash) {
        ProjectInvite invite = projectInviteMapper.lockByTokenHash(hash);
        if (invite == null || invite.getId() == null) {
            throw BusinessException.notFound(InviteRules.NOT_FOUND);
        }
        return invite;
    }

    private static void rejectArchived(Project project) {
        if ("archived".equals(project.getStatus())) {
            throw new PendingProductRuleException(InviteRules.ARCHIVED_PREFIX + project.getId());
        }
        if (!"active".equals(project.getStatus())) {
            throw new RuntimeException("project status is inconsistent");
        }
    }

    private String decisionOf(long inviteId, long userId) {
        ProjectInviteAcceptance acceptance = acceptanceMapper.findByInviteAndUser(inviteId, userId);
        return acceptance == null ? null : acceptance.getDecision();
    }

    private static boolean isActiveMember(ProjectMemberRow member, long userId) {
        return member != null && Long.valueOf(userId).equals(member.getUserId()) && "active".equals(member.getStatus());
    }

    private static String hashOf(String token) {
        if (token == null || token.isBlank()) {
            throw BusinessException.notFound(InviteRules.NOT_FOUND);
        }
        return InviteToken.sha256(token);
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }

    private static final class MemberOutcome {
        private final String role;
        private final boolean alreadyActive;

        private MemberOutcome(String role, boolean alreadyActive) {
            this.role = role;
            this.alreadyActive = alreadyActive;
        }
    }

    private static final class CreatedInvite {
        private final ProjectInvite invite;
        private final String plainToken;

        private CreatedInvite(ProjectInvite invite, String plainToken) {
            this.invite = invite;
            this.plainToken = plainToken;
        }
    }
}
