package com.iot.platform.cloud.edgesync.outbox;

import com.iot.platform.cloud.mqtt.CloudMqttClient;
import com.iot.platform.common.protocol.MqttTopics;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboxDispatcherTest {

    @Mock
    private CloudOutboxRepository outboxRepository;

    @Mock
    private CloudMqttClient mqttClient;

    @InjectMocks
    private OutboxDispatcher dispatcher;

    private UUID edgeId;

    @BeforeEach
    void setUp() {
        edgeId = UUID.randomUUID();
    }

    @Test
    void emptyOutbox_doesNothing() throws Exception {
        when(outboxRepository.findTop100BySentAtIsNullOrderByCreatedAtAsc())
                .thenReturn(List.of());

        dispatcher.dispatch();

        verify(mqttClient, never()).publish(anyString(), anyString());
    }

    @Test
    void userSyncMessage_publishesToUserSyncTopic() throws Exception {
        CloudOutbox row = CloudOutbox.builder()
                .id(UUID.randomUUID())
                .edgeId(edgeId)
                .messageType("USER_SYNC")
                .payload("{\"operation\":\"GRANT\"}")
                .attempts(0)
                .build();
        when(outboxRepository.findTop100BySentAtIsNullOrderByCreatedAtAsc())
                .thenReturn(List.of(row));

        dispatcher.dispatch();

        verify(mqttClient).publish(MqttTopics.userSync(edgeId), row.getPayload());
        ArgumentCaptor<CloudOutbox> saved = ArgumentCaptor.forClass(CloudOutbox.class);
        verify(outboxRepository, atLeastOnce()).save(saved.capture());
        assertThat(saved.getValue().getSentAt()).isNotNull();
        assertThat(saved.getValue().getLastError()).isNull();
    }

    @Test
    void edgeRegisteredMessage_publishesToBootstrapRegisteredTopic() throws Exception {
        CloudOutbox row = CloudOutbox.builder()
                .id(UUID.randomUUID())
                .edgeId(edgeId)
                .messageType("EDGE_REGISTERED")
                .payload("{\"pairingCode\":\"ABCD-1234\"}")
                .attempts(0)
                .build();
        when(outboxRepository.findTop100BySentAtIsNullOrderByCreatedAtAsc())
                .thenReturn(List.of(row));

        dispatcher.dispatch();

        verify(mqttClient).publish(MqttTopics.bootstrapRegistered(edgeId), row.getPayload());
    }

    @Test
    void mqttException_incrementsAttemptsAndKeepsRowPending() throws Exception {
        CloudOutbox row = CloudOutbox.builder()
                .id(UUID.randomUUID())
                .edgeId(edgeId)
                .messageType("USER_SYNC")
                .payload("{}")
                .attempts(0)
                .build();
        when(outboxRepository.findTop100BySentAtIsNullOrderByCreatedAtAsc())
                .thenReturn(List.of(row));
        doThrow(new MqttException(MqttException.REASON_CODE_CONNECTION_LOST))
                .when(mqttClient).publish(anyString(), anyString());

        dispatcher.dispatch();

        ArgumentCaptor<CloudOutbox> saved = ArgumentCaptor.forClass(CloudOutbox.class);
        verify(outboxRepository).save(saved.capture());
        CloudOutbox after = saved.getValue();
        assertThat(after.getAttempts()).isEqualTo(1);
        assertThat(after.getSentAt()).isNull();
        assertThat(after.getLastAttemptAt()).isNotNull();
        assertThat(after.getLastError()).isNotNull();
    }

    @Test
    void unknownMessageType_isSkippedWithoutMarkingSent() throws MqttException {
        CloudOutbox row = CloudOutbox.builder()
                .id(UUID.randomUUID())
                .edgeId(edgeId)
                .messageType("WHATEVER_FUTURE_TYPE")
                .payload("{}")
                .attempts(0)
                .build();
        when(outboxRepository.findTop100BySentAtIsNullOrderByCreatedAtAsc())
                .thenReturn(List.of(row));

        dispatcher.dispatch();

        verify(mqttClient, never()).publish(anyString(), anyString());
        verify(outboxRepository, never()).save(any(CloudOutbox.class));
    }
}
