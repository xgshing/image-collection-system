package com.xgsheng.backend.mapper;

import com.xgsheng.backend.entity.Project;
import com.xgsheng.backend.entity.ProjectSummaryRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 项目表读写。这里的语句不查询 project_photo。
 */
@Mapper
public interface ProjectMapper {

    int insert(Project project);

    Project findById(@Param("id") long id);

    Project lockById(@Param("id") long id);

    int updateOwner(@Param("projectId") long projectId,
                    @Param("previousOwnerUserId") long previousOwnerUserId,
                    @Param("newOwnerUserId") long newOwnerUserId,
                    @Param("updatedAt") java.time.LocalDateTime updatedAt);

    int updateActive(Project project);

    int archiveActive(@Param("projectId") long projectId,
                      @Param("ownerUserId") long ownerUserId,
                      @Param("updatedAt") java.time.LocalDateTime updatedAt);

    int deleteMessagesForProject(@Param("projectId") long projectId);

    int deleteAcceptancesForProject(@Param("projectId") long projectId);

    int deleteInvitesForProject(@Param("projectId") long projectId);

    int deletePhotosForProject(@Param("projectId") long projectId);

    int deleteMembersForProject(@Param("projectId") long projectId);

    int deleteArchived(@Param("projectId") long projectId,
                       @Param("ownerUserId") long ownerUserId);

    List<ProjectSummaryRow> listActiveForUser(@Param("userId") long userId,
                                              @Param("limit") int limit,
                                              @Param("offset") long offset);

    long countActiveForUser(@Param("userId") long userId);

    ProjectSummaryRow findActiveDetail(@Param("projectId") long projectId,
                                       @Param("userId") long userId);
}
