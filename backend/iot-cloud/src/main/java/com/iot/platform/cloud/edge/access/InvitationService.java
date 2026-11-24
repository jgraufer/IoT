package com.iot.platform.cloud.edge.access;

import com.fasterxml.jackson.core.JsonProcessingException;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class InvitationService {

    private final EdgeInvitationRepository invitationRepository;
    private final UserEdgeAccessRepository userEdgeAccessRepository;
    private final EdgeRepository edgeRepository;
    private final UserRepository userRepository;
    private final CloudOutboxRepository cloudOutboxRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public InvitationDTO invite(UUID inviterId, UUID edgeId, String email) {
        requireOwner(edgeId, inviterId);

        User invitee = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", email));

        if (userEdgeAccessRepository.findByUserIdAndEdgeId(invitee.getId(), edgeId).isPresent()) {
            throw new AlreadyHasAccessException();
        }
        if (invitationRepository.existsByUserIdAndEdgeIdAndStatus(
                invitee.getId(), edgeId, EdgeInvitation.Status.PENDING)) {
            throw new InvitationAlreadyPendingException();
        }

        EdgeInvitation invitation = EdgeInvitation.builder()
                .userId(invitee.getId())
                .edgeId(edgeId)
                .status(EdgeInvitation.Status.PENDING)
                .invitedBy(inviterId)
                .expiresAt(Instant.now().plus(Duration.ofDays(7)))
                .build();
        invitation = invitationRepository.save(invitation);

        log.info("Invitation created: invitationId={}, edgeId={}, inviteeId={}, invitedBy={}",
                invitation.getId(), edgeId, invitee.getId(), inviterId);

        return mapToDTO(invitation, invitee, edgeName(edgeId));
    }

    @Transactional(readOnly = true)
    public List<InvitationDTO> listSentInvitations(UUID edgeId, UUID requesterId) {
        requireOwner(edgeId, requesterId);

        List<EdgeInvitation> pending = invitationRepository
                .findByEdgeIdAndStatus(edgeId, EdgeInvitation.Status.PENDING);
        if (pending.isEmpty()) {
            return List.of();
        }

        Map<UUID, User> usersById = usersByIds(
                pending.stream().map(EdgeInvitation::getUserId).toList());
        String edgeName = edgeName(edgeId);

        return pending.stream()
                .map(inv -> mapToDTO(inv, usersById.get(inv.getUserId()), edgeName))
                .toList();
    }

    @Transactional
    public void cancelInvitation(UUID edgeId, UUID invitationId, UUID requesterId) {
        requireOwner(edgeId, requesterId);

        EdgeInvitation invitation = loadPending(invitationId);
        if (!invitation.getEdgeId().equals(edgeId)) {
            throw new ResourceNotFoundException("EdgeInvitation", "id", invitationId);
        }

        invitation.setStatus(EdgeInvitation.Status.CANCELLED);
        invitation.setResolvedAt(Instant.now());
        invitation.setResolvedBy(requesterId);
        invitationRepository.save(invitation);

        log.info("Invitation {} cancelled by owner {}", invitationId, requesterId);
    }

    @Transactional(readOnly = true)
    public List<InvitationDTO> listMyInvitations(UUID userId) {
        List<EdgeInvitation> pending = invitationRepository
                .findByUserIdAndStatus(userId, EdgeInvitation.Status.PENDING);
        if (pending.isEmpty()) {
            return List.of();
        }

        Map<UUID, String> edgeNames = edgeRepository.findAllById(
                        pending.stream().map(EdgeInvitation::getEdgeId).toList()).stream()
                .collect(Collectors.toMap(Edge::getId, Edge::getName));

        return pending.stream()
                .map(inv -> mapToDTO(inv, null, edgeNames.get(inv.getEdgeId())))
                .toList();
    }

    @Transactional
    public InvitationDTO accept(UUID userId, UUID invitationId) {
        EdgeInvitation invitation = loadPending(invitationId);
        requireInvitee(invitation, userId);

        UUID edgeId = invitation.getEdgeId();
        if (userEdgeAccessRepository.findByUserIdAndEdgeId(userId, edgeId).isPresent()) {
            throw new AlreadyHasAccessException();
        }

        User invitee = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        invitation.setStatus(EdgeInvitation.Status.ACCEPTED);
        invitation.setResolvedAt(Instant.now());
        invitation.setResolvedBy(userId);
        invitationRepository.save(invitation);

        UserEdgeAccess access = UserEdgeAccess.builder()
                .userId(userId)
                .edgeId(edgeId)
                .role(EdgeRole.MEMBER)
                .grantedBy(invitation.getInvitedBy())
                .build();
        userEdgeAccessRepository.save(access);

        enqueueGrant(edgeId, invitee);

        log.info("Invitation {} accepted: edgeId={}, userId={}", invitationId, edgeId, userId);

        return mapToDTO(invitation, invitee, edgeName(edgeId));
    }

    @Transactional
    public InvitationDTO reject(UUID userId, UUID invitationId) {
        EdgeInvitation invitation = loadPending(invitationId);
        requireInvitee(invitation, userId);

        invitation.setStatus(EdgeInvitation.Status.REJECTED);
        invitation.setResolvedAt(Instant.now());
        invitation.setResolvedBy(userId);
        invitationRepository.save(invitation);

        log.info("Invitation {} rejected by invitee {}", invitationId, userId);

        User invitee = userRepository.findById(userId).orElse(null);
        return mapToDTO(invitation, invitee, edgeName(invitation.getEdgeId()));
    }

    private void enqueueGrant(UUID edgeId, User invitee) {
        UserSyncDTO sync = UserSyncDTO.builder()
                .operation(UserSyncOperation.GRANT)
                .userId(invitee.getId())
                .username(invitee.getUsername())
                .email(invitee.getEmail())
                .name(invitee.getName())
                .passwordHash(invitee.getPasswordHash())
                .role(EdgeRole.MEMBER)
                .timestamp(Instant.now())
                .build();
        try {
            CloudOutbox outboxRow = CloudOutbox.builder()
                    .edgeId(edgeId)
                    .messageType("USER_SYNC")
                    .payload(objectMapper.writeValueAsString(sync))
                    .attempts(0)
                    .build();
            cloudOutboxRepository.save(outboxRow);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize UserSyncDTO for invitation accept", e);
        }
    }

    private EdgeInvitation loadPending(UUID invitationId) {
        EdgeInvitation invitation = invitationRepository.findById(invitationId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "EdgeInvitation", "id", invitationId));
        if (invitation.getStatus() != EdgeInvitation.Status.PENDING) {
            throw new InvalidInvitationStateException(
                    "Invitation is not pending: status=" + invitation.getStatus());
        }
        if (invitation.getExpiresAt().isBefore(Instant.now())) {
            throw new InvalidInvitationStateException("Invitation has expired");
        }
        return invitation;
    }

    private void requireOwner(UUID edgeId, UUID requesterId) {
        UserEdgeAccess access = userEdgeAccessRepository
                .findByUserIdAndEdgeId(requesterId, edgeId)
                .orElseThrow(() -> new AccessDeniedException(
                        "Only the Owner of this edge can manage invitations"));
        if (access.getRole() != EdgeRole.OWNER) {
            throw new AccessDeniedException(
                    "Only the Owner of this edge can manage invitations");
        }
    }

    private void requireInvitee(EdgeInvitation invitation, UUID userId) {
        if (!invitation.getUserId().equals(userId)) {
            throw new AccessDeniedException("This invitation does not belong to you");
        }
    }

    private String edgeName(UUID edgeId) {
        return edgeRepository.findById(edgeId).map(Edge::getName).orElse(null);
    }

    private Map<UUID, User> usersByIds(List<UUID> userIds) {
        return userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));
    }

    private InvitationDTO mapToDTO(EdgeInvitation inv, User user, String edgeName) {
        return InvitationDTO.builder()
                .id(inv.getId())
                .edgeId(inv.getEdgeId())
                .edgeName(edgeName)
                .invitedUserId(inv.getUserId())
                .invitedUsername(user == null ? null : user.getUsername())
                .invitedEmail(user == null ? null : user.getEmail())
                .invitedName(user == null ? null : user.getName())
                .invitedBy(inv.getInvitedBy())
                .createdAt(inv.getCreatedAt())
                .expiresAt(inv.getExpiresAt())
                .build();
    }
}
