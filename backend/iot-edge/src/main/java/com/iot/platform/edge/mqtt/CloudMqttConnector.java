package com.iot.platform.edge.mqtt;

import com.iot.platform.common.protocol.MqttTopics;
import com.iot.platform.edge.bootstrap.EdgeIdentity;
import com.iot.platform.edge.bootstrap.EdgeIdentityRepository;
import com.iot.platform.edge.bootstrap.EdgeRegisteredConsumer;
import com.iot.platform.edge.bootstrap.EdgeRegistrationService;
import com.iot.platform.edge.cloudsync.UserSyncConsumer;
import com.iot.platform.edge.command.CommandConsumer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.IMqttAsyncClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class CloudMqttConnector {

    private final IMqttAsyncClient mqttClient;
    private final EdgeMqttProperties properties;
    private final EdgeIdentityRepository edgeIdentityRepository;
    private final EdgeRegisteredConsumer edgeRegisteredConsumer;
    private final EdgeRegistrationService edgeRegistrationService;
    private final UserSyncConsumer userSyncConsumer;
    private final CommandConsumer commandConsumer;

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        log.info("Connecting Edge MQTT client to {} as {}",
                properties.getCloudBrokerUrl(), properties.getCloudBootstrapUsername());
        MqttAutoConnector.start(mqttClient, buildOptions(), "Edge-Cloud", this::onConnect);
    }

    private MqttConnectOptions buildOptions() {
        MqttConnectOptions options = new MqttConnectOptions();
        options.setCleanSession(false);
        options.setAutomaticReconnect(true);
        options.setConnectionTimeout(10);
        options.setKeepAliveInterval(30);
        options.setUserName(properties.getCloudBootstrapUsername());
        if (properties.getCloudBootstrapPassword() != null) {
            options.setPassword(properties.getCloudBootstrapPassword().toCharArray());
        }
        return options;
    }

    private void onConnect() {
        try {
            EdgeIdentity identity = edgeIdentityRepository.findSingleton()
                    .orElseThrow(() -> new IllegalStateException(
                            "Edge identity must exist before subscribing"));
            UUID edgeId = identity.getEdgeId();

            mqttClient.subscribe(MqttTopics.bootstrapRegistered(edgeId), 1, edgeRegisteredConsumer);
            log.info("Subscribed to {}", MqttTopics.bootstrapRegistered(edgeId));
            mqttClient.subscribe(MqttTopics.userSync(edgeId), 1, userSyncConsumer);
            log.info("Subscribed to {}", MqttTopics.userSync(edgeId));

            commandConsumer.subscribe();
        } catch (MqttException e) {
            log.error("Edge->Cloud MQTT subscribe failed: {}", e.getMessage(), e);
            return;
        }
        edgeRegistrationService.publishIfNotPaired();
    }
}
