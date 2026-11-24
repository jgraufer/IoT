package com.iot.platform.edge.bootstrap;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.iot.platform.common.protocol.MqttTopics;
import com.iot.platform.edge.mqtt.CloudMqttClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EdgeRegistrationServiceTest {

    @Mock
    private EdgeIdentityRepository repository;

    @Mock
    private CloudMqttClient cloudMqttClient;

    private EdgeRegistrationService service;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @BeforeEach
    void setUp() {
        service = new EdgeRegistrationService(repository, cloudMqttClient, objectMapper);
    }

    @Test
    void notPaired_publishesEdgeRegisterDTO() throws Exception {
        UUID edgeId = UUID.randomUUID();
        EdgeIdentity identity = EdgeIdentity.builder()
                .id(1)
                .edgeId(edgeId)
                .jwtSecret("secret")
                .pairedAt(null)
                .build();
        when(repository.findSingleton()).thenReturn(Optional.of(identity));

        service.publishIfNotPaired();

        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(cloudMqttClient).publish(eq(MqttTopics.BOOTSTRAP_REGISTER), payload.capture());
        assertThat(payload.getValue()).contains(edgeId.toString());
        assertThat(payload.getValue()).contains("\"timestamp\"");
    }

    @Test
    void alreadyPaired_doesNotPublish() throws Exception {
        EdgeIdentity identity = EdgeIdentity.builder()
                .id(1)
                .edgeId(UUID.randomUUID())
                .jwtSecret("secret")
                .pairedAt(Instant.now())
                .build();
        when(repository.findSingleton()).thenReturn(Optional.of(identity));

        service.publishIfNotPaired();

        verify(cloudMqttClient, never()).publish(anyString(), anyString());
    }
}
