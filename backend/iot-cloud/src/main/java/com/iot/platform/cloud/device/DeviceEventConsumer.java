package com.iot.platform.cloud.device;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iot.platform.common.device.CapabilityType;
import com.iot.platform.common.device.DeviceDTO;
import com.iot.platform.common.device.DeviceEventDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.IMqttMessageListener;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class DeviceEventConsumer implements IMqttMessageListener {

    private final ObjectMapper objectMapper;
    private final DeviceRepository deviceRepository;

    @Override
    @Transactional
    public void messageArrived(String topic, MqttMessage message) {
        DeviceEventDTO dto;
        try {
            dto = objectMapper.readValue(message.getPayload(), DeviceEventDTO.class);
        } catch (Exception e) {
            log.error("Malformed device event on topic {}; discarded: {}", topic, e.getMessage());
            return;
        }
        apply(dto);
    }

    void apply(DeviceEventDTO dto) {
        if (dto.getEventType() == null) {
            log.warn("DeviceEvent received without eventType; ignoring");
            return;
        }
        if (dto.getDeviceId() == null) {
            log.warn("DeviceEvent received without deviceId; ignoring");
            return;
        }
        if (dto.getTimestamp() == null) {
            log.warn("DeviceEvent received without timestamp; ignoring");
            return;
        }

        switch (dto.getEventType()) {
            case DEVICE_CREATED, DEVICE_UPDATED -> upsert(dto);
            case DEVICE_DELETED -> delete(dto);
        }
    }

    private void upsert(DeviceEventDTO dto) {
        DeviceDTO body = dto.getDevice();
        if (body == null) {
            log.warn("DeviceEvent {} for device {} has no device body; ignoring",
                    dto.getEventType(), dto.getDeviceId());
            return;
        }

        Optional<Device> maybeExisting = deviceRepository.findById(dto.getDeviceId());
        if (maybeExisting.isPresent() && isObsolete(maybeExisting.get(), dto.getTimestamp())) {
            log.debug("DeviceEvent ignored (obsolete timestamp) for device {}", dto.getDeviceId());
            return;
        }

        Instant ts = dto.getTimestamp();
        boolean isNew = maybeExisting.isEmpty();

        Device device = maybeExisting.orElseGet(() -> Device.builder()
                .id(dto.getDeviceId())
                .createdAt(ts)
                .build());
        device.setEdgeId(dto.getEdgeId());
        device.setName(body.getName());
        device.setType(body.getType());
        device.setUpdatedAt(ts);
        device.setLastSyncedAt(ts);

        if (!isNew) {
            device.getCapabilities().clear();
            deviceRepository.flush();
        }
        replaceCapabilities(device, body.getCapabilities());

        deviceRepository.save(device);
        log.info("DeviceEvent {} applied: deviceId={}, edgeId={}",
                dto.getEventType(), dto.getDeviceId(), dto.getEdgeId());
    }

    private void delete(DeviceEventDTO dto) {
        Optional<Device> maybeExisting = deviceRepository.findById(dto.getDeviceId());
        if (maybeExisting.isEmpty()) {
            log.info("DEVICE_DELETED ignored (already absent) for device {}", dto.getDeviceId());
            return;
        }
        deviceRepository.delete(maybeExisting.get());
        log.info("DEVICE_DELETED applied: deviceId={}", dto.getDeviceId());
    }

    private void replaceCapabilities(Device device, List<CapabilityType> capabilities) {
        LinkedHashSet<CapabilityType> unique = new LinkedHashSet<>(
                capabilities == null ? List.of() : capabilities);
        for (CapabilityType type : unique) {
            DeviceCapability cap = DeviceCapability.builder()
                    .device(device)
                    .capabilityType(type)
                    .build();
            device.getCapabilities().add(cap);
        }
    }

    private boolean isObsolete(Device existing, Instant incoming) {
        Instant last = existing.getLastSyncedAt();
        if (last == null) {
            return false;
        }
        return !last.isBefore(incoming);
    }
}
