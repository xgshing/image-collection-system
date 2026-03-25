package com.xgsheng.backend.mapper;

import com.xgsheng.backend.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface UserMapper {

    User findByOpenId(@Param("openId") String openId);

    User findById(@Param("id") Long id);

    void insert(User user);

    void updateProfileById(@Param("id") Long id,
                           @Param("nickName") String nickName,
                           @Param("avatarUrl") String avatarUrl);
}