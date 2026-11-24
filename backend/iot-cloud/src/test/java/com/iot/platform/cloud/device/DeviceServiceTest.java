package com.iot.platform.cloud.device;

import com.iot.platform.cloud.device.dto.DeviceResponseDTO;
import com.iot.platform.cloud.edge.access.EdgeAccessGuard;
import com.iot.platform.cloud.edge.access.UserEdgeAccess;
import com.iot.platform.cloud.exception.AccessDeniedException;
import com.iot.platform.cloud.exception.ResourceNotFoundException;
import com.iot.platform.common.device.CapabilityType;
import com.iot.platform.common.device.DeviceType;
import com.iot.platform.common.edge.EdgeRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeviceServiceTest {

    @Mock
    private DeviceRepository deviceRepository;
    @Mock
    private EdgeAccessGuard edgeAccessGuard;

    @InjectMocks
    private DeviceService service;

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

    private Device sampleDevice() {
        Device device = Device.builder()
                .id(deviceId)
                .edgeId(edgeId)
                .name("Lámpara salón")
                .type(DeviceType.LIGHT)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .lastSyncedAt(Instant.now())
                .build();
        device.getCapabilities().add(DeviceCapability.builder()
                .device(device).capabilityType(CapabilityType.POWER_SWITCH).build());
        device.getCapabilities().add(DeviceCapability.builder()
                .device(device).capabilityType(CapabilityType.BRIGHTNESS).build());
        return device;
    }

    @Test
    void listByEdge_withAccess_returnsDevices() {
        grantAccess(EdgeRole.MEMBER);
        when(deviceRepository.findByEdgeId(edgeId)).thenReturn(List.of(sampleDevice()));

        List<DeviceResponseDTO> result = service.listByEdge(edgeId, userId);

        assertThat(result).hasSize(1);
        DeviceResponseDTO dto = result.get(0);
        assertThat(dto.getId()).isEqualTo(deviceId);
        assertThat(dto.getEdgeId()).isEqualTo(edgeId);
        assertThat(dto.getName()).isEqualTo("Lámpara salón");
        assertThat(dto.getCapabilities())
                .containsExactly(CapabilityType.POWER_SWITCH, CapabilityType.BRIGHTNESS);
    }

    @Test
    void listByEdge_noAccess_throws403() {
        denyAccess();

        assertThatThrownBy(() -> service.listByEdge(edgeId, userId))
                .isInstanceOf(AccessDeniedException.class);

        verify(deviceRepository, never()).findByEdgeId(edgeId);
    }

    @Test
    void getById_withAccess_returnsDevice() {
        grantAccess(EdgeRole.OWNER);
        when(deviceRepository.findByIdAndEdgeId(deviceId, edgeId))
                .thenReturn(Optional.of(sampleDevice()));

        DeviceResponseDTO dto = service.getById(edgeId, deviceId, userId);

        assertThat(dto.getId()).isEqualTo(deviceId);
        assertThat(dto.getType()).isEqualTo(DeviceType.LIGHT);
    }

    @Test
    void getById_deviceNotInEdge_throws404() {
        grantAccess(EdgeRole.ADMIN);
        when(deviceRepository.findByIdAndEdgeId(deviceId, edgeId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(edgeId, deviceId, userId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getById_noAccess_throws403() {
        denyAccess();

        assertThatThrownBy(() -> service.getById(edgeId, deviceId, userId))
                .isInstanceOf(AccessDeniedException.class);

        verify(deviceRepository, never()).findByIdAndEdgeId(deviceId, edgeId);
    }
}
