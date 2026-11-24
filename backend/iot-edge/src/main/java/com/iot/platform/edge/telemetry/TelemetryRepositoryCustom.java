package com.iot.platform.edge.telemetry;

import com.iot.platform.common.device.CapabilityType;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

interface TelemetryRepositoryCustom {

    List<Telemetry> search(UUID deviceId, CapabilityType capability,
                           Instant since, Instant until, int limit);
}
