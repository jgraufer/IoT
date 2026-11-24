package com.iot.platform.edge.device;

import com.iot.platform.common.shared.ApiResponse;
import com.iot.platform.edge.device.dto.CreateDeviceRequestDTO;
import com.iot.platform.edge.device.dto.DeviceResponseDTO;
import com.iot.platform.edge.device.dto.UpdateDeviceRequestDTO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/devices")
@RequiredArgsConstructor
public class DeviceController {

    private final DeviceService deviceService;

    @GetMapping
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','MEMBER')")
    public ResponseEntity<ApiResponse<List<DeviceResponseDTO>>> listAll() {
        return ResponseEntity.ok(ApiResponse.success(deviceService.listAll()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','MEMBER')")
    public ResponseEntity<ApiResponse<DeviceResponseDTO>> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(deviceService.getById(id)));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public ResponseEntity<ApiResponse<DeviceResponseDTO>> create(
            @Valid @RequestBody CreateDeviceRequestDTO request) {
        DeviceResponseDTO created = deviceService.createDevice(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("Device created", created));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public ResponseEntity<ApiResponse<DeviceResponseDTO>> update(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateDeviceRequestDTO request) {
        return ResponseEntity.ok(ApiResponse.success(
                "Device updated", deviceService.updateDevice(id, request)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        deviceService.deleteDevice(id);
        return ResponseEntity.ok(ApiResponse.success("Device deleted"));
    }
}
