package com.iot.platform.edge.command;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iot.platform.common.device.CommandDTO;
import com.iot.platform.common.protocol.MqttTopics;
import com.iot.platform.edge.bootstrap.EdgeIdentity;
import com.iot.platform.edge.bootstrap.EdgeIdentityRepository;
import com.iot.platform.edge.mqtt.CloudMqttClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.IMqttMessageListener;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class CommandConsumer implements IMqttMessageListener {

    private final ObjectMapper objectMapper;
    private final CloudMqttClient cloudMqttClient;
    private final EdgeIdentityRepository edgeIdentityRepository;
    private final CommandService commandService;

    public void subscribe() throws MqttException {
        EdgeIdentity identity = edgeIdentityRepository.findSingleton()
                .orElseThrow(() -> new IllegalStateException(
                        "Edge identity must exist before subscribing to commands"));
        cloudMqttClient.subscribe(MqttTopics.commands(identity.getEdgeId()), this);
    }

    @Override
    public void messageArrived(String topic, MqttMessage message) {
        try {
            CommandDTO dto = objectMapper.readValue(message.getPayload(), CommandDTO.class);
            commandService.issueCommand(
                    dto.getDeviceId(), dto.getCapability(), dto.getValue(), dto.getIssuedBy());
        } catch (Exception e) {
            log.error("Failed to process command from Cloud on topic {}: {}",
                    topic, e.getMessage(), e);
        }
    }
}
