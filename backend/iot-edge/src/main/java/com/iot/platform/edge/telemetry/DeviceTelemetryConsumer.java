package com.iot.platform.edge.telemetry;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iot.platform.common.device.DeviceTelemetryDTO;
import com.iot.platform.edge.device.Device;
import com.iot.platform.edge.device.DeviceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.IMqttMessageListener;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class DeviceTelemetryConsumer implements IMqttMessageListener {

    private final ObjectMapper objectMapper;
    private final DeviceRepository deviceRepository;
    private final TelemetryRepository telemetryRepository;

    @Override
    @Transactional
    public void messageArrived(String topic, MqttMessage message) {
        UUID deviceId = deviceIdFromTopic(topic);
        if (deviceId == null) {
            return;
        }
        DeviceTelemetryDTO dto;
        try {
            dto = objectMapper.readValue(message.getPayload(), DeviceTelemetryDTO.class);
        } catch (Exception e) {
            log.error("Malformed telemetry on topic {}; discarded: {}", topic, e.getMessage());
            return;
        }
        ingest(deviceId, dto);
    }

    private void ingest(UUID deviceId, DeviceTelemetryDTO dto) {
        if (dto.getCapability() == null) {
            log.warn("Telemetry for device {} without capability; discarded", deviceId);
            return;
        }

        Device device = deviceRepository.findById(deviceId).orElse(null);
        if (device == null) {
            log.warn("Telemetry for unknown device {}; discarded", deviceId);
            return;
        }

        boolean deviceHasCapability = device.getCapabilities().stream()
                .anyMatch(c -> c.getCapabilityType() == dto.getCapability());
        if (!deviceHasCapability) {
            log.warn("Telemetry for device {} with capability {} not assigned to it; discarded",
                    deviceId, dto.getCapability());
            return;
        }

        if (!dto.getCapability().isValidType(dto.getValue())) {
            log.warn("Telemetry for device {} capability {}: value {} is not a valid {}; discarded",
                    deviceId, dto.getCapability(), dto.getValue(), dto.getCapability().getDataType());
            return;
        }

        Instant readingInstant = dto.getTimestamp() != null ? dto.getTimestamp() : Instant.now();

        Telemetry telemetry = Telemetry.builder()
                .deviceId(deviceId)
                .capabilityType(dto.getCapability())
                .value(objectMapper.valueToTree(dto.getValue()))
                .timestamp(readingInstant)
                .synced(false)
                .build();
        telemetryRepository.save(telemetry);

        log.debug("Telemetry stored: device={} capability={} value={}",
                deviceId, dto.getCapability(), dto.getValue());
    }

    private UUID deviceIdFromTopic(String topic) {
        String[] parts = topic.split("/");
        if (parts.length != 3) {
            log.warn("Unexpected telemetry topic '{}'; discarded", topic);
            return null;
        }
        try {
            return UUID.fromString(parts[1]);
        } catch (IllegalArgumentException e) {
            log.warn("Telemetry topic '{}' has a non-UUID device segment; discarded", topic);
            return null;
        }
    }
}
