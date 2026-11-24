package com.iot.platform.edge.mqtt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.iot.platform.edge.bootstrap.BootstrapService;
import com.iot.platform.edge.bootstrap.EdgeIdentity;
import com.iot.platform.edge.bootstrap.EdgeIdentityRepository;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.IMqttAsyncClient;
import org.eclipse.paho.client.mqttv3.MqttAsyncClient;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Primary;

@Slf4j
@Configuration
@EnableConfigurationProperties(EdgeMqttProperties.class)
public class CloudMqttConfig {

    @Bean
    @Primary
    public ObjectMapper mqttObjectMapper() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    @Bean(destroyMethod = "disconnectForcibly")
    @Primary
    @DependsOn("bootstrapService")
    public IMqttAsyncClient mqttAsyncClient(
            EdgeMqttProperties properties,
            EdgeIdentityRepository edgeIdentityRepository) throws MqttException {

        EdgeIdentity identity = edgeIdentityRepository.findSingleton()
                .orElseThrow(() -> new IllegalStateException(
                        "Edge identity must exist before MQTT client is created"));

        String clientId = identity.getEdgeId().toString();
        log.info("Creating Edge MQTT client (clientId={}) for broker {}",
                clientId, properties.getCloudBrokerUrl());

        return new MqttAsyncClient(
                properties.getCloudBrokerUrl(),
                clientId,
                new MemoryPersistence());
    }
}
