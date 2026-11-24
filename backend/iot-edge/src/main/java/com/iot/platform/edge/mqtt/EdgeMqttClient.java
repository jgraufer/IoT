package com.iot.platform.edge.mqtt;

import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.IMqttAsyncClient;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Slf4j
@Component
public class EdgeMqttClient {

    private final IMqttAsyncClient mqttClient;

    public EdgeMqttClient(@Qualifier("edgeMqttAsyncClient") IMqttAsyncClient mqttClient) {
        this.mqttClient = mqttClient;
    }

    public void publish(String topic, String payload) throws MqttException {
        MqttMessage message = new MqttMessage(payload.getBytes(StandardCharsets.UTF_8));
        message.setQos(1);
        message.setRetained(false);
        mqttClient.publish(topic, message);
        log.debug("Published to LAN {} ({} bytes)", topic, message.getPayload().length);
    }
}
