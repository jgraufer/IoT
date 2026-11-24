package com.iot.platform.edge.device.dto;

import com.iot.platform.common.device.CapabilityType;
import com.iot.platform.common.device.DeviceType;
import jakarta.validation.constraints.Pattern;
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
public class UpdateDeviceRequestDTO {

    @Size(min = 1, max = 128, message = "name must be 1-128 characters")
    @Pattern(regexp = ".*\\S.*", message = "name must not be blank")
    private String name;

    private DeviceType type;

    @Size(min = 1, message = "capabilities, if provided, must include at least one entry")
    private List<CapabilityType> capabilities;
}
