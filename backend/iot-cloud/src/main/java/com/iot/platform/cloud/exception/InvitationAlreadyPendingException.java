package com.iot.platform.cloud.exception;

public class InvitationAlreadyPendingException extends RuntimeException {

    public InvitationAlreadyPendingException() {
        super("There is already a pending invitation for this user");
    }
}
