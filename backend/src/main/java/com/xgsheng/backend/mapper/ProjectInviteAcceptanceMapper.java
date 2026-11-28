package com.xgsheng.backend.mapper;

import com.xgsheng.backend.entity.ProjectInviteAcceptance;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

@Mapper
public interface ProjectInviteAcceptanceMapper {

    ProjectInviteAcceptance findByInviteAndUser(@Param("inviteId") long inviteId,
                                                @Param("userId") long userId);

    int insert(@Param("inviteId") long inviteId,
               @Param("userId") long userId,
               @Param("decision") String decision,
               @Param("decidedAt") LocalDateTime decidedAt);

    int updateRejectedToAccepted(@Param("id") long id,
                                 @Param("inviteId") long inviteId,
                                 @Param("userId") long userId,
                                 @Param("decidedAt") LocalDateTime decidedAt);
}
