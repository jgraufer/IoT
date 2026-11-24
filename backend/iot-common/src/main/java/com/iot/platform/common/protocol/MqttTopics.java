package com.iot.platform.common.protocol;

import java.util.UUID;

public final class MqttTopics {

    private MqttTopics() {}

    public static final String BOOTSTRAP_REGISTER = "edge/bootstrap/register";

    public static final String DEVICE_EVENTS_WILDCARD = "edge/+/devices/events";

    public static final String TELEMETRY_WILDCARD = "edge/+/telemetry";

    public static final String DEVICE_TELEMETRY_WILDCARD = "devices/+/telemetry";

    public static String bootstrapRegistered(UUID edgeId) {
        return "cloud/bootstrap/" + edgeId + "/registered";
    }

    public static String userSync(UUID edgeId) {
        return "cloud/" + edgeId + "/sync/users";
    }

    public static String commands(UUID edgeId) {
        return "cloud/" + edgeId + "/commands";
    }

    public static String telemetry(UUID edgeId) {
        return "edge/" + edgeId + "/telemetry";
    }

    public static String deviceEvents(UUID edgeId) {
        return "edge/" + edgeId + "/devices/events";
    }

    public static String deviceTelemetry(UUID deviceId) {
        return "devices/" + deviceId + "/telemetry";
    }

    public static String deviceCommand(UUID deviceId) {
        return "devices/" + deviceId + "/commands";
    }
}
