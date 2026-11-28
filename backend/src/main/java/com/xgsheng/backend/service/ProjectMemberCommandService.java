package com.xgsheng.backend.service;

import com.xgsheng.backend.access.ProjectAccessRecord;
import com.xgsheng.backend.auth.AuthContext;
import com.xgsheng.backend.entity.Project;
import com.xgsheng.backend.entity.ProjectMemberRow;
import com.xgsheng.backend.exception.BusinessException;
import com.xgsheng.backend.mapper.ProjectMapper;
import com.xgsheng.backend.mapper.ProjectMemberMapper;
import com.xgsheng.backend.mapper.UserMessageMapper;
import com.xgsheng.backend.project.TransferOwnerRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ProjectMemberCommandService {

    private static final Logger log = LoggerFactory.getLogger(ProjectMemberCommandService.class);

    private final ProjectMapper projectMapper;
    private final ProjectMemberMapper projectMemberMapper;
    private final UserMessageMapper userMessageMapper;
    private final Clock clock;

    @Autowired
    public ProjectMemberCommandService(ProjectMapper projectMapper,
                                       ProjectMemberMapper projectMemberMapper,
                                       UserMessageMapper userMessageMapper) {
        this(projectMapper, projectMemberMapper, userMessageMapper, Clock.systemUTC());
    }

    ProjectMemberCommandService(ProjectMapper projectMapper,
                                ProjectMemberMapper projectMemberMapper,
                                UserMessageMapper userMessageMapper,
                                Clock clock) {
        this.projectMapper = projectMapper;
        this.projectMemberMapper = projectMemberMapper;
        this.userMessageMapper = userMessageMapper;
        this.clock = clock;
    }

    @Transactional
    public Map<String, Object> transfer(long projectId, TransferOwnerRequest request) {
        long userId = AuthContext.requireUserId();
        if (request == null || request.getTargetUserId() == null) {
            throw new IllegalArgumentException("targetUserId is required");
        }
        long targetUserId = request.getTargetUserId();
        Project project = projectMapper.lockById(projectId);
        List<ProjectMemberRow> locked = projectMemberMapper.lockByUserIds(projectId, userId, targetUserId);
        if (!isActiveOwner(project, findUser(locked, userId), userId)) {
            throw BusinessException.badRequest("only owner can transfer");
        }
        if (targetUserId == userId) {
            throw BusinessException.badRequest("target is already owner");
        }
        ProjectMemberRow target = findUser(locked, targetUserId);
        if (!isActiveOrdinaryMember(target)) {
            throw BusinessException.badRequest("target is not a project member");
        }
        int activeOwners = projectMemberMapper.countActiveOwners(projectId);
        if (activeOwners != 1) {
            rejectInconsistent(projectId, "active-owner-count");
        }
        LocalDateTime now = now();
        if (projectMemberMapper.demoteOwner(findUser(locked, userId).getId(), userId) != 1) {
            rejectInconsistent(projectId, "demote");
        }
        if (projectMemberMapper.promoteMember(target.getId(), targetUserId) != 1) {
            rejectInconsistent(projectId, "promote");
        }
        if (projectMapper.updateOwner(projectId, userId, targetUserId, now) != 1) {
            rejectInconsistent(projectId, "owner-user-id");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("projectId", projectId);
        body.put("ownerUserId", targetUserId);
        body.put("previousOwnerUserId", userId);
        return body;
    }

    @Transactional
    public Map<String, Object> remove(long projectId, long targetUserId) {
        long operatorId = AuthContext.requireUserId();
        Project project = projectMapper.lockById(projectId);
        ProjectAccessRecord operator = projectMemberMapper.findMembership(projectId, operatorId);
        if (!isActiveOwner(project, operator, operatorId)) {
            throw BusinessException.forbidden("only owner can remove member");
        }
        ProjectMemberRow target = projectMemberMapper.lockByUserId(projectId, targetUserId);
        if (target == null) {
            throw BusinessException.notFound("member not found");
        }
        if (isInactive(target)) {
            return removalResult(projectId, targetUserId, target.getStatus(), true);
        }
        if ("active".equals(target.getStatus())
                && ("owner".equals(target.getRole()) || target.getUserId() == operatorId)) {
            throw BusinessException.badRequest("cannot remove owner");
        }
        if (!isActiveOrdinaryMember(target)) {
            rejectInconsistent(projectId, "remove-target");
        }
        LocalDateTime now = now();
        if (projectMemberMapper.markRemoved(target.getId(), operatorId, now) != 1) {
            rejectInconsistent(projectId, "mark-removed");
        }
        String projectName = project.getName() == null ? "" : project.getName();
        if (userMessageMapper.insertRemovalNotice(targetUserId, projectId, projectName, projectName, now) != 1) {
            rejectInconsistent(projectId, "removal-notice");
        }
        return removalResult(projectId, targetUserId, "removed", false);
    }

    @Transactional
    public Map<String, Object> leave(long projectId) {
        long userId = AuthContext.requireUserId();
        Project project = projectMapper.lockById(projectId);
        ProjectMemberRow self = projectMemberMapper.lockByUserId(projectId, userId);
        if (self == null || project == null) {
            throw BusinessException.notFound("member not found");
        }
        if ("owner".equals(self.getRole()) && "active".equals(self.getStatus())) {
            throw BusinessException.badRequest("owner must transfer before leaving");
        }
        if (userId == (project.getOwnerUserId() == null ? -1L : project.getOwnerUserId())
                && !"owner".equals(self.getRole())) {
            rejectInconsistent(projectId, "leave-owner-mismatch");
        }
        if (isInactive(self)) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("projectId", projectId);
            body.put("status", self.getStatus());
            body.put("alreadyInactive", true);
            return body;
        }
        if (!isActiveOrdinaryMember(self)) {
            rejectInconsistent(projectId, "leave-target");
        }
        if (projectMemberMapper.markLeft(self.getId(), now()) != 1) {
            rejectInconsistent(projectId, "mark-left");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("projectId", projectId);
        body.put("status", "left");
        body.put("alreadyInactive", false);
        return body;
    }

    @Transactional
    public Map<String, Object> rename(long projectId, long targetUserId, String nickName) {
        long userId = AuthContext.requireUserId();
        String normalized = normalizeMemberName(nickName);
        Project project = projectMapper.lockById(projectId);
        if (project == null) {
            throw BusinessException.notFound("project not found");
        }
        ProjectMemberRow actor = projectMemberMapper.lockByUserId(projectId, userId);
        if (actor == null || actor.getUserId() == null || actor.getUserId() != userId || !"active".equals(actor.getStatus())) {
            throw BusinessException.forbidden("not a project member");
        }
        if (targetUserId != userId && !isActiveOwner(project, actor, userId)) {
            throw BusinessException.forbidden("only owner can rename other members");
        }
        ProjectMemberRow target = targetUserId == userId ? actor : projectMemberMapper.lockByUserId(projectId, targetUserId);
        if (target == null || target.getUserId() == null || target.getUserId() != targetUserId || !"active".equals(target.getStatus())) {
            throw BusinessException.badRequest("target is not a project member");
        }
        if (projectMemberMapper.updateNickName(targetUserId, normalized) != 1) {
            log.error("Member nickname update affected no row, projectId={}, userId={}", projectId, targetUserId);
            throw new RuntimeException("member nickname update row count mismatch");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("userId", targetUserId);
        body.put("nickName", normalized);
        return body;
    }

    private static String normalizeMemberName(String nickName) {
        String value = nickName == null ? "" : nickName.trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException("member name is required");
        }
        if (value.codePointCount(0, value.length()) > 32) {
            throw new IllegalArgumentException("member name is too long");
        }
        if (value.codePoints().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("member name is invalid");
        }
        return value;
    }

    private static boolean isActiveOwner(Project project, ProjectAccessRecord membership, long userId) {
        return isActiveOwner(project, membership == null ? null : membership.getRole(),
                membership == null ? null : membership.getStatus(),
                membership == null ? null : membership.getUserId(), userId);
    }

    private static boolean isActiveOwner(Project project, ProjectMemberRow membership, long userId) {
        return isActiveOwner(project, membership == null ? null : membership.getRole(),
                membership == null ? null : membership.getStatus(),
                membership == null ? null : membership.getUserId(), userId);
    }

    private static boolean isActiveOwner(Project project, String role, String status, Long membershipUserId, long userId) {
        return project != null
                && project.getOwnerUserId() != null
                && project.getOwnerUserId() == userId
                && membershipUserId != null
                && membershipUserId == userId
                && "owner".equals(role)
                && "active".equals(status);
    }

    private static boolean isActiveOrdinaryMember(ProjectMemberRow row) {
        return row != null && "member".equals(row.getRole()) && "active".equals(row.getStatus());
    }

    private static boolean isInactive(ProjectMemberRow row) {
        return "left".equals(row.getStatus()) || "removed".equals(row.getStatus());
    }

    private static ProjectMemberRow findUser(List<ProjectMemberRow> rows, long userId) {
        if (rows == null) {
            return null;
        }
        for (ProjectMemberRow row : rows) {
            if (row.getUserId() != null && row.getUserId() == userId) {
                return row;
            }
        }
        return null;
    }

    private static Map<String, Object> removalResult(long projectId, long userId, String status, boolean alreadyInactive) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("projectId", projectId);
        body.put("userId", userId);
        body.put("status", status);
        body.put("alreadyInactive", alreadyInactive);
        return body;
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }

    private static void rejectInconsistent(long projectId, String checkpoint) {
        log.error("Member write rejected because data is inconsistent, projectId={}, checkpoint={}", projectId, checkpoint);
        throw new RuntimeException("project member data is inconsistent");
    }
}
