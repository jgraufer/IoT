package com.iot.platform.cloud.telemetry;

import com.iot.platform.cloud.telemetry.dto.TelemetryAggregatePointDTO;
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
@RequestMapping("/api/edges/{edgeId}/telemetry")
@RequiredArgsConstructor
public class EdgeTelemetryController {

    private final TelemetryService telemetryService;

    @GetMapping("/average")
    public ResponseEntity<ApiResponse<List<TelemetryAggregatePointDTO>>> average(
            @PathVariable UUID edgeId,
            @AuthenticationPrincipal UUID requesterId,
            @RequestParam CapabilityType capability,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant since,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant until,
            @RequestParam(required = false) Integer bucketMinutes) {
        return ResponseEntity.ok(ApiResponse.success(
                telemetryService.aggregate(
                        edgeId, null, requesterId, capability, since, until, bucketMinutes)));
    }
}
