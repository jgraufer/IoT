package com.iot.platform.cloud.command;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iot.platform.cloud.device.Device;
import com.iot.platform.cloud.device.DeviceCapability;
import com.iot.platform.cloud.device.DeviceRepository;
import com.iot.platform.cloud.edge.access.EdgeAccessGuard;
import com.iot.platform.cloud.edge.access.UserEdgeAccess;
import com.iot.platform.cloud.edgesync.outbox.CloudOutbox;
import com.iot.platform.cloud.edgesync.outbox.CloudOutboxRepository;
import com.iot.platform.cloud.exception.AccessDeniedException;
import com.iot.platform.cloud.exception.InvalidCommandException;
import com.iot.platform.cloud.exception.ResourceNotFoundException;
import com.iot.platform.common.device.CapabilityType;
import com.iot.platform.common.device.CommandDTO;
import com.iot.platform.common.device.DeviceType;
import com.iot.platform.common.edge.EdgeRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommandProxyServiceTest {

    @Mock
    private EdgeAccessGuard edgeAccessGuard;
    @Mock
    private DeviceRepository deviceRepository;
    @Mock
    private CloudOutboxRepository outboxRepository;
    @Spy
    private ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @InjectMocks
    private CommandProxyService service;

    private UUID edgeId;
    private UUID deviceId;
    private UUID issuedBy;
    private Device lightWithSwitchAndBrightness;

    @BeforeEach
    void setUp() {
        edgeId = UUID.randomUUID();
        deviceId = UUID.randomUUID();
        issuedBy = UUID.randomUUID();
        lightWithSwitchAndBrightness = device(
                CapabilityType.POWER_SWITCH, CapabilityType.BRIGHTNESS);
    }

    private Device device(CapabilityType... caps) {
        Device d = Device.builder()
                .id(deviceId)
                .edgeId(edgeId)
                .name("Bombilla salón")
                .type(DeviceType.LIGHT)
                .capabilities(new ArrayList<>())
                .build();
        for (CapabilityType cap : caps) {
            d.getCapabilities().add(DeviceCapability.builder()
                    .device(d).capabilityType(cap).build());
        }
        return d;
    }

    private void grantAccess(EdgeRole role) {
        when(edgeAccessGuard.requireAccess(edgeId, issuedBy))
                .thenReturn(UserEdgeAccess.builder()
                        .userId(issuedBy).edgeId(edgeId).role(role).build());
    }

    @Test
    void proxyCommand_noAccess_throws403() {
        when(edgeAccessGuard.requireAccess(edgeId, issuedBy))
                .thenThrow(new AccessDeniedException("You do not have access to this edge"));

        assertThatThrownBy(() -> service.proxyCommand(
                edgeId, deviceId, CapabilityType.POWER_SWITCH, true, issuedBy))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(deviceRepository);
        verify(outboxRepository, never()).save(any());
    }

    @Test
    void proxyCommand_deviceNotInEdge_throws404() {
        grantAccess(EdgeRole.MEMBER);
        when(deviceRepository.findByIdAndEdgeId(deviceId, edgeId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.proxyCommand(
                edgeId, deviceId, CapabilityType.POWER_SWITCH, true, issuedBy))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(outboxRepository, never()).save(any());
    }

    @Test
    void proxyCommand_capabilityNotAssigned_throws400() {
        grantAccess(EdgeRole.MEMBER);
        when(deviceRepository.findByIdAndEdgeId(deviceId, edgeId))
                .thenReturn(Optional.of(lightWithSwitchAndBrightness));

        assertThatThrownBy(() -> service.proxyCommand(
                edgeId, deviceId, CapabilityType.TEMPERATURE, 22.0, issuedBy))
                .isInstanceOf(InvalidCommandException.class)
                .hasMessageContaining("does not have capability");

        verify(outboxRepository, never()).save(any());
    }

    @Test
    void proxyCommand_passiveCapability_throws400() {
        grantAccess(EdgeRole.MEMBER);
        Device sensor = device(CapabilityType.TEMPERATURE);
        when(deviceRepository.findByIdAndEdgeId(deviceId, edgeId)).thenReturn(Optional.of(sensor));

        assertThatThrownBy(() -> service.proxyCommand(
                edgeId, deviceId, CapabilityType.TEMPERATURE, 22.0, issuedBy))
                .isInstanceOf(InvalidCommandException.class)
                .hasMessageContaining("PASSIVE");

        verify(outboxRepository, never()).save(any());
    }

    @Test
    void proxyCommand_invalidValueType_throws400() {
        grantAccess(EdgeRole.MEMBER);
        when(deviceRepository.findByIdAndEdgeId(deviceId, edgeId))
                .thenReturn(Optional.of(lightWithSwitchAndBrightness));

        assertThatThrownBy(() -> service.proxyCommand(
                edgeId, deviceId, CapabilityType.POWER_SWITCH, 5, issuedBy))
                .isInstanceOf(InvalidCommandException.class)
                .hasMessageContaining("not a valid");

        verify(outboxRepository, never()).save(any());
    }

    @Test
    void proxyCommand_valueOutOfRange_throws400() {
        grantAccess(EdgeRole.MEMBER);
        when(deviceRepository.findByIdAndEdgeId(deviceId, edgeId))
                .thenReturn(Optional.of(lightWithSwitchAndBrightness));

        assertThatThrownBy(() -> service.proxyCommand(
                edgeId, deviceId, CapabilityType.BRIGHTNESS, 150, issuedBy))
                .isInstanceOf(InvalidCommandException.class)
                .hasMessageContaining("out of range");

        verify(outboxRepository, never()).save(any());
    }

    @Test
    void proxyCommand_valid_enqueuesCommandInOutbox() throws Exception {
        grantAccess(EdgeRole.MEMBER);
        when(deviceRepository.findByIdAndEdgeId(deviceId, edgeId))
                .thenReturn(Optional.of(lightWithSwitchAndBrightness));

        CommandDTO returned = service.proxyCommand(
                edgeId, deviceId, CapabilityType.POWER_SWITCH, true, issuedBy);

        ArgumentCaptor<CloudOutbox> captor = ArgumentCaptor.forClass(CloudOutbox.class);
        verify(outboxRepository).save(captor.capture());
        CloudOutbox row = captor.getValue();
        assertThat(row.getEdgeId()).isEqualTo(edgeId);
        assertThat(row.getMessageType()).isEqualTo("COMMAND");

        CommandDTO payload = objectMapper.readValue(row.getPayload(), CommandDTO.class);
        assertThat(payload.getEdgeId()).isEqualTo(edgeId);
        assertThat(payload.getDeviceId()).isEqualTo(deviceId);
        assertThat(payload.getCapability()).isEqualTo(CapabilityType.POWER_SWITCH);
        assertThat(payload.getValue()).isEqualTo(true);
        assertThat(payload.getIssuedBy()).isEqualTo(issuedBy);
        assertThat(payload.getIssuedAt()).isNotNull();

        assertThat(returned.getDeviceId()).isEqualTo(deviceId);
        assertThat(returned.getValue()).isEqualTo(true);
        assertThat(returned.getIssuedBy()).isEqualTo(issuedBy);
    }
}
