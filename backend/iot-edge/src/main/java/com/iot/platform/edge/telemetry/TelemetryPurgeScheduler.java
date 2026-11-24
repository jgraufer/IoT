package com.iot.platform.edge.telemetry;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

@Slf4j
@Component
@RequiredArgsConstructor
public class TelemetryPurgeScheduler {

    private final TelemetryRepository telemetryRepository;

    @Value("${app.telemetry.retention-hours:24}")
    private long retentionHours;

    @Scheduled(cron = "0 0 * * * *")
    @Transactional
    public void purge() {
        Instant cutoff = Instant.now().minus(Duration.ofHours(retentionHours));
        int deleted = telemetryRepository.deleteSyncedBefore(cutoff);
        if (deleted > 0) {
            log.info("Telemetry purge: removed {} synced reading(s) older than {}", deleted, cutoff);
        }
    }
}
