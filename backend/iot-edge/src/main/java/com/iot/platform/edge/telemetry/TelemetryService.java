package com.iot.platform.edge.telemetry;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iot.platform.common.device.CapabilityDataType;
import com.iot.platform.common.device.CapabilityType;
import com.iot.platform.edge.device.DeviceRepository;
import com.iot.platform.edge.telemetry.dto.CapabilityStateDTO;
import com.iot.platform.edge.telemetry.dto.DeviceStateDTO;
import com.iot.platform.edge.telemetry.dto.TelemetryAggregatePointDTO;
import com.iot.platform.edge.telemetry.dto.TelemetryResponseDTO;
import com.iot.platform.edge.exception.DeviceNotFoundException;
import com.iot.platform.edge.exception.UnsupportedCapabilityException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TelemetryService {

    static final int DEFAULT_LIMIT = 100;
    static final int MAX_LIMIT = 1000;

    static final int DEFAULT_BUCKET_MINUTES = 2;
    static final int MAX_BUCKET_MINUTES = 60;
    static final Duration DEFAULT_WINDOW = Duration.ofHours(4);

    private final TelemetryRepository telemetryRepository;
    private final DeviceRepository deviceRepository;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public List<TelemetryResponseDTO> query(UUID deviceId, Instant since, Instant until,
                                            CapabilityType capability, Integer limit) {
        if (!deviceRepository.existsById(deviceId)) {
            throw new DeviceNotFoundException(deviceId);
        }

        int effectiveLimit = normalizeLimit(limit);

        List<Telemetry> rows = telemetryRepository.search(
                deviceId, capability, since, until, effectiveLimit);

        List<TelemetryResponseDTO> ascending = new ArrayList<>(rows.size());
        for (int i = rows.size() - 1; i >= 0; i--) {
            ascending.add(toResponse(rows.get(i)));
        }
        return ascending;
    }

    @Transactional(readOnly = true)
    public DeviceStateDTO getCurrentState(UUID deviceId) {
        if (!deviceRepository.existsById(deviceId)) {
            throw new DeviceNotFoundException(deviceId);
        }

        List<Telemetry> latest = telemetryRepository.findLatestPerCapability(deviceId);

        List<CapabilityStateDTO> states = new ArrayList<>(latest.size());
        for (Telemetry t : latest) {
            states.add(CapabilityStateDTO.builder()
                    .capability(t.getCapabilityType())
                    .value(objectMapper.convertValue(t.getValue(), Object.class))
                    .timestamp(t.getTimestamp())
                    .build());
        }
        return DeviceStateDTO.builder()
                .deviceId(deviceId)
                .states(states)
                .build();
    }

    @Transactional(readOnly = true)
    public List<TelemetryAggregatePointDTO> aggregate(UUID deviceId, CapabilityType capability,
                                                      Instant since, Instant until,
                                                      Integer bucketMinutes) {
        if (deviceId != null && !deviceRepository.existsById(deviceId)) {
            throw new DeviceNotFoundException(deviceId);
        }
        if (capability.getDataType() == CapabilityDataType.BOOLEAN) {
            throw new UnsupportedCapabilityException(capability);
        }

        Instant effectiveUntil = (until != null) ? until : Instant.now();
        Instant effectiveSince = (since != null) ? since : effectiveUntil.minus(DEFAULT_WINDOW);
        int bucket = normalizeBucket(bucketMinutes);

        List<AggregatePointProjection> rows = telemetryRepository.aggregate(
                deviceId, capability.name(), effectiveSince, effectiveUntil, bucket);

        List<TelemetryAggregatePointDTO> points = new ArrayList<>(rows.size());
        for (AggregatePointProjection r : rows) {
            points.add(TelemetryAggregatePointDTO.builder()
                    .timestamp(Instant.ofEpochSecond(r.getBucketEpoch()))
                    .average(r.getAverage())
                    .count(r.getSampleCount())
                    .build());
        }
        return points;
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }

    private int normalizeBucket(Integer bucketMinutes) {
        if (bucketMinutes == null || bucketMinutes <= 0) {
            return DEFAULT_BUCKET_MINUTES;
        }
        return Math.min(bucketMinutes, MAX_BUCKET_MINUTES);
    }

    private TelemetryResponseDTO toResponse(Telemetry telemetry) {
        return TelemetryResponseDTO.builder()
                .id(telemetry.getId())
                .deviceId(telemetry.getDeviceId())
                .capabilityType(telemetry.getCapabilityType())
                .value(objectMapper.convertValue(telemetry.getValue(), Object.class))
                .timestamp(telemetry.getTimestamp())
                .build();
    }
}
