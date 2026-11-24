package com.iot.platform.cloud.telemetry;

import com.iot.platform.common.device.CapabilityType;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

interface TelemetryRepositoryCustom {

    List<Telemetry> search(UUID edgeId, UUID deviceId, CapabilityType capability,
                           Instant since, Instant until, int limit);
}
