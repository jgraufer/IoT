package com.iot.platform.edge.telemetry;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iot.platform.common.device.CapabilityType;
import com.iot.platform.common.device.DeviceTelemetryDTO;
import com.iot.platform.common.device.DeviceType;
import com.iot.platform.common.protocol.MqttTopics;
import com.iot.platform.edge.device.Device;
import com.iot.platform.edge.device.DeviceCapability;
import com.iot.platform.edge.device.DeviceRepository;
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
import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeviceTelemetryConsumerTest {

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    @Mock
    private DeviceRepository deviceRepository;
    @Mock
    private TelemetryRepository telemetryRepository;

    @InjectMocks
    private DeviceTelemetryConsumer consumer;

    private UUID deviceId;

    @BeforeEach
    void setUp() {
        deviceId = UUID.randomUUID();
    }

    private Device sensorWith(CapabilityType... caps) {
        Device device = Device.builder()
                .id(deviceId)
                .name("Sensor salón")
                .type(DeviceType.SENSOR)
                .capabilities(new ArrayList<>())
                .build();
        for (CapabilityType cap : caps) {
            device.addCapability(DeviceCapability.builder().capabilityType(cap).build());
        }
        return device;
    }

    private MqttMessage message(DeviceTelemetryDTO dto) throws Exception {
        return new MqttMessage(objectMapper.writeValueAsString(dto).getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void validReading_persistsWithSyncedFalse() throws Exception {
        when(deviceRepository.findById(deviceId))
                .thenReturn(Optional.of(sensorWith(CapabilityType.TEMPERATURE)));
        Instant ts = Instant.parse("2026-06-03T10:00:00Z");
        DeviceTelemetryDTO dto = DeviceTelemetryDTO.builder()
                .capability(CapabilityType.TEMPERATURE)
                .value(22.5)
                .timestamp(ts)
                .build();

        consumer.messageArrived(MqttTopics.deviceTelemetry(deviceId), message(dto));

        ArgumentCaptor<Telemetry> captor = ArgumentCaptor.forClass(Telemetry.class);
        verify(telemetryRepository).save(captor.capture());
        Telemetry saved = captor.getValue();
        assertThat(saved.getDeviceId()).isEqualTo(deviceId);
        assertThat(saved.getCapabilityType()).isEqualTo(CapabilityType.TEMPERATURE);
        assertThat(saved.getValue()).isEqualTo(objectMapper.valueToTree(22.5));
        assertThat(saved.getTimestamp()).isEqualTo(ts);
        assertThat(saved.isSynced()).isFalse();
    }

    @Test
    void missingTimestamp_isStampedOnReceipt() throws Exception {
        when(deviceRepository.findById(deviceId))
                .thenReturn(Optional.of(sensorWith(CapabilityType.HUMIDITY)));
        DeviceTelemetryDTO dto = DeviceTelemetryDTO.builder()
                .capability(CapabilityType.HUMIDITY)
                .value(40)
                .build();

        Instant before = Instant.now().minusSeconds(5);
        consumer.messageArrived(MqttTopics.deviceTelemetry(deviceId), message(dto));

        ArgumentCaptor<Telemetry> captor = ArgumentCaptor.forClass(Telemetry.class);
        verify(telemetryRepository).save(captor.capture());
        assertThat(captor.getValue().getTimestamp()).isAfterOrEqualTo(before);
    }

    @Test
    void unknownDevice_isDiscarded() throws Exception {
        when(deviceRepository.findById(deviceId)).thenReturn(Optional.empty());
        DeviceTelemetryDTO dto = DeviceTelemetryDTO.builder()
                .capability(CapabilityType.TEMPERATURE)
                .value(21.0)
                .timestamp(Instant.now())
                .build();

        consumer.messageArrived(MqttTopics.deviceTelemetry(deviceId), message(dto));

        verify(telemetryRepository, never()).save(any());
    }

    @Test
    void capabilityNotAssignedToDevice_isDiscarded() throws Exception {
        when(deviceRepository.findById(deviceId))
                .thenReturn(Optional.of(sensorWith(CapabilityType.TEMPERATURE)));
        DeviceTelemetryDTO dto = DeviceTelemetryDTO.builder()
                .capability(CapabilityType.HUMIDITY)
                .value(55.0)
                .timestamp(Instant.now())
                .build();

        consumer.messageArrived(MqttTopics.deviceTelemetry(deviceId), message(dto));

        verify(telemetryRepository, never()).save(any());
    }

    @Test
    void wrongValueType_isDiscarded() throws Exception {
        when(deviceRepository.findById(deviceId))
                .thenReturn(Optional.of(sensorWith(CapabilityType.TEMPERATURE)));
        DeviceTelemetryDTO dto = DeviceTelemetryDTO.builder()
                .capability(CapabilityType.TEMPERATURE)
                .value(true)
                .timestamp(Instant.now())
                .build();

        consumer.messageArrived(MqttTopics.deviceTelemetry(deviceId), message(dto));

        verify(telemetryRepository, never()).save(any());
    }

    @Test
    void malformedTopic_isDiscardedWithoutLookup() throws Exception {
        DeviceTelemetryDTO dto = DeviceTelemetryDTO.builder()
                .capability(CapabilityType.TEMPERATURE)
                .value(20.0)
                .timestamp(Instant.now())
                .build();

        consumer.messageArrived("devices/not-a-uuid/telemetry", message(dto));

        verify(deviceRepository, never()).findById(any());
        verify(telemetryRepository, never()).save(any());
    }
}
