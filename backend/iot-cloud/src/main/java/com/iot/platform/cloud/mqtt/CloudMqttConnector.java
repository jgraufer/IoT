package com.iot.platform.cloud.mqtt;

import com.iot.platform.cloud.device.DeviceEventConsumer;
import com.iot.platform.cloud.edge.EdgeBootstrapConsumer;
import com.iot.platform.cloud.telemetry.TelemetryConsumer;
import com.iot.platform.common.protocol.MqttTopics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.IMqttAsyncClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class CloudMqttConnector {

    private final IMqttAsyncClient mqttClient;
    private final CloudMqttProperties properties;
    private final EdgeBootstrapConsumer edgeBootstrapConsumer;
    private final DeviceEventConsumer deviceEventConsumer;
    private final TelemetryConsumer telemetryConsumer;

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        log.info("Connecting Cloud MQTT client to {} as {}",
                properties.getBrokerUrl(), properties.getUsername());
        MqttAutoConnector.start(mqttClient, buildOptions(), "Cloud", this::subscribeAll);
    }

    private MqttConnectOptions buildOptions() {
        MqttConnectOptions options = new MqttConnectOptions();
        options.setCleanSession(true);
        options.setAutomaticReconnect(true);
        options.setConnectionTimeout(10);
        options.setKeepAliveInterval(30);
        options.setUserName(properties.getUsername());
        if (properties.getPassword() != null) {
            options.setPassword(properties.getPassword().toCharArray());
        }
        return options;
    }

    private void subscribeAll() {
        try {
            mqttClient.subscribe(MqttTopics.BOOTSTRAP_REGISTER, 1, edgeBootstrapConsumer);
            log.info("Subscribed to {}", MqttTopics.BOOTSTRAP_REGISTER);
            mqttClient.subscribe(MqttTopics.DEVICE_EVENTS_WILDCARD, 1, deviceEventConsumer);
            log.info("Subscribed to {}", MqttTopics.DEVICE_EVENTS_WILDCARD);
            mqttClient.subscribe(MqttTopics.TELEMETRY_WILDCARD, 1, telemetryConsumer);
            log.info("Subscribed to {}", MqttTopics.TELEMETRY_WILDCARD);
        } catch (MqttException e) {
            log.error("Cloud MQTT subscribe failed: {}", e.getMessage(), e);
        }
    }
}
