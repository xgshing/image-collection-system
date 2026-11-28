package com.xgsheng.backend.service;

import com.xgsheng.backend.access.ProjectAccessLookup;
import com.xgsheng.backend.access.ProjectAccessRecord;
import com.xgsheng.backend.access.ProjectMemberStatus;
import com.xgsheng.backend.access.ProjectRole;
import com.xgsheng.backend.auth.AuthContext;
import com.xgsheng.backend.exception.BusinessException;
import org.springframework.stereotype.Service;

/**
 * 项目权限的唯一入口。当前用户只来自 AuthContext.requireUserId()，不接受客户端传入的 userId。
 */
@Service
public class ProjectAccessService {

    static final String NOT_A_MEMBER = "not a project member";
    static final String PROJECT_NOT_FOUND = "project not found";
    static final String OWNER_REQUIRED = "only owner can perform this action";

    private final ProjectAccessLookup lookup;

    public ProjectAccessService(ProjectAccessLookup lookup) {
        this.lookup = lookup;
    }

    public ProjectAccessRecord requireActiveMember(long projectId) {
        long userId = AuthContext.requireUserId();
        if (!lookup.projectExists(projectId)) {
            throw BusinessException.notFound(PROJECT_NOT_FOUND);
        }
        ProjectAccessRecord membership = lookup.findMembership(projectId, userId);
        if (!isActiveMember(membership, userId)) {
            throw BusinessException.forbidden(NOT_A_MEMBER);
        }
        return membership;
    }

    public ProjectAccessRecord requireOwner(long projectId) {
        return requireOwner(projectId, OWNER_REQUIRED);
    }

    public ProjectAccessRecord requireOwner(long projectId, String message) {
        ProjectAccessRecord membership = requireActiveMember(projectId);
        if (!isActiveOwner(membership)) {
            throw BusinessException.forbidden(message);
        }
        return membership;
    }

    private static boolean isActiveMember(ProjectAccessRecord membership, long userId) {
        if (membership == null || membership.getUserId() == null || membership.getUserId() != userId) {
            return false;
        }
        return ProjectMemberStatus.fromCode(membership.getStatus()) == ProjectMemberStatus.ACTIVE;
    }

    private static boolean isActiveOwner(ProjectAccessRecord membership) {
        if (ProjectRole.fromCode(membership.getRole()) != ProjectRole.OWNER) {
            return false;
        }
        Long ownerUserId = membership.getOwnerUserId();
        return ownerUserId != null && ownerUserId.equals(membership.getUserId());
    }
}
