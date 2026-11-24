package com.iot.platform.edge.mqtt;

import com.iot.platform.edge.bootstrap.BootstrapService;
import com.iot.platform.edge.bootstrap.EdgeIdentity;
import com.iot.platform.edge.bootstrap.EdgeIdentityRepository;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.IMqttAsyncClient;
import org.eclipse.paho.client.mqttv3.MqttAsyncClient;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;

@Slf4j
@Configuration
public class EdgeMqttConfig {

    @Bean(name = "edgeMqttAsyncClient", destroyMethod = "disconnectForcibly")
    @DependsOn("bootstrapService")
    public IMqttAsyncClient edgeMqttAsyncClient(
            EdgeMqttProperties properties,
            EdgeIdentityRepository edgeIdentityRepository) throws MqttException {

        EdgeIdentity identity = edgeIdentityRepository.findSingleton()
                .orElseThrow(() -> new IllegalStateException(
                        "Edge identity must exist before local MQTT client is created"));

        String clientId = "iot-edge-local-" + identity.getEdgeId();
        log.info("Creating Edge LAN MQTT client (clientId={}) for broker {}",
                clientId, properties.getLocalBrokerUrl());

        return new MqttAsyncClient(
                properties.getLocalBrokerUrl(),
                clientId,
                new MemoryPersistence());
    }
}
