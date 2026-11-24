package com.iot.platform.cloud.edge.access;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iot.platform.cloud.auth.User;
import com.iot.platform.cloud.auth.UserRepository;
import com.iot.platform.cloud.edge.Edge;
import com.iot.platform.cloud.edge.EdgeRepository;
import com.iot.platform.cloud.edge.access.dto.InvitationDTO;
import com.iot.platform.cloud.edgesync.outbox.CloudOutbox;
import com.iot.platform.cloud.edgesync.outbox.CloudOutboxRepository;
import com.iot.platform.cloud.exception.AccessDeniedException;
import com.iot.platform.cloud.exception.AlreadyHasAccessException;
import com.iot.platform.cloud.exception.InvalidInvitationStateException;
import com.iot.platform.cloud.exception.InvitationAlreadyPendingException;
import com.iot.platform.cloud.exception.ResourceNotFoundException;
import com.iot.platform.common.auth.UserSyncDTO;
import com.iot.platform.common.auth.UserSyncOperation;
import com.iot.platform.common.edge.EdgeRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InvitationServiceTest {

    @Mock
    private EdgeInvitationRepository invitationRepository;
    @Mock
    private UserEdgeAccessRepository userEdgeAccessRepository;
    @Mock
    private EdgeRepository edgeRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private CloudOutboxRepository cloudOutboxRepository;
    @Spy
    private ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @InjectMocks
    private InvitationService service;

    private UUID inviteeId;
    private UUID ownerId;
    private UUID edgeId;
    private User invitee;

    @BeforeEach
    void setUp() {
        inviteeId = UUID.randomUUID();
        ownerId = UUID.randomUUID();
        edgeId = UUID.randomUUID();
        invitee = User.builder()
                .id(inviteeId).username("juan").email("juan@example.com").name("Juan Pérez")
                .passwordHash("$2a$hashed").isActive(true).build();
    }

    private void mockOwner() {
        when(userEdgeAccessRepository.findByUserIdAndEdgeId(ownerId, edgeId))
                .thenReturn(Optional.of(UserEdgeAccess.builder()
                        .userId(ownerId).edgeId(edgeId).role(EdgeRole.OWNER).build()));
    }

    private EdgeInvitation pendingInvitation(UUID id) {
        return EdgeInvitation.builder()
                .id(id).userId(inviteeId).edgeId(edgeId)
                .status(EdgeInvitation.Status.PENDING)
                .invitedBy(ownerId)
                .expiresAt(Instant.now().plus(Duration.ofDays(7)))
                .build();
    }

    @Test
    void invite_success_persistsPending() {
        mockOwner();
        when(userRepository.findByEmail("juan@example.com")).thenReturn(Optional.of(invitee));
        when(userEdgeAccessRepository.findByUserIdAndEdgeId(inviteeId, edgeId))
                .thenReturn(Optional.empty());
        when(invitationRepository.existsByUserIdAndEdgeIdAndStatus(
                inviteeId, edgeId, EdgeInvitation.Status.PENDING)).thenReturn(false);
        when(invitationRepository.save(any(EdgeInvitation.class))).thenAnswer(inv -> {
            EdgeInvitation e = inv.getArgument(0);
            e.setId(UUID.randomUUID());
            e.setCreatedAt(Instant.now());
            return e;
        });
        when(edgeRepository.findById(edgeId))
                .thenReturn(Optional.of(Edge.builder().id(edgeId).name("Casa").build()));

        InvitationDTO response = service.invite(ownerId, edgeId, "juan@example.com");

        assertThat(response.getInvitedUserId()).isEqualTo(inviteeId);
        assertThat(response.getInvitedEmail()).isEqualTo("juan@example.com");
        assertThat(response.getEdgeName()).isEqualTo("Casa");

        ArgumentCaptor<EdgeInvitation> captor = ArgumentCaptor.forClass(EdgeInvitation.class);
        verify(invitationRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(EdgeInvitation.Status.PENDING);
        assertThat(captor.getValue().getInvitedBy()).isEqualTo(ownerId);
        assertThat(captor.getValue().getExpiresAt())
                .isAfter(Instant.now().plus(Duration.ofDays(6)))
                .isBefore(Instant.now().plus(Duration.ofDays(8)));
    }

    @Test
    void invite_unknownEmail_throws404() {
        mockOwner();
        when(userRepository.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.invite(ownerId, edgeId, "ghost@example.com"))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(invitationRepository, never()).save(any());
    }

    @Test
    void invite_nonOwner_throws403() {
        when(userEdgeAccessRepository.findByUserIdAndEdgeId(ownerId, edgeId))
                .thenReturn(Optional.of(UserEdgeAccess.builder()
                        .userId(ownerId).edgeId(edgeId).role(EdgeRole.ADMIN).build()));

        assertThatThrownBy(() -> service.invite(ownerId, edgeId, "juan@example.com"))
                .isInstanceOf(AccessDeniedException.class);

        verify(invitationRepository, never()).save(any());
    }

    @Test
    void invite_alreadyMember_throws409() {
        mockOwner();
        when(userRepository.findByEmail("juan@example.com")).thenReturn(Optional.of(invitee));
        when(userEdgeAccessRepository.findByUserIdAndEdgeId(inviteeId, edgeId))
                .thenReturn(Optional.of(UserEdgeAccess.builder()
                        .userId(inviteeId).edgeId(edgeId).role(EdgeRole.MEMBER).build()));

        assertThatThrownBy(() -> service.invite(ownerId, edgeId, "juan@example.com"))
                .isInstanceOf(AlreadyHasAccessException.class);

        verify(invitationRepository, never()).save(any());
    }

    @Test
    void invite_pendingExists_throws409() {
        mockOwner();
        when(userRepository.findByEmail("juan@example.com")).thenReturn(Optional.of(invitee));
        when(userEdgeAccessRepository.findByUserIdAndEdgeId(inviteeId, edgeId))
                .thenReturn(Optional.empty());
        when(invitationRepository.existsByUserIdAndEdgeIdAndStatus(
                inviteeId, edgeId, EdgeInvitation.Status.PENDING)).thenReturn(true);

        assertThatThrownBy(() -> service.invite(ownerId, edgeId, "juan@example.com"))
                .isInstanceOf(InvitationAlreadyPendingException.class);

        verify(invitationRepository, never()).save(any());
    }

    @Test
    void invite_afterRejected_allowed() {
        mockOwner();
        when(userRepository.findByEmail("juan@example.com")).thenReturn(Optional.of(invitee));
        when(userEdgeAccessRepository.findByUserIdAndEdgeId(inviteeId, edgeId))
                .thenReturn(Optional.empty());
        when(invitationRepository.existsByUserIdAndEdgeIdAndStatus(
                inviteeId, edgeId, EdgeInvitation.Status.PENDING)).thenReturn(false);
        when(invitationRepository.save(any(EdgeInvitation.class))).thenAnswer(inv -> inv.getArgument(0));
        when(edgeRepository.findById(edgeId))
                .thenReturn(Optional.of(Edge.builder().id(edgeId).name("Casa").build()));

        InvitationDTO response = service.invite(ownerId, edgeId, "juan@example.com");

        assertThat(response.getInvitedUserId()).isEqualTo(inviteeId);
        verify(invitationRepository).save(any(EdgeInvitation.class));
    }

    @Test
    void listSent_nonOwner_throws403() {
        when(userEdgeAccessRepository.findByUserIdAndEdgeId(ownerId, edgeId))
                .thenReturn(Optional.of(UserEdgeAccess.builder()
                        .userId(ownerId).edgeId(edgeId).role(EdgeRole.MEMBER).build()));

        assertThatThrownBy(() -> service.listSentInvitations(edgeId, ownerId))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void listSent_owner_returnsEnrichedList() {
        mockOwner();
        EdgeInvitation inv = pendingInvitation(UUID.randomUUID());
        when(invitationRepository.findByEdgeIdAndStatus(edgeId, EdgeInvitation.Status.PENDING))
                .thenReturn(List.of(inv));
        when(userRepository.findAllById(List.of(inviteeId))).thenReturn(List.of(invitee));
        when(edgeRepository.findById(edgeId))
                .thenReturn(Optional.of(Edge.builder().id(edgeId).name("Casa").build()));

        List<InvitationDTO> result = service.listSentInvitations(edgeId, ownerId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getInvitedEmail()).isEqualTo("juan@example.com");
    }

    @Test
    void listMine_returnsPendingWithEdgeName() {
        EdgeInvitation inv = pendingInvitation(UUID.randomUUID());
        when(invitationRepository.findByUserIdAndStatus(inviteeId, EdgeInvitation.Status.PENDING))
                .thenReturn(List.of(inv));
        when(edgeRepository.findAllById(List.of(edgeId)))
                .thenReturn(List.of(Edge.builder().id(edgeId).name("Casa").build()));

        List<InvitationDTO> result = service.listMyInvitations(inviteeId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getEdgeId()).isEqualTo(edgeId);
        assertThat(result.get(0).getEdgeName()).isEqualTo("Casa");
    }

    @Test
    void accept_success_grantsMemberAndPublishesUserSync() throws Exception {
        UUID invitationId = UUID.randomUUID();
        EdgeInvitation inv = pendingInvitation(invitationId);
        when(invitationRepository.findById(invitationId)).thenReturn(Optional.of(inv));
        when(userEdgeAccessRepository.findByUserIdAndEdgeId(inviteeId, edgeId))
                .thenReturn(Optional.empty());
        when(userRepository.findById(inviteeId)).thenReturn(Optional.of(invitee));
        when(edgeRepository.findById(edgeId))
                .thenReturn(Optional.of(Edge.builder().id(edgeId).name("Casa").build()));

        InvitationDTO response = service.accept(inviteeId, invitationId);

        assertThat(response.getId()).isEqualTo(invitationId);

        ArgumentCaptor<UserEdgeAccess> accessCaptor = ArgumentCaptor.forClass(UserEdgeAccess.class);
        verify(userEdgeAccessRepository).save(accessCaptor.capture());
        assertThat(accessCaptor.getValue().getRole()).isEqualTo(EdgeRole.MEMBER);
        assertThat(accessCaptor.getValue().getGrantedBy()).isEqualTo(ownerId);

        ArgumentCaptor<CloudOutbox> outboxCaptor = ArgumentCaptor.forClass(CloudOutbox.class);
        verify(cloudOutboxRepository).save(outboxCaptor.capture());
        CloudOutbox outboxRow = outboxCaptor.getValue();
        assertThat(outboxRow.getEdgeId()).isEqualTo(edgeId);
        assertThat(outboxRow.getMessageType()).isEqualTo("USER_SYNC");

        UserSyncDTO sync = objectMapper.readValue(outboxRow.getPayload(), UserSyncDTO.class);
        assertThat(sync.getOperation()).isEqualTo(UserSyncOperation.GRANT);
        assertThat(sync.getRole()).isEqualTo(EdgeRole.MEMBER);
        assertThat(sync.getUserId()).isEqualTo(inviteeId);
        assertThat(sync.getUsername()).isEqualTo("juan");
        assertThat(sync.getEmail()).isEqualTo("juan@example.com");
        assertThat(sync.getName()).isEqualTo("Juan Pérez");

        assertThat(inv.getStatus()).isEqualTo(EdgeInvitation.Status.ACCEPTED);
        assertThat(inv.getResolvedBy()).isEqualTo(inviteeId);
    }

    @Test
    void accept_notInvitee_throws403() {
        UUID invitationId = UUID.randomUUID();
        when(invitationRepository.findById(invitationId))
                .thenReturn(Optional.of(pendingInvitation(invitationId)));

        assertThatThrownBy(() -> service.accept(UUID.randomUUID(), invitationId))
                .isInstanceOf(AccessDeniedException.class);

        verify(cloudOutboxRepository, never()).save(any());
        verify(userEdgeAccessRepository, never()).save(any());
    }

    @Test
    void accept_notPending_throwsInvalidState() {
        UUID invitationId = UUID.randomUUID();
        EdgeInvitation inv = pendingInvitation(invitationId);
        inv.setStatus(EdgeInvitation.Status.REJECTED);
        when(invitationRepository.findById(invitationId)).thenReturn(Optional.of(inv));

        assertThatThrownBy(() -> service.accept(inviteeId, invitationId))
                .isInstanceOf(InvalidInvitationStateException.class);
    }

    @Test
    void accept_expired_throwsInvalidState() {
        UUID invitationId = UUID.randomUUID();
        EdgeInvitation inv = pendingInvitation(invitationId);
        inv.setExpiresAt(Instant.now().minus(Duration.ofMinutes(1)));
        when(invitationRepository.findById(invitationId)).thenReturn(Optional.of(inv));

        assertThatThrownBy(() -> service.accept(inviteeId, invitationId))
                .isInstanceOf(InvalidInvitationStateException.class);
    }

    @Test
    void accept_alreadyMember_throws409() {
        UUID invitationId = UUID.randomUUID();
        when(invitationRepository.findById(invitationId))
                .thenReturn(Optional.of(pendingInvitation(invitationId)));
        when(userEdgeAccessRepository.findByUserIdAndEdgeId(inviteeId, edgeId))
                .thenReturn(Optional.of(UserEdgeAccess.builder()
                        .userId(inviteeId).edgeId(edgeId).role(EdgeRole.MEMBER).build()));

        assertThatThrownBy(() -> service.accept(inviteeId, invitationId))
                .isInstanceOf(AlreadyHasAccessException.class);

        verify(cloudOutboxRepository, never()).save(any());
    }

    @Test
    void reject_success_marksRejectedAndDoesNotPublish() {
        UUID invitationId = UUID.randomUUID();
        EdgeInvitation inv = pendingInvitation(invitationId);
        when(invitationRepository.findById(invitationId)).thenReturn(Optional.of(inv));
        when(userRepository.findById(inviteeId)).thenReturn(Optional.of(invitee));
        when(edgeRepository.findById(edgeId))
                .thenReturn(Optional.of(Edge.builder().id(edgeId).name("Casa").build()));

        InvitationDTO response = service.reject(inviteeId, invitationId);

        assertThat(response.getId()).isEqualTo(invitationId);
        assertThat(inv.getStatus()).isEqualTo(EdgeInvitation.Status.REJECTED);
        assertThat(inv.getResolvedBy()).isEqualTo(inviteeId);
        verify(cloudOutboxRepository, never()).save(any());
        verify(userEdgeAccessRepository, never()).save(any());
    }

    @Test
    void reject_notInvitee_throws403() {
        UUID invitationId = UUID.randomUUID();
        when(invitationRepository.findById(invitationId))
                .thenReturn(Optional.of(pendingInvitation(invitationId)));

        assertThatThrownBy(() -> service.reject(UUID.randomUUID(), invitationId))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void cancel_success_marksCancelledAndDoesNotPublish() {
        UUID invitationId = UUID.randomUUID();
        EdgeInvitation inv = pendingInvitation(invitationId);
        mockOwner();
        when(invitationRepository.findById(invitationId)).thenReturn(Optional.of(inv));

        service.cancelInvitation(edgeId, invitationId, ownerId);

        assertThat(inv.getStatus()).isEqualTo(EdgeInvitation.Status.CANCELLED);
        assertThat(inv.getResolvedBy()).isEqualTo(ownerId);
        verify(cloudOutboxRepository, never()).save(any());
    }

    @Test
    void cancel_nonOwner_throws403() {
        when(userEdgeAccessRepository.findByUserIdAndEdgeId(ownerId, edgeId))
                .thenReturn(Optional.of(UserEdgeAccess.builder()
                        .userId(ownerId).edgeId(edgeId).role(EdgeRole.MEMBER).build()));

        assertThatThrownBy(() -> service.cancelInvitation(edgeId, UUID.randomUUID(), ownerId))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void cancel_notPending_throwsInvalidState() {
        UUID invitationId = UUID.randomUUID();
        EdgeInvitation inv = pendingInvitation(invitationId);
        inv.setStatus(EdgeInvitation.Status.ACCEPTED);
        mockOwner();
        when(invitationRepository.findById(invitationId)).thenReturn(Optional.of(inv));

        assertThatThrownBy(() -> service.cancelInvitation(edgeId, invitationId, ownerId))
                .isInstanceOf(InvalidInvitationStateException.class);
    }

    @Test
    void cancel_differentEdge_throws404() {
        UUID invitationId = UUID.randomUUID();
        EdgeInvitation inv = pendingInvitation(invitationId);
        inv.setEdgeId(UUID.randomUUID());
        mockOwner();
        when(invitationRepository.findById(invitationId)).thenReturn(Optional.of(inv));

        assertThatThrownBy(() -> service.cancelInvitation(edgeId, invitationId, ownerId))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
