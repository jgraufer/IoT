package com.iot.platform.cloud.telemetry;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iot.platform.cloud.device.DeviceRepository;
import com.iot.platform.common.device.CapabilityType;
import com.iot.platform.common.device.TelemetryBatchDTO;
import com.iot.platform.common.device.TelemetryReadingDTO;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TelemetryConsumerTest {

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    @Mock
    private TelemetryRepository telemetryRepository;
    @Mock
    private DeviceRepository deviceRepository;

    @InjectMocks
    private TelemetryConsumer consumer;

    private UUID edgeId;

    @BeforeEach
    void setUp() {
        edgeId = UUID.randomUUID();
    }

    private TelemetryReadingDTO reading(UUID deviceId, CapabilityType cap, Object value, Instant ts) {
        return TelemetryReadingDTO.builder()
                .deviceId(deviceId)
                .capability(cap)
                .value(value)
                .timestamp(ts)
                .build();
    }

    private MqttMessage message(TelemetryBatchDTO batch) throws Exception {
        return new MqttMessage(objectMapper.writeValueAsString(batch).getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void batch_persistsEveryReadingWithEdgeIdAndReceivedAt() throws Exception {
        UUID d1 = UUID.randomUUID();
        UUID d2 = UUID.randomUUID();
        when(deviceRepository.existsById(any())).thenReturn(true);
        Instant ts1 = Instant.parse("2026-06-03T10:00:00Z");
        Instant ts2 = Instant.parse("2026-06-03T10:00:05Z");
        TelemetryBatchDTO batch = TelemetryBatchDTO.builder()
                .edgeId(edgeId)
                .batchTimestamp(Instant.now())
                .readings(List.of(
                        reading(d1, CapabilityType.TEMPERATURE, 22.5, ts1),
                        reading(d2, CapabilityType.POWER_SWITCH, true, ts2)))
                .build();

        consumer.messageArrived("edge/" + edgeId + "/telemetry", message(batch));

        ArgumentCaptor<Telemetry> captor = ArgumentCaptor.forClass(Telemetry.class);
        verify(telemetryRepository, times(2)).save(captor.capture());
        List<Telemetry> saved = captor.getAllValues();
        assertThat(saved).allSatisfy(t -> {
            assertThat(t.getEdgeId()).isEqualTo(edgeId);
            assertThat(t.getReceivedAt()).isNotNull();
        });
        assertThat(saved).extracting(Telemetry::getDeviceId).containsExactly(d1, d2);
        assertThat(saved.get(0).getValue()).isEqualTo(objectMapper.valueToTree(22.5));
        assertThat(saved.get(0).getTimestamp()).isEqualTo(ts1);
        assertThat(saved.get(1).getValue()).isEqualTo(objectMapper.valueToTree(true));
    }

    @Test
    void readingForUnreplicatedDevice_isSkipped() throws Exception {
        UUID known = UUID.randomUUID();
        UUID unknown = UUID.randomUUID();
        when(deviceRepository.existsById(known)).thenReturn(true);
        when(deviceRepository.existsById(unknown)).thenReturn(false);
        TelemetryBatchDTO batch = TelemetryBatchDTO.builder()
                .edgeId(edgeId)
                .batchTimestamp(Instant.now())
                .readings(List.of(
                        reading(known, CapabilityType.TEMPERATURE, 21.0, Instant.now()),
                        reading(unknown, CapabilityType.TEMPERATURE, 19.0, Instant.now())))
                .build();

        consumer.messageArrived("edge/" + edgeId + "/telemetry", message(batch));

        ArgumentCaptor<Telemetry> captor = ArgumentCaptor.forClass(Telemetry.class);
        verify(telemetryRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getDeviceId()).isEqualTo(known);
    }

    @Test
    void incompleteReading_isSkipped() throws Exception {
        UUID deviceId = UUID.randomUUID();
        lenient().when(deviceRepository.existsById(any())).thenReturn(true);
        TelemetryBatchDTO batch = TelemetryBatchDTO.builder()
                .edgeId(edgeId)
                .batchTimestamp(Instant.now())
                .readings(List.of(reading(deviceId, CapabilityType.TEMPERATURE, null, Instant.now())))
                .build();

        consumer.messageArrived("edge/" + edgeId + "/telemetry", message(batch));

        verify(telemetryRepository, never()).save(any());
    }

    @Test
    void emptyBatch_persistsNothing() throws Exception {
        TelemetryBatchDTO batch = TelemetryBatchDTO.builder()
                .edgeId(edgeId)
                .batchTimestamp(Instant.now())
                .readings(List.of())
                .build();

        consumer.messageArrived("edge/" + edgeId + "/telemetry", message(batch));

        verify(telemetryRepository, never()).save(any());
    }
}
