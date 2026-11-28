package com.xgsheng.backend.access;

/**
 * 项目权限所需的只读查找。实现可以来自 Mapper，单元测试使用内存替身。
 * 本接口本身不连接数据库。
 */
public interface ProjectAccessLookup {

    boolean projectExists(long projectId);

    ProjectAccessRecord findMembership(long projectId, long userId);
}
