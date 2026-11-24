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
public class DeviceEventDTO {

    private DeviceEventType eventType;

    private UUID edgeId;

    private UUID deviceId;

    private DeviceDTO device;

    private Instant timestamp;
}
