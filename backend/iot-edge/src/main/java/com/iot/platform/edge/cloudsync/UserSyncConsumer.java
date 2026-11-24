package com.iot.platform.edge.cloudsync;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iot.platform.common.auth.UserSyncDTO;
import com.iot.platform.common.edge.EdgeRole;
import com.iot.platform.edge.auth.LocalUser;
import com.iot.platform.edge.auth.LocalUserRepository;
import com.iot.platform.edge.auth.RefreshTokenRepository;
import com.iot.platform.edge.bootstrap.EdgeIdentityRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.IMqttMessageListener;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserSyncConsumer implements IMqttMessageListener {

    private final ObjectMapper objectMapper;
    private final LocalUserRepository localUserRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final EdgeIdentityRepository edgeIdentityRepository;

    @Override
    @Transactional
    public void messageArrived(String topic, MqttMessage message) {
        UserSyncDTO dto;
        try {
            dto = objectMapper.readValue(message.getPayload(), UserSyncDTO.class);
        } catch (Exception e) {
            log.error("Malformed user sync on topic {}; discarded: {}", topic, e.getMessage());
            return;
        }
        apply(dto);
    }

    void apply(UserSyncDTO dto) {
        if (dto.getOperation() == null) {
            log.warn("UserSync received without operation; ignoring");
            return;
        }
        if (dto.getTimestamp() == null) {
            log.warn("UserSync received without timestamp; ignoring");
            return;
        }

        switch (dto.getOperation()) {
            case GRANT -> applyGrant(dto);
            case UPDATE -> applyUpdate(dto);
            case REVOKE -> applyRevoke(dto);
            case OWNER_TRANSFER -> applyOwnerTransfer(dto);
        }
    }

    private void applyGrant(UserSyncDTO dto) {
        if (isObsolete(dto.getUserId(), dto.getTimestamp())) {
            log.info("GRANT ignored (obsolete timestamp) for userId={}", dto.getUserId());
            return;
        }

        LocalUser user = localUserRepository.findById(dto.getUserId())
                .orElseGet(() -> LocalUser.builder().id(dto.getUserId()).build());
        user.setUsername(dto.getUsername());
        user.setEmail(dto.getEmail());
        user.setName(dto.getName());
        user.setPasswordHash(dto.getPasswordHash());
        user.setRole(dto.getRole());
        user.setLastSyncedAt(dto.getTimestamp());
        localUserRepository.save(user);
        log.info("GRANT applied: userId={}, role={}", dto.getUserId(), dto.getRole());

        if (dto.getRole() == EdgeRole.OWNER) {
            markPairedIfNeeded(dto.getUserId());
        }
    }

    private void markPairedIfNeeded(UUID ownerUserId) {
        edgeIdentityRepository.findSingleton().ifPresent(identity -> {
            if (identity.getPairedAt() != null) {
                return;
            }
            identity.setPairedAt(Instant.now());
            identity.setPairingCode(null);
            identity.setPairingCodeExpiresAt(null);
            edgeIdentityRepository.save(identity);
            log.info("Edge marked as paired by GRANT of Owner {}", ownerUserId);
        });
    }

    private void applyUpdate(UserSyncDTO dto) {
        Optional<LocalUser> maybeUser = localUserRepository.findById(dto.getUserId());
        if (maybeUser.isEmpty()) {
            log.warn("UPDATE ignored: LocalUser not found (userId={})", dto.getUserId());
            return;
        }
        if (isObsolete(dto.getUserId(), dto.getTimestamp())) {
            log.info("UPDATE ignored (obsolete timestamp) for userId={}", dto.getUserId());
            return;
        }
        LocalUser user = maybeUser.get();
        if (dto.getPasswordHash() != null) {
            user.setPasswordHash(dto.getPasswordHash());
        }
        if (dto.getRole() != null) {
            user.setRole(dto.getRole());
        }
        if (dto.getUsername() != null) {
            user.setUsername(dto.getUsername());
        }
        if (dto.getEmail() != null) {
            user.setEmail(dto.getEmail());
        }
        if (dto.getName() != null) {
            user.setName(dto.getName());
        }
        user.setLastSyncedAt(dto.getTimestamp());
        localUserRepository.save(user);
        log.info("UPDATE applied: userId={}", dto.getUserId());
    }

    private void applyRevoke(UserSyncDTO dto) {
        if (localUserRepository.findById(dto.getUserId()).isEmpty()) {
            log.info("REVOKE ignored (already absent) for userId={}", dto.getUserId());
            return;
        }
        if (isObsolete(dto.getUserId(), dto.getTimestamp())) {
            log.info("REVOKE ignored (obsolete timestamp) for userId={}", dto.getUserId());
            return;
        }
        localUserRepository.deleteById(dto.getUserId());
        log.info("REVOKE applied: userId={}", dto.getUserId());
    }

    private void applyOwnerTransfer(UserSyncDTO dto) {
        UUID previousOwnerId = dto.getPreviousOwnerId();
        UUID newOwnerId = dto.getNewOwnerId();
        if (previousOwnerId == null || newOwnerId == null) {
            log.error("OWNER_TRANSFER missing previousOwnerId/newOwnerId; ignoring");
            return;
        }

        LocalUser previous = localUserRepository.findById(previousOwnerId).orElse(null);
        LocalUser next = localUserRepository.findById(newOwnerId).orElse(null);

        if (previous == null || next == null) {
            log.error("OWNER_TRANSFER cannot apply: previousOwner={} present={}, newOwner={} present={}",
                    previousOwnerId, previous != null, newOwnerId, next != null);
            throw new IllegalStateException(
                    "OWNER_TRANSFER references missing LocalUser; rolling back");
        }

        if (previous.getRole() == EdgeRole.ADMIN && next.getRole() == EdgeRole.OWNER) {
            log.info("OWNER_TRANSFER already applied (idempotent) for {} -> {}",
                    previousOwnerId, newOwnerId);
            return;
        }

        Instant ts = dto.getTimestamp();
        previous.setRole(EdgeRole.ADMIN);
        previous.setLastSyncedAt(ts);
        next.setRole(EdgeRole.OWNER);
        next.setLastSyncedAt(ts);
        localUserRepository.save(previous);
        localUserRepository.save(next);

        refreshTokenRepository.revokeAllByUserId(previousOwnerId);
        refreshTokenRepository.revokeAllByUserId(newOwnerId);

        log.info("OWNER_TRANSFER applied: {} -> {}", previousOwnerId, newOwnerId);
    }

    private boolean isObsolete(UUID userId, Instant incomingTimestamp) {
        return localUserRepository.findById(userId)
                .map(existing -> {
                    Instant existingTs = existing.getLastSyncedAt();
                    if (existingTs == null) return false;
                    return !existingTs.isBefore(incomingTimestamp);
                })
                .orElse(false);
    }
}
