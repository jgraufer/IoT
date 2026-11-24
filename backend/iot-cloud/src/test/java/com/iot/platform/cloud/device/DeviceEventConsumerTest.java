package com.iot.platform.cloud.device;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iot.platform.common.device.CapabilityType;
import com.iot.platform.common.device.DeviceDTO;
import com.iot.platform.common.device.DeviceEventDTO;
import com.iot.platform.common.device.DeviceEventType;
import com.iot.platform.common.device.DeviceType;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeviceEventConsumerTest {

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    @Mock
    private DeviceRepository deviceRepository;

    @InjectMocks
    private DeviceEventConsumer consumer;

    private UUID edgeId;
    private UUID deviceId;

    @BeforeEach
    void setUp() {
        edgeId = UUID.randomUUID();
        deviceId = UUID.randomUUID();
    }

    private DeviceEventDTO event(DeviceEventType type, DeviceDTO device, Instant ts) {
        return DeviceEventDTO.builder()
                .eventType(type)
                .edgeId(edgeId)
                .deviceId(deviceId)
                .device(device)
                .timestamp(ts)
                .build();
    }

    private DeviceDTO body(List<CapabilityType> caps) {
        return DeviceDTO.builder()
                .id(deviceId)
                .name("Bombilla salón")
                .type(DeviceType.LIGHT)
                .capabilities(caps)
                .build();
    }

    @Test
    void deviceCreated_new_persistsDeviceWithCapabilities() {
        Instant ts = Instant.parse("2026-05-30T10:00:00Z");
        when(deviceRepository.findById(deviceId)).thenReturn(Optional.empty());

        consumer.apply(event(DeviceEventType.DEVICE_CREATED,
                body(List.of(CapabilityType.POWER_SWITCH, CapabilityType.BRIGHTNESS)), ts));

        ArgumentCaptor<Device> captor = ArgumentCaptor.forClass(Device.class);
        verify(deviceRepository).save(captor.capture());
        Device saved = captor.getValue();
        assertThat(saved.getId()).isEqualTo(deviceId);
        assertThat(saved.getEdgeId()).isEqualTo(edgeId);
        assertThat(saved.getName()).isEqualTo("Bombilla salón");
        assertThat(saved.getType()).isEqualTo(DeviceType.LIGHT);
        Instant expectedTs = ts;
        assertThat(saved.getCreatedAt()).isEqualTo(expectedTs);
        assertThat(saved.getUpdatedAt()).isEqualTo(expectedTs);
        assertThat(saved.getLastSyncedAt()).isEqualTo(expectedTs);
        assertThat(saved.getCapabilities()).extracting(DeviceCapability::getCapabilityType)
                .containsExactly(CapabilityType.POWER_SWITCH, CapabilityType.BRIGHTNESS);
        verify(deviceRepository, never()).flush();
    }

    @Test
    void deviceUpdated_existing_replacesCapabilitiesAndFlushes() {
        Instant ts = Instant.parse("2026-05-30T12:00:00Z");
        Device existing = Device.builder()
                .id(deviceId)
                .edgeId(edgeId)
                .name("Viejo nombre")
                .type(DeviceType.LIGHT)
                .createdAt(Instant.parse("2026-05-30T09:00:00Z"))
                .updatedAt(Instant.parse("2026-05-30T09:00:00Z"))
                .lastSyncedAt(Instant.parse("2026-05-30T09:00:00Z"))
                .build();
        existing.getCapabilities().add(DeviceCapability.builder()
                .device(existing).capabilityType(CapabilityType.POWER_SWITCH).build());
        existing.getCapabilities().add(DeviceCapability.builder()
                .device(existing).capabilityType(CapabilityType.BRIGHTNESS).build());
        when(deviceRepository.findById(deviceId)).thenReturn(Optional.of(existing));

        consumer.apply(event(DeviceEventType.DEVICE_UPDATED,
                body(List.of(CapabilityType.POWER_SWITCH)), ts));

        verify(deviceRepository).flush();
        ArgumentCaptor<Device> captor = ArgumentCaptor.forClass(Device.class);
        verify(deviceRepository).save(captor.capture());
        Device saved = captor.getValue();
        assertThat(saved.getName()).isEqualTo("Bombilla salón");
        assertThat(saved.getCapabilities()).extracting(DeviceCapability::getCapabilityType)
                .containsExactly(CapabilityType.POWER_SWITCH);
        assertThat(saved.getLastSyncedAt())
                .isEqualTo(ts);
    }

    @Test
    void obsoleteTimestamp_isDiscarded() {
        Instant lastSync = Instant.parse("2026-05-30T12:00:00Z");
        Instant olderEvent = Instant.parse("2026-05-30T11:00:00Z");
        Device existing = Device.builder()
                .id(deviceId).edgeId(edgeId).name("Actual").type(DeviceType.LIGHT)
                .createdAt(lastSync)
                .updatedAt(lastSync)
                .lastSyncedAt(lastSync)
                .build();
        when(deviceRepository.findById(deviceId)).thenReturn(Optional.of(existing));

        consumer.apply(event(DeviceEventType.DEVICE_UPDATED,
                body(List.of(CapabilityType.POWER_SWITCH)), olderEvent));

        verify(deviceRepository, never()).save(any());
        verify(deviceRepository, never()).flush();
    }

    @Test
    void sameTimestamp_isDiscardedAsObsolete() {
        Instant ts = Instant.parse("2026-05-30T12:00:00Z");
        Device existing = Device.builder()
                .id(deviceId).edgeId(edgeId).name("Actual").type(DeviceType.LIGHT)
                .createdAt(ts)
                .updatedAt(ts)
                .lastSyncedAt(ts)
                .build();
        when(deviceRepository.findById(deviceId)).thenReturn(Optional.of(existing));

        consumer.apply(event(DeviceEventType.DEVICE_UPDATED,
                body(List.of(CapabilityType.POWER_SWITCH)), ts));

        verify(deviceRepository, never()).save(any());
    }

    @Test
    void deviceDeleted_existing_deletesWithCascade() {
        Instant ts = Instant.parse("2026-05-30T13:00:00Z");
        Device existing = Device.builder()
                .id(deviceId).edgeId(edgeId).name("A borrar").type(DeviceType.LIGHT)
                .createdAt(Instant.now()).updatedAt(Instant.now())
                .lastSyncedAt(Instant.parse("2026-05-30T09:00:00Z"))
                .build();
        when(deviceRepository.findById(deviceId)).thenReturn(Optional.of(existing));

        consumer.apply(event(DeviceEventType.DEVICE_DELETED, null, ts));

        verify(deviceRepository).delete(existing);
        verify(deviceRepository, never()).save(any());
    }

    @Test
    void deviceDeleted_absent_isNoOp() {
        Instant ts = Instant.parse("2026-05-30T13:00:00Z");
        when(deviceRepository.findById(deviceId)).thenReturn(Optional.empty());

        consumer.apply(event(DeviceEventType.DEVICE_DELETED, null, ts));

        verify(deviceRepository, never()).delete(any());
    }

    @Test
    void createdWithoutDeviceBody_isIgnored() {
        Instant ts = Instant.parse("2026-05-30T10:00:00Z");
        consumer.apply(event(DeviceEventType.DEVICE_CREATED, null, ts));

        verify(deviceRepository, never()).save(any());
        verify(deviceRepository, never()).findById(any());
    }
}
