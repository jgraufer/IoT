package com.iot.platform.edge.mqtt;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.mqtt")
public class EdgeMqttProperties {

    private String cloudBrokerUrl;
    private String cloudBootstrapUsername;
    private String cloudBootstrapPassword;

    private String localBrokerUrl;
    private String localUsername;
    private String localPassword;
}
