package com.iot.platform.cloud.telemetry.dto;

import com.iot.platform.common.device.CapabilityType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CapabilityStateDTO {

    private CapabilityType capability;
    private Object value;
    private Instant timestamp;
}
