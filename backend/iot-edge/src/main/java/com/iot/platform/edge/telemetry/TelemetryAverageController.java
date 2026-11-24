package com.iot.platform.edge.telemetry;

import com.iot.platform.common.device.CapabilityType;
import com.iot.platform.common.shared.ApiResponse;
import com.iot.platform.edge.telemetry.dto.TelemetryAggregatePointDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/telemetry")
@RequiredArgsConstructor
public class TelemetryAverageController {

    private final TelemetryService telemetryService;

    @GetMapping("/average")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','MEMBER')")
    public ResponseEntity<ApiResponse<List<TelemetryAggregatePointDTO>>> average(
            @RequestParam CapabilityType capability,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant since,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant until,
            @RequestParam(required = false) Integer bucketMinutes) {
        return ResponseEntity.ok(ApiResponse.success(
                telemetryService.aggregate(null, capability, since, until, bucketMinutes)));
    }
}
