package com.iot.platform.edge.telemetry.dto;

import com.iot.platform.common.device.CapabilityType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TelemetryResponseDTO {

    private UUID id;
    private UUID deviceId;
    private CapabilityType capabilityType;
    private Object value;
    private Instant timestamp;
}
