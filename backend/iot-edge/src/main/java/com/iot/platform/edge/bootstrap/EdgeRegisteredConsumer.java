package com.iot.platform.edge.bootstrap;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iot.platform.common.edge.EdgeRegisteredDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.IMqttMessageListener;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class EdgeRegisteredConsumer implements IMqttMessageListener {

    private final EdgeIdentityRepository edgeIdentityRepository;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public void messageArrived(String topic, MqttMessage message) {
        EdgeRegisteredDTO dto;
        try {
            dto = objectMapper.readValue(message.getPayload(), EdgeRegisteredDTO.class);
        } catch (Exception e) {
            log.error("Malformed registered message on topic {}; discarded: {}", topic, e.getMessage());
            return;
        }
        applyRegistered(dto);
    }

    void applyRegistered(EdgeRegisteredDTO dto) {
        EdgeIdentity identity = edgeIdentityRepository.findSingleton()
                .orElseThrow(() -> new IllegalStateException(
                        "Edge identity must exist when applying registered message"));

        if (!identity.getEdgeId().equals(dto.getEdgeId())) {
            log.warn("Received registered message for edgeId={} but local edgeId={}; ignoring",
                    dto.getEdgeId(), identity.getEdgeId());
            return;
        }

        identity.setPairingCode(dto.getPairingCode());
        identity.setPairingCodeExpiresAt(dto.getPairingCodeExpiresAt());
        edgeIdentityRepository.save(identity);

        log.info("Pairing code received: {} (expires {})",
                dto.getPairingCode(), dto.getPairingCodeExpiresAt());
    }
}
