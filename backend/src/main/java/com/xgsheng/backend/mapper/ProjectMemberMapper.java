package com.xgsheng.backend.mapper;

import com.xgsheng.backend.access.ProjectAccessRecord;
import com.xgsheng.backend.entity.ProjectMemberRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 项目成员权限查询。表尚未创建时不得调用。
 */
@Mapper
public interface ProjectMemberMapper {

    int countProject(@Param("projectId") long projectId);

    ProjectAccessRecord findMembership(@Param("projectId") long projectId,
                                       @Param("userId") long userId);

    int insertOwner(@Param("projectId") long projectId,
                    @Param("userId") long userId,
                    @Param("joinedAt") LocalDateTime joinedAt);

    List<ProjectMemberRow> lockByUserIds(@Param("projectId") long projectId,
                                         @Param("firstUserId") long firstUserId,
                                         @Param("secondUserId") long secondUserId);

    ProjectMemberRow lockByUserId(@Param("projectId") long projectId,
                                  @Param("userId") long userId);

    int countActiveOwners(@Param("projectId") long projectId);

    int demoteOwner(@Param("id") long id, @Param("userId") long userId);

    int promoteMember(@Param("id") long id, @Param("userId") long userId);

    int markRemoved(@Param("id") long id,
                    @Param("removedByUserId") long removedByUserId,
                    @Param("changedAt") LocalDateTime changedAt);

    int markLeft(@Param("id") long id, @Param("changedAt") LocalDateTime changedAt);

    int insertMember(@Param("projectId") long projectId,
                     @Param("userId") long userId,
                     @Param("joinedAt") LocalDateTime joinedAt);

    int reactivateMember(@Param("id") long id,
                         @Param("userId") long userId,
                         @Param("changedAt") LocalDateTime changedAt);

    List<ProjectMemberRow> listActive(@Param("projectId") long projectId);

    int updateNickName(@Param("userId") long userId, @Param("nickName") String nickName);
}
