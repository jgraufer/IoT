package com.iot.platform.cloud.exception;

import com.iot.platform.common.device.CapabilityType;

public class UnsupportedCapabilityException extends RuntimeException {

    public UnsupportedCapabilityException(CapabilityType capability) {
        super("La agregación de telemetría no está soportada para la capability '"
                + capability + "' (no numérica).");
    }
}
