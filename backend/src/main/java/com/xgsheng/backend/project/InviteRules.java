package com.xgsheng.backend.project;

import com.xgsheng.backend.entity.ProjectInvite;
import com.xgsheng.backend.exception.BusinessException;

import java.time.LocalDateTime;

public final class InviteRules {

    public static final String NOT_FOUND = "invite not found";
    public static final String REVOKED = "invite revoked";
    public static final String EXPIRED = "invite expired";
    public static final String ALREADY_ACCEPTED = "invite already accepted";
    public static final String ARCHIVED_PREFIX = "archived project invite is not confirmed, projectId=";
    static final int TITLE_MAX = 255;

    private InviteRules() {
    }

    public static String displayStatus(String storedStatus, LocalDateTime expiresAt, LocalDateTime now) {
        if ("revoked".equals(storedStatus)) {
            return "revoked";
        }
        if ("expired".equals(storedStatus) || isPastExpiry(storedStatus, expiresAt, now)) {
            return "expired";
        }
        return storedStatus;
    }

    public static void rejectIfClosed(ProjectInvite invite, LocalDateTime now) {
        if ("revoked".equals(invite.getStatus())) {
            throw BusinessException.badRequest(REVOKED);
        }
        if ("expired".equals(invite.getStatus()) || isPastExpiry(invite.getStatus(), invite.getExpiresAt(), now)) {
            throw BusinessException.badRequest(EXPIRED);
        }
        if (!"active".equals(invite.getStatus())) {
            throw new RuntimeException("invite status is inconsistent");
        }
    }

    public static String inviteTitle(String nickName) {
        String title = nickName == null || nickName.isBlank() ? "邀请你加入项目" : nickName.trim() + "邀请你加入项目";
        if (title.length() <= TITLE_MAX) {
            return title;
        }
        return title.substring(0, TITLE_MAX);
    }

    private static boolean isPastExpiry(String storedStatus, LocalDateTime expiresAt, LocalDateTime now) {
        return "active".equals(storedStatus) && (expiresAt == null || !expiresAt.isAfter(now));
    }
}
