package com.xgsheng.backend.entity;

import java.time.LocalDateTime;

public class UserMessageView extends UserMessage {

    private String inviteStatus;
    private LocalDateTime inviteExpiresAt;
    private String myDecision;

    public String getInviteStatus() {
        return inviteStatus;
    }

    public void setInviteStatus(String inviteStatus) {
        this.inviteStatus = inviteStatus;
    }

    public LocalDateTime getInviteExpiresAt() {
        return inviteExpiresAt;
    }

    public void setInviteExpiresAt(LocalDateTime inviteExpiresAt) {
        this.inviteExpiresAt = inviteExpiresAt;
    }

    public String getMyDecision() {
        return myDecision;
    }

    public void setMyDecision(String myDecision) {
        this.myDecision = myDecision;
    }
}
