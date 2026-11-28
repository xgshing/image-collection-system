package com.xgsheng.backend.service;

import com.xgsheng.backend.auth.AuthContext;
import com.xgsheng.backend.entity.UserMessage;
import com.xgsheng.backend.entity.UserMessageView;
import com.xgsheng.backend.exception.BusinessException;
import com.xgsheng.backend.mapper.UserMessageMapper;
import com.xgsheng.backend.project.InviteRules;
import com.xgsheng.backend.project.ProjectPage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class MessageQueryService {

    private static final Logger log = LoggerFactory.getLogger(MessageQueryService.class);

    private final UserMessageMapper userMessageMapper;
    private final Clock clock;

    @Autowired
    public MessageQueryService(UserMessageMapper userMessageMapper) {
        this(userMessageMapper, Clock.systemUTC());
    }

    MessageQueryService(UserMessageMapper userMessageMapper, Clock clock) {
        this.userMessageMapper = userMessageMapper;
        this.clock = clock;
    }

    public Map<String, Object> list(Integer page, Integer pageSize) {
        long userId = AuthContext.requireUserId();
        ProjectPage resolved = ProjectPage.resolve(page, pageSize);
        LocalDateTime now = now();
        List<Map<String, Object>> items = new ArrayList<>();
        for (UserMessageView row : userMessageMapper.listForUser(userId, resolved.getPageSize(), resolved.offset())) {
            items.add(toItem(row, now));
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("items", items);
        body.put("page", resolved.getPage());
        body.put("pageSize", resolved.getPageSize());
        body.put("total", userMessageMapper.countForUser(userId));
        return body;
    }

    public Map<String, Object> unreadCount() {
        long userId = AuthContext.requireUserId();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("count", userMessageMapper.countUnread(userId));
        return body;
    }

    @Transactional
    public Map<String, Object> markRead(long messageId) {
        long userId = AuthContext.requireUserId();
        UserMessage message = userMessageMapper.findOwn(messageId, userId);
        if (message == null) {
            throw BusinessException.notFound("message not found");
        }
        if (message.getReadAt() == null) {
            int rows = userMessageMapper.markReadIfUnread(messageId, userId, now());
            if (rows != 1) {
                UserMessage again = userMessageMapper.findOwn(messageId, userId);
                if (again == null || again.getReadAt() == null) {
                    log.error("Message read update affected {} rows, messageId={}", rows, messageId);
                    throw new RuntimeException("message read state is inconsistent");
                }
            }
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", messageId);
        body.put("read", true);
        return body;
    }

    private Map<String, Object> toItem(UserMessageView row, LocalDateTime now) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", row.getId());
        item.put("type", row.getType());
        item.put("title", row.getTitle());
        item.put("body", row.getBody());
        item.put("projectId", row.getProjectId());
        item.put("inviteId", row.getInviteId());
        item.put("read", row.getReadAt() != null);
        item.put("inviteStatus", row.getInviteId() == null
                ? null
                : InviteRules.displayStatus(row.getInviteStatus(), row.getInviteExpiresAt(), now));
        item.put("myDecision", row.getMyDecision());
        item.put("createdAt", ProjectResponses.formatTime(row.getCreatedAt()));
        return item;
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }
}
