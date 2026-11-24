package com.iot.platform.cloud.mqtt;

import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.IMqttAsyncClient;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;

@Slf4j
final class MqttAutoConnector {

    private static final long INITIAL_BACKOFF_MS = 2_000L;
    private static final long MAX_BACKOFF_MS = 60_000L;

    private MqttAutoConnector() {
    }

    static void start(IMqttAsyncClient client, MqttConnectOptions options,
                      String name, Runnable onConnect) {
        client.setCallback(new MqttCallbackExtended() {
            @Override
            public void connectComplete(boolean reconnect, String serverURI) {
                log.info("{} MQTT connectComplete (reconnect={}, uri={})", name, reconnect, serverURI);
                onConnect.run();
            }

            @Override
            public void connectionLost(Throwable cause) {
                log.warn("{} MQTT connection lost: {}", name,
                        cause == null ? "unknown" : cause.getMessage());
            }

            @Override
            public void messageArrived(String topic, MqttMessage message) {
            }

            @Override
            public void deliveryComplete(IMqttDeliveryToken token) {
            }
        });

        Thread t = new Thread(() -> runConnectLoop(client, options, name), "mqtt-connect-" + name);
        t.setDaemon(true);
        t.start();
    }

    private static void runConnectLoop(IMqttAsyncClient client, MqttConnectOptions options, String name) {
        long backoffMs = INITIAL_BACKOFF_MS;
        while (!Thread.currentThread().isInterrupted()) {
            try {
                client.connect(options).waitForCompletion();
                log.info("{} MQTT client connected", name);
                return;
            } catch (MqttException e) {
                log.warn("{} MQTT initial connect failed; retrying in {} ms: {}",
                        name, backoffMs, e.getMessage());
                try {
                    Thread.sleep(backoffMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                }
                backoffMs = Math.min(backoffMs * 2, MAX_BACKOFF_MS);
            }
        }
    }
}
