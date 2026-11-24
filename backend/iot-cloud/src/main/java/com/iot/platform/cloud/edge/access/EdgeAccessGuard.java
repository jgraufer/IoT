package com.iot.platform.cloud.edge.access;

import com.iot.platform.cloud.exception.AccessDeniedException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class EdgeAccessGuard {

    private final UserEdgeAccessRepository userEdgeAccessRepository;

    public UserEdgeAccess requireAccess(UUID edgeId, UUID requesterId) {
        return userEdgeAccessRepository.findByUserIdAndEdgeId(requesterId, edgeId)
                .orElseThrow(() -> new AccessDeniedException(
                        "You do not have access to this edge"));
    }
}
