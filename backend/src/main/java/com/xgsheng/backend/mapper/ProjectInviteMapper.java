package com.xgsheng.backend.mapper;

import com.xgsheng.backend.entity.ProjectInvite;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ProjectInviteMapper {

    int insert(ProjectInvite invite);

    ProjectInvite findDetailByTokenHash(@Param("tokenHash") String tokenHash);

    ProjectInvite lockByTokenHash(@Param("tokenHash") String tokenHash);
}
