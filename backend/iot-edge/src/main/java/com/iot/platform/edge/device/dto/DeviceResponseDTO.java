package com.iot.platform.edge.device.dto;

import com.iot.platform.common.device.CapabilityType;
import com.iot.platform.common.device.DeviceType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeviceResponseDTO {

    private UUID id;
    private String name;
    private DeviceType type;
    private List<CapabilityType> capabilities;
    private Instant createdAt;
    private Instant updatedAt;
}
