package com.iot.platform.edge.device.dto;

import com.iot.platform.common.device.CapabilityType;
import com.iot.platform.common.device.DeviceType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateDeviceRequestDTO {

    @NotBlank(message = "name is required")
    @Size(max = 128, message = "name must be at most 128 characters")
    private String name;

    @NotNull(message = "type is required")
    private DeviceType type;

    @NotEmpty(message = "capabilities must include at least one entry")
    private List<CapabilityType> capabilities;
}
