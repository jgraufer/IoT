package com.iot.platform.cloud.mqtt;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.mqtt")
public class CloudMqttProperties {

    private String brokerUrl;
    private String username;
    private String password;

    private final Outbox outbox = new Outbox();

    @Getter
    @Setter
    public static class Outbox {
        private long fixedDelayMs = 5000L;
    }
}
