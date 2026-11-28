package com.xgsheng.backend.mapper;

import com.xgsheng.backend.entity.UserMessage;
import com.xgsheng.backend.entity.UserMessageView;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 成员移除通知和邀请消息。移除通知的 invite_id 保持 NULL，不绕过外键。
 */
@Mapper
public interface UserMessageMapper {

    int insertRemovalNotice(@Param("userId") long userId,
                            @Param("projectId") long projectId,
                            @Param("title") String title,
                            @Param("body") String body,
                            @Param("createdAt") LocalDateTime createdAt);

    int insertInvite(UserMessage message);

    UserMessage findByUserAndInvite(@Param("userId") long userId, @Param("inviteId") long inviteId);

    List<UserMessageView> listForUser(@Param("userId") long userId,
                                      @Param("limit") int limit,
                                      @Param("offset") long offset);

    long countForUser(@Param("userId") long userId);

    long countUnread(@Param("userId") long userId);

    UserMessage findOwn(@Param("id") long id, @Param("userId") long userId);

    int markReadIfUnread(@Param("id") long id,
                         @Param("userId") long userId,
                         @Param("readAt") LocalDateTime readAt);
}
