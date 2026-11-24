package com.iot.platform.edge.mqtt;

import com.iot.platform.common.protocol.MqttTopics;
import com.iot.platform.edge.telemetry.DeviceTelemetryConsumer;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.IMqttAsyncClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class EdgeMqttConnector {

    private final IMqttAsyncClient mqttClient;
    private final EdgeMqttProperties properties;
    private final DeviceTelemetryConsumer deviceTelemetryConsumer;

    public EdgeMqttConnector(
            @Qualifier("edgeMqttAsyncClient") IMqttAsyncClient mqttClient,
            EdgeMqttProperties properties,
            DeviceTelemetryConsumer deviceTelemetryConsumer) {
        this.mqttClient = mqttClient;
        this.properties = properties;
        this.deviceTelemetryConsumer = deviceTelemetryConsumer;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        log.info("Connecting Edge LAN MQTT client to {} as {}",
                properties.getLocalBrokerUrl(), properties.getLocalUsername());
        MqttAutoConnector.start(mqttClient, buildOptions(), "Edge-LAN", this::subscribeAll);
    }

    private MqttConnectOptions buildOptions() {
        MqttConnectOptions options = new MqttConnectOptions();
        options.setCleanSession(true);
        options.setAutomaticReconnect(true);
        options.setConnectionTimeout(10);
        options.setKeepAliveInterval(30);
        options.setUserName(properties.getLocalUsername());
        if (properties.getLocalPassword() != null) {
            options.setPassword(properties.getLocalPassword().toCharArray());
        }
        return options;
    }

    private void subscribeAll() {
        try {
            mqttClient.subscribe(MqttTopics.DEVICE_TELEMETRY_WILDCARD, 1, deviceTelemetryConsumer);
            log.info("Subscribed to LAN {}", MqttTopics.DEVICE_TELEMETRY_WILDCARD);
        } catch (MqttException e) {
            log.error("Edge LAN MQTT subscribe failed: {}", e.getMessage(), e);
        }
    }
}
