package com.iot.platform.cloud.exception;

public class PairingCodeExpiredException extends RuntimeException {

    public static final String CODE = "PAIRING_CODE_EXPIRED";

    public PairingCodeExpiredException() {
        super("Pairing code expired");
    }
}
