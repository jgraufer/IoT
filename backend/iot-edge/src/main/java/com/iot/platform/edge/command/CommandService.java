package com.iot.platform.edge.command;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.iot.platform.common.device.CapabilityKind;
import com.iot.platform.common.device.CapabilityType;
import com.iot.platform.common.device.DeviceCommandDTO;
import com.iot.platform.common.protocol.MqttTopics;
import com.iot.platform.edge.device.Device;
import com.iot.platform.edge.device.DeviceRepository;
import com.iot.platform.edge.exception.DeviceNotFoundException;
import com.iot.platform.edge.exception.InvalidCommandException;
import com.iot.platform.edge.mqtt.EdgeMqttClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CommandService {

    private final DeviceRepository deviceRepository;
    private final DeviceCommandRepository deviceCommandRepository;
    private final EdgeMqttClient edgeMqttClient;
    private final ObjectMapper objectMapper;

    @Transactional
    public DeviceCommandDTO issueCommand(UUID deviceId, CapabilityType capability,
                                         Object value, UUID issuedBy) {
        Device device = deviceRepository.findById(deviceId)
                .orElseThrow(() -> new DeviceNotFoundException(deviceId));

        boolean deviceHasCapability = device.getCapabilities().stream()
                .anyMatch(c -> c.getCapabilityType() == capability);
        if (!deviceHasCapability) {
            throw new InvalidCommandException(
                    "Device " + deviceId + " does not have capability " + capability);
        }

        if (capability.getKind() != CapabilityKind.ACTIVE) {
            throw new InvalidCommandException(
                    "Capability " + capability + " is " + capability.getKind()
                            + " and cannot receive commands");
        }

        if (!capability.isValidType(value)) {
            throw new InvalidCommandException(
                    "Value " + value + " is not a valid " + capability.getDataType()
                            + " for capability " + capability);
        }

        if (!capability.isInRange(value)) {
            throw new InvalidCommandException(
                    "Value " + value + " is out of range [" + capability.getMin()
                            + ", " + capability.getMax() + "] for capability " + capability);
        }

        DeviceCommand command = deviceCommandRepository.save(DeviceCommand.builder()
                .deviceId(deviceId)
                .capabilityType(capability)
                .value(objectMapper.valueToTree(value))
                .issuedBy(issuedBy)
                .build());

        DeviceCommandDTO payload = DeviceCommandDTO.builder()
                .commandId(command.getId())
                .capability(capability)
                .value(value)
                .issuedAt(command.getIssuedAt())
                .build();

        publish(deviceId, payload);

        log.info("Command issued: device={} capability={} value={} issuedBy={} commandId={}",
                deviceId, capability, value, issuedBy, command.getId());
        return payload;
    }

    private void publish(UUID deviceId, DeviceCommandDTO payload) {
        try {
            edgeMqttClient.publish(
                    MqttTopics.deviceCommand(deviceId),
                    objectMapper.writeValueAsString(payload));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize DeviceCommandDTO", e);
        } catch (MqttException e) {
            throw new IllegalStateException(
                    "Failed to publish command to LAN broker for device " + deviceId, e);
        }
    }
}
