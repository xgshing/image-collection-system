package com.xgsheng.backend.service;

import com.xgsheng.backend.access.ProjectAccessLookup;
import com.xgsheng.backend.access.ProjectAccessRecord;
import com.xgsheng.backend.auth.AuthContext;
import com.xgsheng.backend.entity.Project;
import com.xgsheng.backend.entity.User;
import com.xgsheng.backend.exception.BusinessException;
import com.xgsheng.backend.exception.PendingProductRuleException;
import com.xgsheng.backend.mapper.ProjectMapper;
import com.xgsheng.backend.mapper.ProjectMemberMapper;
import com.xgsheng.backend.project.CreateProjectRequest;
import com.xgsheng.backend.project.UpdateProjectRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProjectCommandServiceTest {

    private final FakeProjectMapper projects = new FakeProjectMapper();
    private final FakeMemberMapper members = new FakeMemberMapper();
    private final MemoryLookup lookup = new MemoryLookup();
    private final ProjectCommandService service = new ProjectCommandService(
            projects,
            members,
            new ProjectAccessService(lookup),
            Clock.fixed(Instant.parse("2026-11-27T05:00:00Z"), ZoneOffset.UTC));

    @AfterEach
    void clearAuth() {
        AuthContext.clear();
    }

    @Test
    void createUsesAuthUserAndWritesOwnerInTheSameCallSequence() {
        login(7L);
        CreateProjectRequest request = new CreateProjectRequest();
        request.setName("  深圳技术大学装修工程  ");

        Map<String, Object> body = service.create(request);

        assertEquals(12L, body.get("id"));
        assertEquals("深圳技术大学装修工程", body.get("name"));
        assertEquals("owner", body.get("role"));
        assertEquals("active", body.get("status"));
        assertEquals(Boolean.TRUE, body.get("watermarkEnabled"));
        assertEquals("bottom_left", body.get("defaultWatermarkPosition"));
        assertEquals("2026-11-27T05:00:00Z", body.get("createdAt"));
        assertFalse(body.containsKey("creatorUserId"));
        assertFalse(body.containsKey("userId"));
        assertEquals(7L, projects.inserted.getOwnerUserId());
        assertEquals(7L, members.userId);
        assertEquals(12L, members.projectId);
        assertEquals(1, projects.inserts);
        assertEquals(1, members.inserts);
    }

    @Test
    void createRejectsBlankNameBeforeAnyInsert() {
        login(7L);
        CreateProjectRequest request = new CreateProjectRequest();
        request.setName(" ");
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> service.create(request));
        assertEquals("project name is required", ex.getMessage());
        assertEquals(0, projects.inserts);
        assertEquals(0, members.inserts);
    }

    @Test
    void missingOwnerMembershipDoesNotReturnSuccess() {
        login(7L);
        members.rows = 0;
        CreateProjectRequest request = new CreateProjectRequest();
        request.setName("项目");
        RuntimeException ex = assertThrows(RuntimeException.class, () -> service.create(request));
        assertEquals("project owner membership row count mismatch", ex.getMessage());
        assertFalse(ex instanceof BusinessException);
    }

    @Test
    void ordinaryMemberCannotUpdate() {
        login(8L);
        lookup.projectExists = true;
        lookup.membership = membership(7L, 8L, "member", "active");
        UpdateProjectRequest request = new UpdateProjectRequest();
        request.setName("新名称");
        BusinessException ex = assertThrows(BusinessException.class, () -> service.updateActive(3L, request));
        assertEquals(403, ex.getStatus());
        assertEquals("only owner can update project", ex.getMessage());
        assertEquals(0, projects.updates);
    }

    @Test
    void archivedUpdateIsNotGivenABusinessResult() {
        login(7L);
        lookup.projectExists = true;
        lookup.membership = membership(7L, 7L, "owner", "active");
        projects.current = project(3L, 7L, "archived");
        UpdateProjectRequest request = new UpdateProjectRequest();
        request.setName("新名称");
        assertThrows(PendingProductRuleException.class, () -> service.updateActive(3L, request));
        assertEquals(0, projects.updates);
    }

    @Test
    void activeOwnerUpdateRequiresOneRow() {
        login(7L);
        lookup.projectExists = true;
        lookup.membership = membership(7L, 7L, "owner", "active");
        projects.current = project(3L, 7L, "active");
        projects.updateRows = 0;
        UpdateProjectRequest request = new UpdateProjectRequest();
        request.setName("新名称");
        RuntimeException ex = assertThrows(RuntimeException.class, () -> service.updateActive(3L, request));
        assertEquals("project update row count mismatch", ex.getMessage());
        assertEquals(1, projects.updates);
    }

    @Test
    void ownerArchiveMarksTheProjectReadOnly() {
        login(7L);
        lookup.projectExists = true;
        lookup.membership = membership(7L, 7L, "owner", "active");
        projects.current = project(3L, 7L, "active");

        service.archive(3L);

        assertEquals("archived", projects.current.getStatus());
        assertEquals(1, projects.archives);
    }

    @Test
    void ordinaryMemberCannotArchive() {
        login(8L);
        lookup.projectExists = true;
        lookup.membership = membership(7L, 8L, "member", "active");
        projects.current = project(3L, 7L, "active");
        BusinessException ex = assertThrows(BusinessException.class, () -> service.archive(3L));
        assertEquals(403, ex.getStatus());
        assertEquals("only owner can delete project", ex.getMessage());
        assertEquals("active", projects.current.getStatus());
        assertEquals(0, projects.archives);
    }

    @Test
    void archivedProjectCannotBeArchivedAgain() {
        login(7L);
        lookup.projectExists = true;
        lookup.membership = membership(7L, 7L, "owner", "active");
        projects.current = project(3L, 7L, "archived");
        BusinessException ex = assertThrows(BusinessException.class, () -> service.archive(3L));
        assertEquals(400, ex.getStatus());
        assertEquals("archived project is read only", ex.getMessage());
        assertEquals(0, projects.archives);
    }

    @Test
    void ownerCanPurgeArchivedProjectWithoutTouchingImages() {
        login(7L);
        lookup.projectExists = true;
        lookup.membership = membership(7L, 7L, "owner", "active");
        projects.current = project(3L, 7L, "archived");

        service.purge(3L);

        assertEquals(null, projects.current);
        assertEquals(5, projects.childDeletes);
        assertEquals(1, projects.purges);
    }

    @Test
    void activeProjectCannotBePurged() {
        login(7L);
        lookup.projectExists = true;
        lookup.membership = membership(7L, 7L, "owner", "active");
        projects.current = project(3L, 7L, "active");
        BusinessException ex = assertThrows(BusinessException.class, () -> service.purge(3L));
        assertEquals(400, ex.getStatus());
        assertEquals("only archived project can be purged", ex.getMessage());
        assertEquals("active", projects.current.getStatus());
        assertEquals(0, projects.childDeletes);
        assertEquals(0, projects.purges);
    }

    @Test
    void ordinaryMemberCannotPurge() {
        login(8L);
        lookup.projectExists = true;
        lookup.membership = membership(7L, 8L, "member", "active");
        projects.current = project(3L, 7L, "archived");
        BusinessException ex = assertThrows(BusinessException.class, () -> service.purge(3L));
        assertEquals(403, ex.getStatus());
        assertEquals("only owner can delete project", ex.getMessage());
        assertEquals(0, projects.purges);
    }

    @Test
    void unauthenticatedCreateStaysUnauthorized() {
        CreateProjectRequest request = new CreateProjectRequest();
        request.setName("项目");
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> service.create(request));
        assertEquals("unauthorized", ex.getMessage());
        assertEquals(0, projects.inserts);
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

    private static Project project(long id, long ownerUserId, String status) {
        Project project = new Project();
        project.setId(id);
        project.setOwnerUserId(ownerUserId);
        project.setName("旧名称");
        project.setStatus(status);
        project.setWatermarkEnabled(1);
        project.setDefaultWatermarkPosition("bottom_left");
        return project;
    }

    private static final class FakeProjectMapper implements ProjectMapper {
        private Project inserted;
        private Project current;
        private int inserts;
        private int updates;
        private int archives;
        private int childDeletes;
        private int purges;
        private int updateRows = 1;

        @Override
        public int insert(Project project) {
            inserts++;
            project.setId(12L);
            inserted = project;
            return 1;
        }

        @Override
        public Project findById(long id) {
            return current;
        }

        @Override
        public Project lockById(long id) {
            return current;
        }

        @Override
        public int updateOwner(long projectId, long previousOwnerUserId, long newOwnerUserId, java.time.LocalDateTime updatedAt) {
            return 0;
        }

        @Override
        public int updateActive(Project project) {
            updates++;
            return updateRows;
        }

        @Override
        public int archiveActive(long projectId, long ownerUserId, LocalDateTime updatedAt) {
            archives++;
            if (current == null || current.getId() != projectId || current.getOwnerUserId() != ownerUserId || !"active".equals(current.getStatus())) {
                return 0;
            }
            current.setStatus("archived");
            current.setUpdatedAt(updatedAt);
            return 1;
        }

        @Override
        public int deleteMessagesForProject(long projectId) {
            return markChildDelete(projectId);
        }

        @Override
        public int deleteAcceptancesForProject(long projectId) {
            return markChildDelete(projectId);
        }

        @Override
        public int deleteInvitesForProject(long projectId) {
            return markChildDelete(projectId);
        }

        @Override
        public int deletePhotosForProject(long projectId) {
            return markChildDelete(projectId);
        }

        @Override
        public int deleteMembersForProject(long projectId) {
            return markChildDelete(projectId);
        }

        @Override
        public int deleteArchived(long projectId, long ownerUserId) {
            purges++;
            if (current == null || current.getId() != projectId || current.getOwnerUserId() != ownerUserId || !"archived".equals(current.getStatus())) {
                return 0;
            }
            current = null;
            return 1;
        }

        private int markChildDelete(long projectId) {
            if (current == null || current.getId() != projectId) return 0;
            childDeletes++;
            return 1;
        }

        @Override
        public List<com.xgsheng.backend.entity.ProjectSummaryRow> listActiveForUser(long userId, int limit, long offset) {
            return List.of();
        }

        @Override
        public long countActiveForUser(long userId) {
            return 0;
        }

        @Override
        public com.xgsheng.backend.entity.ProjectSummaryRow findActiveDetail(long projectId, long userId) {
            return null;
        }
    }

    private static final class FakeMemberMapper implements ProjectMemberMapper {
        private int rows = 1;
        private int inserts;
        private long projectId;
        private long userId;

        @Override
        public int countProject(long projectId) {
            return 0;
        }

        @Override
        public ProjectAccessRecord findMembership(long projectId, long userId) {
            return null;
        }

        @Override
        public int insertOwner(long projectId, long userId, LocalDateTime joinedAt) {
            inserts++;
            this.projectId = projectId;
            this.userId = userId;
            return rows;
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
    }

    private static final class MemoryLookup implements ProjectAccessLookup {
        private boolean projectExists;
        private ProjectAccessRecord membership;

        @Override
        public boolean projectExists(long projectId) {
            return projectExists;
        }

        @Override
        public ProjectAccessRecord findMembership(long projectId, long userId) {
            return membership;
        }
    }
}
