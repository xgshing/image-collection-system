package com.xgsheng.backend.service;

import com.xgsheng.backend.access.ProjectAccessLookup;
import com.xgsheng.backend.access.ProjectAccessRecord;
import com.xgsheng.backend.auth.AuthContext;
import com.xgsheng.backend.entity.Project;
import com.xgsheng.backend.entity.ProjectInvite;
import com.xgsheng.backend.entity.ProjectInviteAcceptance;
import com.xgsheng.backend.entity.ProjectMemberRow;
import com.xgsheng.backend.entity.User;
import com.xgsheng.backend.entity.UserMessage;
import com.xgsheng.backend.entity.UserMessageView;
import com.xgsheng.backend.exception.BusinessException;
import com.xgsheng.backend.exception.PendingProductRuleException;
import com.xgsheng.backend.mapper.ProjectInviteAcceptanceMapper;
import com.xgsheng.backend.mapper.ProjectInviteMapper;
import com.xgsheng.backend.mapper.ProjectMapper;
import com.xgsheng.backend.mapper.ProjectMemberMapper;
import com.xgsheng.backend.mapper.UserMessageMapper;
import com.xgsheng.backend.project.InviteToken;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProjectInviteCommandServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-27T05:00:00Z");
    private static final LocalDateTime NOW_TIME = LocalDateTime.ofInstant(NOW, ZoneOffset.UTC);

    private final Store store = new Store();
    private final ProjectInviteCommandService service = new ProjectInviteCommandService(
            new ProjectGateway(store), new MemberGateway(store), new InviteGateway(store),
            new AcceptanceGateway(store), new MessageGateway(store),
            new ProjectAccessService(new Lookup(store)), Clock.fixed(NOW, ZoneOffset.UTC));

    @AfterEach
    void clearAuth() {
        AuthContext.clear();
    }

    @Test
    void activeMemberCreatesInviteWithoutChangingRoleOrWritingAMessage() {
        login(8L);
        Map<String, Object> body = tx(() -> service.create(12L));
        String token = (String) body.get("token");
        assertEquals(27, token.length());
        assertEquals("pinv_" + token, body.get("scene"));
        assertEquals(32, ((String) body.get("scene")).length());
        assertEquals("2026-12-04T05:00:00Z", body.get("expiresAt"));
        assertEquals("active", body.get("status"));
        assertEquals(InviteToken.sha256(token), store.committed.invites.get(0).tokenHash);
        assertNotEquals(token, store.committed.invites.get(0).tokenHash);
        assertEquals("member", store.committed.roleOf(8L));
        assertEquals(0, store.committed.memberInserts);
        assertEquals(0, store.committed.acceptances.size());
        assertEquals(0, store.committed.messages.size());
        assertEquals(List.of("lock-project", "lock-member"), store.calls);
    }

    @Test
    void outsiderCannotCreateInvite() {
        login(9L);
        BusinessException ex = assertThrows(BusinessException.class, () -> tx(() -> service.create(12L)));
        assertEquals(403, ex.getStatus());
        assertEquals("not a project member", ex.getMessage());
        assertTrue(store.committed.invites.isEmpty());
    }

    @Test
    void archivedProjectStaysUndecided() {
        login(8L);
        store.committed.projectStatus = "archived";
        PendingProductRuleException ex = assertThrows(PendingProductRuleException.class, () -> tx(() -> service.create(12L)));
        assertTrue(ex.getMessage().contains("projectId=12"));
        assertFalse(ex.getMessage().contains("pinv_"));
        assertTrue(store.committed.invites.isEmpty());
    }

    @Test
    void repeatedAcceptDoesNotInsertASecondMember() {
        login(8L);
        String token = (String) tx(() -> service.create(12L)).get("token");
        login(9L);
        Map<String, Object> first = tx(() -> service.accept(token));
        Map<String, Object> second = tx(() -> service.accept(token));
        assertEquals(Boolean.FALSE, first.get("alreadyMember"));
        assertEquals("member", first.get("role"));
        assertEquals(Boolean.TRUE, second.get("alreadyMember"));
        assertEquals(1, store.committed.membersOf(9L));
        assertEquals(1, store.committed.decisionsFor(token));
        assertEquals(NOW_TIME, store.committed.joinedAt(9L));
    }

    @Test
    void twoPeopleCanAcceptTheSameInvite() {
        login(8L);
        String token = (String) tx(() -> service.create(12L)).get("token");
        login(9L);
        tx(() -> service.accept(token));
        login(10L);
        tx(() -> service.accept(token));
        assertEquals(1, store.committed.membersOf(9L));
        assertEquals(1, store.committed.membersOf(10L));
        assertEquals("member", store.committed.roleOf(8L));
    }

    @Test
    void concurrentAcceptsLeaveOneMembership() throws Exception {
        login(8L);
        String token = (String) tx(() -> service.create(12L)).get("token");
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Map<String, Object>> first = pool.submit(() -> acceptAs(9L, token));
            Future<Map<String, Object>> second = pool.submit(() -> acceptAs(9L, token));
            List<Map<String, Object>> results = List.of(first.get(), second.get());
            long fresh = results.stream().filter(body -> Boolean.FALSE.equals(body.get("alreadyMember"))).count();
            long repeated = results.stream().filter(body -> Boolean.TRUE.equals(body.get("alreadyMember"))).count();
            assertEquals(1, fresh);
            assertEquals(1, repeated);
            assertEquals(1, store.committed.membersOf(9L));
            assertEquals(1, store.committed.decisionsFor(token));
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void rejectedInviteCanBeAcceptedByUpdatingTheSameRow() {
        login(8L);
        String token = (String) tx(() -> service.create(12L)).get("token");
        login(9L);
        tx(() -> service.reject(token));
        Map<String, Object> body = tx(() -> service.accept(token));
        assertEquals("accepted", body.get("decision"));
        assertEquals(Boolean.FALSE, body.get("alreadyMember"));
        assertEquals(1, store.committed.decisionsFor(token));
        assertEquals("accepted", store.committed.decisionOf(token, 9L));
        assertEquals(1, store.committed.membersOf(9L));
    }

    @Test
    void leftMemberRejoinsWithoutLosingTheFirstJoinTime() {
        login(8L);
        String token = (String) tx(() -> service.create(12L)).get("token");
        LocalDateTime joined = LocalDateTime.of(2026, 1, 2, 3, 4);
        store.committed.addMember(9L, "member", "left", joined, null);
        login(9L);
        Map<String, Object> body = tx(() -> service.accept(token));
        assertEquals(Boolean.FALSE, body.get("alreadyMember"));
        assertEquals("member", body.get("role"));
        assertEquals("active", store.committed.statusOf(9L));
        assertEquals(joined, store.committed.joinedAt(9L));
        assertNull(store.committed.removedBy(9L));
        assertEquals(1, store.committed.membersOf(9L));
    }

    @Test
    void removedMemberRejoinsAndClearsRemovedBy() {
        login(8L);
        String token = (String) tx(() -> service.create(12L)).get("token");
        store.committed.addMember(9L, "member", "removed", LocalDateTime.of(2026, 2, 2, 0, 0), 3L);
        login(9L);
        tx(() -> service.accept(token));
        assertEquals("active", store.committed.statusOf(9L));
        assertNull(store.committed.removedBy(9L));
        assertEquals(LocalDateTime.of(2026, 2, 2, 0, 0), store.committed.joinedAt(9L));
    }

    @Test
    void ownerAcceptDoesNotChangeTheOwnerRole() {
        login(8L);
        String token = (String) tx(() -> service.create(12L)).get("token");
        login(3L);
        Map<String, Object> body = tx(() -> service.accept(token));
        assertEquals("owner", body.get("role"));
        assertEquals(Boolean.TRUE, body.get("alreadyMember"));
        assertEquals("owner", store.committed.roleOf(3L));
        assertEquals(0, store.committed.memberInserts);
    }

    @Test
    void expiredAndRevokedInvitesCannotBeAcceptedOrRejected() {
        String expired = store.committed.seed(NOW_TIME.minusMinutes(1), "active");
        String revoked = store.committed.seed(NOW_TIME.plusDays(1), "revoked");
        login(9L);
        assertEquals("invite expired", failure(() -> service.accept(expired)));
        assertEquals("invite expired", failure(() -> service.reject(expired)));
        assertEquals("invite revoked", failure(() -> service.accept(revoked)));
        assertEquals("active", store.committed.statusOfToken(expired));
        assertTrue(store.committed.acceptances.isEmpty());
        assertEquals(0, store.committed.membersOf(9L));
    }

    @Test
    void acceptedInviteCannotBeRejected() {
        login(8L);
        String token = (String) tx(() -> service.create(12L)).get("token");
        login(9L);
        tx(() -> service.accept(token));
        BusinessException ex = assertThrows(BusinessException.class, () -> tx(() -> service.reject(token)));
        assertEquals(400, ex.getStatus());
        assertEquals("invite already accepted", ex.getMessage());
        assertEquals("active", store.committed.statusOf(9L));
        assertEquals("accepted", store.committed.decisionOf(token, 9L));
    }

    @Test
    void repeatedRejectKeepsOneRowAndNoMember() {
        login(8L);
        String token = (String) tx(() -> service.create(12L)).get("token");
        login(9L);
        assertEquals("rejected", tx(() -> service.reject(token)).get("decision"));
        assertEquals("rejected", tx(() -> service.reject(token)).get("decision"));
        assertEquals(1, store.committed.decisionsFor(token));
        assertEquals(0, store.committed.membersOf(9L));
    }

    @Test
    void acceptAndRejectSerializeToTheAcceptedMembership() throws Exception {
        login(8L);
        String token = (String) tx(() -> service.create(12L)).get("token");
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Object> accept = pool.submit(() -> call(() -> service.accept(token), 9L));
            Future<Object> reject = pool.submit(() -> call(() -> service.reject(token), 9L));
            List<Object> results = List.of(accept.get(), reject.get());
            assertEquals("accepted", store.committed.decisionOf(token, 9L));
            assertEquals("active", store.committed.statusOf(9L));
            assertEquals(1, store.committed.membersOf(9L));
            assertEquals(1, store.committed.decisionsFor(token));
            long rejected = results.stream().filter(BusinessException.class::isInstance).count();
            if (rejected == 1) {
                BusinessException ex = (BusinessException) results.stream()
                        .filter(BusinessException.class::isInstance).findFirst().orElseThrow();
                assertEquals("invite already accepted", ex.getMessage());
            }
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void memberInsertFailureRollsBackTheAcceptance() {
        login(8L);
        String token = (String) tx(() -> service.create(12L)).get("token");
        store.failMemberInsert = true;
        login(9L);
        assertThrows(RuntimeException.class, () -> tx(() -> service.accept(token)));
        assertTrue(store.committed.acceptances.isEmpty());
        assertEquals(0, store.committed.membersOf(9L));
    }

    @Test
    void inviteMessageIsSeparateFromMembershipAndKeepsReadState() {
        login(8L);
        String token = (String) tx(() -> service.create(12L)).get("token");
        login(9L);
        Map<String, Object> created = tx(() -> service.createMessage(token));
        assertEquals(Boolean.TRUE, created.get("created"));
        assertEquals(Boolean.FALSE, created.get("read"));
        assertEquals(Boolean.FALSE, created.get("alreadyMember"));
        assertNull(created.get("myDecision"));
        assertEquals("active", created.get("status"));
        UserMessage message = store.committed.messages.get(0);
        assertEquals(9L, message.getUserId());
        assertEquals(12L, message.getProjectId());
        assertEquals("李工邀请你加入项目", message.getTitle());
        assertEquals("深圳技术大学装修工程", message.getBody());
        assertEquals("project_invite", message.getType());
        message.setReadAt(NOW_TIME);
        Map<String, Object> again = tx(() -> service.createMessage(token));
        assertEquals(Boolean.FALSE, again.get("created"));
        assertEquals(Boolean.TRUE, again.get("read"));
        assertEquals(message.getId(), again.get("messageId"));
        assertEquals(NOW_TIME, store.committed.messages.get(0).getReadAt());
        assertEquals(0, store.committed.membersOf(9L));
        assertEquals(List.of("lock-project", "lock-invite"), store.calls);
    }

    @Test
    void expiredInviteCanStillCreateAMessageWithoutJoining() {
        String token = store.committed.seed(NOW_TIME.minusSeconds(1), "active");
        login(9L);
        Map<String, Object> body = tx(() -> service.createMessage(token));
        assertEquals("expired", body.get("status"));
        assertEquals(Boolean.TRUE, body.get("created"));
        assertEquals("active", store.committed.statusOfToken(token));
        assertEquals(0, store.committed.membersOf(9L));
    }

    @Test
    void duplicateMessageInsertDoesNotClearReadAt() {
        String token = store.committed.seed(NOW_TIME.plusDays(1), "active");
        long inviteId = store.committed.invites.get(0).id;
        UserMessage existing = new UserMessage();
        existing.setId(90L);
        existing.setUserId(9L);
        existing.setInviteId(inviteId);
        existing.setReadAt(NOW_TIME);
        store.committed.messages.add(existing);
        store.hideFirstMessageRead = true;
        login(9L);
        Map<String, Object> body = tx(() -> service.createMessage(token));
        assertEquals(Boolean.FALSE, body.get("created"));
        assertEquals(Boolean.TRUE, body.get("read"));
        assertEquals(90L, body.get("messageId"));
        assertEquals(NOW_TIME, store.committed.messages.get(0).getReadAt());
    }

    @Test
    void messageInsertFailureIsNotReportedAsCreated() {
        String token = store.committed.seed(NOW_TIME.plusDays(1), "active");
        store.failMessage = true;
        login(9L);
        assertThrows(RuntimeException.class, () -> tx(() -> service.createMessage(token)));
        assertTrue(store.committed.messages.isEmpty());
    }

    @Test
    void missingTokenDoesNotEchoTheValue() {
        login(9L);
        BusinessException ex = assertThrows(BusinessException.class, () -> tx(() -> service.accept("missing-token")));
        assertEquals(404, ex.getStatus());
        assertEquals("invite not found", ex.getMessage());
        assertFalse(ex.getMessage().contains("missing-token"));
    }

    private Map<String, Object> acceptAs(long userId, String token) {
        return (Map<String, Object>) call(() -> service.accept(token), userId);
    }

    private Object call(java.util.function.Supplier<Map<String, Object>> work, long userId) {
        try {
            login(userId);
            return tx(work);
        } catch (RuntimeException ex) {
            return ex;
        } finally {
            AuthContext.clear();
        }
    }

    private String failure(java.util.function.Supplier<Map<String, Object>> work) {
        return assertThrows(BusinessException.class, () -> tx(work)).getMessage();
    }

    private <T> T tx(java.util.function.Supplier<T> work) {
        store.lock.lock();
        try {
            store.working = store.committed.copy();
            store.calls.clear();
            T result = work.get();
            store.committed = store.working;
            return result;
        } catch (RuntimeException ex) {
            store.working = store.committed.copy();
            throw ex;
        } finally {
            store.lock.unlock();
        }
    }

    private static void login(long userId) {
        User user = new User();
        user.setId(userId);
        AuthContext.setUser(user);
    }

    private static final class Store {
        private final java.util.concurrent.locks.ReentrantLock lock = new java.util.concurrent.locks.ReentrantLock();
        private State committed = State.sample();
        private State working;
        private final List<String> calls = new ArrayList<>();
        private boolean failMemberInsert;
        private boolean failMessage;
        private boolean hideFirstMessageRead;
    }

    private static final class State {
        private String projectStatus = "active";
        private final List<Member> members = new ArrayList<>();
        private final List<Invite> invites = new ArrayList<>();
        private final List<Decision> acceptances = new ArrayList<>();
        private final List<UserMessage> messages = new ArrayList<>();
        private int memberInserts;
        private long nextId = 40;

        private static State sample() {
            State state = new State();
            state.members.add(new Member(1, 3L, "owner", "active", NOW_TIME, null, "陈工"));
            state.members.add(new Member(2, 8L, "member", "active", NOW_TIME, null, "李工"));
            return state;
        }

        private State copy() {
            State copy = new State();
            copy.projectStatus = projectStatus;
            copy.memberInserts = memberInserts;
            copy.nextId = nextId;
            for (Member member : members) {
                copy.members.add(member.copy());
            }
            for (Invite invite : invites) {
                copy.invites.add(invite.copy());
            }
            for (Decision decision : acceptances) {
                copy.acceptances.add(decision.copy());
            }
            for (UserMessage message : messages) {
                copy.messages.add(copyMessage(message));
            }
            return copy;
        }

        private String seed(LocalDateTime expiresAt, String status) {
            String token = InviteToken.generate();
            Invite invite = new Invite();
            invite.id = nextId++;
            invite.inviterUserId = 8L;
            invite.tokenHash = InviteToken.sha256(token);
            invite.plain = token;
            invite.status = status;
            invite.expiresAt = expiresAt;
            invites.add(invite);
            return token;
        }

        private void addMember(long userId, String role, String status, LocalDateTime joinedAt, Long removedBy) {
            members.add(new Member(nextId++, userId, role, status, joinedAt, removedBy, "访客"));
        }

        private int membersOf(long userId) {
            int count = 0;
            for (Member member : members) {
                if (member.userId == userId) {
                    count++;
                }
            }
            return count;
        }

        private String roleOf(long userId) {
            return member(userId).role;
        }

        private String statusOf(long userId) {
            return member(userId).status;
        }

        private LocalDateTime joinedAt(long userId) {
            return member(userId).joinedAt;
        }

        private Long removedBy(long userId) {
            return member(userId).removedBy;
        }

        private Member member(long userId) {
            for (Member member : members) {
                if (member.userId == userId) {
                    return member;
                }
            }
            return null;
        }

        private Invite inviteByHash(String hash) {
            for (Invite invite : invites) {
                if (invite.tokenHash.equals(hash)) {
                    return invite;
                }
            }
            return null;
        }

        private String statusOfToken(String token) {
            return inviteByHash(InviteToken.sha256(token)).status;
        }

        private int decisionsFor(String token) {
            long inviteId = inviteByHash(InviteToken.sha256(token)).id;
            int count = 0;
            for (Decision decision : acceptances) {
                if (decision.inviteId == inviteId) {
                    count++;
                }
            }
            return count;
        }

        private String decisionOf(String token, long userId) {
            long inviteId = inviteByHash(InviteToken.sha256(token)).id;
            for (Decision decision : acceptances) {
                if (decision.inviteId == inviteId && decision.userId == userId) {
                    return decision.decision;
                }
            }
            return null;
        }
    }

    private static final class Member {
        private long id;
        private long userId;
        private String role;
        private String status;
        private LocalDateTime joinedAt;
        private Long removedBy;
        private String nickName;

        private Member(long id, long userId, String role, String status, LocalDateTime joinedAt, Long removedBy, String nickName) {
            this.id = id;
            this.userId = userId;
            this.role = role;
            this.status = status;
            this.joinedAt = joinedAt;
            this.removedBy = removedBy;
            this.nickName = nickName;
        }

        private Member copy() {
            return new Member(id, userId, role, status, joinedAt, removedBy, nickName);
        }
    }

    private static final class Invite {
        private long id;
        private long inviterUserId;
        private String tokenHash;
        private String plain;
        private String status;
        private LocalDateTime expiresAt;

        private Invite copy() {
            Invite copy = new Invite();
            copy.id = id;
            copy.inviterUserId = inviterUserId;
            copy.tokenHash = tokenHash;
            copy.plain = plain;
            copy.status = status;
            copy.expiresAt = expiresAt;
            return copy;
        }
    }

    private static final class Decision {
        private long id;
        private long inviteId;
        private long userId;
        private String decision;
        private LocalDateTime decidedAt;

        private Decision copy() {
            Decision copy = new Decision();
            copy.id = id;
            copy.inviteId = inviteId;
            copy.userId = userId;
            copy.decision = decision;
            copy.decidedAt = decidedAt;
            return copy;
        }
    }

    private static UserMessage copyMessage(UserMessage source) {
        UserMessage copy = new UserMessage();
        copy.setId(source.getId());
        copy.setUserId(source.getUserId());
        copy.setType(source.getType());
        copy.setTitle(source.getTitle());
        copy.setBody(source.getBody());
        copy.setProjectId(source.getProjectId());
        copy.setInviteId(source.getInviteId());
        copy.setReadAt(source.getReadAt());
        copy.setCreatedAt(source.getCreatedAt());
        return copy;
    }

    private static ProjectInvite toInvite(State state, Invite invite, boolean detail) {
        ProjectInvite row = new ProjectInvite();
        row.setId(invite.id);
        row.setProjectId(12L);
        row.setInviterUserId(invite.inviterUserId);
        row.setTokenHash(invite.tokenHash);
        row.setStatus(invite.status);
        row.setExpiresAt(invite.expiresAt);
        if (detail) {
            row.setProjectName("深圳技术大学装修工程");
            row.setProjectStatus(state.projectStatus);
            Member inviter = state.member(invite.inviterUserId);
            row.setInviterNickName(inviter == null ? null : inviter.nickName);
        }
        return row;
    }

    private static final class Lookup implements ProjectAccessLookup {
        private final Store store;

        private Lookup(Store store) {
            this.store = store;
        }

        @Override
        public boolean projectExists(long projectId) {
            return projectId == 12L;
        }

        @Override
        public ProjectAccessRecord findMembership(long projectId, long userId) {
            Member member = store.working.member(userId);
            if (member == null) {
                return null;
            }
            ProjectAccessRecord row = new ProjectAccessRecord();
            row.setOwnerUserId(3L);
            row.setUserId(member.userId);
            row.setRole(member.role);
            row.setStatus(member.status);
            return row;
        }
    }

    private static final class ProjectGateway implements ProjectMapper {
        private final Store store;

        private ProjectGateway(Store store) {
            this.store = store;
        }

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
            store.calls.add("lock-project");
            if (id != 12L) {
                return null;
            }
            Project project = new Project();
            project.setId(12L);
            project.setName("深圳技术大学装修工程");
            project.setStatus(store.working.projectStatus);
            project.setOwnerUserId(3L);
            return project;
        }

        @Override
        public int updateOwner(long projectId, long previousOwnerUserId, long newOwnerUserId, LocalDateTime updatedAt) {
            return 0;
        }

        @Override
        public int updateActive(Project project) {
            return 0;
        }

        @Override
        public int archiveActive(long projectId, long ownerUserId, LocalDateTime updatedAt) {
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

    private static final class MemberGateway implements ProjectMemberMapper {
        private final Store store;

        private MemberGateway(Store store) {
            this.store = store;
        }

        @Override
        public int countProject(long projectId) {
            return 0;
        }

        @Override
        public ProjectAccessRecord findMembership(long projectId, long userId) {
            Member member = store.working.member(userId);
            if (member == null) {
                return null;
            }
            ProjectAccessRecord row = new ProjectAccessRecord();
            row.setUserId(member.userId);
            row.setRole(member.role);
            row.setStatus(member.status);
            return row;
        }

        @Override
        public int insertOwner(long projectId, long userId, LocalDateTime joinedAt) {
            throw new AssertionError("accept must not insert an owner");
        }

        @Override
        public List<ProjectMemberRow> lockByUserIds(long projectId, long firstUserId, long secondUserId) {
            return List.of();
        }

        @Override
        public ProjectMemberRow lockByUserId(long projectId, long userId) {
            store.calls.add("lock-member");
            return toRow(store.working.member(userId));
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
            if (store.working.member(userId) != null) {
                throw new DuplicateKeyException("member");
            }
            if (store.failMemberInsert) {
                return 0;
            }
            store.working.memberInserts++;
            store.working.members.add(new Member(store.working.nextId++, userId, "member", "active", joinedAt, null, "新成员"));
            return 1;
        }

        @Override
        public int reactivateMember(long id, long userId, LocalDateTime changedAt) {
            Member member = store.working.member(userId);
            if (member == null || member.id != id || !"member".equals(member.role)) {
                return 0;
            }
            if (!"left".equals(member.status) && !"removed".equals(member.status)) {
                return 0;
            }
            member.status = "active";
            member.removedBy = null;
            return 1;
        }

        @Override
        public List<ProjectMemberRow> listActive(long projectId) {
            return List.of();
        }

        @Override
        public int updateNickName(long userId, String nickName) {
            return 0;
        }

        private static ProjectMemberRow toRow(Member member) {
            if (member == null) {
                return null;
            }
            ProjectMemberRow row = new ProjectMemberRow();
            row.setId(member.id);
            row.setProjectId(12L);
            row.setUserId(member.userId);
            row.setRole(member.role);
            row.setStatus(member.status);
            row.setJoinedAt(member.joinedAt);
            row.setRemovedByUserId(member.removedBy);
            return row;
        }
    }

    private static final class InviteGateway implements ProjectInviteMapper {
        private final Store store;

        private InviteGateway(Store store) {
            this.store = store;
        }

        @Override
        public int insert(ProjectInvite invite) {
            if (store.working.inviteByHash(invite.getTokenHash()) != null) {
                throw new DuplicateKeyException("invite");
            }
            Invite saved = new Invite();
            saved.id = store.working.nextId++;
            saved.inviterUserId = invite.getInviterUserId();
            saved.tokenHash = invite.getTokenHash();
            saved.status = "active";
            saved.expiresAt = invite.getExpiresAt();
            store.working.invites.add(saved);
            invite.setId(saved.id);
            return 1;
        }

        @Override
        public ProjectInvite findDetailByTokenHash(String tokenHash) {
            Invite invite = store.working.inviteByHash(tokenHash);
            return invite == null ? null : toInvite(store.working, invite, true);
        }

        @Override
        public ProjectInvite lockByTokenHash(String tokenHash) {
            store.calls.add("lock-invite");
            Invite invite = store.working.inviteByHash(tokenHash);
            return invite == null ? null : toInvite(store.working, invite, false);
        }
    }

    private static final class AcceptanceGateway implements ProjectInviteAcceptanceMapper {
        private final Store store;

        private AcceptanceGateway(Store store) {
            this.store = store;
        }

        @Override
        public ProjectInviteAcceptance findByInviteAndUser(long inviteId, long userId) {
            for (Decision decision : store.working.acceptances) {
                if (decision.inviteId == inviteId && decision.userId == userId) {
                    ProjectInviteAcceptance row = new ProjectInviteAcceptance();
                    row.setId(decision.id);
                    row.setInviteId(inviteId);
                    row.setInviteeUserId(userId);
                    row.setDecision(decision.decision);
                    row.setDecidedAt(decision.decidedAt);
                    return row;
                }
            }
            return null;
        }

        @Override
        public int insert(long inviteId, long userId, String decision, LocalDateTime decidedAt) {
            for (Decision existing : store.working.acceptances) {
                if (existing.inviteId == inviteId && existing.userId == userId) {
                    throw new DuplicateKeyException("acceptance");
                }
            }
            Decision saved = new Decision();
            saved.id = store.working.nextId++;
            saved.inviteId = inviteId;
            saved.userId = userId;
            saved.decision = decision;
            saved.decidedAt = decidedAt;
            store.working.acceptances.add(saved);
            return 1;
        }

        @Override
        public int updateRejectedToAccepted(long id, long inviteId, long userId, LocalDateTime decidedAt) {
            for (Decision decision : store.working.acceptances) {
                if (decision.id == id && decision.inviteId == inviteId && decision.userId == userId
                        && "rejected".equals(decision.decision)) {
                    decision.decision = "accepted";
                    decision.decidedAt = decidedAt;
                    return 1;
                }
            }
            return 0;
        }
    }

    private static final class MessageGateway implements UserMessageMapper {
        private final Store store;

        private MessageGateway(Store store) {
            this.store = store;
        }

        @Override
        public int insertRemovalNotice(long userId, long projectId, String title, String body, LocalDateTime createdAt) {
            return 0;
        }

        @Override
        public int insertInvite(UserMessage message) {
            if (store.failMessage) {
                throw new RuntimeException("message insert failed");
            }
            for (UserMessage existing : store.working.messages) {
                if (existing.getUserId().equals(message.getUserId()) && existing.getInviteId().equals(message.getInviteId())) {
                    throw new DuplicateKeyException("message");
                }
            }
            message.setId(store.working.nextId++);
            message.setType("project_invite");
            store.working.messages.add(copyMessage(message));
            return 1;
        }

        @Override
        public UserMessage findByUserAndInvite(long userId, long inviteId) {
            if (store.hideFirstMessageRead) {
                store.hideFirstMessageRead = false;
                return null;
            }
            for (UserMessage message : store.working.messages) {
                if (message.getUserId() == userId && message.getInviteId() == inviteId) {
                    return copyMessage(message);
                }
            }
            return null;
        }

        @Override
        public List<UserMessageView> listForUser(long userId, int limit, long offset) {
            return List.of();
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
            return 0;
        }
    }
}
