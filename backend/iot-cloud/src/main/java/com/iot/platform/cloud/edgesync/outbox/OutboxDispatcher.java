package com.iot.platform.cloud.edgesync.outbox;

import com.iot.platform.cloud.mqtt.CloudMqttClient;
import com.iot.platform.common.protocol.MqttTopics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxDispatcher {

    static final String MSG_USER_SYNC = "USER_SYNC";
    static final String MSG_EDGE_REGISTERED = "EDGE_REGISTERED";
    public static final String MSG_COMMAND = "COMMAND";

    private final CloudOutboxRepository outboxRepository;
    private final CloudMqttClient mqttClient;

    @Scheduled(
            fixedDelayString = "${app.mqtt.outbox.fixed-delay-ms:5000}",
            initialDelay = 10_000L)
    @Transactional
    public void dispatch() {
        List<CloudOutbox> pending = outboxRepository.findTop100BySentAtIsNullOrderByCreatedAtAsc();
        if (pending.isEmpty()) {
            return;
        }
        log.debug("Outbox: {} pending message(s)", pending.size());

        for (CloudOutbox row : pending) {
            String topic = topicFor(row);
            if (topic == null) {
                log.warn("Outbox row {} has unknown messageType '{}', skipping",
                        row.getId(), row.getMessageType());
                continue;
            }
            try {
                mqttClient.publish(topic, row.getPayload());
                row.setSentAt(Instant.now());
                row.setLastError(null);
                outboxRepository.save(row);
                log.debug("Outbox row {} sent to {}", row.getId(), topic);
            } catch (MqttException e) {
                row.setAttempts(row.getAttempts() == null ? 1 : row.getAttempts() + 1);
                row.setLastAttemptAt(Instant.now());
                row.setLastError(e.getMessage());
                outboxRepository.save(row);
                log.warn("Outbox row {} failed (attempts={}): {}",
                        row.getId(), row.getAttempts(), e.getMessage());
            }
        }
    }

    static String topicFor(CloudOutbox row) {
        return switch (row.getMessageType()) {
            case MSG_USER_SYNC -> MqttTopics.userSync(row.getEdgeId());
            case MSG_EDGE_REGISTERED -> MqttTopics.bootstrapRegistered(row.getEdgeId());
            case MSG_COMMAND -> MqttTopics.commands(row.getEdgeId());
            default -> null;
        };
    }
}
