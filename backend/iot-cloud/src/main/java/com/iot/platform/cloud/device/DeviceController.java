package com.iot.platform.cloud.device;

import com.iot.platform.cloud.device.dto.DeviceResponseDTO;
import com.iot.platform.common.shared.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/edges/{edgeId}/devices")
@RequiredArgsConstructor
public class DeviceController {

    private final DeviceService deviceService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<DeviceResponseDTO>>> list(
            @PathVariable UUID edgeId,
            @AuthenticationPrincipal UUID requesterId) {
        return ResponseEntity.ok(ApiResponse.success(
                deviceService.listByEdge(edgeId, requesterId)));
    }

    @GetMapping("/{deviceId}")
    public ResponseEntity<ApiResponse<DeviceResponseDTO>> get(
            @PathVariable UUID edgeId,
            @PathVariable UUID deviceId,
            @AuthenticationPrincipal UUID requesterId) {
        return ResponseEntity.ok(ApiResponse.success(
                deviceService.getById(edgeId, deviceId, requesterId)));
    }
}
