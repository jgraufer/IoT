package com.iot.platform.cloud.device;

import com.iot.platform.cloud.device.dto.CapabilityInfoDTO;
import com.iot.platform.common.device.CapabilityType;
import com.iot.platform.common.shared.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/api/capabilities")
@RequiredArgsConstructor
public class CapabilityController {

    @GetMapping
    public ResponseEntity<ApiResponse<List<CapabilityInfoDTO>>> list() {
        List<CapabilityInfoDTO> catalog = Arrays.stream(CapabilityType.values())
                .map(t -> CapabilityInfoDTO.builder()
                        .type(t)
                        .kind(t.getKind())
                        .dataType(t.getDataType())
                        .unit(t.getUnit())
                        .min(t.getMin())
                        .max(t.getMax())
                        .build())
                .toList();
        return ResponseEntity.ok(ApiResponse.success(catalog));
    }
}
