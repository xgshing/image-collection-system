package com.xgsheng.backend.mapper;

import com.xgsheng.backend.entity.ProjectPhoto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 项目照片。查询失败必须抛给调用方，不能把缺表或 SQL 错误收成 0。
 */
@Mapper
public interface ProjectPhotoMapper {

    int countByProjectId(@Param("projectId") long projectId);

    int countByImageId(@Param("imageId") long imageId);

    ProjectPhoto findByIdInProject(@Param("projectId") long projectId, @Param("photoId") long photoId);

    ProjectPhoto findByImageId(@Param("imageId") long imageId);

    ProjectPhoto findByPhotographerAndRequest(@Param("photographerUserId") long photographerUserId,
                                              @Param("clientRequestId") String clientRequestId);

    List<ProjectPhoto> listByProject(@Param("projectId") long projectId,
                                     @Param("limit") int limit,
                                     @Param("offset") long offset);

    int insert(ProjectPhoto photo);

    int updateEditable(@Param("id") long id,
                       @Param("projectId") long projectId,
                       @Param("photographerUserId") long photographerUserId,
                       @Param("note") String note,
                       @Param("watermarkPosition") String watermarkPosition,
                       @Param("watermarkVisible") int watermarkVisible,
                       @Param("updatedAt") LocalDateTime updatedAt);

    int deleteByIdInProject(@Param("projectId") long projectId, @Param("photoId") long photoId);
}
