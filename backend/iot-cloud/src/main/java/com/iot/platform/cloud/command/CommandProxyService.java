package com.iot.platform.cloud.command;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.iot.platform.cloud.device.Device;
import com.iot.platform.cloud.device.DeviceRepository;
import com.iot.platform.cloud.edge.access.EdgeAccessGuard;
import com.iot.platform.cloud.edgesync.outbox.CloudOutbox;
import com.iot.platform.cloud.edgesync.outbox.CloudOutboxRepository;
import com.iot.platform.cloud.edgesync.outbox.OutboxDispatcher;
import com.iot.platform.cloud.exception.InvalidCommandException;
import com.iot.platform.cloud.exception.ResourceNotFoundException;
import com.iot.platform.common.device.CapabilityKind;
import com.iot.platform.common.device.CapabilityType;
import com.iot.platform.common.device.CommandDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CommandProxyService {

    private final EdgeAccessGuard edgeAccessGuard;
    private final DeviceRepository deviceRepository;
    private final CloudOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public CommandDTO proxyCommand(UUID edgeId, UUID deviceId, CapabilityType capability,
                                   Object value, UUID issuedBy) {
        edgeAccessGuard.requireAccess(edgeId, issuedBy);

        Device device = deviceRepository.findByIdAndEdgeId(deviceId, edgeId)
                .orElseThrow(() -> new ResourceNotFoundException("Device", "id", deviceId));

        validateCommand(device, capability, value);

        CommandDTO payload = CommandDTO.builder()
                .edgeId(edgeId)
                .deviceId(deviceId)
                .capability(capability)
                .value(value)
                .issuedBy(issuedBy)
                .issuedAt(Instant.now())
                .build();

        enqueue(edgeId, payload);

        log.info("Command proxied: edge={} device={} capability={} value={} issuedBy={}",
                edgeId, deviceId, capability, value, issuedBy);
        return payload;
    }

    private void validateCommand(Device device, CapabilityType capability, Object value) {
        boolean deviceHasCapability = device.getCapabilities().stream()
                .anyMatch(c -> c.getCapabilityType() == capability);
        if (!deviceHasCapability) {
            throw new InvalidCommandException(
                    "Device " + device.getId() + " does not have capability " + capability);
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
    }

    private void enqueue(UUID edgeId, CommandDTO payload) {
        try {
            outboxRepository.save(CloudOutbox.builder()
                    .edgeId(edgeId)
                    .messageType(OutboxDispatcher.MSG_COMMAND)
                    .payload(objectMapper.writeValueAsString(payload))
                    .build());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize CommandDTO", e);
        }
    }
}
