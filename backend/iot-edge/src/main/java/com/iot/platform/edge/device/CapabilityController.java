package com.iot.platform.edge.device;

import com.iot.platform.common.device.CapabilityType;
import com.iot.platform.common.shared.ApiResponse;
import com.iot.platform.edge.device.dto.CapabilityInfoDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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
    @PreAuthorize("isAuthenticated()")
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
