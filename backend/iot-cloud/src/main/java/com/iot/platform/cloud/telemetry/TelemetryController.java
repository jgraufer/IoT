package com.iot.platform.cloud.telemetry;

import com.iot.platform.cloud.telemetry.dto.DeviceStateDTO;
import com.iot.platform.cloud.telemetry.dto.TelemetryAggregatePointDTO;
import com.iot.platform.cloud.telemetry.dto.TelemetryResponseDTO;
import com.iot.platform.common.device.CapabilityType;
import com.iot.platform.common.shared.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/edges/{edgeId}/devices/{deviceId}")
@RequiredArgsConstructor
public class TelemetryController {

    private final TelemetryService telemetryService;

    @GetMapping("/telemetry")
    public ResponseEntity<ApiResponse<List<TelemetryResponseDTO>>> telemetry(
            @PathVariable UUID edgeId,
            @PathVariable UUID deviceId,
            @AuthenticationPrincipal UUID requesterId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant since,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant until,
            @RequestParam(required = false) CapabilityType capability,
            @RequestParam(required = false) Integer limit) {
        return ResponseEntity.ok(ApiResponse.success(
                telemetryService.query(
                        edgeId, deviceId, requesterId, since, until, capability, limit)));
    }

    @GetMapping("/state")
    public ResponseEntity<ApiResponse<DeviceStateDTO>> state(
            @PathVariable UUID edgeId,
            @PathVariable UUID deviceId,
            @AuthenticationPrincipal UUID requesterId) {
        return ResponseEntity.ok(ApiResponse.success(
                telemetryService.getCurrentState(edgeId, deviceId, requesterId)));
    }

    @GetMapping("/telemetry/aggregate")
    public ResponseEntity<ApiResponse<List<TelemetryAggregatePointDTO>>> aggregate(
            @PathVariable UUID edgeId,
            @PathVariable UUID deviceId,
            @AuthenticationPrincipal UUID requesterId,
            @RequestParam CapabilityType capability,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant since,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant until,
            @RequestParam(required = false) Integer bucketMinutes) {
        return ResponseEntity.ok(ApiResponse.success(
                telemetryService.aggregate(
                        edgeId, deviceId, requesterId, capability, since, until, bucketMinutes)));
    }
}
