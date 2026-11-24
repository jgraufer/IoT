package com.iot.platform.cloud.exception;

public class AlreadyHasAccessException extends RuntimeException {

    public AlreadyHasAccessException() {
        super("User already has access to this edge");
    }
}
