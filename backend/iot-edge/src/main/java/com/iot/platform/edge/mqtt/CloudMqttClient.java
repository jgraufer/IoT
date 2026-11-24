package com.iot.platform.edge.mqtt;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.IMqttAsyncClient;
import org.eclipse.paho.client.mqttv3.IMqttMessageListener;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Slf4j
@Component
@RequiredArgsConstructor
public class CloudMqttClient {

    private final IMqttAsyncClient mqttClient;

    public void publish(String topic, String payload) throws MqttException {
        MqttMessage message = new MqttMessage(payload.getBytes(StandardCharsets.UTF_8));
        message.setQos(1);
        message.setRetained(false);
        mqttClient.publish(topic, message);
        log.debug("Published to {} ({} bytes)", topic, message.getPayload().length);
    }

    public void subscribe(String topic, IMqttMessageListener listener) throws MqttException {
        mqttClient.subscribe(topic, 1, listener);
        log.info("Subscribed to {}", topic);
    }
}
