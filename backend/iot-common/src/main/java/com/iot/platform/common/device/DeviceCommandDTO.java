package com.iot.platform.common.device;

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
public class DeviceCommandDTO {

    private UUID commandId;

    private CapabilityType capability;

    private Object value;

    private Instant issuedAt;
}
