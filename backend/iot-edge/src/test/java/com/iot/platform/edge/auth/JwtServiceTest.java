package com.iot.platform.edge.auth;

import com.iot.platform.common.edge.EdgeRole;
import com.iot.platform.edge.bootstrap.EdgeIdentity;
import com.iot.platform.edge.bootstrap.EdgeIdentityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtServiceTest {

    @Mock
    private EdgeIdentityRepository edgeIdentityRepository;

    @InjectMocks
    private JwtService service;

    @BeforeEach
    void setUp() {
        EdgeIdentity identity = EdgeIdentity.builder()
                .id(1)
                .edgeId(UUID.randomUUID())
                .jwtSecret("test-edge-secret-32-bytes-minimum-required-length-for-hs256")
                .build();
        when(edgeIdentityRepository.findSingleton()).thenReturn(Optional.of(identity));
        service.init();
    }

    @Test
    void accessToken_carriesUserIdAndRoleAndAccessTyp() {
        UUID userId = UUID.randomUUID();
        String token = service.generateAccessToken(userId, EdgeRole.OWNER);

        assertThat(service.validateToken(token)).isTrue();
        assertThat(service.getUserIdFromToken(token)).isEqualTo(userId);
        assertThat(service.getRoleFromToken(token)).isEqualTo(EdgeRole.OWNER);
        assertThat(service.getTypFromToken(token)).isEqualTo(JwtService.TYP_ACCESS);
    }

    @Test
    void refreshToken_hasRefreshTypAndNoRoleClaim() {
        UUID userId = UUID.randomUUID();
        String token = service.generateRefreshToken(userId);

        assertThat(service.validateToken(token)).isTrue();
        assertThat(service.getUserIdFromToken(token)).isEqualTo(userId);
        assertThat(service.getTypFromToken(token)).isEqualTo(JwtService.TYP_REFRESH);
        assertThat(service.getRoleFromToken(token)).isNull();
    }

    @Test
    void validateToken_garbledToken_returnsFalse() {
        assertThat(service.validateToken("not-a-real-jwt")).isFalse();
    }

    @Test
    void validateToken_tokenSignedWithDifferentSecret_returnsFalse() {
        EdgeIdentity other = EdgeIdentity.builder()
                .id(1)
                .edgeId(UUID.randomUUID())
                .jwtSecret("different-secret-32-bytes-minimum-required-length-for-hs256")
                .build();
        EdgeIdentityRepository otherRepo = org.mockito.Mockito.mock(EdgeIdentityRepository.class);
        when(otherRepo.findSingleton()).thenReturn(Optional.of(other));
        JwtService otherService = new JwtService(otherRepo);
        otherService.init();

        String tokenFromOther = otherService.generateAccessToken(UUID.randomUUID(), EdgeRole.OWNER);
        assertThat(service.validateToken(tokenFromOther)).isFalse();
    }
}
