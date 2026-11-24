package com.iot.platform.cloud.command.dto;

import com.iot.platform.common.device.CapabilityType;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IssueCommandRequestDTO {

    @NotNull(message = "capability is required")
    private CapabilityType capability;

    @NotNull(message = "value is required")
    private Object value;
}
