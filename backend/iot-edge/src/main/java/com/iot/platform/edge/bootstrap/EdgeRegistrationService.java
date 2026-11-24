package com.iot.platform.edge.bootstrap;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iot.platform.common.edge.EdgeRegisterDTO;
import com.iot.platform.common.protocol.MqttTopics;
import com.iot.platform.edge.mqtt.CloudMqttClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class EdgeRegistrationService {

    private final EdgeIdentityRepository edgeIdentityRepository;
    private final CloudMqttClient cloudMqttClient;
    private final ObjectMapper objectMapper;

    public void publishIfNotPaired() {
        EdgeIdentity identity = edgeIdentityRepository.findSingleton()
                .orElseThrow(() -> new IllegalStateException(
                        "Edge identity must exist before publishing register"));

        if (identity.getPairedAt() != null) {
            log.debug("Edge already paired (edgeId={}), skipping bootstrap register",
                    identity.getEdgeId());
            return;
        }

        try {
            EdgeRegisterDTO dto = EdgeRegisterDTO.builder()
                    .edgeId(identity.getEdgeId())
                    .timestamp(Instant.now())
                    .build();

            String payload = objectMapper.writeValueAsString(dto);
            cloudMqttClient.publish(MqttTopics.BOOTSTRAP_REGISTER, payload);
            log.info("Publishing EdgeRegisterDTO (edgeId={})", identity.getEdgeId());
        } catch (MqttException e) {
            log.error("Failed to publish bootstrap register: {}", e.getMessage(), e);
        } catch (Exception e) {
            log.error("Failed to serialize EdgeRegisterDTO: {}", e.getMessage(), e);
        }
    }
}
