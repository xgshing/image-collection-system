package com.xgsheng.backend.service;

import com.xgsheng.backend.access.ProjectAccessLookup;
import com.xgsheng.backend.access.ProjectAccessRecord;
import com.xgsheng.backend.mapper.ProjectMemberMapper;
import org.springframework.stereotype.Component;

/**
 * Mapper 适配器。只有被调用时才会执行 SQL。项目表创建前，调用方不得使用本类。
 */
@Component
public class MapperProjectAccessLookup implements ProjectAccessLookup {

    private final ProjectMemberMapper projectMemberMapper;

    public MapperProjectAccessLookup(ProjectMemberMapper projectMemberMapper) {
        this.projectMemberMapper = projectMemberMapper;
    }

    @Override
    public boolean projectExists(long projectId) {
        return projectMemberMapper.countProject(projectId) > 0;
    }

    @Override
    public ProjectAccessRecord findMembership(long projectId, long userId) {
        return projectMemberMapper.findMembership(projectId, userId);
    }
}
