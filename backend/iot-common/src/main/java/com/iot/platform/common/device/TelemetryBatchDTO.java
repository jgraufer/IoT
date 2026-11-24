package com.iot.platform.common.device;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TelemetryBatchDTO {

    private UUID edgeId;

    private Instant batchTimestamp;

    private List<TelemetryReadingDTO> readings;
}
