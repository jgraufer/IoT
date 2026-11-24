package com.iot.platform.edge.cloudsync.outbox;

import com.iot.platform.common.protocol.MqttTopics;
import com.iot.platform.edge.bootstrap.EdgeIdentity;
import com.iot.platform.edge.bootstrap.EdgeIdentityRepository;
import com.iot.platform.edge.mqtt.CloudMqttClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxDispatcher {

    static final String MSG_DEVICE_EVENT = "DEVICE_EVENT";

    private final EdgeOutboxRepository outboxRepository;
    private final EdgeIdentityRepository edgeIdentityRepository;
    private final CloudMqttClient mqttClient;

    @Scheduled(
            fixedDelayString = "${app.mqtt.outbox.fixed-delay-ms:5000}",
            initialDelay = 10_000L)
    @Transactional
    public void dispatch() {
        List<EdgeOutbox> pending = outboxRepository.findTop100BySentAtIsNullOrderByCreatedAtAsc();
        if (pending.isEmpty()) {
            return;
        }

        UUID edgeId = edgeIdentityRepository.findSingleton()
                .map(EdgeIdentity::getEdgeId)
                .orElse(null);
        if (edgeId == null) {
            log.warn("Edge identity not present yet; skipping outbox dispatch of {} message(s)",
                    pending.size());
            return;
        }

        log.debug("Outbox: {} pending message(s)", pending.size());

        for (EdgeOutbox row : pending) {
            String topic = topicFor(row, edgeId);
            if (topic == null) {
                log.warn("Outbox row {} has unknown type '{}', skipping",
                        row.getId(), row.getType());
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

    static String topicFor(EdgeOutbox row, UUID edgeId) {
        return switch (row.getType()) {
            case MSG_DEVICE_EVENT -> MqttTopics.deviceEvents(edgeId);
            default -> null;
        };
    }
}
