package com.iot.platform.edge.telemetry.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TelemetryAggregatePointDTO {

    private Instant timestamp;
    private Double average;
    private Long count;
}
