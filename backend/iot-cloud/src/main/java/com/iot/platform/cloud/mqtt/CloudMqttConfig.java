package com.iot.platform.cloud.mqtt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.IMqttAsyncClient;
import org.eclipse.paho.client.mqttv3.MqttAsyncClient;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Slf4j
@Configuration
@EnableConfigurationProperties(CloudMqttProperties.class)
public class CloudMqttConfig {

    private static final String CLIENT_ID = "iot-cloud";

    @Bean
    @Primary
    public ObjectMapper mqttObjectMapper() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    @Bean(destroyMethod = "disconnectForcibly")
    public IMqttAsyncClient mqttAsyncClient(CloudMqttProperties properties) throws MqttException {
        log.info("Creating Cloud MQTT client (clientId={}) for broker {}",
                CLIENT_ID, properties.getBrokerUrl());
        return new MqttAsyncClient(
                properties.getBrokerUrl(),
                CLIENT_ID,
                new MemoryPersistence());
    }
}
