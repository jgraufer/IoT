package com.iot.platform.cloud.edge;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.iot.platform.cloud.edgesync.outbox.CloudOutbox;
import com.iot.platform.cloud.edgesync.outbox.CloudOutboxRepository;
import com.iot.platform.common.edge.EdgeRegisterDTO;
import com.iot.platform.common.edge.EdgeRegisteredDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.IMqttMessageListener;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class EdgeBootstrapConsumer implements IMqttMessageListener {

    static final long PAIRING_CODE_TTL_HOURS = 24;

    private final ObjectMapper objectMapper;
    private final EdgeRepository edgeRepository;
    private final EdgePairingCodeRepository edgePairingCodeRepository;
    private final CloudOutboxRepository cloudOutboxRepository;
    private final PairingCodeGenerator pairingCodeGenerator;

    @Override
    @Transactional
    public void messageArrived(String topic, MqttMessage message) {
        EdgeRegisterDTO dto;
        try {
            dto = objectMapper.readValue(message.getPayload(), EdgeRegisterDTO.class);
        } catch (Exception e) {
            log.error("Malformed bootstrap register on topic {}; discarded: {}", topic, e.getMessage());
            return;
        }
        handleRegister(dto);
    }

    void handleRegister(EdgeRegisterDTO dto) {
        UUID edgeId = dto.getEdgeId();
        if (edgeId == null) {
            log.warn("Received bootstrap register without edgeId; ignoring");
            return;
        }

        Edge existing = edgeRepository.findById(edgeId).orElse(null);
        if (existing != null) {
            if (existing.getPairedAt() != null) {
                log.warn("Edge {} already paired; ignoring bootstrap message", edgeId);
                return;
            }
            if (edgePairingCodeRepository.existsByEdgeIdAndUsedFalseAndExpiresAtAfter(edgeId, Instant.now())) {
                log.warn("Edge {} already has an active pairing code; ignoring duplicate bootstrap message", edgeId);
                return;
            }
            log.info("Edge {} not paired and its pairing code expired; re-issuing", edgeId);
        } else {
            Edge edge = Edge.builder()
                    .id(edgeId)
                    .name("Edge " + edgeId.toString().substring(0, 8))
                    .build();
            edgeRepository.save(edge);
        }

        String code = pairingCodeGenerator.generate();
        Instant expiresAt = Instant.now().plus(PAIRING_CODE_TTL_HOURS, ChronoUnit.HOURS);

        EdgePairingCode pairingCode = EdgePairingCode.builder()
                .code(code)
                .edgeId(edgeId)
                .expiresAt(expiresAt)
                .used(false)
                .build();
        edgePairingCodeRepository.save(pairingCode);

        EdgeRegisteredDTO registered = EdgeRegisteredDTO.builder()
                .edgeId(edgeId)
                .pairingCode(code)
                .pairingCodeExpiresAt(expiresAt)
                .build();

        try {
            CloudOutbox outboxRow = CloudOutbox.builder()
                    .edgeId(edgeId)
                    .messageType("EDGE_REGISTERED")
                    .payload(objectMapper.writeValueAsString(registered))
                    .attempts(0)
                    .build();
            cloudOutboxRepository.save(outboxRow);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize EdgeRegisteredDTO", e);
        }

        log.info("Issued pairing code {} for edge {} (expires {})",
                code, edgeId, expiresAt);
    }
}
