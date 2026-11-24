package com.iot.platform.cloud.edge;

import com.iot.platform.cloud.auth.User;
import com.iot.platform.cloud.auth.UserRepository;
import com.iot.platform.cloud.edge.access.EdgeAccessGuard;
import com.iot.platform.cloud.edge.access.UserEdgeAccess;
import com.iot.platform.cloud.edge.access.UserEdgeAccessRepository;
import com.iot.platform.cloud.edge.dto.EdgeDetailDTO;
import com.iot.platform.cloud.edge.dto.MemberDTO;
import com.iot.platform.cloud.exception.AccessDeniedException;
import com.iot.platform.cloud.exception.ResourceNotFoundException;
import com.iot.platform.common.edge.EdgeRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EdgeServiceTest {

    @Mock
    private UserEdgeAccessRepository userEdgeAccessRepository;
    @Mock
    private EdgeRepository edgeRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private EdgeAccessGuard edgeAccessGuard;

    @InjectMocks
    private EdgeService service;

    private UUID edgeId;
    private UUID ownerId;
    private UUID adminId;
    private UUID memberId;

    @BeforeEach
    void setUp() {
        edgeId = UUID.randomUUID();
        ownerId = UUID.randomUUID();
        adminId = UUID.randomUUID();
        memberId = UUID.randomUUID();
    }

    private UserEdgeAccess access(UUID userId, EdgeRole role) {
        return UserEdgeAccess.builder()
                .userId(userId).edgeId(edgeId).role(role).grantedAt(Instant.now()).build();
    }

    private User user(UUID id, String username, String name) {
        return User.builder()
                .id(id).username(username).name(name).email(username + "@example.com")
                .passwordHash("$2a$hash").isActive(true).build();
    }

    @Test
    void getEdgeDetail_success_ordersMembersByRoleThenName() {
        UserEdgeAccess memberAccess = access(memberId, EdgeRole.MEMBER);
        when(edgeAccessGuard.requireAccess(edgeId, memberId)).thenReturn(memberAccess);
        when(edgeRepository.findById(edgeId)).thenReturn(Optional.of(Edge.builder()
                .id(edgeId).name("Casa").pairedAt(Instant.now()).lastSeen(Instant.now()).build()));
        when(userEdgeAccessRepository.findByEdgeId(edgeId)).thenReturn(List.of(
                memberAccess, access(ownerId, EdgeRole.OWNER), access(adminId, EdgeRole.ADMIN)));
        when(userRepository.findAllById(List.of(memberId, ownerId, adminId))).thenReturn(List.of(
                user(memberId, "member", "Ana"),
                user(ownerId, "owner", "Zoe"),
                user(adminId, "admin", "Bea")));

        EdgeDetailDTO detail = service.getEdgeDetail(edgeId, memberId);

        assertThat(detail.getName()).isEqualTo("Casa");
        assertThat(detail.getRole()).isEqualTo(EdgeRole.MEMBER);
        assertThat(detail.getMembers()).extracting(MemberDTO::getRole)
                .containsExactly(EdgeRole.OWNER, EdgeRole.ADMIN, EdgeRole.MEMBER);
        assertThat(detail.getMembers().get(0).getName()).isEqualTo("Zoe");
        assertThat(detail.getMembers().get(0).getEmail()).isEqualTo("owner@example.com");
    }

    @Test
    void getEdgeDetail_toleratesMissingUser() {
        when(edgeAccessGuard.requireAccess(edgeId, ownerId))
                .thenReturn(access(ownerId, EdgeRole.OWNER));
        when(edgeRepository.findById(edgeId)).thenReturn(Optional.of(Edge.builder()
                .id(edgeId).name("Casa").build()));
        when(userEdgeAccessRepository.findByEdgeId(edgeId))
                .thenReturn(List.of(access(ownerId, EdgeRole.OWNER)));
        when(userRepository.findAllById(List.of(ownerId))).thenReturn(List.of());

        EdgeDetailDTO detail = service.getEdgeDetail(edgeId, ownerId);

        assertThat(detail.getMembers()).hasSize(1);
        assertThat(detail.getMembers().get(0).getName()).isNull();
        assertThat(detail.getMembers().get(0).getRole()).isEqualTo(EdgeRole.OWNER);
    }

    @Test
    void getEdgeDetail_noAccess_throws403() {
        when(edgeAccessGuard.requireAccess(edgeId, memberId))
                .thenThrow(new AccessDeniedException("You do not have access to this edge"));

        assertThatThrownBy(() -> service.getEdgeDetail(edgeId, memberId))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void getEdgeDetail_unknownEdge_throws404() {
        when(edgeAccessGuard.requireAccess(edgeId, ownerId))
                .thenReturn(access(ownerId, EdgeRole.OWNER));
        when(edgeRepository.findById(edgeId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getEdgeDetail(edgeId, ownerId))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
