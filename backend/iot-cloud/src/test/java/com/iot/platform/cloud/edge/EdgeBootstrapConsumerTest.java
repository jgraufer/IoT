package com.iot.platform.cloud.edge;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.iot.platform.cloud.edgesync.outbox.CloudOutbox;
import com.iot.platform.cloud.edgesync.outbox.CloudOutboxRepository;
import com.iot.platform.common.edge.EdgeRegisterDTO;
import org.eclipse.paho.client.mqttv3.MqttMessage;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EdgeBootstrapConsumerTest {

    @Mock
    private EdgeRepository edgeRepository;

    @Mock
    private EdgePairingCodeRepository edgePairingCodeRepository;

    @Mock
    private CloudOutboxRepository cloudOutboxRepository;

    @Mock
    private PairingCodeGenerator pairingCodeGenerator;

    private EdgeBootstrapConsumer consumer;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @BeforeEach
    void setUp() {
        consumer = new EdgeBootstrapConsumer(
                objectMapper, edgeRepository, edgePairingCodeRepository,
                cloudOutboxRepository, pairingCodeGenerator);
    }

    @Test
    void newEdge_persistsEdgePairingCodeAndEnqueuesEdgeRegistered() throws Exception {
        UUID edgeId = UUID.randomUUID();
        EdgeRegisterDTO dto = EdgeRegisterDTO.builder()
                .edgeId(edgeId).timestamp(Instant.now()).build();
        when(edgeRepository.findById(edgeId)).thenReturn(Optional.empty());
        when(pairingCodeGenerator.generate()).thenReturn("ABCD-1234");

        MqttMessage message = new MqttMessage(objectMapper.writeValueAsBytes(dto));
        consumer.messageArrived("edge/bootstrap/register", message);

        ArgumentCaptor<Edge> edgeCaptor = ArgumentCaptor.forClass(Edge.class);
        verify(edgeRepository).save(edgeCaptor.capture());
        assertThat(edgeCaptor.getValue().getId()).isEqualTo(edgeId);
        assertThat(edgeCaptor.getValue().getName()).startsWith("Edge ");

        ArgumentCaptor<EdgePairingCode> codeCaptor = ArgumentCaptor.forClass(EdgePairingCode.class);
        verify(edgePairingCodeRepository).save(codeCaptor.capture());
        assertThat(codeCaptor.getValue().getCode()).isEqualTo("ABCD-1234");
        assertThat(codeCaptor.getValue().getEdgeId()).isEqualTo(edgeId);
        assertThat(codeCaptor.getValue().getUsed()).isFalse();

        ArgumentCaptor<CloudOutbox> outboxCaptor = ArgumentCaptor.forClass(CloudOutbox.class);
        verify(cloudOutboxRepository).save(outboxCaptor.capture());
        CloudOutbox row = outboxCaptor.getValue();
        assertThat(row.getEdgeId()).isEqualTo(edgeId);
        assertThat(row.getMessageType()).isEqualTo("EDGE_REGISTERED");
        assertThat(row.getPayload()).contains("ABCD-1234").contains(edgeId.toString());
    }

    @Test
    void existingUnpairedEdgeWithActiveCode_isIgnoredIdempotently() throws Exception {
        UUID edgeId = UUID.randomUUID();
        when(edgeRepository.findById(edgeId)).thenReturn(Optional.of(unpairedEdge(edgeId)));
        when(edgePairingCodeRepository.existsByEdgeIdAndUsedFalseAndExpiresAtAfter(eq(edgeId), any(Instant.class)))
                .thenReturn(true);

        consumer.messageArrived("edge/bootstrap/register", registerMessage(edgeId));

        verifyNothingIssued();
    }

    @Test
    void pairedEdge_isIgnored() throws Exception {
        UUID edgeId = UUID.randomUUID();
        Edge paired = unpairedEdge(edgeId);
        paired.setPairedAt(Instant.now());
        when(edgeRepository.findById(edgeId)).thenReturn(Optional.of(paired));

        consumer.messageArrived("edge/bootstrap/register", registerMessage(edgeId));

        verifyNothingIssued();
    }

    @Test
    void existingUnpairedEdgeWithExpiredCode_reissuesCodeWithoutRecreatingEdge() throws Exception {
        UUID edgeId = UUID.randomUUID();
        when(edgeRepository.findById(edgeId)).thenReturn(Optional.of(unpairedEdge(edgeId)));
        when(edgePairingCodeRepository.existsByEdgeIdAndUsedFalseAndExpiresAtAfter(eq(edgeId), any(Instant.class)))
                .thenReturn(false);
        when(pairingCodeGenerator.generate()).thenReturn("WXYZ-5678");

        consumer.messageArrived("edge/bootstrap/register", registerMessage(edgeId));

        verify(edgeRepository, never()).save(any(Edge.class));

        ArgumentCaptor<EdgePairingCode> codeCaptor = ArgumentCaptor.forClass(EdgePairingCode.class);
        verify(edgePairingCodeRepository).save(codeCaptor.capture());
        assertThat(codeCaptor.getValue().getCode()).isEqualTo("WXYZ-5678");
        assertThat(codeCaptor.getValue().getEdgeId()).isEqualTo(edgeId);
        assertThat(codeCaptor.getValue().getExpiresAt()).isAfter(Instant.now());

        ArgumentCaptor<CloudOutbox> outboxCaptor = ArgumentCaptor.forClass(CloudOutbox.class);
        verify(cloudOutboxRepository).save(outboxCaptor.capture());
        assertThat(outboxCaptor.getValue().getMessageType()).isEqualTo("EDGE_REGISTERED");
        assertThat(outboxCaptor.getValue().getPayload()).contains("WXYZ-5678");
    }

    private Edge unpairedEdge(UUID edgeId) {
        return Edge.builder().id(edgeId).name("Edge " + edgeId.toString().substring(0, 8)).build();
    }

    private MqttMessage registerMessage(UUID edgeId) throws Exception {
        EdgeRegisterDTO dto = EdgeRegisterDTO.builder()
                .edgeId(edgeId).timestamp(Instant.now()).build();
        return new MqttMessage(objectMapper.writeValueAsBytes(dto));
    }

    private void verifyNothingIssued() {
        verify(edgeRepository, never()).save(any(Edge.class));
        verify(edgePairingCodeRepository, never()).save(any(EdgePairingCode.class));
        verify(cloudOutboxRepository, never()).save(any(CloudOutbox.class));
    }
}
