package com.xgsheng.backend.service;

import com.xgsheng.backend.access.ProjectAccessLookup;
import com.xgsheng.backend.access.ProjectAccessRecord;
import com.xgsheng.backend.auth.AuthContext;
import com.xgsheng.backend.entity.Project;
import com.xgsheng.backend.entity.ProjectMemberRow;
import com.xgsheng.backend.entity.User;
import com.xgsheng.backend.entity.UserMessage;
import com.xgsheng.backend.entity.UserMessageView;
import com.xgsheng.backend.exception.BusinessException;
import com.xgsheng.backend.mapper.ProjectMapper;
import com.xgsheng.backend.mapper.ProjectMemberMapper;
import com.xgsheng.backend.mapper.UserMessageMapper;
import com.xgsheng.backend.project.TransferOwnerRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProjectMemberCommandServiceTest {

    private final MemberStore store = new MemberStore();
    private final ProjectMemberCommandService service = new ProjectMemberCommandService(
            new StoreProjectMapper(store), new StoreMemberMapper(store), new StoreMessageMapper(store));

    @AfterEach
    void clearAuth() {
        AuthContext.clear();
    }

    @Test
    void transferSucceedsAndKeepsASingleActiveOwner() {
        login(3L);
        Map<String, Object> body = inTransaction(() -> service.transfer(12L, target(8L)));
        assertEquals(12L, body.get("projectId"));
        assertEquals(8L, body.get("ownerUserId"));
        assertEquals(3L, body.get("previousOwnerUserId"));
        assertEquals(List.of("lock-project", "lock-members"), store.calls);
        assertEquals(8L, store.committed.ownerUserId);
        assertEquals("member", store.committed.roleOf(3L));
        assertEquals("owner", store.committed.roleOf(8L));
        assertEquals("active", store.committed.statusOf(3L));
        assertEquals(1, store.committed.activeOwnerCount());
    }

    @Test
    void nonOwnerCannotTransferOrProbeTheTarget() {
        login(8L);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> inTransaction(() -> service.transfer(12L, target(99L))));
        assertEquals(400, ex.getStatus());
        assertEquals("only owner can transfer", ex.getMessage());
        assertEquals(3L, store.committed.ownerUserId);
    }

    @Test
    void targetMustBeAnActiveMember() {
        login(3L);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> inTransaction(() -> service.transfer(12L, target(4L))));
        assertEquals(400, ex.getStatus());
        assertEquals("target is not a project member", ex.getMessage());
        assertEquals("owner", store.committed.roleOf(3L));
    }

    @Test
    void repeatedTransferToTheCurrentOwnerDoesNotChangeRoles() {
        login(3L);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> inTransaction(() -> service.transfer(12L, target(3L))));
        assertEquals(400, ex.getStatus());
        assertEquals("target is already owner", ex.getMessage());
        assertEquals(3L, store.committed.ownerUserId);
        assertEquals("owner", store.committed.roleOf(3L));
    }

    @Test
    void concurrentTransfersLeaveOneOwner() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Object> first = pool.submit(() -> transferAs(3L, 8L));
            Future<Object> second = pool.submit(() -> transferAs(3L, 9L));
            List<Object> results = List.of(await(first), await(second));
            long successes = results.stream().filter(Map.class::isInstance).count();
            long rejected = results.stream().filter(BusinessException.class::isInstance).count();
            assertEquals(1, successes);
            assertEquals(1, rejected);
            BusinessException rejectedResult = (BusinessException) results.stream()
                    .filter(BusinessException.class::isInstance).findFirst().orElseThrow();
            assertEquals(400, rejectedResult.getStatus());
            assertEquals("only owner can transfer", rejectedResult.getMessage());
            assertEquals(1, store.committed.activeOwnerCount());
            assertTrue(store.committed.ownerUserId == 8L || store.committed.ownerUserId == 9L);
            assertEquals("member", store.committed.roleOf(3L));
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void failedTransferRollsBackEveryWrite() {
        login(3L);
        store.failPromote = true;
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> inTransaction(() -> service.transfer(12L, target(8L))));
        assertEquals("project member data is inconsistent", ex.getMessage());
        assertFalse(ex instanceof BusinessException);
        assertEquals(3L, store.committed.ownerUserId);
        assertEquals("owner", store.committed.roleOf(3L));
        assertEquals("member", store.committed.roleOf(8L));
        assertEquals(0, store.notices);
    }

    @Test
    void ownerCannotLeave() {
        login(3L);
        BusinessException ex = assertThrows(BusinessException.class, () -> inTransaction(() -> service.leave(12L)));
        assertEquals(400, ex.getStatus());
        assertEquals("owner must transfer before leaving", ex.getMessage());
        assertEquals("owner", store.committed.roleOf(3L));
        assertEquals("active", store.committed.statusOf(3L));
        assertEquals(0, store.notices);
    }

    @Test
    void ordinaryMemberLeavesWithoutANotice() {
        login(8L);
        Map<String, Object> body = inTransaction(() -> service.leave(12L));
        assertEquals("left", body.get("status"));
        assertEquals(Boolean.FALSE, body.get("alreadyInactive"));
        assertEquals("left", store.committed.statusOf(8L));
        assertNull(store.committed.removedBy(8L));
        assertEquals(0, store.notices);
        ProjectAccessService access = new ProjectAccessService(new ProjectAccessLookup() {
            @Override
            public boolean projectExists(long projectId) {
                return true;
            }

            @Override
            public ProjectAccessRecord findMembership(long projectId, long userId) {
                ProjectAccessRecord row = new ProjectAccessRecord();
                row.setOwnerUserId(3L);
                row.setUserId(8L);
                row.setRole("member");
                row.setStatus("left");
                return row;
            }
        });
        BusinessException denied = assertThrows(BusinessException.class, () -> access.requireActiveMember(12L));
        assertEquals(403, denied.getStatus());
        assertEquals("not a project member", denied.getMessage());
    }

    @Test
    void nonOwnerRemoveDoesNotRevealTheTarget() {
        login(8L);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> inTransaction(() -> service.remove(12L, 4L)));
        assertEquals(403, ex.getStatus());
        assertEquals("only owner can remove member", ex.getMessage());
        assertEquals(0, store.targetLocks);
        assertEquals("left", store.committed.statusOf(4L));
        assertEquals(0, store.notices);
    }

    @Test
    void removeMissingMemberDoesNotSendANotice() {
        login(3L);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> inTransaction(() -> service.remove(12L, 100L)));
        assertEquals(404, ex.getStatus());
        assertEquals("member not found", ex.getMessage());
        assertEquals(0, store.notices);
    }

    @Test
    void repeatedRemovalDoesNotSendAnotherNotice() {
        login(3L);
        Map<String, Object> first = inTransaction(() -> service.remove(12L, 8L));
        assertEquals(Boolean.FALSE, first.get("alreadyInactive"));
        assertEquals("removed", store.committed.statusOf(8L));
        assertEquals(1, store.notices);
        Map<String, Object> second = inTransaction(() -> service.remove(12L, 8L));
        assertEquals(Boolean.TRUE, second.get("alreadyInactive"));
        assertEquals(1, store.notices);
    }

    @Test
    void ownerCannotRemoveSelf() {
        login(3L);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> inTransaction(() -> service.remove(12L, 3L)));
        assertEquals(400, ex.getStatus());
        assertEquals("cannot remove owner", ex.getMessage());
        assertEquals("owner", store.committed.roleOf(3L));
        assertEquals(0, store.notices);
    }

    @Test
    void removalAndNoticeCommitTogetherOrNotAtAll() {
        login(3L);
        store.noticeFailure = new RuntimeException("user_message is missing");
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> inTransaction(() -> service.remove(12L, 8L)));
        assertTrue(ex.getMessage().contains("user_message"));
        assertEquals("active", store.committed.statusOf(8L));
        assertEquals("member", store.committed.roleOf(8L));
        assertEquals(0, store.notices);
    }

    private Object transferAs(long userId, long targetUserId) {
        login(userId);
        try {
            return inTransaction(() -> service.transfer(12L, target(targetUserId)));
        } catch (BusinessException ex) {
            return ex;
        } finally {
            AuthContext.clear();
        }
    }

    private Object await(Future<Object> future) throws Exception {
        try {
            return future.get(5, TimeUnit.SECONDS);
        } catch (ExecutionException ex) {
            if (ex.getCause() instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw ex;
        }
    }

    private <T> T inTransaction(Tx<T> call) {
        try {
            T result = call.run();
            store.commit();
            return result;
        } catch (RuntimeException ex) {
            store.rollback();
            throw ex;
        }
    }

    private static TransferOwnerRequest target(long userId) {
        TransferOwnerRequest request = new TransferOwnerRequest();
        request.setTargetUserId(userId);
        return request;
    }

    @Test
    void memberCanRenameSelfButNotAnotherMember() {
        login(8L);
        Map<String, Object> body = inTransaction(() -> service.rename(12L, 8L, "  老徐  "));
        assertEquals("老徐", body.get("nickName"));
        assertEquals(8L, store.renamedUserId);
        BusinessException ex = assertThrows(BusinessException.class, () -> inTransaction(() -> service.rename(12L, 9L, "别人")));
        assertEquals(403, ex.getStatus());
        assertEquals("only owner can rename other members", ex.getMessage());
    }

    @Test
    void ownerCanRenameAnotherMember() {
        login(3L);
        Map<String, Object> body = inTransaction(() -> service.rename(12L, 8L, "现场老徐"));
        assertEquals("现场老徐", body.get("nickName"));
        assertEquals(8L, store.renamedUserId);
    }

    @Test
    void blankMemberNameIsRejected() {
        login(8L);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> service.rename(12L, 8L, " "));
        assertEquals("member name is required", ex.getMessage());
        assertEquals(0L, store.renamedUserId);
    }

    private static void login(long userId) {
        User user = new User();
        user.setId(userId);
        AuthContext.setUser(user);
    }

    private interface Tx<T> {
        T run();
    }

    private static final class MemberStore {
        private final java.util.concurrent.locks.ReentrantLock lock = new java.util.concurrent.locks.ReentrantLock();
        private State committed = State.seed();
        private State working = committed.copy();
        private State snapshot = committed.copy();
        private boolean open;
        private final List<String> calls = new ArrayList<>();
        private boolean failPromote;
        private RuntimeException noticeFailure;
        private int notices;
        private int targetLocks;
        private long renamedUserId;
        private String renamedNick;

        private void begin() {
            lock.lock();
            if (!open) {
                snapshot = committed.copy();
                working = committed.copy();
                open = true;
                calls.add("lock-project");
            }
        }

        private void commit() {
            if (open) {
                committed = working.copy();
                open = false;
            }
            unlock();
        }

        private void rollback() {
            if (open) {
                working = snapshot.copy();
                committed = snapshot.copy();
                open = false;
            }
            unlock();
        }

        private void unlock() {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    private static final class State {
        private long ownerUserId;
        private String projectName;
        private final List<Member> members = new ArrayList<>();

        private static State seed() {
            State state = new State();
            state.ownerUserId = 3L;
            state.projectName = "深圳项目";
            state.members.add(member(1L, 3L, "owner", "active"));
            state.members.add(member(2L, 8L, "member", "active"));
            state.members.add(member(3L, 9L, "member", "active"));
            state.members.add(member(4L, 4L, "member", "left"));
            return state;
        }

        private State copy() {
            State copy = new State();
            copy.ownerUserId = ownerUserId;
            copy.projectName = projectName;
            for (Member member : members) {
                copy.members.add(member.copy());
            }
            return copy;
        }

        private Member find(long userId) {
            for (Member member : members) {
                if (member.userId == userId) {
                    return member;
                }
            }
            return null;
        }

        private String roleOf(long userId) {
            return find(userId).role;
        }

        private String statusOf(long userId) {
            return find(userId).status;
        }

        private Long removedBy(long userId) {
            return find(userId).removedBy;
        }

        private int activeOwnerCount() {
            int count = 0;
            for (Member member : members) {
                if ("owner".equals(member.role) && "active".equals(member.status)) {
                    count++;
                }
            }
            return count;
        }

        private static Member member(long id, long userId, String role, String status) {
            Member member = new Member();
            member.id = id;
            member.userId = userId;
            member.role = role;
            member.status = status;
            return member;
        }
    }

    private static final class Member {
        private long id;
        private long userId;
        private String role;
        private String status;
        private Long removedBy;

        private Member copy() {
            Member copy = new Member();
            copy.id = id;
            copy.userId = userId;
            copy.role = role;
            copy.status = status;
            copy.removedBy = removedBy;
            return copy;
        }
    }

    private static final class StoreProjectMapper implements ProjectMapper {
        private final MemberStore store;

        private StoreProjectMapper(MemberStore store) {
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
            store.begin();
            if (id != 12L) {
                return null;
            }
            Project project = new Project();
            project.setId(12L);
            project.setName(store.working.projectName);
            project.setOwnerUserId(store.working.ownerUserId);
            project.setStatus("active");
            return project;
        }

        @Override
        public int updateOwner(long projectId, long previousOwnerUserId, long newOwnerUserId, LocalDateTime updatedAt) {
            if (store.working.ownerUserId != previousOwnerUserId) {
                return 0;
            }
            store.working.ownerUserId = newOwnerUserId;
            return 1;
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

    private static final class StoreMemberMapper implements ProjectMemberMapper {
        private final MemberStore store;

        private StoreMemberMapper(MemberStore store) {
            this.store = store;
        }

        @Override
        public int countProject(long projectId) {
            return 0;
        }

        @Override
        public ProjectAccessRecord findMembership(long projectId, long userId) {
            Member member = store.working.find(userId);
            if (member == null) {
                return null;
            }
            ProjectAccessRecord row = new ProjectAccessRecord();
            row.setOwnerUserId(store.working.ownerUserId);
            row.setUserId(member.userId);
            row.setRole(member.role);
            row.setStatus(member.status);
            return row;
        }

        @Override
        public int insertOwner(long projectId, long userId, LocalDateTime joinedAt) {
            return 0;
        }

        @Override
        public List<ProjectMemberRow> lockByUserIds(long projectId, long firstUserId, long secondUserId) {
            if (!store.lock.isHeldByCurrentThread()) {
                throw new IllegalStateException("members locked before project");
            }
            store.calls.add("lock-members");
            List<ProjectMemberRow> rows = new ArrayList<>();
            addLocked(rows, firstUserId);
            if (secondUserId != firstUserId) {
                addLocked(rows, secondUserId);
            }
            rows.sort((left, right) -> Long.compare(left.getId(), right.getId()));
            return rows;
        }

        @Override
        public ProjectMemberRow lockByUserId(long projectId, long userId) {
            store.targetLocks++;
            Member member = store.working.find(userId);
            return member == null ? null : toRow(member);
        }

        @Override
        public int countActiveOwners(long projectId) {
            return store.working.activeOwnerCount();
        }

        @Override
        public int demoteOwner(long id, long userId) {
            Member member = store.working.find(userId);
            if (member == null || member.id != id || !"owner".equals(member.role) || !"active".equals(member.status)) {
                return 0;
            }
            member.role = "member";
            return 1;
        }

        @Override
        public int promoteMember(long id, long userId) {
            if (store.failPromote) {
                return 0;
            }
            Member member = store.working.find(userId);
            if (member == null || member.id != id || !"member".equals(member.role) || !"active".equals(member.status)) {
                return 0;
            }
            member.role = "owner";
            return 1;
        }

        @Override
        public int markRemoved(long id, long removedByUserId, LocalDateTime changedAt) {
            for (Member member : store.working.members) {
                if (member.id == id && "member".equals(member.role) && "active".equals(member.status)) {
                    member.status = "removed";
                    member.removedBy = removedByUserId;
                    return 1;
                }
            }
            return 0;
        }

        @Override
        public int markLeft(long id, LocalDateTime changedAt) {
            for (Member member : store.working.members) {
                if (member.id == id && "member".equals(member.role) && "active".equals(member.status)) {
                    member.status = "left";
                    member.removedBy = null;
                    return 1;
                }
            }
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
            return List.of();
        }

        @Override
        public int updateNickName(long userId, String nickName) {
            Member member = store.working.find(userId);
            if (member == null || !"active".equals(member.status)) {
                return 0;
            }
            store.renamedUserId = userId;
            store.renamedNick = nickName;
            return 1;
        }

        private void addLocked(List<ProjectMemberRow> rows, long userId) {
            Member member = store.working.find(userId);
            if (member != null) {
                rows.add(toRow(member));
            }
        }

        private static ProjectMemberRow toRow(Member member) {
            ProjectMemberRow row = new ProjectMemberRow();
            row.setId(member.id);
            row.setUserId(member.userId);
            row.setRole(member.role);
            row.setStatus(member.status);
            row.setRemovedByUserId(member.removedBy);
            return row;
        }
    }

    private static final class StoreMessageMapper implements UserMessageMapper {
        private final MemberStore store;

        private StoreMessageMapper(MemberStore store) {
            this.store = store;
        }

        @Override
        public int insertRemovalNotice(long userId, long projectId, String title, String body, LocalDateTime createdAt) {
            if (store.noticeFailure != null) {
                throw store.noticeFailure;
            }
            if (!title.equals(store.working.projectName) || !body.equals(store.working.projectName)) {
                return 0;
            }
            store.notices++;
            return 1;
        }

        @Override
        public int insertInvite(UserMessage message) {
            return 0;
        }

        @Override
        public UserMessage findByUserAndInvite(long userId, long inviteId) {
            return null;
        }

        @Override
        public java.util.List<UserMessageView> listForUser(long userId, int limit, long offset) {
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
            return 0;
        }
    }
}
