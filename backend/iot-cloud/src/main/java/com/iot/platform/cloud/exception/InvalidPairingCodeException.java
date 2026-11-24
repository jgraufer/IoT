package com.iot.platform.cloud.exception;

public class InvalidPairingCodeException extends RuntimeException {

    public static final String CODE = "PAIRING_CODE_INVALID";

    public InvalidPairingCodeException() {
        super("Invalid pairing code");
    }
}
