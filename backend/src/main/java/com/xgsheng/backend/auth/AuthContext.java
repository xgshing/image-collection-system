package com.xgsheng.backend.auth;

import com.xgsheng.backend.entity.User;

public class AuthContext {

    private static final ThreadLocal<User> HOLDER = new ThreadLocal<>();

    private AuthContext() {
    }

    public static void setUser(User user) {
        HOLDER.set(user);
    }

    public static User getUser() {
        return HOLDER.get();
    }

    public static User requireUser() {
        User user = HOLDER.get();
        if (user == null) {
            throw new IllegalStateException("unauthorized");
        }
        return user;
    }

    public static Long requireUserId() {
        return requireUser().getId();
    }

    public static void clear() {
        HOLDER.remove();
    }
}