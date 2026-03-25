package com.xgsheng.backend.mapper;

import com.xgsheng.backend.entity.UserSession;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Date;

@Mapper
public interface UserSessionMapper {

    void insert(UserSession userSession);

    UserSession findBySessionToken(@Param("sessionToken") String sessionToken);

    void deleteExpired(@Param("now") Date now);

    void deleteBySessionToken(@Param("sessionToken") String sessionToken);
}