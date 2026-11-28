package com.xgsheng.backend.mapper;

import com.xgsheng.backend.entity.Image;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ImageMapper {

    void insert(Image image);

    Image findByMd5(@Param("userId") Long userId, @Param("md5") String md5);

    Image findById(@Param("userId") Long userId, @Param("id") Long id);

    Image lockByOwner(@Param("userId") Long userId, @Param("id") Long id);

    List<Image> findByTag(@Param("userId") Long userId, @Param("tag") String tag);

    List<Image> findByTags(@Param("userId") Long userId, @Param("tags") List<String> tags);

    List<Image> findAll(@Param("userId") Long userId);

    List<String> findAllTagNames(@Param("userId") Long userId);

    List<String> findRecentTagNames(@Param("userId") Long userId);

    List<String> findTagNamesByImageId(@Param("imageId") Long imageId);

    Long findTagIdByName(@Param("name") String name);

    void insertTag(@Param("name") String name);

    void insertImageTag(@Param("imageId") Long imageId, @Param("tagId") Long tagId);

    void deleteImageTagByImageId(@Param("imageId") Long imageId);

    void deleteImageTagByUserIdAndTagId(@Param("userId") Long userId, @Param("tagId") Long tagId);

    void insertImageTagForUserImagesByTagId(@Param("userId") Long userId,
                                            @Param("sourceTagId") Long sourceTagId,
                                            @Param("targetTagId") Long targetTagId);

    void deleteById(@Param("userId") Long userId, @Param("id") Long id);

    void updateCreateTime(@Param("userId") Long userId,
                          @Param("id") Long id,
                          @Param("createTime") java.util.Date createTime);

    void updateOrigin(@Param("userId") Long userId,
                      @Param("id") Long id,
                      @Param("origin") String origin);

    /** 锁定水印相机原图行（origin=project），删除项目照片时同步清理。 */
    Image lockProjectOriginById(@Param("id") Long id);

    void deleteProjectOriginImageById(@Param("id") Long id);
}
