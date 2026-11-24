package com.iot.platform.common.device;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeviceDTO {

    private UUID id;

    private String name;

    private DeviceType type;

    private List<CapabilityType> capabilities;
}
