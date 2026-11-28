package com.xgsheng.backend.service;

import com.xgsheng.backend.access.ProjectAccessLookup;
import com.xgsheng.backend.access.ProjectAccessRecord;
import com.xgsheng.backend.auth.AuthContext;
import com.xgsheng.backend.entity.ProjectInvite;
import com.xgsheng.backend.entity.ProjectInviteAcceptance;
import com.xgsheng.backend.entity.User;
import com.xgsheng.backend.entity.UserMessage;
import com.xgsheng.backend.exception.BusinessException;
import com.xgsheng.backend.exception.PendingProductRuleException;
import com.xgsheng.backend.mapper.ProjectInviteAcceptanceMapper;
import com.xgsheng.backend.mapper.ProjectInviteMapper;
import com.xgsheng.backend.mapper.ProjectMemberMapper;
import com.xgsheng.backend.mapper.UserMessageMapper;
import com.xgsheng.backend.project.InviteToken;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProjectInviteQueryServiceTest {

    private final Clock clock = Clock.fixed(Instant.parse("2026-11-27T05:00:00Z"), ZoneOffset.UTC);
    private final FakeInvite invites = new FakeInvite();
    private final FakeMessages messages = new FakeMessages();
    private final FakeWechat wechat = new FakeWechat();
    private final MemoryLookup lookup = new MemoryLookup();
    private final ProjectInviteQueryService service = new ProjectInviteQueryService(
            invites, new FakeAcceptance(), new FakeMembers(), messages,
            new ProjectAccessService(lookup), wechat, clock);

    @AfterEach
    void clearAuth() {
        AuthContext.clear();
    }

    @Test
    void detailIsReadOnlyAndShowsExpiryWithoutUpdatingTheRow() {
        login(9L);
        invites.expiresAt = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC).minusSeconds(1);
        Map<String, Object> body = service.detail(invites.token);
        assertEquals("expired", body.get("status"));
        assertEquals("active", invites.status);
        assertNull(body.get("myDecision"));
        assertNull(body.get("messageId"));
        assertEquals(Boolean.FALSE, body.get("alreadyMember"));
        assertEquals(0, messages.writes);
        assertEquals(0, invites.writes);
    }

    @Test
    void missingInviteDoesNotEchoTheToken() {
        login(9L);
        BusinessException ex = assertThrows(BusinessException.class, () -> service.detail("not-a-real-token"));
        assertEquals(404, ex.getStatus());
        assertEquals("invite not found", ex.getMessage());
    }

    @Test
    void archivedDetailStaysUndecided() {
        login(9L);
        invites.projectStatus = "archived";
        assertThrows(PendingProductRuleException.class, () -> service.detail(invites.token));
        assertEquals(0, messages.writes);
    }

    @Test
    void qrcodeUsesInviteSceneAndKeepsTheInviteWhenWechatFails() {
        login(8L);
        lookup.active = true;
        byte[] first = service.qrcode(invites.token);
        byte[] second = service.qrcode(invites.token);
        assertEquals(1, wechat.calls);
        assertEquals("pinv_" + invites.token, wechat.scene);
        assertEquals(32, wechat.scene.length());
        assertEquals(first.length, second.length);
        assertEquals("active", invites.status);
        assertEquals(0, invites.writes);
    }

    @Test
    void qrcodeFailureDoesNotChangeTheInvite() {
        login(8L);
        lookup.active = true;
        wechat.fail = true;
        BusinessException ex = assertThrows(BusinessException.class, () -> service.qrcode(invites.token));
        assertEquals(503, ex.getStatus());
        assertEquals("wechat mini code failed", ex.getMessage());
        assertFalse(ex.getMessage().contains(invites.token));
        assertEquals("active", invites.status);
        assertEquals(0, invites.writes);
    }

    @Test
    void closedInviteDoesNotCallWechat() {
        login(8L);
        lookup.active = true;
        invites.status = "revoked";
        BusinessException ex = assertThrows(BusinessException.class, () -> service.qrcode(invites.token));
        assertEquals(400, ex.getStatus());
        assertEquals("invite revoked", ex.getMessage());
        assertEquals(0, wechat.calls);
    }

    @Test
    void outsiderCannotRequestInviteCode() {
        login(9L);
        BusinessException ex = assertThrows(BusinessException.class, () -> service.qrcode(invites.token));
        assertEquals(403, ex.getStatus());
        assertEquals(0, wechat.calls);
    }

    @Test
    void inviteSqlStoresOnlyTheHashAndReactivateKeepsJoinedAt() throws Exception {
        String invite = read("mapper/ProjectInviteMapper.xml");
        String acceptance = read("mapper/ProjectInviteAcceptanceMapper.xml");
        String member = read("mapper/ProjectMemberMapper.xml");
        String message = read("mapper/UserMessageMapper.xml");
        assertTrue(invite.contains("token_hash"));
        assertTrue(invite.contains("FOR UPDATE"));
        assertFalse(invite.contains("(token,"));
        assertTrue(acceptance.contains("decision = 'accepted'"));
        assertTrue(acceptance.contains("decision = 'rejected'"));
        int reactivate = member.indexOf("id=\"reactivateMember\"");
        String block = member.substring(reactivate, member.indexOf("</update>", reactivate));
        assertFalse(block.contains("joined_at"));
        assertTrue(block.contains("removed_by_user_id = NULL"));
        assertTrue(member.contains("'member', 'active'"));
        assertTrue(message.contains("'project_invite'"));
        assertTrue(message.contains("#{projectId}"));
        assertTrue(message.contains("read_at IS NULL"));
    }

    private static String read(String path) throws Exception {
        try (InputStream in = ProjectInviteQueryService.class.getClassLoader().getResourceAsStream(path)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static void login(long userId) {
        User user = new User();
        user.setId(userId);
        AuthContext.setUser(user);
    }

    private static final class FakeInvite implements ProjectInviteMapper {
        private final String token = InviteToken.generate();
        private String status = "active";
        private String projectStatus = "active";
        private LocalDateTime expiresAt = LocalDateTime.of(2026, 12, 4, 5, 0);
        private int writes;

        @Override
        public int insert(ProjectInvite invite) {
            writes++;
            return 1;
        }

        @Override
        public ProjectInvite findDetailByTokenHash(String tokenHash) {
            if (!InviteToken.sha256(token).equals(tokenHash)) {
                return null;
            }
            ProjectInvite invite = new ProjectInvite();
            invite.setId(40L);
            invite.setProjectId(12L);
            invite.setInviterUserId(8L);
            invite.setTokenHash(tokenHash);
            invite.setStatus(status);
            invite.setExpiresAt(expiresAt);
            invite.setProjectName("深圳技术大学装修工程");
            invite.setProjectStatus(projectStatus);
            invite.setInviterNickName("李工");
            return invite;
        }

        @Override
        public ProjectInvite lockByTokenHash(String tokenHash) {
            return findDetailByTokenHash(tokenHash);
        }
    }

    private static final class FakeAcceptance implements ProjectInviteAcceptanceMapper {
        @Override
        public ProjectInviteAcceptance findByInviteAndUser(long inviteId, long userId) {
            return null;
        }

        @Override
        public int insert(long inviteId, long userId, String decision, LocalDateTime decidedAt) {
            throw new AssertionError("detail must not write a decision");
        }

        @Override
        public int updateRejectedToAccepted(long id, long inviteId, long userId, LocalDateTime decidedAt) {
            throw new AssertionError("detail must not write a decision");
        }
    }

    private static final class FakeMembers implements ProjectMemberMapper {
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
        public int markRemoved(long id, long removedByUserId, LocalDateTime changedAt) {
            return 0;
        }

        @Override
        public int markLeft(long id, LocalDateTime changedAt) {
            return 0;
        }

        @Override
        public int insertMember(long projectId, long userId, LocalDateTime joinedAt) {
            throw new AssertionError("detail must not insert a member");
        }

        @Override
        public int reactivateMember(long id, long userId, LocalDateTime changedAt) {
            throw new AssertionError("detail must not reactivate a member");
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

    private static final class FakeMessages implements UserMessageMapper {
        private int writes;

        @Override
        public int insertRemovalNotice(long userId, long projectId, String title, String body, LocalDateTime createdAt) {
            writes++;
            return 1;
        }

        @Override
        public int insertInvite(UserMessage message) {
            writes++;
            return 1;
        }

        @Override
        public UserMessage findByUserAndInvite(long userId, long inviteId) {
            return null;
        }

        @Override
        public java.util.List<com.xgsheng.backend.entity.UserMessageView> listForUser(long userId, int limit, long offset) {
            return java.util.List.of();
        }

        @Override
        public long countForUser(long userId) {
            return 0;
        }

        @Override
        public long countUnread(long userId) {
            return 0;
        }

        @Override
        public UserMessage findOwn(long id, long userId) {
            return null;
        }

        @Override
        public int markReadIfUnread(long id, long userId, LocalDateTime readAt) {
            writes++;
            return 1;
        }
    }

    private static final class MemoryLookup implements ProjectAccessLookup {
        private boolean active;

        @Override
        public boolean projectExists(long projectId) {
            return true;
        }

        @Override
        public ProjectAccessRecord findMembership(long projectId, long userId) {
            if (!active || userId != 8L) {
                return null;
            }
            ProjectAccessRecord row = new ProjectAccessRecord();
            row.setUserId(8L);
            row.setRole("member");
            row.setStatus("active");
            row.setOwnerUserId(3L);
            return row;
        }
    }

    private static final class FakeWechat extends WechatMiniProgramService {
        private int calls;
        private String scene;
        private boolean fail;

        private FakeWechat() {
            super(new com.xgsheng.backend.config.WechatMiniProgramProperties(), new WechatTransport() {
                @Override
                public String get(java.net.URI uri) {
                    return "";
                }

                @Override
                public byte[] post(java.net.URI uri, String json) {
                    return new byte[0];
                }
            });
        }

        @Override
        public byte[] createInviteMiniCode(String scene) {
            if (fail) {
                throw com.xgsheng.backend.exception.BusinessException.unavailable("wechat mini code failed");
            }
            calls++;
            this.scene = scene;
            return new byte[] {1, 2, 3};
        }
    }
}
