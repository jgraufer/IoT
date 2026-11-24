package com.iot.platform.edge.command;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iot.platform.common.device.CapabilityType;
import com.iot.platform.common.device.DeviceCommandDTO;
import com.iot.platform.common.device.DeviceType;
import com.iot.platform.common.protocol.MqttTopics;
import com.iot.platform.edge.device.Device;
import com.iot.platform.edge.device.DeviceCapability;
import com.iot.platform.edge.device.DeviceRepository;
import com.iot.platform.edge.exception.DeviceNotFoundException;
import com.iot.platform.edge.exception.InvalidCommandException;
import com.iot.platform.edge.mqtt.EdgeMqttClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CommandServiceTest {

    @Mock
    private DeviceRepository deviceRepository;
    @Mock
    private DeviceCommandRepository deviceCommandRepository;
    @Mock
    private EdgeMqttClient edgeMqttClient;
    @Spy
    private ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @InjectMocks
    private CommandService commandService;

    private UUID deviceId;
    private UUID issuedBy;
    private Device lightWithSwitchAndBrightness;

    @BeforeEach
    void setUp() {
        deviceId = UUID.randomUUID();
        issuedBy = UUID.randomUUID();
        lightWithSwitchAndBrightness = Device.builder()
                .id(deviceId)
                .name("Bombilla salón")
                .type(DeviceType.LIGHT)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .capabilities(new ArrayList<>())
                .build();
        lightWithSwitchAndBrightness.addCapability(DeviceCapability.builder()
                .capabilityType(CapabilityType.POWER_SWITCH).build());
        lightWithSwitchAndBrightness.addCapability(DeviceCapability.builder()
                .capabilityType(CapabilityType.BRIGHTNESS).build());
    }

    @Test
    void issueCommand_deviceNotFound_throws404() {
        when(deviceRepository.findById(deviceId)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                commandService.issueCommand(deviceId, CapabilityType.POWER_SWITCH, true, issuedBy))
                .isInstanceOf(DeviceNotFoundException.class)
                .hasMessageContaining(deviceId.toString());

        verify(deviceCommandRepository, never()).save(any());
        verifyNoInteractions(edgeMqttClient);
    }

    @Test
    void issueCommand_capabilityNotAssignedToDevice_throwsInvalidCommand() {
        when(deviceRepository.findById(deviceId))
                .thenReturn(Optional.of(lightWithSwitchAndBrightness));

        assertThatThrownBy(() ->
                commandService.issueCommand(deviceId, CapabilityType.TEMPERATURE, 22, issuedBy))
                .isInstanceOf(InvalidCommandException.class)
                .hasMessageContaining("does not have capability");

        verify(deviceCommandRepository, never()).save(any());
        verifyNoInteractions(edgeMqttClient);
    }

    @Test
    void issueCommand_passiveCapability_throwsInvalidCommand() {
        Device sensor = Device.builder()
                .id(deviceId)
                .name("Sensor salón")
                .type(DeviceType.SENSOR)
                .capabilities(new ArrayList<>())
                .build();
        sensor.addCapability(DeviceCapability.builder()
                .capabilityType(CapabilityType.TEMPERATURE).build());
        when(deviceRepository.findById(deviceId)).thenReturn(Optional.of(sensor));

        assertThatThrownBy(() ->
                commandService.issueCommand(deviceId, CapabilityType.TEMPERATURE, 22, issuedBy))
                .isInstanceOf(InvalidCommandException.class)
                .hasMessageContaining("PASSIVE");

        verify(deviceCommandRepository, never()).save(any());
        verifyNoInteractions(edgeMqttClient);
    }

    @Test
    void issueCommand_invalidValueType_throwsInvalidCommand() {
        when(deviceRepository.findById(deviceId))
                .thenReturn(Optional.of(lightWithSwitchAndBrightness));

        assertThatThrownBy(() ->
                commandService.issueCommand(deviceId, CapabilityType.POWER_SWITCH, 5, issuedBy))
                .isInstanceOf(InvalidCommandException.class)
                .hasMessageContaining("not a valid");

        verify(deviceCommandRepository, never()).save(any());
        verifyNoInteractions(edgeMqttClient);
    }

    @Test
    void issueCommand_valueOutOfRange_throwsInvalidCommand() {
        when(deviceRepository.findById(deviceId))
                .thenReturn(Optional.of(lightWithSwitchAndBrightness));

        assertThatThrownBy(() ->
                commandService.issueCommand(deviceId, CapabilityType.BRIGHTNESS, 150, issuedBy))
                .isInstanceOf(InvalidCommandException.class)
                .hasMessageContaining("out of range");

        verify(deviceCommandRepository, never()).save(any());
        verifyNoInteractions(edgeMqttClient);
    }

    @Test
    void issueCommand_valid_persistsAndPublishes() throws Exception {
        when(deviceRepository.findById(deviceId))
                .thenReturn(Optional.of(lightWithSwitchAndBrightness));
        UUID commandId = UUID.randomUUID();
        Instant issuedAt = Instant.now();
        when(deviceCommandRepository.save(any(DeviceCommand.class)))
                .thenAnswer(invocation -> {
                    DeviceCommand c = invocation.getArgument(0);
                    c.setId(commandId);
                    c.setIssuedAt(issuedAt);
                    return c;
                });

        DeviceCommandDTO result =
                commandService.issueCommand(deviceId, CapabilityType.POWER_SWITCH, true, issuedBy);

        ArgumentCaptor<DeviceCommand> commandCaptor = ArgumentCaptor.forClass(DeviceCommand.class);
        verify(deviceCommandRepository).save(commandCaptor.capture());
        DeviceCommand persisted = commandCaptor.getValue();
        assertThat(persisted.getDeviceId()).isEqualTo(deviceId);
        assertThat(persisted.getCapabilityType()).isEqualTo(CapabilityType.POWER_SWITCH);
        assertThat(persisted.getValue()).isEqualTo(objectMapper.valueToTree(true));
        assertThat(persisted.getIssuedBy()).isEqualTo(issuedBy);

        ArgumentCaptor<String> topicCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(edgeMqttClient).publish(topicCaptor.capture(), payloadCaptor.capture());
        assertThat(topicCaptor.getValue()).isEqualTo(MqttTopics.deviceCommand(deviceId));

        DeviceCommandDTO published =
                objectMapper.readValue(payloadCaptor.getValue(), DeviceCommandDTO.class);
        assertThat(published.getCommandId()).isEqualTo(commandId);
        assertThat(published.getCapability()).isEqualTo(CapabilityType.POWER_SWITCH);
        assertThat(published.getValue()).isEqualTo(true);
        assertThat(published.getIssuedAt()).isNotNull();

        assertThat(result.getCommandId()).isEqualTo(commandId);
        assertThat(result.getCapability()).isEqualTo(CapabilityType.POWER_SWITCH);
        assertThat(result.getValue()).isEqualTo(true);
    }

    @Test
    void issueCommand_integerValue_persistsAsJsonNode() {
        when(deviceRepository.findById(deviceId))
                .thenReturn(Optional.of(lightWithSwitchAndBrightness));
        when(deviceCommandRepository.save(any(DeviceCommand.class)))
                .thenAnswer(invocation -> {
                    DeviceCommand c = invocation.getArgument(0);
                    c.setId(UUID.randomUUID());
                    c.setIssuedAt(Instant.now());
                    return c;
                });

        DeviceCommandDTO result =
                commandService.issueCommand(deviceId, CapabilityType.BRIGHTNESS, 75, issuedBy);

        ArgumentCaptor<DeviceCommand> commandCaptor = ArgumentCaptor.forClass(DeviceCommand.class);
        verify(deviceCommandRepository).save(commandCaptor.capture());
        DeviceCommand persisted = commandCaptor.getValue();
        assertThat(persisted.getCapabilityType()).isEqualTo(CapabilityType.BRIGHTNESS);
        assertThat(persisted.getValue()).isEqualTo(objectMapper.valueToTree(75));
        assertThat(persisted.getValue().isInt()).isTrue();

        assertThat(result.getValue()).isEqualTo(75);
    }
}
