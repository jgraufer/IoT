package com.iot.platform.cloud.auth;

import com.iot.platform.cloud.exception.InvalidRefreshTokenException;
import com.iot.platform.common.auth.AuthResponseDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    @Mock
    private RefreshTokenRepository refreshTokenRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private JwtService jwtService;

    @InjectMocks
    private RefreshTokenService service;

    private final String presented = "REFRESH_TOKEN_PLAIN";
    private final String hash = TokenHashUtil.sha256Hex(presented);
    private UUID userId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
    }

    @Test
    void refresh_validToken_rotatesTokens() {
        User user = User.builder()
                .id(userId).username("juan").email("juan@example.com")
                .passwordHash("h").isActive(true).build();
        RefreshToken row = RefreshToken.builder()
                .userId(userId).tokenHash(hash).revoked(false)
                .expiresAt(Instant.now().plus(Duration.ofDays(1))).build();

        when(jwtService.validateToken(presented)).thenReturn(true);
        when(jwtService.getTypFromToken(presented)).thenReturn(JwtService.TYP_REFRESH);
        when(refreshTokenRepository.findByTokenHash(hash)).thenReturn(Optional.of(row));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(jwtService.generateAccessToken(userId)).thenReturn("NEW_ACCESS");
        when(jwtService.generateRefreshToken(userId)).thenReturn("NEW_REFRESH");
        when(jwtService.refreshTokenTtl()).thenReturn(Duration.ofDays(7));

        AuthResponseDTO response = service.refresh(presented);

        assertThat(response.getAccessToken()).isEqualTo("NEW_ACCESS");
        assertThat(response.getRefreshToken()).isEqualTo("NEW_REFRESH");

        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository, times(2)).save(captor.capture());
        assertThat(captor.getAllValues().get(0).getRevoked()).isTrue();
        assertThat(captor.getAllValues().get(1).getRevoked()).isFalse();
        assertThat(captor.getAllValues().get(1).getTokenHash())
                .isEqualTo(TokenHashUtil.sha256Hex("NEW_REFRESH"));
    }

    @Test
    void refresh_reusedRevokedToken_revokesAllAndThrows401() {
        RefreshToken row = RefreshToken.builder()
                .userId(userId).tokenHash(hash).revoked(true)
                .expiresAt(Instant.now().plus(Duration.ofDays(1))).build();

        when(jwtService.validateToken(presented)).thenReturn(true);
        when(jwtService.getTypFromToken(presented)).thenReturn(JwtService.TYP_REFRESH);
        when(refreshTokenRepository.findByTokenHash(hash)).thenReturn(Optional.of(row));

        assertThatThrownBy(() -> service.refresh(presented))
                .isInstanceOf(InvalidRefreshTokenException.class);

        verify(refreshTokenRepository).revokeAllByUserId(userId);
        verify(refreshTokenRepository, never()).save(any(RefreshToken.class));
    }

    @Test
    void refresh_unknownToken_throws401() {
        when(jwtService.validateToken(presented)).thenReturn(true);
        when(jwtService.getTypFromToken(presented)).thenReturn(JwtService.TYP_REFRESH);
        when(refreshTokenRepository.findByTokenHash(hash)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.refresh(presented))
                .isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    void refresh_expiredToken_throws401() {
        RefreshToken row = RefreshToken.builder()
                .userId(userId).tokenHash(hash).revoked(false)
                .expiresAt(Instant.now().minus(Duration.ofMinutes(1))).build();
        when(jwtService.validateToken(presented)).thenReturn(true);
        when(jwtService.getTypFromToken(presented)).thenReturn(JwtService.TYP_REFRESH);
        when(refreshTokenRepository.findByTokenHash(hash)).thenReturn(Optional.of(row));

        assertThatThrownBy(() -> service.refresh(presented))
                .isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    void refresh_invalidJwtSignature_throws401() {
        when(jwtService.validateToken(presented)).thenReturn(false);

        assertThatThrownBy(() -> service.refresh(presented))
                .isInstanceOf(InvalidRefreshTokenException.class);

        verify(refreshTokenRepository, never()).findByTokenHash(any());
    }

    @Test
    void refresh_accessTokenInsteadOfRefresh_throws401() {
        when(jwtService.validateToken(presented)).thenReturn(true);
        when(jwtService.getTypFromToken(presented)).thenReturn(JwtService.TYP_ACCESS);

        assertThatThrownBy(() -> service.refresh(presented))
                .isInstanceOf(InvalidRefreshTokenException.class);

        verify(refreshTokenRepository, never()).findByTokenHash(any());
    }

    @Test
    void logout_existingActiveToken_marksRevoked() {
        RefreshToken row = RefreshToken.builder()
                .userId(userId).tokenHash(hash).revoked(false)
                .expiresAt(Instant.now().plus(Duration.ofDays(1))).build();
        when(refreshTokenRepository.findByTokenHash(hash)).thenReturn(Optional.of(row));

        service.logout(presented);

        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(captor.capture());
        assertThat(captor.getValue().getRevoked()).isTrue();
    }

    @Test
    void logout_alreadyRevoked_isIdempotent() {
        RefreshToken row = RefreshToken.builder()
                .userId(userId).tokenHash(hash).revoked(true)
                .expiresAt(Instant.now().plus(Duration.ofDays(1))).build();
        when(refreshTokenRepository.findByTokenHash(hash)).thenReturn(Optional.of(row));

        service.logout(presented);

        verify(refreshTokenRepository, never()).save(any(RefreshToken.class));
    }

    @Test
    void logout_unknownToken_isNoOp() {
        when(refreshTokenRepository.findByTokenHash(hash)).thenReturn(Optional.empty());

        service.logout(presented);

        verify(refreshTokenRepository, never()).save(any(RefreshToken.class));
    }
}
