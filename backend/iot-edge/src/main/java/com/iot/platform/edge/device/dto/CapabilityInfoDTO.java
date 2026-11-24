package com.iot.platform.edge.device.dto;

import com.iot.platform.common.device.CapabilityDataType;
import com.iot.platform.common.device.CapabilityKind;
import com.iot.platform.common.device.CapabilityType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CapabilityInfoDTO {

    private CapabilityType type;
    private CapabilityKind kind;
    private CapabilityDataType dataType;
    private String unit;
    private Integer min;
    private Integer max;
}
