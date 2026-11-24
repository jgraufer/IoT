package com.iot.platform.cloud.telemetry;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iot.platform.cloud.device.DeviceRepository;
import com.iot.platform.common.device.TelemetryBatchDTO;
import com.iot.platform.common.device.TelemetryReadingDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.IMqttMessageListener;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class TelemetryConsumer implements IMqttMessageListener {

    private final ObjectMapper objectMapper;
    private final TelemetryRepository telemetryRepository;
    private final DeviceRepository deviceRepository;

    @Override
    @Transactional
    public void messageArrived(String topic, MqttMessage message) {
        TelemetryBatchDTO batch;
        try {
            batch = objectMapper.readValue(message.getPayload(), TelemetryBatchDTO.class);
        } catch (Exception e) {
            log.error("Malformed telemetry batch on topic {}; discarded: {}", topic, e.getMessage());
            return;
        }
        persist(batch);
    }

    private void persist(TelemetryBatchDTO batch) {
        if (batch.getEdgeId() == null) {
            log.warn("Telemetry batch without edgeId; ignoring");
            return;
        }
        List<TelemetryReadingDTO> readings = batch.getReadings();
        if (readings == null || readings.isEmpty()) {
            return;
        }

        Instant receivedAt = Instant.now();
        int stored = 0;
        for (TelemetryReadingDTO reading : readings) {
            if (isIncomplete(reading)) {
                log.warn("Telemetry reading in batch from edge {} is incomplete; skipped", batch.getEdgeId());
                continue;
            }
            if (!deviceRepository.existsById(reading.getDeviceId())) {
                log.warn("Telemetry for device {} not yet replicated in Cloud; skipped",
                        reading.getDeviceId());
                continue;
            }

            telemetryRepository.save(Telemetry.builder()
                    .deviceId(reading.getDeviceId())
                    .edgeId(batch.getEdgeId())
                    .capabilityType(reading.getCapability())
                    .value(objectMapper.valueToTree(reading.getValue()))
                    .timestamp(reading.getTimestamp())
                    .receivedAt(receivedAt)
                    .build());
            stored++;
        }

        log.info("Telemetry batch archived: {}/{} reading(s) from edge {}",
                stored, readings.size(), batch.getEdgeId());
    }

    private boolean isIncomplete(TelemetryReadingDTO reading) {
        return reading.getDeviceId() == null
                || reading.getCapability() == null
                || reading.getValue() == null
                || reading.getTimestamp() == null;
    }
}
