package com.iot.platform.cloud.edge;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.iot.platform.cloud.auth.User;
import com.iot.platform.cloud.auth.UserRepository;
import com.iot.platform.cloud.edge.access.UserEdgeAccess;
import com.iot.platform.cloud.edge.access.UserEdgeAccessRepository;
import com.iot.platform.cloud.edge.dto.PairResponseDTO;
import com.iot.platform.cloud.edgesync.outbox.CloudOutbox;
import com.iot.platform.cloud.edgesync.outbox.CloudOutboxRepository;
import com.iot.platform.cloud.exception.AlreadyHasAccessException;
import com.iot.platform.cloud.exception.InvalidPairingCodeException;
import com.iot.platform.cloud.exception.PairingCodeExpiredException;
import com.iot.platform.common.edge.EdgeRole;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EdgePairingServiceTest {

    @Mock
    private EdgePairingCodeRepository edgePairingCodeRepository;
    @Mock
    private EdgeRepository edgeRepository;
    @Mock
    private UserEdgeAccessRepository userEdgeAccessRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private CloudOutboxRepository cloudOutboxRepository;

    private EdgePairingService service;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private UUID userId;
    private UUID edgeId;
    private final String code = "ABCD-1234";

    @BeforeEach
    void setUp() {
        service = new EdgePairingService(
                edgePairingCodeRepository, edgeRepository,
                userEdgeAccessRepository, userRepository,
                cloudOutboxRepository, objectMapper);
        userId = UUID.randomUUID();
        edgeId = UUID.randomUUID();
    }

    @Test
    void unknownCode_throwsInvalidPairingCode() {
        when(edgePairingCodeRepository.findByCodeAndUsedFalse(code)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.pair(userId, code))
                .isInstanceOf(InvalidPairingCodeException.class);

        verify(cloudOutboxRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void expiredCode_throwsPairingCodeExpired() {
        EdgePairingCode pc = EdgePairingCode.builder()
                .code(code).edgeId(edgeId)
                .expiresAt(Instant.parse("2020-01-01T00:00:00Z"))
                .used(false).build();
        when(edgePairingCodeRepository.findByCodeAndUsedFalse(code)).thenReturn(Optional.of(pc));

        assertThatThrownBy(() -> service.pair(userId, code))
                .isInstanceOf(PairingCodeExpiredException.class);
    }

    @Test
    void userAlreadyHasAccess_throwsAlreadyHasAccess() {
        EdgePairingCode pc = EdgePairingCode.builder()
                .code(code).edgeId(edgeId)
                .expiresAt(Instant.parse("2099-01-01T00:00:00Z"))
                .used(false).build();
        when(edgePairingCodeRepository.findByCodeAndUsedFalse(code)).thenReturn(Optional.of(pc));
        when(userEdgeAccessRepository.findByUserIdAndEdgeId(userId, edgeId))
                .thenReturn(Optional.of(UserEdgeAccess.builder().build()));

        assertThatThrownBy(() -> service.pair(userId, code))
                .isInstanceOf(AlreadyHasAccessException.class);
    }

    @Test
    void successfulPair_marksCodeUsedAndEnqueuesUserSync() throws Exception {
        EdgePairingCode pc = EdgePairingCode.builder()
                .code(code).edgeId(edgeId)
                .expiresAt(Instant.parse("2099-01-01T00:00:00Z"))
                .used(false).build();
        Edge edge = Edge.builder().id(edgeId).name("Edge abcdef12").build();
        User user = User.builder()
                .id(userId).username("juan").email("juan@example.com").name("Juan Pérez")
                .passwordHash("$2a$10$hashed").build();

        when(edgePairingCodeRepository.findByCodeAndUsedFalse(code)).thenReturn(Optional.of(pc));
        when(userEdgeAccessRepository.findByUserIdAndEdgeId(userId, edgeId)).thenReturn(Optional.empty());
        when(edgeRepository.findById(edgeId)).thenReturn(Optional.of(edge));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        PairResponseDTO response = service.pair(userId, code);

        assertThat(response.getEdgeId()).isEqualTo(edgeId);
        assertThat(response.getName()).isEqualTo("Edge abcdef12");

        ArgumentCaptor<EdgePairingCode> codeCaptor = ArgumentCaptor.forClass(EdgePairingCode.class);
        verify(edgePairingCodeRepository).save(codeCaptor.capture());
        assertThat(codeCaptor.getValue().getUsed()).isTrue();

        ArgumentCaptor<Edge> edgeCaptor = ArgumentCaptor.forClass(Edge.class);
        verify(edgeRepository).save(edgeCaptor.capture());
        assertThat(edgeCaptor.getValue().getPairedAt()).isNotNull();

        ArgumentCaptor<UserEdgeAccess> accessCaptor = ArgumentCaptor.forClass(UserEdgeAccess.class);
        verify(userEdgeAccessRepository).save(accessCaptor.capture());
        UserEdgeAccess saved = accessCaptor.getValue();
        assertThat(saved.getUserId()).isEqualTo(userId);
        assertThat(saved.getEdgeId()).isEqualTo(edgeId);
        assertThat(saved.getRole()).isEqualTo(EdgeRole.OWNER);
        assertThat(saved.getGrantedBy()).isNull();

        ArgumentCaptor<CloudOutbox> outboxCaptor = ArgumentCaptor.forClass(CloudOutbox.class);
        verify(cloudOutboxRepository).save(outboxCaptor.capture());
        CloudOutbox row = outboxCaptor.getValue();
        assertThat(row.getMessageType()).isEqualTo("USER_SYNC");
        assertThat(row.getEdgeId()).isEqualTo(edgeId);
        assertThat(row.getPayload())
                .contains("\"operation\":\"GRANT\"")
                .contains("\"role\":\"OWNER\"")
                .contains("juan")
                .contains("\"email\":\"juan@example.com\"")
                .contains("\"name\":\"Juan Pérez\"")
                .contains(userId.toString());
    }
}
