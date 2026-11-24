package com.iot.platform.cloud.edge;

import com.iot.platform.cloud.auth.User;
import com.iot.platform.cloud.auth.UserRepository;
import com.iot.platform.cloud.edge.access.EdgeAccessGuard;
import com.iot.platform.cloud.edge.access.UserEdgeAccess;
import com.iot.platform.cloud.edge.access.UserEdgeAccessRepository;
import com.iot.platform.cloud.edge.dto.EdgeDetailDTO;
import com.iot.platform.cloud.edge.dto.EdgeSummaryDTO;
import com.iot.platform.cloud.edge.dto.MemberDTO;
import com.iot.platform.cloud.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class EdgeService {

    private final UserEdgeAccessRepository userEdgeAccessRepository;
    private final EdgeRepository edgeRepository;
    private final UserRepository userRepository;
    private final EdgeAccessGuard edgeAccessGuard;

    @Transactional(readOnly = true)
    public List<EdgeSummaryDTO> listMyEdges(UUID userId) {
        List<UserEdgeAccess> accesses = userEdgeAccessRepository.findByUserId(userId);
        if (accesses.isEmpty()) {
            return List.of();
        }

        List<UUID> edgeIds = accesses.stream().map(UserEdgeAccess::getEdgeId).toList();
        Map<UUID, Edge> edgesById = edgeRepository.findAllById(edgeIds).stream()
                .collect(Collectors.toMap(Edge::getId, e -> e));

        return accesses.stream()
                .map(access -> {
                    Edge edge = edgesById.get(access.getEdgeId());
                    return EdgeSummaryDTO.builder()
                            .edgeId(access.getEdgeId())
                            .name(edge == null ? null : edge.getName())
                            .role(access.getRole())
                            .build();
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public EdgeDetailDTO getEdgeDetail(UUID edgeId, UUID requesterId) {
        UserEdgeAccess access = edgeAccessGuard.requireAccess(edgeId, requesterId);
        Edge edge = edgeRepository.findById(edgeId)
                .orElseThrow(() -> new ResourceNotFoundException("Edge", "id", edgeId));

        List<UserEdgeAccess> memberships = userEdgeAccessRepository.findByEdgeId(edgeId);
        List<UUID> userIds = memberships.stream().map(UserEdgeAccess::getUserId).toList();
        Map<UUID, User> usersById = userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        List<MemberDTO> members = memberships.stream()
                .map(m -> mapToMemberDTO(m, usersById.get(m.getUserId())))
                .sorted(Comparator
                        .comparingInt((MemberDTO m) -> m.getRole().ordinal())
                        .thenComparing(MemberDTO::getName,
                                Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();

        return EdgeDetailDTO.builder()
                .edgeId(edgeId)
                .name(edge.getName())
                .role(access.getRole())
                .pairedAt(edge.getPairedAt())
                .lastSeen(edge.getLastSeen())
                .members(members)
                .build();
    }

    private MemberDTO mapToMemberDTO(UserEdgeAccess access, User user) {
        return MemberDTO.builder()
                .userId(access.getUserId())
                .name(user == null ? null : user.getName())
                .username(user == null ? null : user.getUsername())
                .email(user == null ? null : user.getEmail())
                .role(access.getRole())
                .grantedAt(access.getGrantedAt())
                .build();
    }
}
