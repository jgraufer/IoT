package com.iot.platform.edge.bootstrap;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.iot.platform.common.edge.EdgeRegisteredDTO;
import com.iot.platform.common.protocol.MqttTopics;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EdgeRegisteredConsumerTest {

    @Mock
    private EdgeIdentityRepository repository;

    private EdgeRegisteredConsumer handler;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @BeforeEach
    void setUp() {
        handler = new EdgeRegisteredConsumer(repository, objectMapper);
    }

    @Test
    void validPayload_persistsPairingCodeAndExpiresAt() throws Exception {
        UUID edgeId = UUID.randomUUID();
        Instant expiresAt = Instant.parse("2026-05-08T12:00:00Z");
        EdgeRegisteredDTO dto = EdgeRegisteredDTO.builder()
                .edgeId(edgeId)
                .pairingCode("ABCD-1234")
                .pairingCodeExpiresAt(expiresAt)
                .build();

        EdgeIdentity identity = EdgeIdentity.builder()
                .id(1)
                .edgeId(edgeId)
                .jwtSecret("secret")
                .build();
        when(repository.findSingleton()).thenReturn(Optional.of(identity));

        MqttMessage message = new MqttMessage(objectMapper.writeValueAsBytes(dto));
        handler.messageArrived(MqttTopics.bootstrapRegistered(edgeId), message);

        ArgumentCaptor<EdgeIdentity> captor = ArgumentCaptor.forClass(EdgeIdentity.class);
        verify(repository).save(captor.capture());
        EdgeIdentity saved = captor.getValue();
        assertThat(saved.getPairingCode()).isEqualTo("ABCD-1234");
        assertThat(saved.getPairingCodeExpiresAt()).isNotNull();
    }

    @Test
    void mismatchedEdgeId_isIgnored() throws Exception {
        UUID localEdgeId = UUID.randomUUID();
        UUID otherEdgeId = UUID.randomUUID();
        EdgeRegisteredDTO dto = EdgeRegisteredDTO.builder()
                .edgeId(otherEdgeId)
                .pairingCode("XXXX-YYYY")
                .pairingCodeExpiresAt(Instant.now())
                .build();
        EdgeIdentity identity = EdgeIdentity.builder()
                .id(1).edgeId(localEdgeId).jwtSecret("s").build();
        when(repository.findSingleton()).thenReturn(Optional.of(identity));

        MqttMessage message = new MqttMessage(objectMapper.writeValueAsBytes(dto));
        handler.messageArrived(MqttTopics.bootstrapRegistered(localEdgeId), message);

        verify(repository, never()).save(any(EdgeIdentity.class));
    }

    @Test
    void invalidJsonPayload_doesNotThrowAndDoesNotSave() {
        MqttMessage message = new MqttMessage("not valid json".getBytes(StandardCharsets.UTF_8));

        handler.messageArrived(MqttTopics.bootstrapRegistered(UUID.randomUUID()), message);

        verify(repository, never()).save(any(EdgeIdentity.class));
    }
}
