package com.xgsheng.backend.access;

import com.xgsheng.backend.auth.AuthContext;
import com.xgsheng.backend.entity.User;
import com.xgsheng.backend.exception.BusinessException;
import com.xgsheng.backend.mapper.ProjectMemberMapper;
import com.xgsheng.backend.service.MapperProjectAccessLookup;
import com.xgsheng.backend.service.ProjectAccessService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProjectAccessServiceTest {

    private final MemoryLookup lookup = new MemoryLookup();
    private final ProjectAccessService service = new ProjectAccessService(lookup);

    @AfterEach
    void clearAuth() {
        AuthContext.clear();
    }

    @Test
    void missingLoginStaysUnauthorized() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> service.requireActiveMember(1L));
        assertEquals("unauthorized", ex.getMessage());
        assertFalse(lookup.membershipQueried);
    }

    @Test
    void missingProjectReturns404() {
        login(8L);
        lookup.projectExists = false;
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.requireActiveMember(3L));
        assertEquals(404, ex.getStatus());
        assertEquals("project not found", ex.getMessage());
        assertFalse(lookup.membershipQueried);
    }

    @Test
    void inactiveMembersReturn403() {
        login(8L);
        assertForbidden("left");
        assertForbidden("removed");
        assertForbidden("disabled");
        assertForbidden(null);
        lookup.membership = null;
        BusinessException missing = assertThrows(BusinessException.class,
                () -> service.requireActiveMember(3L));
        assertEquals(403, missing.getStatus());
        assertEquals("not a project member", missing.getMessage());
    }

    @Test
    void activeMemberUsesAuthContextUserOnly() {
        login(8L);
        lookup.membership = membership(8L, 8L, "member", "active");
        ProjectAccessRecord row = service.requireActiveMember(3L);
        assertSame(lookup.membership, row);
        assertEquals(8L, lookup.queriedUserId);
        assertEquals(3L, lookup.queriedProjectId);
    }

    @Test
    void ordinaryMemberCannotCallOwnerOperation() {
        login(8L);
        lookup.membership = membership(99L, 8L, "member", "active");
        BusinessException ex = assertThrows(BusinessException.class, () -> service.requireOwner(3L));
        assertEquals(403, ex.getStatus());
        assertEquals("only owner can perform this action", ex.getMessage());
    }

    @Test
    void ownerRequiresRoleOwnerAndOwnerUserId() {
        login(8L);
        lookup.membership = membership(8L, 8L, "owner", "active");
        assertSame(lookup.membership, service.requireOwner(3L, "only owner can remove member"));

        lookup.membership = membership(9L, 8L, "owner", "active");
        BusinessException mismatch = assertThrows(BusinessException.class,
                () -> service.requireOwner(3L, "only owner can remove member"));
        assertEquals(403, mismatch.getStatus());
        assertEquals("only owner can remove member", mismatch.getMessage());

        lookup.membership = membership(8L, 8L, "owner", "removed");
        BusinessException removed = assertThrows(BusinessException.class, () -> service.requireOwner(3L));
        assertEquals(403, removed.getStatus());
        assertEquals("not a project member", removed.getMessage());
    }

    @Test
    void mapperAdapterDelegatesWithoutOpeningADatabase() {
        ProjectAccessRecord row = membership(8L, 8L, "member", "active");
        ProjectMemberMapper mapper = new ProjectMemberMapper() {
            @Override
            public int countProject(long projectId) {
                return projectId == 3L ? 1 : 0;
            }

            @Override
            public ProjectAccessRecord findMembership(long projectId, long userId) {
                return projectId == 3L && userId == 8L ? row : null;
            }

            @Override
            public int insertOwner(long projectId, long userId, java.time.LocalDateTime joinedAt) {
                return 0;
            }

            @Override
            public java.util.List<com.xgsheng.backend.entity.ProjectMemberRow> lockByUserIds(long projectId, long firstUserId, long secondUserId) {
                return java.util.List.of();
            }

            @Override
            public com.xgsheng.backend.entity.ProjectMemberRow lockByUserId(long projectId, long userId) {
                return null;
            }

            @Override
            public int countActiveOwners(long projectId) {
                return 0;
            }

            @Override
            public int demoteOwner(long id, long userId) {
                return 0;
            }

            @Override
            public int promoteMember(long id, long userId) {
                return 0;
            }

            @Override
            public int markRemoved(long id, long removedByUserId, java.time.LocalDateTime changedAt) {
                return 0;
            }

            @Override
            public int markLeft(long id, java.time.LocalDateTime changedAt) {
                return 0;
            }

            @Override
            public int insertMember(long projectId, long userId, java.time.LocalDateTime joinedAt) {
                return 0;
            }

            @Override
            public int reactivateMember(long id, long userId, java.time.LocalDateTime changedAt) {
                return 0;
            }

            @Override
            public java.util.List<com.xgsheng.backend.entity.ProjectMemberRow> listActive(long projectId) {
                return java.util.List.of();
            }

            @Override
            public int updateNickName(long userId, String nickName) {
                return 0;
            }
        };
        MapperProjectAccessLookup adapter = new MapperProjectAccessLookup(mapper);
        assertTrue(adapter.projectExists(3L));
        assertFalse(adapter.projectExists(4L));
        assertSame(row, adapter.findMembership(3L, 8L));
    }

    private void assertForbidden(String status) {
        lookup.membership = membership(8L, 8L, "member", status);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.requireActiveMember(3L));
        assertEquals(403, ex.getStatus());
        assertEquals("not a project member", ex.getMessage());
    }

    private static void login(long userId) {
        User user = new User();
        user.setId(userId);
        AuthContext.setUser(user);
    }

    private static ProjectAccessRecord membership(long ownerUserId, long userId, String role, String status) {
        ProjectAccessRecord row = new ProjectAccessRecord();
        row.setOwnerUserId(ownerUserId);
        row.setUserId(userId);
        row.setRole(role);
        row.setStatus(status);
        return row;
    }

    private static final class MemoryLookup implements ProjectAccessLookup {
        private boolean projectExists = true;
        private ProjectAccessRecord membership;
        private long queriedProjectId;
        private long queriedUserId;
        private boolean membershipQueried;

        @Override
        public boolean projectExists(long projectId) {
            return projectExists;
        }

        @Override
        public ProjectAccessRecord findMembership(long projectId, long userId) {
            membershipQueried = true;
            queriedProjectId = projectId;
            queriedUserId = userId;
            return membership;
        }
    }
}
