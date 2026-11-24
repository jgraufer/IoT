package com.iot.platform.edge.telemetry;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TelemetryPurgeSchedulerTest {

    @Mock
    private TelemetryRepository telemetryRepository;

    @InjectMocks
    private TelemetryPurgeScheduler scheduler;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(scheduler, "retentionHours", 24L);
    }

    @Test
    void purge_deletesSyncedOlderThanRetention() {
        when(telemetryRepository.deleteSyncedBefore(org.mockito.ArgumentMatchers.any()))
                .thenReturn(3);

        Instant before = Instant.now().minus(Duration.ofHours(24));
        scheduler.purge();
        Instant after = Instant.now().minus(Duration.ofHours(24));

        ArgumentCaptor<Instant> cutoffCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(telemetryRepository).deleteSyncedBefore(cutoffCaptor.capture());
        Instant cutoff = cutoffCaptor.getValue();
        assertThat(cutoff).isBetween(before.minusSeconds(5), after.plusSeconds(5));
    }
}
