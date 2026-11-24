package com.iot.platform.edge.telemetry;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.iot.platform.common.device.TelemetryBatchDTO;
import com.iot.platform.common.device.TelemetryReadingDTO;
import com.iot.platform.common.protocol.MqttTopics;
import com.iot.platform.edge.bootstrap.EdgeIdentity;
import com.iot.platform.edge.bootstrap.EdgeIdentityRepository;
import com.iot.platform.edge.mqtt.CloudMqttClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class TelemetrySyncScheduler {

    static final int BATCH_SIZE = 500;

    private final TelemetryRepository telemetryRepository;
    private final EdgeIdentityRepository edgeIdentityRepository;
    private final CloudMqttClient cloudMqttClient;
    private final ObjectMapper objectMapper;

    @Scheduled(
            fixedDelayString = "${app.telemetry.sync-delay-ms:60000}",
            initialDelay = 15_000L)
    @Transactional
    public void sync() {
        List<Telemetry> pending = telemetryRepository.findBySyncedFalseOrderByTimestampAsc(
                PageRequest.of(0, BATCH_SIZE));
        if (pending.isEmpty()) {
            return;
        }

        UUID edgeId = edgeIdentityRepository.findSingleton()
                .map(EdgeIdentity::getEdgeId)
                .orElse(null);
        if (edgeId == null) {
            log.warn("Edge identity not present yet; skipping telemetry sync of {} reading(s)",
                    pending.size());
            return;
        }

        TelemetryBatchDTO batch = TelemetryBatchDTO.builder()
                .edgeId(edgeId)
                .batchTimestamp(Instant.now())
                .readings(pending.stream().map(this::toReading).toList())
                .build();

        try {
            cloudMqttClient.publish(MqttTopics.telemetry(edgeId), objectMapper.writeValueAsString(batch));
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize telemetry batch; will retry next cycle", e);
            return;
        } catch (MqttException e) {
            log.warn("Telemetry batch publish failed ({} reading(s)); will retry next cycle: {}",
                    pending.size(), e.getMessage());
            return;
        }

        pending.forEach(t -> t.setSynced(true));
        telemetryRepository.saveAll(pending);
        log.info("Telemetry batch synced: {} reading(s) for edge {}", pending.size(), edgeId);
    }

    private TelemetryReadingDTO toReading(Telemetry t) {
        return TelemetryReadingDTO.builder()
                .deviceId(t.getDeviceId())
                .capability(t.getCapabilityType())
                .value(objectMapper.convertValue(t.getValue(), Object.class))
                .timestamp(t.getTimestamp())
                .build();
    }
}
