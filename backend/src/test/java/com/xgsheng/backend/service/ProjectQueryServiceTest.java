package com.xgsheng.backend.service;

import com.xgsheng.backend.access.ProjectAccessLookup;
import com.xgsheng.backend.access.ProjectAccessRecord;
import com.xgsheng.backend.auth.AuthContext;
import com.xgsheng.backend.entity.Project;
import com.xgsheng.backend.entity.ProjectPhoto;
import com.xgsheng.backend.entity.ProjectSummaryRow;
import com.xgsheng.backend.entity.User;
import com.xgsheng.backend.exception.BusinessException;
import com.xgsheng.backend.exception.PendingProductRuleException;
import com.xgsheng.backend.mapper.ProjectMapper;
import com.xgsheng.backend.mapper.ProjectPhotoMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProjectQueryServiceTest {

    private final FakeProjectMapper projects = new FakeProjectMapper();
    private final FakePhotoMapper photos = new FakePhotoMapper();
    private final MemoryLookup lookup = new MemoryLookup();
    private final ProjectQueryService service = new ProjectQueryService(
            new ProjectAccessService(lookup), projects, photos);

    @AfterEach
    void clearAuth() {
        AuthContext.clear();
    }

    @Test
    void listUsesTheAuthenticatedUserAndTheRealPhotoCount() {
        login(8L);
        projects.rows = List.of(row(3L, "member", 2L));
        photos.count = 4;

        Map<String, Object> body = service.list(null, null);

        assertEquals(8L, projects.queriedUserId);
        assertEquals(1, body.get("page"));
        assertEquals(20, body.get("pageSize"));
        List<?> items = (List<?>) body.get("items");
        Map<?, ?> item = (Map<?, ?>) items.get(0);
        assertEquals(4, item.get("photoCount"));
        assertEquals(2L, item.get("memberCount"));
        assertEquals(1, photos.calls);
    }

    @Test
    void photoCountFailureIsNotTurnedIntoSuccess() {
        login(8L);
        projects.rows = List.of(row(3L, "owner", 1L));
        photos.failure = new RuntimeException("Table 'image_collection.project_photo' doesn't exist");

        RuntimeException ex = assertThrows(RuntimeException.class, () -> service.list(1, 20));
        assertTrue(ex.getMessage().contains("project_photo"));
        assertFalse(ex instanceof BusinessException);
    }

    @Test
    void detailDoesNotReadPhotosWhenTheCallerIsNotAnActiveMember() {
        login(8L);
        lookup.projectExists = true;
        lookup.membership = membership(7L, 8L, "member", "removed");

        BusinessException ex = assertThrows(BusinessException.class, () -> service.detail(3L));
        assertEquals(403, ex.getStatus());
        assertEquals("not a project member", ex.getMessage());
        assertEquals(0, photos.calls);
        assertEquals(0, projects.detailCalls);
    }

    @Test
    void missingProjectDoesNotReadPhotos() {
        login(8L);
        lookup.projectExists = false;
        BusinessException ex = assertThrows(BusinessException.class, () -> service.detail(99L));
        assertEquals(404, ex.getStatus());
        assertEquals(0, photos.calls);
    }

    @Test
    void outOfRangePageDoesNotQuery() {
        login(8L);
        assertThrows(PendingProductRuleException.class, () -> service.list(1, 51));
        assertEquals(0L, projects.queriedUserId);
        assertEquals(0, photos.calls);
    }

    @Test
    void photoCountSqlIsIsolatedFromProjectQueries() throws Exception {
        String projectSql = read("mapper/ProjectMapper.xml");
        String photoSql = read("mapper/ProjectPhotoMapper.xml");
        // 项目的查询语句不能读 project_photo；项目彻底删除时清理照片行属于 <delete>，不在此限
        String projectReadSql = projectSql.replaceAll("(?s)<delete\\b.*?</delete>", "");
        assertFalse(projectReadSql.contains("project_photo"));
        assertTrue(projectSql.contains("DELETE FROM project_photo"));
        assertTrue(photoSql.contains("FROM project_photo"));
        assertFalse(photoSql.contains("FROM project_member"));
    }

    private static String read(String path) throws Exception {
        try (InputStream in = ProjectQueryService.class.getClassLoader().getResourceAsStream(path)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static void login(long userId) {
        User user = new User();
        user.setId(userId);
        AuthContext.setUser(user);
    }

    private static ProjectSummaryRow row(long id, String role, long memberCount) {
        ProjectSummaryRow row = new ProjectSummaryRow();
        row.setId(id);
        row.setName("项目");
        row.setRole(role);
        row.setStatus("active");
        row.setWatermarkEnabled(1);
        row.setDefaultWatermarkPosition("bottom_left");
        row.setOwnerNickName("陈工");
        row.setMemberCount(memberCount);
        row.setCreatedAt(LocalDateTime.of(2026, 11, 27, 5, 0));
        row.setUpdatedAt(LocalDateTime.of(2026, 11, 27, 6, 0));
        return row;
    }

    private static ProjectAccessRecord membership(long ownerUserId, long userId, String role, String status) {
        ProjectAccessRecord row = new ProjectAccessRecord();
        row.setOwnerUserId(ownerUserId);
        row.setUserId(userId);
        row.setRole(role);
        row.setStatus(status);
        return row;
    }

    private static final class FakeProjectMapper implements ProjectMapper {
        private List<ProjectSummaryRow> rows = List.of();
        private long queriedUserId;
        private int detailCalls;

        @Override
        public int insert(Project project) {
            return 0;
        }

        @Override
        public Project findById(long id) {
            return null;
        }

        @Override
        public Project lockById(long id) {
            return null;
        }

        @Override
        public int updateOwner(long projectId, long previousOwnerUserId, long newOwnerUserId, java.time.LocalDateTime updatedAt) {
            return 0;
        }

        @Override
        public int updateActive(Project project) {
            return 0;
        }

        @Override
        public int archiveActive(long projectId, long ownerUserId, java.time.LocalDateTime updatedAt) {
            return 0;
        }

        @Override
        public int deleteMessagesForProject(long projectId) { return 0; }

        @Override
        public int deleteAcceptancesForProject(long projectId) { return 0; }

        @Override
        public int deleteInvitesForProject(long projectId) { return 0; }

        @Override
        public int deletePhotosForProject(long projectId) { return 0; }

        @Override
        public int deleteMembersForProject(long projectId) { return 0; }

        @Override
        public int deleteArchived(long projectId, long ownerUserId) { return 0; }

        @Override
        public List<ProjectSummaryRow> listActiveForUser(long userId, int limit, long offset) {
            queriedUserId = userId;
            return rows;
        }

        @Override
        public long countActiveForUser(long userId) {
            queriedUserId = userId;
            return rows.size();
        }

        @Override
        public ProjectSummaryRow findActiveDetail(long projectId, long userId) {
            detailCalls++;
            return null;
        }
    }

    private static final class FakePhotoMapper implements ProjectPhotoMapper {
        private int count;
        private int calls;
        private RuntimeException failure;

        @Override
        public int countByProjectId(long projectId) {
            calls++;
            if (failure != null) {
                throw failure;
            }
            return count;
        }

        @Override
        public int countByImageId(long imageId) {
            return 0;
        }

        @Override
        public ProjectPhoto findByIdInProject(long projectId, long photoId) {
            return null;
        }

        @Override
        public ProjectPhoto findByImageId(long imageId) {
            return null;
        }

        @Override
        public ProjectPhoto findByPhotographerAndRequest(long photographerUserId, String clientRequestId) {
            return null;
        }

        @Override
        public List<ProjectPhoto> listByProject(long projectId, int limit, long offset) {
            return List.of();
        }

        @Override
        public int insert(ProjectPhoto photo) {
            return 0;
        }

        @Override
        public int updateEditable(long id, long projectId, long photographerUserId, String note,
                                  String watermarkPosition, int watermarkVisible, LocalDateTime updatedAt) {
            return 0;
        }

        @Override
        public int deleteByIdInProject(long projectId, long photoId) {
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
