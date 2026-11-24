package com.iot.platform.cloud.telemetry;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iot.platform.cloud.device.DeviceRepository;
import com.iot.platform.cloud.telemetry.dto.DeviceStateDTO;
import com.iot.platform.cloud.telemetry.dto.TelemetryResponseDTO;
import com.iot.platform.cloud.edge.access.EdgeAccessGuard;
import com.iot.platform.cloud.edge.access.UserEdgeAccess;
import com.iot.platform.cloud.exception.AccessDeniedException;
import com.iot.platform.cloud.exception.ResourceNotFoundException;
import com.iot.platform.cloud.exception.UnsupportedCapabilityException;
import com.iot.platform.cloud.telemetry.dto.TelemetryAggregatePointDTO;
import com.iot.platform.common.device.CapabilityType;
import com.iot.platform.common.edge.EdgeRole;
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
    @Mock
    private EdgeAccessGuard edgeAccessGuard;
    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    private static final ObjectMapper JSON = new ObjectMapper();

    @InjectMocks
    private TelemetryService service;

    private UUID edgeId;
    private UUID userId;
    private UUID deviceId;

    @BeforeEach
    void setUp() {
        edgeId = UUID.randomUUID();
        userId = UUID.randomUUID();
        deviceId = UUID.randomUUID();
    }

    private void grantAccess(EdgeRole role) {
        when(edgeAccessGuard.requireAccess(edgeId, userId))
                .thenReturn(UserEdgeAccess.builder()
                        .userId(userId).edgeId(edgeId).role(role).build());
    }

    private void denyAccess() {
        when(edgeAccessGuard.requireAccess(edgeId, userId))
                .thenThrow(new AccessDeniedException("You do not have access to this edge"));
    }

    private Telemetry reading(Instant timestamp) {
        return Telemetry.builder()
                .id(UUID.randomUUID())
                .deviceId(deviceId)
                .edgeId(edgeId)
                .capabilityType(CapabilityType.TEMPERATURE)
                .value(JSON.valueToTree(21.5))
                .timestamp(timestamp)
                .receivedAt(timestamp.plusSeconds(5))
                .build();
    }

    @Test
    void query_withAccess_returnsMappedReading() {
        grantAccess(EdgeRole.MEMBER);
        when(deviceRepository.existsByIdAndEdgeId(deviceId, edgeId)).thenReturn(true);
        when(telemetryRepository.search(eq(edgeId), eq(deviceId), eq(CapabilityType.TEMPERATURE),
                isNull(), isNull(), anyInt()))
                .thenReturn(List.of(reading(Instant.parse("2026-05-30T10:00:00Z"))));

        List<TelemetryResponseDTO> result = service.query(
                edgeId, deviceId, userId, null, null, CapabilityType.TEMPERATURE, 100);

        assertThat(result).hasSize(1);
        TelemetryResponseDTO dto = result.get(0);
        assertThat(dto.getDeviceId()).isEqualTo(deviceId);
        assertThat(dto.getCapabilityType()).isEqualTo(CapabilityType.TEMPERATURE);
        assertThat(dto.getValue()).isEqualTo(21.5);
    }

    @Test
    void query_noTemporalWindow_appliesLimitAndReturnsAscending() {
        grantAccess(EdgeRole.MEMBER);
        when(deviceRepository.existsByIdAndEdgeId(deviceId, edgeId)).thenReturn(true);
        Instant newest = Instant.parse("2026-05-30T12:00:00Z");
        Instant oldest = Instant.parse("2026-05-30T10:00:00Z");
        when(telemetryRepository.search(eq(edgeId), eq(deviceId), isNull(), isNull(), isNull(), anyInt()))
                .thenReturn(List.of(reading(newest), reading(oldest)));

        List<TelemetryResponseDTO> result = service.query(
                edgeId, deviceId, userId, null, null, null, 50);

        assertThat(result).extracting(TelemetryResponseDTO::getTimestamp)
                .containsExactly(oldest, newest);
        verify(telemetryRepository).search(eq(edgeId), eq(deviceId), isNull(), isNull(), isNull(), eq(50));
    }

    @Test
    void query_limitClampedToMax() {
        grantAccess(EdgeRole.MEMBER);
        when(deviceRepository.existsByIdAndEdgeId(deviceId, edgeId)).thenReturn(true);
        when(telemetryRepository.search(eq(edgeId), eq(deviceId), isNull(), isNull(), isNull(), anyInt()))
                .thenReturn(List.of());

        service.query(edgeId, deviceId, userId, null, null, null, 9999);

        verify(telemetryRepository).search(eq(edgeId), eq(deviceId), isNull(), isNull(), isNull(), eq(1000));
    }

    @Test
    void query_nullLimitDefaultsTo100_edgeWideSkipsExistenceCheck() {
        grantAccess(EdgeRole.OWNER);
        when(telemetryRepository.search(eq(edgeId), isNull(), isNull(), isNull(), isNull(), anyInt()))
                .thenReturn(List.of());

        service.query(edgeId, null, userId, null, null, null, null);

        verify(deviceRepository, never()).existsByIdAndEdgeId(any(), any());
        verify(telemetryRepository).search(eq(edgeId), isNull(), isNull(), isNull(), isNull(), eq(100));
    }

    @Test
    void query_noAccess_throws403() {
        denyAccess();

        assertThatThrownBy(() -> service.query(
                edgeId, deviceId, userId, null, null, null, null))
                .isInstanceOf(AccessDeniedException.class);

        verify(telemetryRepository, never()).search(any(), any(), any(), any(), any(), anyInt());
    }

    @Test
    void query_deviceNotInEdge_throws404() {
        grantAccess(EdgeRole.ADMIN);
        when(deviceRepository.existsByIdAndEdgeId(deviceId, edgeId)).thenReturn(false);

        assertThatThrownBy(() -> service.query(
                edgeId, deviceId, userId, null, null, null, null))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(telemetryRepository, never()).search(any(), any(), any(), any(), any(), anyInt());
    }

    private Telemetry reading(CapabilityType capability, Object value, Instant timestamp) {
        return Telemetry.builder()
                .id(UUID.randomUUID())
                .deviceId(deviceId)
                .edgeId(edgeId)
                .capabilityType(capability)
                .value(JSON.valueToTree(value))
                .timestamp(timestamp)
                .receivedAt(timestamp.plusSeconds(5))
                .build();
    }

    @Test
    void getCurrentState_withAccess_mapsLatestPerCapability() {
        grantAccess(EdgeRole.MEMBER);
        when(deviceRepository.existsByIdAndEdgeId(deviceId, edgeId)).thenReturn(true);
        Instant ts = Instant.parse("2026-05-30T12:00:00Z");
        when(telemetryRepository.findLatestPerCapability(edgeId, deviceId))
                .thenReturn(List.of(
                        reading(CapabilityType.POWER_SWITCH, true, ts),
                        reading(CapabilityType.TEMPERATURE, 21.5, ts.minusSeconds(60))));

        DeviceStateDTO state = service.getCurrentState(edgeId, deviceId, userId);

        assertThat(state.getDeviceId()).isEqualTo(deviceId);
        assertThat(state.getStates()).hasSize(2);
        assertThat(state.getStates())
                .extracting("capability", "value", "timestamp")
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(CapabilityType.POWER_SWITCH, true, ts),
                        org.assertj.core.groups.Tuple.tuple(
                                CapabilityType.TEMPERATURE, 21.5, ts.minusSeconds(60)));
    }

    @Test
    void getCurrentState_deviceWithoutReadings_returnsEmptyStates() {
        grantAccess(EdgeRole.MEMBER);
        when(deviceRepository.existsByIdAndEdgeId(deviceId, edgeId)).thenReturn(true);
        when(telemetryRepository.findLatestPerCapability(edgeId, deviceId))
                .thenReturn(List.of());

        DeviceStateDTO state = service.getCurrentState(edgeId, deviceId, userId);

        assertThat(state.getDeviceId()).isEqualTo(deviceId);
        assertThat(state.getStates()).isEmpty();
    }

    @Test
    void getCurrentState_noAccess_throws403() {
        denyAccess();

        assertThatThrownBy(() -> service.getCurrentState(edgeId, deviceId, userId))
                .isInstanceOf(AccessDeniedException.class);

        verify(telemetryRepository, never()).findLatestPerCapability(any(), any());
    }

    @Test
    void getCurrentState_deviceNotInEdge_throws404() {
        grantAccess(EdgeRole.ADMIN);
        when(deviceRepository.existsByIdAndEdgeId(deviceId, edgeId)).thenReturn(false);

        assertThatThrownBy(() -> service.getCurrentState(edgeId, deviceId, userId))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(telemetryRepository, never()).findLatestPerCapability(any(), any());
    }

    private AggregatePointProjection point(long bucketEpoch, double average, long count) {
        return new AggregatePointProjection() {
            @Override public Long getBucketEpoch() { return bucketEpoch; }
            @Override public Double getAverage()   { return average; }
            @Override public Long getSampleCount() { return count; }
        };
    }

    @Test
    void aggregate_withAccess_mapsPointsEpochToInstant() {
        grantAccess(EdgeRole.MEMBER);
        when(deviceRepository.existsByIdAndEdgeId(deviceId, edgeId)).thenReturn(true);
        Instant bucket = Instant.parse("2026-05-30T10:00:00Z");
        when(telemetryRepository.aggregate(eq(edgeId), eq(deviceId), eq("TEMPERATURE"),
                any(), any(), anyInt()))
                .thenReturn(List.of(
                        point(bucket.getEpochSecond(), 21.5, 10),
                        point(bucket.plusSeconds(300).getEpochSecond(), 22.0, 8)));

        List<TelemetryAggregatePointDTO> result = service.aggregate(
                edgeId, deviceId, userId, CapabilityType.TEMPERATURE, null, null, 5);

        assertThat(result).hasSize(2);
        assertThat(result).extracting("timestamp", "average", "count")
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(bucket, 21.5, 10L),
                        org.assertj.core.groups.Tuple.tuple(bucket.plusSeconds(300), 22.0, 8L));
    }

    @Test
    void aggregate_nullSinceUntil_defaultsToLast24hAndBucket5() {
        grantAccess(EdgeRole.MEMBER);
        when(deviceRepository.existsByIdAndEdgeId(deviceId, edgeId)).thenReturn(true);
        when(telemetryRepository.aggregate(any(), any(), anyString(), any(), any(), anyInt()))
                .thenReturn(List.of());

        Instant before = Instant.now();
        service.aggregate(edgeId, deviceId, userId, CapabilityType.HUMIDITY, null, null, null);
        Instant after = Instant.now();

        ArgumentCaptor<Instant> sinceCap = ArgumentCaptor.forClass(Instant.class);
        ArgumentCaptor<Instant> untilCap = ArgumentCaptor.forClass(Instant.class);
        verify(telemetryRepository).aggregate(eq(edgeId), eq(deviceId), eq("HUMIDITY"),
                sinceCap.capture(), untilCap.capture(), eq(TelemetryService.DEFAULT_BUCKET_MINUTES));

        assertThat(untilCap.getValue()).isBetween(before, after);
        assertThat(Duration.between(sinceCap.getValue(), untilCap.getValue()))
                .isEqualTo(TelemetryService.DEFAULT_WINDOW);
    }

    @Test
    void aggregate_customWindowAndOversizedBucket_passesWindowAndClampsBucketTo60() {
        grantAccess(EdgeRole.OWNER);
        when(deviceRepository.existsByIdAndEdgeId(deviceId, edgeId)).thenReturn(true);
        when(telemetryRepository.aggregate(any(), any(), anyString(), any(), any(), anyInt()))
                .thenReturn(List.of());
        Instant since = Instant.parse("2026-05-30T00:00:00Z");
        Instant until = Instant.parse("2026-05-30T06:00:00Z");

        service.aggregate(edgeId, deviceId, userId, CapabilityType.BATTERY_LEVEL, since, until, 999);

        verify(telemetryRepository).aggregate(eq(edgeId), eq(deviceId), eq("BATTERY_LEVEL"),
                eq(since), eq(until), eq(60));
    }

    @Test
    void aggregate_booleanCapability_throws400() {
        grantAccess(EdgeRole.MEMBER);
        when(deviceRepository.existsByIdAndEdgeId(deviceId, edgeId)).thenReturn(true);

        assertThatThrownBy(() -> service.aggregate(
                edgeId, deviceId, userId, CapabilityType.MOTION_DETECTED, null, null, null))
                .isInstanceOf(UnsupportedCapabilityException.class);

        verify(telemetryRepository, never()).aggregate(any(), any(), anyString(), any(), any(), anyInt());
    }

    @Test
    void aggregate_noAccess_throws403() {
        denyAccess();

        assertThatThrownBy(() -> service.aggregate(
                edgeId, deviceId, userId, CapabilityType.TEMPERATURE, null, null, null))
                .isInstanceOf(AccessDeniedException.class);

        verify(telemetryRepository, never()).aggregate(any(), any(), anyString(), any(), any(), anyInt());
    }

    @Test
    void aggregate_deviceNotInEdge_throws404() {
        grantAccess(EdgeRole.ADMIN);
        when(deviceRepository.existsByIdAndEdgeId(deviceId, edgeId)).thenReturn(false);

        assertThatThrownBy(() -> service.aggregate(
                edgeId, deviceId, userId, CapabilityType.TEMPERATURE, null, null, null))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(telemetryRepository, never()).aggregate(any(), any(), anyString(), any(), any(), anyInt());
    }

    @Test
    void aggregate_nullDevice_edgeWideSkipsExistenceCheck() {
        grantAccess(EdgeRole.MEMBER);
        when(telemetryRepository.aggregate(eq(edgeId), isNull(), eq("TEMPERATURE"),
                any(), any(), anyInt()))
                .thenReturn(List.of());

        service.aggregate(edgeId, null, userId, CapabilityType.TEMPERATURE, null, null, null);

        verify(deviceRepository, never()).existsByIdAndEdgeId(any(), any());
        verify(telemetryRepository).aggregate(eq(edgeId), isNull(), eq("TEMPERATURE"),
                any(), any(), anyInt());
    }

    @Test
    void aggregate_nullDevice_noAccess_throws403() {
        denyAccess();

        assertThatThrownBy(() -> service.aggregate(
                edgeId, null, userId, CapabilityType.TEMPERATURE, null, null, null))
                .isInstanceOf(AccessDeniedException.class);

        verify(telemetryRepository, never()).aggregate(any(), any(), anyString(), any(), any(), anyInt());
    }

    @Test
    void aggregate_nullDevice_booleanCapability_throws400() {
        grantAccess(EdgeRole.MEMBER);

        assertThatThrownBy(() -> service.aggregate(
                edgeId, null, userId, CapabilityType.MOTION_DETECTED, null, null, null))
                .isInstanceOf(UnsupportedCapabilityException.class);

        verify(telemetryRepository, never()).aggregate(any(), any(), anyString(), any(), any(), anyInt());
    }
}
