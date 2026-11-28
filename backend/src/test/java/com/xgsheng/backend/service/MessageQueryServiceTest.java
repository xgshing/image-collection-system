package com.xgsheng.backend.service;

import com.xgsheng.backend.auth.AuthContext;
import com.xgsheng.backend.entity.User;
import com.xgsheng.backend.entity.UserMessage;
import com.xgsheng.backend.entity.UserMessageView;
import com.xgsheng.backend.exception.BusinessException;
import com.xgsheng.backend.exception.PendingProductRuleException;
import com.xgsheng.backend.mapper.UserMessageMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MessageQueryServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.ofInstant(Instant.parse("2026-11-27T05:00:00Z"), ZoneOffset.UTC);

    private final Box box = new Box();
    private final MessageQueryService service = new MessageQueryService(
            new Gateway(box), Clock.fixed(NOW.toInstant(ZoneOffset.UTC), ZoneOffset.UTC));

    @AfterEach
    void clearAuth() {
        AuthContext.clear();
    }

    @Test
    void listShowsOnlyTheCurrentUserAndDoesNotMarkRead() {
        box.rows.add(view(90L, 9L, 40L, null, "active", NOW.plusDays(1), null));
        box.rows.add(view(91L, 8L, null, null, null, null, null));
        login(9L);
        Map<String, Object> body = service.list(1, 20);
        List<?> items = (List<?>) body.get("items");
        assertEquals(1, items.size());
        Map<?, ?> item = (Map<?, ?>) items.get(0);
        assertEquals(90L, item.get("id"));
        assertEquals(12L, item.get("projectId"));
        assertEquals(40L, item.get("inviteId"));
        assertEquals(Boolean.FALSE, item.get("read"));
        assertEquals("active", item.get("inviteStatus"));
        assertNull(item.get("myDecision"));
        assertEquals(0, box.readWrites);
        assertNull(box.rows.get(0).getReadAt());
    }

    @Test
    void expiredInviteStatusDoesNotChangeTheStoredMessage() {
        box.rows.add(view(90L, 9L, 40L, null, "active", NOW.minusSeconds(1), "rejected"));
        login(9L);
        Map<?, ?> item = (Map<?, ?>) ((List<?>) service.list(null, null).get("items")).get(0);
        assertEquals("expired", item.get("inviteStatus"));
        assertEquals("rejected", item.get("myDecision"));
        assertEquals("active", box.rows.get(0).getInviteStatus());
    }

    @Test
    void unreadCountIgnoresReadMessages() {
        box.rows.add(view(90L, 9L, 40L, null, "active", NOW.plusDays(1), null));
        box.rows.add(view(91L, 9L, null, NOW, null, null, null));
        login(9L);
        assertEquals(1L, service.unreadCount().get("count"));
    }

    @Test
    void markReadIsIdempotentAndDoesNotTouchAnotherUsersMessage() {
        box.rows.add(view(90L, 9L, 40L, null, "active", NOW.plusDays(1), null));
        login(8L);
        BusinessException missing = assertThrows(BusinessException.class, () -> service.markRead(90L));
        assertEquals(404, missing.getStatus());
        assertEquals(0, box.readWrites);
        login(9L);
        assertEquals(Boolean.TRUE, service.markRead(90L).get("read"));
        LocalDateTime readAt = box.rows.get(0).getReadAt();
        assertEquals(NOW, readAt);
        assertEquals(Boolean.TRUE, service.markRead(90L).get("read"));
        assertEquals(readAt, box.rows.get(0).getReadAt());
        assertEquals(1, box.readWrites);
    }

    @Test
    void outOfRangePageStaysUndecided() {
        login(9L);
        assertThrows(PendingProductRuleException.class, () -> service.list(0, 20));
    }

    private static void login(long userId) {
        User user = new User();
        user.setId(userId);
        AuthContext.setUser(user);
    }

    private static UserMessageView view(long id, long userId, Long inviteId, LocalDateTime readAt,
                                        String inviteStatus, LocalDateTime expiresAt, String decision) {
        UserMessageView view = new UserMessageView();
        view.setId(id);
        view.setUserId(userId);
        view.setType(inviteId == null ? "project_member_removed" : "project_invite");
        view.setTitle("标题");
        view.setBody("深圳技术大学装修工程");
        view.setProjectId(12L);
        view.setInviteId(inviteId);
        view.setReadAt(readAt);
        view.setCreatedAt(NOW);
        view.setInviteStatus(inviteStatus);
        view.setInviteExpiresAt(expiresAt);
        view.setMyDecision(decision);
        return view;
    }

    private static final class Box {
        private final List<UserMessageView> rows = new ArrayList<>();
        private int readWrites;
    }

    private static final class Gateway implements UserMessageMapper {
        private final Box box;

        private Gateway(Box box) {
            this.box = box;
        }

        @Override
        public int insertRemovalNotice(long userId, long projectId, String title, String body, LocalDateTime createdAt) {
            return 0;
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
        public List<UserMessageView> listForUser(long userId, int limit, long offset) {
            List<UserMessageView> matched = new ArrayList<>();
            for (UserMessageView row : box.rows) {
                if (row.getUserId() == userId) {
                    matched.add(row);
                }
            }
            int from = (int) Math.min(offset, matched.size());
            int to = Math.min(matched.size(), from + limit);
            return matched.subList(from, to);
        }

        @Override
        public long countForUser(long userId) {
            return listForUser(userId, Integer.MAX_VALUE, 0).size();
        }

        @Override
        public long countUnread(long userId) {
            long count = 0;
            for (UserMessageView row : box.rows) {
                if (row.getUserId() == userId && row.getReadAt() == null) {
                    count++;
                }
            }
            return count;
        }

        @Override
        public UserMessage findOwn(long id, long userId) {
            for (UserMessageView row : box.rows) {
                if (row.getId() == id && row.getUserId() == userId) {
                    return row;
                }
            }
            return null;
        }

        @Override
        public int markReadIfUnread(long id, long userId, LocalDateTime readAt) {
            UserMessage row = findOwn(id, userId);
            if (row == null || row.getReadAt() != null) {
                return 0;
            }
            row.setReadAt(readAt);
            box.readWrites++;
            return 1;
        }
    }
}
