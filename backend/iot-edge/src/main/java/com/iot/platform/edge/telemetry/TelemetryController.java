package com.iot.platform.edge.telemetry;

import com.iot.platform.common.device.CapabilityType;
import com.iot.platform.common.shared.ApiResponse;
import com.iot.platform.edge.telemetry.dto.DeviceStateDTO;
import com.iot.platform.edge.telemetry.dto.TelemetryAggregatePointDTO;
import com.iot.platform.edge.telemetry.dto.TelemetryResponseDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/devices/{deviceId}")
@RequiredArgsConstructor
public class TelemetryController {

    private final TelemetryService telemetryService;

    @GetMapping("/telemetry")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','MEMBER')")
    public ResponseEntity<ApiResponse<List<TelemetryResponseDTO>>> query(
            @PathVariable UUID deviceId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant since,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant until,
            @RequestParam(required = false) CapabilityType capability,
            @RequestParam(required = false) Integer limit) {
        return ResponseEntity.ok(ApiResponse.success(
                telemetryService.query(deviceId, since, until, capability, limit)));
    }

    @GetMapping("/state")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','MEMBER')")
    public ResponseEntity<ApiResponse<DeviceStateDTO>> currentState(@PathVariable UUID deviceId) {
        return ResponseEntity.ok(ApiResponse.success(telemetryService.getCurrentState(deviceId)));
    }

    @GetMapping("/telemetry/aggregate")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','MEMBER')")
    public ResponseEntity<ApiResponse<List<TelemetryAggregatePointDTO>>> aggregate(
            @PathVariable UUID deviceId,
            @RequestParam CapabilityType capability,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant since,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant until,
            @RequestParam(required = false) Integer bucketMinutes) {
        return ResponseEntity.ok(ApiResponse.success(
                telemetryService.aggregate(deviceId, capability, since, until, bucketMinutes)));
    }
}
