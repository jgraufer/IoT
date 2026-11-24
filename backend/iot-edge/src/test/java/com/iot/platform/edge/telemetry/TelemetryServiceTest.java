package com.iot.platform.edge.telemetry;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iot.platform.common.device.CapabilityType;
import com.iot.platform.edge.device.DeviceRepository;
import com.iot.platform.edge.telemetry.dto.CapabilityStateDTO;
import com.iot.platform.edge.telemetry.dto.DeviceStateDTO;
import com.iot.platform.edge.telemetry.dto.TelemetryAggregatePointDTO;
import com.iot.platform.edge.telemetry.dto.TelemetryResponseDTO;
import com.iot.platform.edge.exception.DeviceNotFoundException;
import com.iot.platform.edge.exception.UnsupportedCapabilityException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TelemetryServiceTest {

    @Mock
    private TelemetryRepository telemetryRepository;
    @Mock
    private DeviceRepository deviceRepository;
    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    private static final ObjectMapper JSON = new ObjectMapper();

    @InjectMocks
    private TelemetryService service;

    private UUID deviceId;

    @BeforeEach
    void setUp() {
        deviceId = UUID.randomUUID();
    }

    private Telemetry reading(Instant timestamp) {
        return Telemetry.builder()
                .id(UUID.randomUUID())
                .deviceId(deviceId)
                .capabilityType(CapabilityType.TEMPERATURE)
                .value(JSON.valueToTree(21.5))
                .timestamp(timestamp)
                .synced(false)
                .build();
    }

    private Telemetry reading(CapabilityType capability, Object value, Instant timestamp) {
        return Telemetry.builder()
                .id(UUID.randomUUID())
                .deviceId(deviceId)
                .capabilityType(capability)
                .value(JSON.valueToTree(value))
                .timestamp(timestamp)
                .synced(false)
                .build();
    }

    private void stubSearch(List<Telemetry> descResult) {
        when(telemetryRepository.search(eq(deviceId), isNull(), isNull(), isNull(), anyInt()))
                .thenReturn(descResult);
    }

    @Test
    void query_noTemporalWindow_appliesLimitAndReturnsAscending() {
        when(deviceRepository.existsById(deviceId)).thenReturn(true);
        Instant newest = Instant.parse("2026-06-03T12:00:00Z");
        Instant oldest = Instant.parse("2026-06-03T10:00:00Z");
        stubSearch(List.of(reading(newest), reading(oldest)));

        List<TelemetryResponseDTO> result = service.query(deviceId, null, null, null, 50);

        assertThat(result).extracting(TelemetryResponseDTO::getTimestamp)
                .containsExactly(oldest, newest);
        verify(telemetryRepository).search(eq(deviceId), isNull(), isNull(), isNull(), eq(50));
    }

    @Test
    void query_limitClampedToMax() {
        when(deviceRepository.existsById(deviceId)).thenReturn(true);
        stubSearch(List.of());

        service.query(deviceId, null, null, null, 9999);

        verify(telemetryRepository).search(eq(deviceId), isNull(), isNull(), isNull(), eq(1000));
    }

    @Test
    void query_nullLimitDefaultsTo100() {
        when(deviceRepository.existsById(deviceId)).thenReturn(true);
        stubSearch(List.of());

        service.query(deviceId, null, null, null, null);

        verify(telemetryRepository).search(eq(deviceId), isNull(), isNull(), isNull(), eq(100));
    }

    @Test
    void query_unknownDevice_throws404() {
        when(deviceRepository.existsById(deviceId)).thenReturn(false);

        assertThatThrownBy(() -> service.query(deviceId, null, null, null, null))
                .isInstanceOf(DeviceNotFoundException.class);

        verify(telemetryRepository, never()).search(any(), any(), any(), any(), anyInt());
    }

    @Test
    void getCurrentState_mapsLatestReadingPerCapability() {
        when(deviceRepository.existsById(deviceId)).thenReturn(true);
        Instant tPower = Instant.parse("2026-06-03T12:00:00Z");
        Instant tBright = Instant.parse("2026-06-03T11:30:00Z");
        when(telemetryRepository.findLatestPerCapability(deviceId)).thenReturn(List.of(
                reading(CapabilityType.BRIGHTNESS, 75, tBright),
                reading(CapabilityType.POWER_SWITCH, true, tPower)));

        DeviceStateDTO state = service.getCurrentState(deviceId);

        assertThat(state.getDeviceId()).isEqualTo(deviceId);
        assertThat(state.getStates())
                .extracting(CapabilityStateDTO::getCapability,
                        CapabilityStateDTO::getValue,
                        CapabilityStateDTO::getTimestamp)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(
                                CapabilityType.BRIGHTNESS, 75, tBright),
                        org.assertj.core.groups.Tuple.tuple(
                                CapabilityType.POWER_SWITCH, true, tPower));
    }

    @Test
    void getCurrentState_noReadings_returnsEmptyStates() {
        when(deviceRepository.existsById(deviceId)).thenReturn(true);
        when(telemetryRepository.findLatestPerCapability(deviceId)).thenReturn(List.of());

        DeviceStateDTO state = service.getCurrentState(deviceId);

        assertThat(state.getDeviceId()).isEqualTo(deviceId);
        assertThat(state.getStates()).isEmpty();
    }

    @Test
    void getCurrentState_unknownDevice_throws404() {
        when(deviceRepository.existsById(deviceId)).thenReturn(false);

        assertThatThrownBy(() -> service.getCurrentState(deviceId))
                .isInstanceOf(DeviceNotFoundException.class);

        verify(telemetryRepository, never()).findLatestPerCapability(any());
    }

    private AggregatePointProjection point(long bucketEpoch, double average, long count) {
        return new AggregatePointProjection() {
            @Override public Long getBucketEpoch() { return bucketEpoch; }
            @Override public Double getAverage()   { return average; }
            @Override public Long getSampleCount() { return count; }
        };
    }

    @Test
    void aggregate_knownDevice_mapsPointsEpochToInstant() {
        when(deviceRepository.existsById(deviceId)).thenReturn(true);
        Instant bucket = Instant.parse("2026-06-03T10:00:00Z");
        when(telemetryRepository.aggregate(eq(deviceId), eq("TEMPERATURE"), any(), any(), anyInt()))
                .thenReturn(List.of(
                        point(bucket.getEpochSecond(), 21.5, 10),
                        point(bucket.plusSeconds(300).getEpochSecond(), 22.0, 8)));

        List<TelemetryAggregatePointDTO> result = service.aggregate(
                deviceId, CapabilityType.TEMPERATURE, null, null, 5);

        assertThat(result).hasSize(2);
        assertThat(result).extracting("timestamp", "average", "count")
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(bucket, 21.5, 10L),
                        org.assertj.core.groups.Tuple.tuple(bucket.plusSeconds(300), 22.0, 8L));
    }

    @Test
    void aggregate_nullSinceUntil_defaultsToLast24hAndBucket5() {
        when(deviceRepository.existsById(deviceId)).thenReturn(true);
        when(telemetryRepository.aggregate(any(), anyString(), any(), any(), anyInt()))
                .thenReturn(List.of());

        Instant before = Instant.now();
        service.aggregate(deviceId, CapabilityType.HUMIDITY, null, null, null);
        Instant after = Instant.now();

        ArgumentCaptor<Instant> sinceCap = ArgumentCaptor.forClass(Instant.class);
        ArgumentCaptor<Instant> untilCap = ArgumentCaptor.forClass(Instant.class);
        verify(telemetryRepository).aggregate(eq(deviceId), eq("HUMIDITY"),
                sinceCap.capture(), untilCap.capture(), eq(TelemetryService.DEFAULT_BUCKET_MINUTES));

        assertThat(untilCap.getValue()).isBetween(before, after);
        assertThat(Duration.between(sinceCap.getValue(), untilCap.getValue()))
                .isEqualTo(TelemetryService.DEFAULT_WINDOW);
    }

    @Test
    void aggregate_customWindowAndOversizedBucket_passesWindowAndClampsBucketTo60() {
        when(deviceRepository.existsById(deviceId)).thenReturn(true);
        when(telemetryRepository.aggregate(any(), anyString(), any(), any(), anyInt()))
                .thenReturn(List.of());
        Instant since = Instant.parse("2026-06-03T00:00:00Z");
        Instant until = Instant.parse("2026-06-03T06:00:00Z");

        service.aggregate(deviceId, CapabilityType.BATTERY_LEVEL, since, until, 999);

        verify(telemetryRepository).aggregate(eq(deviceId), eq("BATTERY_LEVEL"),
                eq(since), eq(until), eq(60));
    }

    @Test
    void aggregate_booleanCapability_throws400() {
        when(deviceRepository.existsById(deviceId)).thenReturn(true);

        assertThatThrownBy(() -> service.aggregate(
                deviceId, CapabilityType.MOTION_DETECTED, null, null, null))
                .isInstanceOf(UnsupportedCapabilityException.class);

        verify(telemetryRepository, never()).aggregate(any(), anyString(), any(), any(), anyInt());
    }

    @Test
    void aggregate_unknownDevice_throws404() {
        when(deviceRepository.existsById(deviceId)).thenReturn(false);

        assertThatThrownBy(() -> service.aggregate(
                deviceId, CapabilityType.TEMPERATURE, null, null, null))
                .isInstanceOf(DeviceNotFoundException.class);

        verify(telemetryRepository, never()).aggregate(any(), anyString(), any(), any(), anyInt());
    }

    @Test
    void aggregate_nullDevice_skipsExistenceCheck() {
        when(telemetryRepository.aggregate(isNull(), eq("TEMPERATURE"), any(), any(), anyInt()))
                .thenReturn(List.of());

        service.aggregate(null, CapabilityType.TEMPERATURE, null, null, null);

        verify(deviceRepository, never()).existsById(any());
        verify(telemetryRepository).aggregate(isNull(), eq("TEMPERATURE"), any(), any(), anyInt());
    }

    @Test
    void aggregate_nullDevice_booleanCapability_throws400() {
        assertThatThrownBy(() -> service.aggregate(
                null, CapabilityType.MOTION_DETECTED, null, null, null))
                .isInstanceOf(UnsupportedCapabilityException.class);

        verify(telemetryRepository, never()).aggregate(any(), anyString(), any(), any(), anyInt());
    }
}
