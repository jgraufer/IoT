package com.iot.platform.edge.telemetry;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iot.platform.common.device.CapabilityType;
import com.iot.platform.common.device.TelemetryBatchDTO;
import com.iot.platform.common.protocol.MqttTopics;
import com.iot.platform.edge.bootstrap.EdgeIdentity;
import com.iot.platform.edge.bootstrap.EdgeIdentityRepository;
import com.iot.platform.edge.mqtt.CloudMqttClient;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class TelemetrySyncSchedulerTest {

    @Mock
    private TelemetryRepository telemetryRepository;
    @Mock
    private EdgeIdentityRepository edgeIdentityRepository;
    @Mock
    private CloudMqttClient cloudMqttClient;
    @Spy
    private ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @InjectMocks
    private TelemetrySyncScheduler scheduler;

    private UUID edgeId;

    @BeforeEach
    void setUp() {
        edgeId = UUID.randomUUID();
        lenient().when(edgeIdentityRepository.findSingleton())
                .thenReturn(Optional.of(EdgeIdentity.builder().edgeId(edgeId).build()));
    }

    private Telemetry reading(CapabilityType cap, Object value) {
        return Telemetry.builder()
                .id(UUID.randomUUID())
                .deviceId(UUID.randomUUID())
                .capabilityType(cap)
                .value(objectMapper.valueToTree(value))
                .timestamp(Instant.now())
                .synced(false)
                .build();
    }

    @Test
    void pendingReadings_publishedAndMarkedSynced() throws Exception {
        List<Telemetry> pending = List.of(
                reading(CapabilityType.TEMPERATURE, 22.5),
                reading(CapabilityType.HUMIDITY, 48));
        when(telemetryRepository.findBySyncedFalseOrderByTimestampAsc(any(Pageable.class)))
                .thenReturn(pending);

        scheduler.sync();

        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(cloudMqttClient).publish(eq(MqttTopics.telemetry(edgeId)), payloadCaptor.capture());
        TelemetryBatchDTO batch =
                objectMapper.readValue(payloadCaptor.getValue(), TelemetryBatchDTO.class);
        assertThat(batch.getEdgeId()).isEqualTo(edgeId);
        assertThat(batch.getReadings()).hasSize(2);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Telemetry>> savedCaptor = ArgumentCaptor.forClass(List.class);
        verify(telemetryRepository).saveAll(savedCaptor.capture());
        assertThat(savedCaptor.getValue()).allMatch(Telemetry::isSynced);
    }

    @Test
    void mqttFailure_doesNotMarkSynced() throws Exception {
        List<Telemetry> pending = List.of(reading(CapabilityType.TEMPERATURE, 21.0));
        when(telemetryRepository.findBySyncedFalseOrderByTimestampAsc(any(Pageable.class)))
                .thenReturn(pending);
        doThrow(new MqttException(MqttException.REASON_CODE_CONNECTION_LOST))
                .when(cloudMqttClient).publish(anyString(), anyString());

        scheduler.sync();

        verify(telemetryRepository, never()).saveAll(any());
    }

    @Test
    void emptyBuffer_doesNotPublish() throws Exception {
        when(telemetryRepository.findBySyncedFalseOrderByTimestampAsc(any(Pageable.class)))
                .thenReturn(List.of());

        scheduler.sync();

        verify(cloudMqttClient, never()).publish(anyString(), anyString());
        verify(telemetryRepository, never()).saveAll(any());
    }

    @Test
    void missingEdgeIdentity_skipsSyncWithoutMarking() throws Exception {
        List<Telemetry> pending = List.of(reading(CapabilityType.TEMPERATURE, 21.0));
        when(telemetryRepository.findBySyncedFalseOrderByTimestampAsc(any(Pageable.class)))
                .thenReturn(pending);
        when(edgeIdentityRepository.findSingleton()).thenReturn(Optional.empty());

        scheduler.sync();

        verify(cloudMqttClient, never()).publish(anyString(), anyString());
        verify(telemetryRepository, never()).saveAll(any());
    }
}
