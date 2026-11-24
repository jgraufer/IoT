package com.iot.platform.edge.cloudsync;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.iot.platform.common.auth.UserSyncDTO;
import com.iot.platform.common.auth.UserSyncOperation;
import com.iot.platform.common.edge.EdgeRole;
import com.iot.platform.common.protocol.MqttTopics;
import com.iot.platform.edge.auth.LocalUser;
import com.iot.platform.edge.auth.LocalUserRepository;
import com.iot.platform.edge.auth.RefreshTokenRepository;
import com.iot.platform.edge.bootstrap.EdgeIdentity;
import com.iot.platform.edge.bootstrap.EdgeIdentityRepository;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserSyncConsumerTest {

    @Mock
    private LocalUserRepository localUserRepository;
    @Mock
    private RefreshTokenRepository refreshTokenRepository;
    @Mock
    private EdgeIdentityRepository edgeIdentityRepository;

    private UserSyncConsumer consumer;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @BeforeEach
    void setUp() {
        consumer = new UserSyncConsumer(
                objectMapper, localUserRepository, refreshTokenRepository, edgeIdentityRepository);
    }

    private MqttMessage messageOf(UserSyncDTO dto) throws Exception {
        return new MqttMessage(objectMapper.writeValueAsBytes(dto));
    }

    @Test
    void grant_createsLocalUserWithEmailAndName() throws Exception {
        UUID userId = UUID.randomUUID();
        UserSyncDTO dto = UserSyncDTO.builder()
                .operation(UserSyncOperation.GRANT)
                .userId(userId)
                .username("juan")
                .email("juan@example.com")
                .name("Juan Pérez")
                .passwordHash("$2a$hashed")
                .role(EdgeRole.OWNER)
                .timestamp(Instant.parse("2026-05-07T08:00:00Z"))
                .build();
        when(localUserRepository.findById(userId)).thenReturn(Optional.empty());

        consumer.messageArrived(MqttTopics.userSync(UUID.randomUUID()), messageOf(dto));

        ArgumentCaptor<LocalUser> captor = ArgumentCaptor.forClass(LocalUser.class);
        verify(localUserRepository).save(captor.capture());
        LocalUser saved = captor.getValue();
        assertThat(saved.getId()).isEqualTo(userId);
        assertThat(saved.getUsername()).isEqualTo("juan");
        assertThat(saved.getEmail()).isEqualTo("juan@example.com");
        assertThat(saved.getName()).isEqualTo("Juan Pérez");
        assertThat(saved.getPasswordHash()).isEqualTo("$2a$hashed");
        assertThat(saved.getRole()).isEqualTo(EdgeRole.OWNER);
        assertThat(saved.getLastSyncedAt()).isNotNull();
    }

    @Test
    void obsoleteTimestamp_grantIsDropped() throws Exception {
        UUID userId = UUID.randomUUID();
        LocalUser existing = LocalUser.builder()
                .id(userId).username("juan").passwordHash("h").role(EdgeRole.MEMBER)
                .lastSyncedAt(Instant.parse("2026-05-07T10:00:00Z"))
                .build();
        UserSyncDTO dto = UserSyncDTO.builder()
                .operation(UserSyncOperation.GRANT)
                .userId(userId).username("juan").passwordHash("h").role(EdgeRole.OWNER)
                .timestamp(Instant.parse("2026-05-07T08:00:00Z"))
                .build();
        when(localUserRepository.findById(userId)).thenReturn(Optional.of(existing));

        consumer.messageArrived(MqttTopics.userSync(UUID.randomUUID()), messageOf(dto));

        verify(localUserRepository, never()).save(any(LocalUser.class));
    }

    @Test
    void update_appliesOnlyNonNullFields() throws Exception {
        UUID userId = UUID.randomUUID();
        LocalUser existing = LocalUser.builder()
                .id(userId).username("juan").email("juan@example.com").passwordHash("oldHash")
                .role(EdgeRole.MEMBER)
                .lastSyncedAt(Instant.parse("2026-05-06T10:00:00Z"))
                .build();
        UserSyncDTO dto = UserSyncDTO.builder()
                .operation(UserSyncOperation.UPDATE)
                .userId(userId)
                .role(EdgeRole.ADMIN)
                .timestamp(Instant.parse("2026-05-07T10:00:00Z"))
                .build();
        when(localUserRepository.findById(userId)).thenReturn(Optional.of(existing));

        consumer.messageArrived(MqttTopics.userSync(UUID.randomUUID()), messageOf(dto));

        ArgumentCaptor<LocalUser> captor = ArgumentCaptor.forClass(LocalUser.class);
        verify(localUserRepository).save(captor.capture());
        LocalUser saved = captor.getValue();
        assertThat(saved.getRole()).isEqualTo(EdgeRole.ADMIN);
        assertThat(saved.getPasswordHash()).isEqualTo("oldHash");
        assertThat(saved.getUsername()).isEqualTo("juan");
        assertThat(saved.getEmail()).isEqualTo("juan@example.com");
    }

    @Test
    void revoke_deletesLocalUser() throws Exception {
        UUID userId = UUID.randomUUID();
        LocalUser existing = LocalUser.builder()
                .id(userId).username("juan").passwordHash("h").role(EdgeRole.MEMBER)
                .lastSyncedAt(Instant.parse("2026-05-06T10:00:00Z"))
                .build();
        UserSyncDTO dto = UserSyncDTO.builder()
                .operation(UserSyncOperation.REVOKE)
                .userId(userId)
                .timestamp(Instant.parse("2026-05-07T10:00:00Z"))
                .build();
        when(localUserRepository.findById(userId)).thenReturn(Optional.of(existing));

        consumer.messageArrived(MqttTopics.userSync(UUID.randomUUID()), messageOf(dto));

        verify(localUserRepository).deleteById(userId);
    }

    @Test
    void revoke_obsoleteTimestamp_isDropped() throws Exception {
        UUID userId = UUID.randomUUID();
        LocalUser existing = LocalUser.builder()
                .id(userId).username("juan").passwordHash("h").role(EdgeRole.MEMBER)
                .lastSyncedAt(Instant.parse("2026-05-07T10:00:00Z"))
                .build();
        UserSyncDTO dto = UserSyncDTO.builder()
                .operation(UserSyncOperation.REVOKE)
                .userId(userId)
                .timestamp(Instant.parse("2026-05-07T08:00:00Z"))
                .build();
        when(localUserRepository.findById(userId)).thenReturn(Optional.of(existing));

        consumer.messageArrived(MqttTopics.userSync(UUID.randomUUID()), messageOf(dto));

        verify(localUserRepository, never()).deleteById(any());
    }

    @Test
    void ownerTransfer_appliesBothRolesAndRevokesTokens() throws Exception {
        UUID prevId = UUID.randomUUID();
        UUID newId = UUID.randomUUID();
        LocalUser prev = LocalUser.builder()
                .id(prevId).username("a").passwordHash("h").role(EdgeRole.OWNER)
                .lastSyncedAt(Instant.parse("2026-05-06T10:00:00Z")).build();
        LocalUser next = LocalUser.builder()
                .id(newId).username("b").passwordHash("h").role(EdgeRole.ADMIN)
                .lastSyncedAt(Instant.parse("2026-05-06T10:00:00Z")).build();
        UserSyncDTO dto = UserSyncDTO.builder()
                .operation(UserSyncOperation.OWNER_TRANSFER)
                .previousOwnerId(prevId).newOwnerId(newId)
                .timestamp(Instant.parse("2026-05-07T10:00:00Z"))
                .build();
        when(localUserRepository.findById(prevId)).thenReturn(Optional.of(prev));
        when(localUserRepository.findById(newId)).thenReturn(Optional.of(next));

        consumer.messageArrived(MqttTopics.userSync(UUID.randomUUID()), messageOf(dto));

        ArgumentCaptor<LocalUser> captor = ArgumentCaptor.forClass(LocalUser.class);
        verify(localUserRepository, org.mockito.Mockito.times(2)).save(captor.capture());

        var saved = captor.getAllValues();
        assertThat(saved).extracting(LocalUser::getId, LocalUser::getRole)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple(prevId, EdgeRole.ADMIN),
                        org.assertj.core.groups.Tuple.tuple(newId, EdgeRole.OWNER));

        verify(refreshTokenRepository).revokeAllByUserId(prevId);
        verify(refreshTokenRepository).revokeAllByUserId(newId);
    }

    @Test
    void ownerTransfer_missingUser_throwsAndRollsBack() {
        UUID prevId = UUID.randomUUID();
        UUID newId = UUID.randomUUID();
        UserSyncDTO dto = UserSyncDTO.builder()
                .operation(UserSyncOperation.OWNER_TRANSFER)
                .previousOwnerId(prevId).newOwnerId(newId)
                .timestamp(Instant.parse("2026-05-07T10:00:00Z"))
                .build();
        when(localUserRepository.findById(prevId)).thenReturn(Optional.empty());
        when(localUserRepository.findById(newId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> consumer.apply(dto))
                .isInstanceOf(IllegalStateException.class);

        verify(localUserRepository, never()).save(any(LocalUser.class));
        verify(refreshTokenRepository, never()).revokeAllByUserId(any());
    }

    @Test
    void grant_ownerOnUnpairedEdge_marksPairedAndClearsCode() throws Exception {
        UUID userId = UUID.randomUUID();
        UserSyncDTO dto = UserSyncDTO.builder()
                .operation(UserSyncOperation.GRANT)
                .userId(userId)
                .username("owner")
                .passwordHash("$2a$hashed")
                .role(EdgeRole.OWNER)
                .timestamp(Instant.parse("2026-05-07T08:00:00Z"))
                .build();
        EdgeIdentity identity = EdgeIdentity.builder()
                .id(1)
                .edgeId(UUID.randomUUID())
                .jwtSecret("secret")
                .pairingCode("ABCD-1234")
                .pairingCodeExpiresAt(Instant.parse("2026-05-07T09:00:00Z"))
                .pairedAt(null)
                .build();
        when(localUserRepository.findById(userId)).thenReturn(Optional.empty());
        when(edgeIdentityRepository.findSingleton()).thenReturn(Optional.of(identity));

        consumer.messageArrived(MqttTopics.userSync(UUID.randomUUID()), messageOf(dto));

        verify(localUserRepository).save(any(LocalUser.class));
        ArgumentCaptor<EdgeIdentity> idCaptor = ArgumentCaptor.forClass(EdgeIdentity.class);
        verify(edgeIdentityRepository).save(idCaptor.capture());
        EdgeIdentity savedIdentity = idCaptor.getValue();
        assertThat(savedIdentity.getPairedAt()).isNotNull();
        assertThat(savedIdentity.getPairingCode()).isNull();
        assertThat(savedIdentity.getPairingCodeExpiresAt()).isNull();
    }

    @Test
    void grant_ownerOnAlreadyPairedEdge_doesNotOverwritePairedAt() throws Exception {
        UUID userId = UUID.randomUUID();
        UserSyncDTO dto = UserSyncDTO.builder()
                .operation(UserSyncOperation.GRANT)
                .userId(userId)
                .username("owner")
                .passwordHash("$2a$hashed")
                .role(EdgeRole.OWNER)
                .timestamp(Instant.parse("2026-05-07T08:00:00Z"))
                .build();
        Instant originalPairedAt = Instant.parse("2026-05-01T12:00:00Z");
        EdgeIdentity identity = EdgeIdentity.builder()
                .id(1)
                .edgeId(UUID.randomUUID())
                .jwtSecret("secret")
                .pairingCode(null)
                .pairingCodeExpiresAt(null)
                .pairedAt(originalPairedAt)
                .build();
        when(localUserRepository.findById(userId)).thenReturn(Optional.empty());
        when(edgeIdentityRepository.findSingleton()).thenReturn(Optional.of(identity));

        consumer.messageArrived(MqttTopics.userSync(UUID.randomUUID()), messageOf(dto));

        verify(localUserRepository).save(any(LocalUser.class));
        verify(edgeIdentityRepository, never()).save(any(EdgeIdentity.class));
        assertThat(identity.getPairedAt()).isEqualTo(originalPairedAt);
    }

    @Test
    void grant_memberOnAlreadyPairedEdge_doesNotTouchEdgeIdentity() throws Exception {
        UUID userId = UUID.randomUUID();
        UserSyncDTO dto = UserSyncDTO.builder()
                .operation(UserSyncOperation.GRANT)
                .userId(userId)
                .username("member")
                .passwordHash("$2a$hashed")
                .role(EdgeRole.MEMBER)
                .timestamp(Instant.parse("2026-05-07T08:00:00Z"))
                .build();
        when(localUserRepository.findById(userId)).thenReturn(Optional.empty());

        consumer.messageArrived(MqttTopics.userSync(UUID.randomUUID()), messageOf(dto));

        verify(localUserRepository).save(any(LocalUser.class));
        verify(edgeIdentityRepository, never()).findSingleton();
        verify(edgeIdentityRepository, never()).save(any(EdgeIdentity.class));
    }

    @Test
    void ownerTransfer_alreadyApplied_isIdempotent() {
        UUID prevId = UUID.randomUUID();
        UUID newId = UUID.randomUUID();
        LocalUser prev = LocalUser.builder()
                .id(prevId).username("a").passwordHash("h").role(EdgeRole.ADMIN)
                .lastSyncedAt(Instant.parse("2026-05-07T10:00:00Z")).build();
        LocalUser next = LocalUser.builder()
                .id(newId).username("b").passwordHash("h").role(EdgeRole.OWNER)
                .lastSyncedAt(Instant.parse("2026-05-07T10:00:00Z")).build();
        UserSyncDTO dto = UserSyncDTO.builder()
                .operation(UserSyncOperation.OWNER_TRANSFER)
                .previousOwnerId(prevId).newOwnerId(newId)
                .timestamp(Instant.parse("2026-05-07T10:00:00Z"))
                .build();
        when(localUserRepository.findById(prevId)).thenReturn(Optional.of(prev));
        when(localUserRepository.findById(newId)).thenReturn(Optional.of(next));

        consumer.apply(dto);

        verify(localUserRepository, never()).save(any(LocalUser.class));
        verify(refreshTokenRepository, never()).revokeAllByUserId(any());
    }
}
