package com.iot.platform.edge.cloudsync.outbox;

import com.iot.platform.common.protocol.MqttTopics;
import com.iot.platform.edge.bootstrap.EdgeIdentity;
import com.iot.platform.edge.bootstrap.EdgeIdentityRepository;
import com.iot.platform.edge.mqtt.CloudMqttClient;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboxDispatcherTest {

    @Mock
    private EdgeOutboxRepository outboxRepository;
    @Mock
    private EdgeIdentityRepository edgeIdentityRepository;
    @Mock
    private CloudMqttClient mqttClient;

    @InjectMocks
    private OutboxDispatcher dispatcher;

    private UUID edgeId;

    @BeforeEach
    void setUp() {
        edgeId = UUID.randomUUID();
        lenient().when(edgeIdentityRepository.findSingleton())
                .thenReturn(Optional.of(EdgeIdentity.builder().edgeId(edgeId).build()));
    }

    @Test
    void emptyOutbox_doesNothing() throws Exception {
        when(outboxRepository.findTop100BySentAtIsNullOrderByCreatedAtAsc())
                .thenReturn(List.of());

        dispatcher.dispatch();

        verify(mqttClient, never()).publish(anyString(), anyString());
    }

    @Test
    void deviceEvent_publishesToDeviceEventsTopic() throws Exception {
        EdgeOutbox row = EdgeOutbox.builder()
                .id(UUID.randomUUID())
                .type("DEVICE_EVENT")
                .payload("{\"eventType\":\"DEVICE_CREATED\"}")
                .attempts(0)
                .build();
        when(outboxRepository.findTop100BySentAtIsNullOrderByCreatedAtAsc())
                .thenReturn(List.of(row));

        dispatcher.dispatch();

        verify(mqttClient).publish(MqttTopics.deviceEvents(edgeId), row.getPayload());
        ArgumentCaptor<EdgeOutbox> saved = ArgumentCaptor.forClass(EdgeOutbox.class);
        verify(outboxRepository, atLeastOnce()).save(saved.capture());
        assertThat(saved.getValue().getSentAt()).isNotNull();
        assertThat(saved.getValue().getLastError()).isNull();
    }

    @Test
    void mqttException_incrementsAttemptsAndKeepsRowPending() throws Exception {
        EdgeOutbox row = EdgeOutbox.builder()
                .id(UUID.randomUUID())
                .type("DEVICE_EVENT")
                .payload("{}")
                .attempts(0)
                .build();
        when(outboxRepository.findTop100BySentAtIsNullOrderByCreatedAtAsc())
                .thenReturn(List.of(row));
        doThrow(new MqttException(MqttException.REASON_CODE_CONNECTION_LOST))
                .when(mqttClient).publish(anyString(), anyString());

        dispatcher.dispatch();

        ArgumentCaptor<EdgeOutbox> saved = ArgumentCaptor.forClass(EdgeOutbox.class);
        verify(outboxRepository).save(saved.capture());
        EdgeOutbox after = saved.getValue();
        assertThat(after.getAttempts()).isEqualTo(1);
        assertThat(after.getSentAt()).isNull();
        assertThat(after.getLastAttemptAt()).isNotNull();
        assertThat(after.getLastError()).isNotNull();
    }

    @Test
    void unknownType_isSkippedWithoutMarkingSent() throws MqttException {
        EdgeOutbox row = EdgeOutbox.builder()
                .id(UUID.randomUUID())
                .type("WHATEVER_FUTURE_TYPE")
                .payload("{}")
                .attempts(0)
                .build();
        when(outboxRepository.findTop100BySentAtIsNullOrderByCreatedAtAsc())
                .thenReturn(List.of(row));

        dispatcher.dispatch();

        verify(mqttClient, never()).publish(anyString(), anyString());
        verify(outboxRepository, never()).save(any(EdgeOutbox.class));
    }

    @Test
    void missingEdgeIdentity_skipsDispatch() throws MqttException {
        EdgeOutbox row = EdgeOutbox.builder()
                .id(UUID.randomUUID())
                .type("DEVICE_EVENT")
                .payload("{}")
                .attempts(0)
                .build();
        when(outboxRepository.findTop100BySentAtIsNullOrderByCreatedAtAsc())
                .thenReturn(List.of(row));
        when(edgeIdentityRepository.findSingleton()).thenReturn(Optional.empty());

        dispatcher.dispatch();

        verify(mqttClient, never()).publish(anyString(), anyString());
        verify(outboxRepository, never()).save(any(EdgeOutbox.class));
    }
}
