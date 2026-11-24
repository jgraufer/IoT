package com.iot.platform.edge.device;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iot.platform.common.device.CapabilityType;
import com.iot.platform.common.device.DeviceEventDTO;
import com.iot.platform.common.device.DeviceEventType;
import com.iot.platform.common.device.DeviceType;
import com.iot.platform.edge.bootstrap.EdgeIdentity;
import com.iot.platform.edge.bootstrap.EdgeIdentityRepository;
import com.iot.platform.edge.cloudsync.outbox.EdgeOutbox;
import com.iot.platform.edge.cloudsync.outbox.EdgeOutboxRepository;
import com.iot.platform.edge.device.dto.CreateDeviceRequestDTO;
import com.iot.platform.edge.device.dto.DeviceResponseDTO;
import com.iot.platform.edge.device.dto.UpdateDeviceRequestDTO;
import com.iot.platform.edge.exception.DeviceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeviceServiceTest {

    @Mock
    private DeviceRepository deviceRepository;
    @Mock
    private EdgeOutboxRepository edgeOutboxRepository;
    @Mock
    private EdgeIdentityRepository edgeIdentityRepository;
    @Spy
    private ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @InjectMocks
    private DeviceService deviceService;

    private UUID existingId;
    private UUID edgeId;
    private Device existing;

    @BeforeEach
    void setUp() {
        existingId = UUID.randomUUID();
        edgeId = UUID.randomUUID();
        lenient().when(edgeIdentityRepository.findSingleton())
                .thenReturn(Optional.of(EdgeIdentity.builder().edgeId(edgeId).build()));
        existing = Device.builder()
                .id(existingId)
                .name("Bombilla salón")
                .type(DeviceType.LIGHT)
                .createdAt(Instant.now().minus(Duration.ofDays(1)))
                .updatedAt(Instant.now().minus(Duration.ofDays(1)))
                .capabilities(new ArrayList<>())
                .build();
        existing.addCapability(DeviceCapability.builder()
                .capabilityType(CapabilityType.POWER_SWITCH).build());
        existing.addCapability(DeviceCapability.builder()
                .capabilityType(CapabilityType.BRIGHTNESS).build());
    }

    private DeviceEventDTO captureEnqueuedEvent() throws Exception {
        ArgumentCaptor<EdgeOutbox> captor = ArgumentCaptor.forClass(EdgeOutbox.class);
        verify(edgeOutboxRepository).save(captor.capture());
        EdgeOutbox row = captor.getValue();
        assertThat(row.getType()).isEqualTo("DEVICE_EVENT");
        return objectMapper.readValue(row.getPayload(), DeviceEventDTO.class);
    }

    @Nested
    class Create {

        @Test
        void createDevice_persistsAndReturnsResponseWithCapabilities() {
            CreateDeviceRequestDTO request = CreateDeviceRequestDTO.builder()
                    .name("Bombilla cocina")
                    .type(DeviceType.LIGHT)
                    .capabilities(List.of(CapabilityType.POWER_SWITCH, CapabilityType.BRIGHTNESS))
                    .build();

            when(deviceRepository.save(any(Device.class)))
                    .thenAnswer(invocation -> {
                        Device d = invocation.getArgument(0);
                        d.setId(UUID.randomUUID());
                        return d;
                    });

            DeviceResponseDTO response = deviceService.createDevice(request);

            assertThat(response.getId()).isNotNull();
            assertThat(response.getName()).isEqualTo("Bombilla cocina");
            assertThat(response.getType()).isEqualTo(DeviceType.LIGHT);
            assertThat(response.getCapabilities())
                    .containsExactly(CapabilityType.POWER_SWITCH, CapabilityType.BRIGHTNESS);

            ArgumentCaptor<Device> captor = ArgumentCaptor.forClass(Device.class);
            verify(deviceRepository).save(captor.capture());
            Device saved = captor.getValue();
            assertThat(saved.getCapabilities()).hasSize(2);
            assertThat(saved.getCapabilities()).allSatisfy(c ->
                    assertThat(c.getDevice()).isSameAs(saved));
        }

        @Test
        void createDevice_deduplicatesRepeatedCapabilities() {
            CreateDeviceRequestDTO request = CreateDeviceRequestDTO.builder()
                    .name("Sensor multi")
                    .type(DeviceType.SENSOR)
                    .capabilities(List.of(
                            CapabilityType.TEMPERATURE,
                            CapabilityType.HUMIDITY,
                            CapabilityType.TEMPERATURE))
                    .build();

            when(deviceRepository.save(any(Device.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            DeviceResponseDTO response = deviceService.createDevice(request);

            assertThat(response.getCapabilities())
                    .containsExactly(CapabilityType.TEMPERATURE, CapabilityType.HUMIDITY);
        }
    }

    @Nested
    class Update {

        @Test
        void updateDevice_partial_onlyNameChanges_capabilitiesRemainIntact() {
            UpdateDeviceRequestDTO request = UpdateDeviceRequestDTO.builder()
                    .name("Bombilla principal")
                    .build();

            when(deviceRepository.findById(existingId)).thenReturn(Optional.of(existing));
            when(deviceRepository.save(any(Device.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            DeviceResponseDTO response = deviceService.updateDevice(existingId, request);

            assertThat(response.getName()).isEqualTo("Bombilla principal");
            assertThat(response.getType()).isEqualTo(DeviceType.LIGHT);
            assertThat(response.getCapabilities())
                    .containsExactly(CapabilityType.POWER_SWITCH, CapabilityType.BRIGHTNESS);
        }

        @Test
        void updateDevice_capabilities_replacesAllNotDelta() {
            UpdateDeviceRequestDTO request = UpdateDeviceRequestDTO.builder()
                    .capabilities(List.of(CapabilityType.POWER_SWITCH))
                    .build();

            when(deviceRepository.findById(existingId)).thenReturn(Optional.of(existing));
            when(deviceRepository.save(any(Device.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            DeviceResponseDTO response = deviceService.updateDevice(existingId, request);

            assertThat(response.getCapabilities())
                    .containsExactly(CapabilityType.POWER_SWITCH);
            assertThat(existing.getCapabilities()).hasSize(1);
            assertThat(existing.getCapabilities().get(0).getCapabilityType())
                    .isEqualTo(CapabilityType.POWER_SWITCH);
        }

        @Test
        void updateDevice_type_changes() {
            UpdateDeviceRequestDTO request = UpdateDeviceRequestDTO.builder()
                    .type(DeviceType.OUTLET)
                    .build();

            when(deviceRepository.findById(existingId)).thenReturn(Optional.of(existing));
            when(deviceRepository.save(any(Device.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            DeviceResponseDTO response = deviceService.updateDevice(existingId, request);

            assertThat(response.getType()).isEqualTo(DeviceType.OUTLET);
            assertThat(response.getCapabilities())
                    .containsExactly(CapabilityType.POWER_SWITCH, CapabilityType.BRIGHTNESS);
        }

        @Test
        void updateDevice_replacingWithSubset_flushesOrphansBeforeSaving() {
            UpdateDeviceRequestDTO request = UpdateDeviceRequestDTO.builder()
                    .capabilities(List.of(CapabilityType.POWER_SWITCH))
                    .build();

            when(deviceRepository.findById(existingId)).thenReturn(Optional.of(existing));
            when(deviceRepository.save(any(Device.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            DeviceResponseDTO response = deviceService.updateDevice(existingId, request);

            InOrder inOrder = inOrder(deviceRepository);
            inOrder.verify(deviceRepository).findById(existingId);
            inOrder.verify(deviceRepository).flush();
            inOrder.verify(deviceRepository).save(any(Device.class));

            assertThat(response.getCapabilities())
                    .containsExactly(CapabilityType.POWER_SWITCH);
            assertThat(existing.getCapabilities()).hasSize(1);
            assertThat(existing.getCapabilities().get(0).getCapabilityType())
                    .isEqualTo(CapabilityType.POWER_SWITCH);
        }

        @Test
        void updateDevice_partial_withoutCapabilitiesField_doesNotFlush() {
            UpdateDeviceRequestDTO request = UpdateDeviceRequestDTO.builder()
                    .name("Solo nombre")
                    .build();

            when(deviceRepository.findById(existingId)).thenReturn(Optional.of(existing));
            when(deviceRepository.save(any(Device.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            deviceService.updateDevice(existingId, request);

            verify(deviceRepository, never()).flush();
        }

        @Test
        void updateDevice_notFound_throws404Exception() {
            UUID missing = UUID.randomUUID();
            when(deviceRepository.findById(missing)).thenReturn(Optional.empty());

            assertThatThrownBy(() ->
                    deviceService.updateDevice(missing, UpdateDeviceRequestDTO.builder().name("x").build()))
                    .isInstanceOf(DeviceNotFoundException.class)
                    .hasMessageContaining(missing.toString());

            verify(deviceRepository, never()).save(any());
        }
    }

    @Nested
    class Delete {

        @Test
        void deleteDevice_existing_callsRepositoryDelete() {
            when(deviceRepository.findById(existingId)).thenReturn(Optional.of(existing));

            deviceService.deleteDevice(existingId);

            verify(deviceRepository).delete(existing);
        }

        @Test
        void deleteDevice_notFound_throws404AndDoesNotDelete() {
            UUID missing = UUID.randomUUID();
            when(deviceRepository.findById(missing)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> deviceService.deleteDevice(missing))
                    .isInstanceOf(DeviceNotFoundException.class);

            verify(deviceRepository, never()).delete(any());
        }
    }

    @Nested
    class Reads {

        @Test
        void listAll_returnsAllDevicesAsResponses() {
            Device second = Device.builder()
                    .id(UUID.randomUUID())
                    .name("Sensor habitación")
                    .type(DeviceType.SENSOR)
                    .createdAt(Instant.now())
                    .updatedAt(Instant.now())
                    .capabilities(new ArrayList<>())
                    .build();
            second.addCapability(DeviceCapability.builder()
                    .capabilityType(CapabilityType.TEMPERATURE).build());

            when(deviceRepository.findAll()).thenReturn(List.of(existing, second));

            List<DeviceResponseDTO> all = deviceService.listAll();

            assertThat(all).hasSize(2);
            assertThat(all).extracting(DeviceResponseDTO::getName)
                    .containsExactly("Bombilla salón", "Sensor habitación");
        }

        @Test
        void getById_existing_returnsResponseWithCapabilities() {
            when(deviceRepository.findById(existingId)).thenReturn(Optional.of(existing));

            DeviceResponseDTO response = deviceService.getById(existingId);

            assertThat(response.getId()).isEqualTo(existingId);
            assertThat(response.getCapabilities())
                    .containsExactly(CapabilityType.POWER_SWITCH, CapabilityType.BRIGHTNESS);
        }

        @Test
        void getById_notFound_throws404() {
            UUID missing = UUID.randomUUID();
            when(deviceRepository.findById(missing)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> deviceService.getById(missing))
                    .isInstanceOf(DeviceNotFoundException.class);
        }

        @Test
        void reads_doNotEnqueueOutboxEvents() {
            when(deviceRepository.findAll()).thenReturn(List.of(existing));
            when(deviceRepository.findById(existingId)).thenReturn(Optional.of(existing));

            deviceService.listAll();
            deviceService.getById(existingId);

            verify(edgeOutboxRepository, never()).save(any());
        }
    }

    @Nested
    class OutboxEvents {

        @Test
        void createDevice_enqueuesDeviceCreatedEvent() throws Exception {
            UUID newId = UUID.randomUUID();
            CreateDeviceRequestDTO request = CreateDeviceRequestDTO.builder()
                    .name("Bombilla cocina")
                    .type(DeviceType.LIGHT)
                    .capabilities(List.of(CapabilityType.POWER_SWITCH, CapabilityType.BRIGHTNESS))
                    .build();
            when(deviceRepository.save(any(Device.class)))
                    .thenAnswer(invocation -> {
                        Device d = invocation.getArgument(0);
                        d.setId(newId);
                        return d;
                    });

            deviceService.createDevice(request);

            DeviceEventDTO event = captureEnqueuedEvent();
            assertThat(event.getEventType()).isEqualTo(DeviceEventType.DEVICE_CREATED);
            assertThat(event.getEdgeId()).isEqualTo(edgeId);
            assertThat(event.getDeviceId()).isEqualTo(newId);
            assertThat(event.getTimestamp()).isNotNull();
            assertThat(event.getDevice()).isNotNull();
            assertThat(event.getDevice().getId()).isEqualTo(newId);
            assertThat(event.getDevice().getName()).isEqualTo("Bombilla cocina");
            assertThat(event.getDevice().getCapabilities())
                    .containsExactly(CapabilityType.POWER_SWITCH, CapabilityType.BRIGHTNESS);
        }

        @Test
        void updateDevice_enqueuesDeviceUpdatedEventWithCurrentCapabilities() throws Exception {
            UpdateDeviceRequestDTO request = UpdateDeviceRequestDTO.builder()
                    .capabilities(List.of(CapabilityType.POWER_SWITCH))
                    .build();
            when(deviceRepository.findById(existingId)).thenReturn(Optional.of(existing));
            when(deviceRepository.save(any(Device.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            deviceService.updateDevice(existingId, request);

            DeviceEventDTO event = captureEnqueuedEvent();
            assertThat(event.getEventType()).isEqualTo(DeviceEventType.DEVICE_UPDATED);
            assertThat(event.getDeviceId()).isEqualTo(existingId);
            assertThat(event.getDevice()).isNotNull();
            assertThat(event.getDevice().getCapabilities())
                    .containsExactly(CapabilityType.POWER_SWITCH);
        }

        @Test
        void deleteDevice_enqueuesDeviceDeletedEventWithNullBody() throws Exception {
            when(deviceRepository.findById(existingId)).thenReturn(Optional.of(existing));

            deviceService.deleteDevice(existingId);

            DeviceEventDTO event = captureEnqueuedEvent();
            assertThat(event.getEventType()).isEqualTo(DeviceEventType.DEVICE_DELETED);
            assertThat(event.getDeviceId()).isEqualTo(existingId);
            assertThat(event.getEdgeId()).isEqualTo(edgeId);
            assertThat(event.getDevice()).isNull();
        }
    }
}
