package com.iot.platform.cloud.device;

import com.iot.platform.cloud.device.dto.DeviceResponseDTO;
import com.iot.platform.cloud.edge.access.EdgeAccessGuard;
import com.iot.platform.cloud.exception.AccessDeniedException;
import com.iot.platform.cloud.exception.ResourceNotFoundException;
import com.iot.platform.common.device.CapabilityType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceService {

    private final DeviceRepository deviceRepository;
    private final EdgeAccessGuard edgeAccessGuard;

    @Transactional(readOnly = true)
    public List<DeviceResponseDTO> listByEdge(UUID edgeId, UUID requesterId) {
        edgeAccessGuard.requireAccess(edgeId, requesterId);
        return deviceRepository.findByEdgeId(edgeId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public DeviceResponseDTO getById(UUID edgeId, UUID deviceId, UUID requesterId) {
        edgeAccessGuard.requireAccess(edgeId, requesterId);
        Device device = deviceRepository.findByIdAndEdgeId(deviceId, edgeId)
                .orElseThrow(() -> new ResourceNotFoundException("Device", "id", deviceId));
        return toResponse(device);
    }

    private DeviceResponseDTO toResponse(Device device) {
        List<CapabilityType> caps = device.getCapabilities().stream()
                .map(DeviceCapability::getCapabilityType)
                .toList();
        return DeviceResponseDTO.builder()
                .id(device.getId())
                .edgeId(device.getEdgeId())
                .name(device.getName())
                .type(device.getType())
                .capabilities(caps)
                .createdAt(device.getCreatedAt())
                .updatedAt(device.getUpdatedAt())
                .lastSyncedAt(device.getLastSyncedAt())
                .build();
    }
}
