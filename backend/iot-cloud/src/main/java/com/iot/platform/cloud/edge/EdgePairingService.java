package com.iot.platform.cloud.edge;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import com.iot.platform.cloud.exception.ResourceNotFoundException;
import com.iot.platform.common.auth.UserSyncDTO;
import com.iot.platform.common.auth.UserSyncOperation;
import com.iot.platform.common.edge.EdgeRole;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class EdgePairingService {

    private final EdgePairingCodeRepository edgePairingCodeRepository;
    private final EdgeRepository edgeRepository;
    private final UserEdgeAccessRepository userEdgeAccessRepository;
    private final UserRepository userRepository;
    private final CloudOutboxRepository cloudOutboxRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public PairResponseDTO pair(UUID userId, String code) {
        EdgePairingCode pairingCode = edgePairingCodeRepository.findByCodeAndUsedFalse(code)
                .orElseThrow(InvalidPairingCodeException::new);

        Instant now = Instant.now();
        if (!pairingCode.getExpiresAt().isAfter(now)) {
            throw new PairingCodeExpiredException();
        }

        UUID edgeId = pairingCode.getEdgeId();

        if (userEdgeAccessRepository.findByUserIdAndEdgeId(userId, edgeId).isPresent()) {
            throw new AlreadyHasAccessException();
        }

        Edge edge = edgeRepository.findById(edgeId)
                .orElseThrow(() -> new ResourceNotFoundException("Edge", "id", edgeId));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        pairingCode.setUsed(true);
        edgePairingCodeRepository.save(pairingCode);

        edge.setPairedAt(now);
        edgeRepository.save(edge);

        UserEdgeAccess access = UserEdgeAccess.builder()
                .userId(userId)
                .edgeId(edgeId)
                .role(EdgeRole.OWNER)
                .grantedBy(null)
                .build();
        userEdgeAccessRepository.save(access);

        UserSyncDTO sync = UserSyncDTO.builder()
                .operation(UserSyncOperation.GRANT)
                .userId(userId)
                .username(user.getUsername())
                .email(user.getEmail())
                .name(user.getName())
                .passwordHash(user.getPasswordHash())
                .role(EdgeRole.OWNER)
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
            throw new IllegalStateException("Failed to serialize UserSyncDTO for pairing", e);
        }

        log.info("User {} paired as OWNER of edge {}", userId, edgeId);

        return PairResponseDTO.builder()
                .edgeId(edgeId)
                .name(edge.getName())
                .build();
    }
}
