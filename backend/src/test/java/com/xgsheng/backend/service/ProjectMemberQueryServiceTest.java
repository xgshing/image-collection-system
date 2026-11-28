package com.xgsheng.backend.service;

import com.xgsheng.backend.access.ProjectAccessLookup;
import com.xgsheng.backend.access.ProjectAccessRecord;
import com.xgsheng.backend.auth.AuthContext;
import com.xgsheng.backend.entity.ProjectMemberRow;
import com.xgsheng.backend.entity.User;
import com.xgsheng.backend.exception.BusinessException;
import com.xgsheng.backend.mapper.ProjectMemberMapper;
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

class ProjectMemberQueryServiceTest {

    private final Listing listing = new Listing();
    private final Signing signing = new Signing();
    private final MemoryLookup lookup = new MemoryLookup();
    private final ProjectMemberQueryService service = new ProjectMemberQueryService(
            new ProjectAccessService(lookup), listing, signing);

    @AfterEach
    void clearAuth() {
        AuthContext.clear();
    }

    @Test
    void activeMemberSeesOnlyThatProjectsActiveMembers() {
        login(8L);
        lookup.projectExists = true;
        lookup.membership = membership(3L, 8L, "member", "active");
        listing.rows = List.of(person(3L, "陈工", "owner"), person(8L, "李工", "member"));

        Map<String, Object> body = service.listMembers(12L);

        assertEquals(12L, listing.projectId);
        List<?> items = (List<?>) body.get("items");
        assertEquals(2, items.size());
        Map<?, ?> first = (Map<?, ?>) items.get(0);
        assertEquals("signed:avatar-3", first.get("avatarUrl"));
        assertEquals("active", first.get("status"));
        assertEquals(2, signing.calls);
    }

    @Test
    void removedMemberCannotListOrSignAvatars() {
        login(8L);
        lookup.projectExists = true;
        lookup.membership = membership(3L, 8L, "member", "removed");
        BusinessException ex = assertThrows(BusinessException.class, () -> service.listMembers(12L));
        assertEquals(403, ex.getStatus());
        assertEquals("not a project member", ex.getMessage());
        assertEquals(0L, listing.projectId);
        assertEquals(0, signing.calls);
    }

    @Test
    void memberSqlOrdersLocksByIdAndRemovalNoticeUsesUserMessage() throws Exception {
        String members = read("mapper/ProjectMemberMapper.xml");
        String messages = read("mapper/UserMessageMapper.xml");
        String project = read("mapper/ProjectMapper.xml");
        assertTrue(members.contains("ORDER BY id\n        FOR UPDATE"));
        assertTrue(project.contains("FOR UPDATE"));
        assertTrue(messages.contains("INSERT INTO user_message"));
        assertTrue(messages.contains("'project_member_removed'"));
        assertTrue(messages.contains("NULL"));
        assertFalse(messages.contains("project_message"));
    }

    private static String read(String path) throws Exception {
        try (InputStream in = ProjectMemberQueryService.class.getClassLoader().getResourceAsStream(path)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
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

    private static ProjectMemberRow person(long userId, String nickName, String role) {
        ProjectMemberRow row = new ProjectMemberRow();
        row.setUserId(userId);
        row.setNickName(nickName);
        row.setAvatarUrl("avatar-" + userId);
        row.setRole(role);
        row.setStatus("active");
        row.setJoinedAt(LocalDateTime.of(2026, 11, 1, 2, 0));
        return row;
    }

    private static final class Listing implements ProjectMemberMapper {
        private List<ProjectMemberRow> rows = List.of();
        private long projectId;

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
            return 0;
        }

        @Override
        public List<ProjectMemberRow> lockByUserIds(long projectId, long firstUserId, long secondUserId) {
            return List.of();
        }

        @Override
        public ProjectMemberRow lockByUserId(long projectId, long userId) {
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
        public int markRemoved(long id, long removedByUserId, LocalDateTime changedAt) {
            return 0;
        }

        @Override
        public int markLeft(long id, LocalDateTime changedAt) {
            return 0;
        }

        @Override
        public int insertMember(long projectId, long userId, LocalDateTime joinedAt) {
            return 0;
        }

        @Override
        public int reactivateMember(long id, long userId, LocalDateTime changedAt) {
            return 0;
        }

        @Override
        public List<ProjectMemberRow> listActive(long projectId) {
            this.projectId = projectId;
            return rows;
        }

        @Override
        public int updateNickName(long userId, String nickName) {
            return 0;
        }
    }

    private static final class Signing implements StorageService {
        private int calls;

        @Override
        public String storeBytes(byte[] bytes, String originalFilename, String contentType, String category) {
            return "";
        }

        @Override
        public String canonicalUrl(String url) {
            return url;
        }

        @Override
        public String readableUrl(String storedUrl) {
            calls++;
            return "signed:" + storedUrl;
        }

        @Override
        public int signedReadTtlSeconds() {
            return 3600;
        }

        @Override
        public byte[] readStoredBytes(String storedUrl) {
            return new byte[0];
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
