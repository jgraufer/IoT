package com.iot.platform.edge.device;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.iot.platform.common.device.CapabilityType;
import com.iot.platform.common.device.DeviceDTO;
import com.iot.platform.common.device.DeviceEventDTO;
import com.iot.platform.common.device.DeviceEventType;
import com.iot.platform.edge.bootstrap.EdgeIdentity;
import com.iot.platform.edge.bootstrap.EdgeIdentityRepository;
import com.iot.platform.edge.cloudsync.outbox.EdgeOutbox;
import com.iot.platform.edge.cloudsync.outbox.EdgeOutboxRepository;
import com.iot.platform.edge.device.dto.CreateDeviceRequestDTO;
import com.iot.platform.edge.device.dto.DeviceResponseDTO;
import com.iot.platform.edge.device.dto.UpdateDeviceRequestDTO;
import com.iot.platform.edge.exception.DeviceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceService {

    private final DeviceRepository deviceRepository;
    private final EdgeOutboxRepository edgeOutboxRepository;
    private final EdgeIdentityRepository edgeIdentityRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public DeviceResponseDTO createDevice(CreateDeviceRequestDTO request) {
        Device device = Device.builder()
                .name(request.getName())
                .type(request.getType())
                .build();

        applyCapabilities(device, request.getCapabilities());

        Device saved = deviceRepository.save(device);
        enqueueDeviceEvent(DeviceEventType.DEVICE_CREATED, saved, saved.getId());
        log.info("Device creado: id={} name={} type={}", saved.getId(), saved.getName(), saved.getType());
        return toResponse(saved);
    }

    @Transactional
    public DeviceResponseDTO updateDevice(UUID id, UpdateDeviceRequestDTO request) {
        Device device = deviceRepository.findById(id)
                .orElseThrow(() -> new DeviceNotFoundException(id));

        if (request.getName() != null) {
            device.setName(request.getName());
        }
        if (request.getType() != null) {
            device.setType(request.getType());
        }
        if (request.getCapabilities() != null) {
            device.getCapabilities().clear();
            deviceRepository.flush();
            applyCapabilities(device, request.getCapabilities());
        }

        Device saved = deviceRepository.save(device);
        enqueueDeviceEvent(DeviceEventType.DEVICE_UPDATED, saved, saved.getId());
        log.info("Device actualizado: id={}", saved.getId());
        return toResponse(saved);
    }

    @Transactional
    public void deleteDevice(UUID id) {
        Device device = deviceRepository.findById(id)
                .orElseThrow(() -> new DeviceNotFoundException(id));
        deviceRepository.delete(device);
        enqueueDeviceEvent(DeviceEventType.DEVICE_DELETED, null, id);
        log.info("Device eliminado: id={}", id);
    }

    @Transactional(readOnly = true)
    public List<DeviceResponseDTO> listAll() {
        return deviceRepository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public DeviceResponseDTO getById(UUID id) {
        Device device = deviceRepository.findById(id)
                .orElseThrow(() -> new DeviceNotFoundException(id));
        return toResponse(device);
    }

    private void applyCapabilities(Device device, List<CapabilityType> capabilities) {
        LinkedHashSet<CapabilityType> unique = new LinkedHashSet<>(capabilities);
        for (CapabilityType type : unique) {
            device.addCapability(DeviceCapability.builder()
                    .capabilityType(type)
                    .build());
        }
    }

    private void enqueueDeviceEvent(DeviceEventType type, Device device, UUID deviceId) {
        UUID edgeId = edgeIdentityRepository.findSingleton()
                .map(EdgeIdentity::getEdgeId)
                .orElseThrow(() -> new IllegalStateException(
                        "Edge identity must exist before publishing device events"));

        DeviceDTO deviceDTO = device == null ? null : DeviceDTO.builder()
                .id(device.getId())
                .name(device.getName())
                .type(device.getType())
                .capabilities(device.getCapabilities().stream()
                        .map(DeviceCapability::getCapabilityType)
                        .toList())
                .build();

        DeviceEventDTO event = DeviceEventDTO.builder()
                .eventType(type)
                .edgeId(edgeId)
                .deviceId(deviceId)
                .device(deviceDTO)
                .timestamp(Instant.now())
                .build();

        try {
            EdgeOutbox row = EdgeOutbox.builder()
                    .type("DEVICE_EVENT")
                    .payload(objectMapper.writeValueAsString(event))
                    .attempts(0)
                    .build();
            edgeOutboxRepository.save(row);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize DeviceEventDTO", e);
        }
    }

    private DeviceResponseDTO toResponse(Device device) {
        List<CapabilityType> caps = device.getCapabilities().stream()
                .map(DeviceCapability::getCapabilityType)
                .toList();
        return DeviceResponseDTO.builder()
                .id(device.getId())
                .name(device.getName())
                .type(device.getType())
                .capabilities(caps)
                .createdAt(device.getCreatedAt())
                .updatedAt(device.getUpdatedAt())
                .build();
    }
}
