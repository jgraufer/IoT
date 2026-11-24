package com.iot.platform.edge.command;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iot.platform.common.device.CapabilityType;
import com.iot.platform.common.device.CommandDTO;
import com.iot.platform.common.protocol.MqttTopics;
import com.iot.platform.edge.bootstrap.EdgeIdentity;
import com.iot.platform.edge.bootstrap.EdgeIdentityRepository;
import com.iot.platform.edge.mqtt.CloudMqttClient;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommandConsumerTest {

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    @Mock
    private CloudMqttClient cloudMqttClient;
    @Mock
    private EdgeIdentityRepository edgeIdentityRepository;
    @Mock
    private CommandService commandService;

    @InjectMocks
    private CommandConsumer consumer;

    private UUID edgeId;
    private UUID deviceId;
    private UUID issuedBy;

    @BeforeEach
    void setUp() {
        edgeId = UUID.randomUUID();
        deviceId = UUID.randomUUID();
        issuedBy = UUID.randomUUID();
    }

    @Test
    void subscribe_usesEdgeCommandsTopic() throws Exception {
        when(edgeIdentityRepository.findSingleton())
                .thenReturn(Optional.of(EdgeIdentity.builder().edgeId(edgeId).build()));

        consumer.subscribe();

        verify(cloudMqttClient).subscribe(eq(MqttTopics.commands(edgeId)), eq(consumer));
    }

    @Test
    void messageArrived_delegatesToCommandServiceWithPayloadIssuedBy() throws Exception {
        CommandDTO dto = CommandDTO.builder()
                .edgeId(edgeId)
                .deviceId(deviceId)
                .capability(CapabilityType.POWER_SWITCH)
                .value(true)
                .issuedBy(issuedBy)
                .issuedAt(Instant.parse("2026-06-03T10:00:00Z"))
                .build();
        MqttMessage message = new MqttMessage(objectMapper.writeValueAsBytes(dto));

        consumer.messageArrived(MqttTopics.commands(edgeId), message);

        verify(commandService).issueCommand(
                eq(deviceId), eq(CapabilityType.POWER_SWITCH), eq(true), eq(issuedBy));
    }

    @Test
    void messageArrived_malformedPayload_isSwallowed() {
        MqttMessage message = new MqttMessage("not-json".getBytes());

        consumer.messageArrived(MqttTopics.commands(edgeId), message);

        verifyNoInteractions(commandService);
    }
}
