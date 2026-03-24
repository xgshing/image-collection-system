package com.xgsheng.backend.mapper;

import com.xgsheng.backend.entity.Image;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ImageMapper {

    void insert(Image image);

    Image findByMd5(@Param("md5") String md5);

    Image findById(@Param("id") Long id);

    List<Image> findByTag(@Param("tag") String tag);

    List<Image> findAll();

    List<String> findAllTagNames();

    List<String> findTagNamesByImageId(@Param("imageId") Long imageId);

    Long findTagIdByName(@Param("name") String name);

    void insertTag(@Param("name") String name);

    void insertImageTag(@Param("imageId") Long imageId, @Param("tagId") Long tagId);

    void deleteImageTagByImageId(@Param("imageId") Long imageId);

    void deleteById(@Param("id") Long id);
}
