package com.iot.platform.edge.auth;

import com.iot.platform.common.auth.AuthResponseDTO;
import com.iot.platform.common.auth.LoginRequestDTO;
import com.iot.platform.common.edge.EdgeRole;
import com.iot.platform.edge.exception.InvalidRefreshTokenException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

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
class AuthServiceTest {

    @Mock
    private LocalUserRepository localUserRepository;
    @Mock
    private RefreshTokenRepository refreshTokenRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtService jwtService;

    @InjectMocks
    private AuthService service;

    private final String presented = "REFRESH_TOKEN_PLAIN";
    private final String hash = TokenHashUtil.sha256Hex(presented);
    private UUID userId;
    private LocalUser owner;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        owner = LocalUser.builder()
                .id(userId)
                .username("juan")
                .email("juan@example.com")
                .name("Juan Pérez")
                .passwordHash("$2a$hashed")
                .role(EdgeRole.OWNER)
                .lastSyncedAt(Instant.now())
                .build();
    }

    @Test
    void login_byUsername_success() {
        when(localUserRepository.findByUsername("juan")).thenReturn(Optional.of(owner));
        when(passwordEncoder.matches("supersecret", "$2a$hashed")).thenReturn(true);
        when(jwtService.generateAccessToken(userId, EdgeRole.OWNER)).thenReturn("ACCESS");
        when(jwtService.generateRefreshToken(userId)).thenReturn("REFRESH");
        when(jwtService.refreshTokenTtl()).thenReturn(Duration.ofDays(7));

        AuthResponseDTO response = service.login(LoginRequestDTO.builder()
                .identifier("juan").password("supersecret").build());

        assertThat(response.getAccessToken()).isEqualTo("ACCESS");
        assertThat(response.getRefreshToken()).isEqualTo("REFRESH");
        assertThat(response.getUser().getUsername()).isEqualTo("juan");
        assertThat(response.getUser().getEmail()).isEqualTo("juan@example.com");
        assertThat(response.getUser().getName()).isEqualTo("Juan Pérez");
        assertThat(response.getUser().getCreatedAt()).isNull();
        assertThat(response.getIsEdgeToken()).isTrue();

        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(captor.capture());
        assertThat(captor.getValue().getTokenHash())
                .isEqualTo(TokenHashUtil.sha256Hex("REFRESH"));
        assertThat(captor.getValue().getRevoked()).isFalse();
    }

    @Test
    void login_byEmail_success() {
        when(localUserRepository.findByEmail("juan@example.com")).thenReturn(Optional.of(owner));
        when(passwordEncoder.matches("supersecret", "$2a$hashed")).thenReturn(true);
        when(jwtService.generateAccessToken(userId, EdgeRole.OWNER)).thenReturn("ACCESS");
        when(jwtService.generateRefreshToken(userId)).thenReturn("REFRESH");
        when(jwtService.refreshTokenTtl()).thenReturn(Duration.ofDays(7));

        AuthResponseDTO response = service.login(LoginRequestDTO.builder()
                .identifier("juan@example.com").password("supersecret").build());

        assertThat(response.getAccessToken()).isEqualTo("ACCESS");
        assertThat(response.getUser().getEmail()).isEqualTo("juan@example.com");
        assertThat(response.getUser().getName()).isEqualTo("Juan Pérez");
        verify(localUserRepository, never()).findByUsername(any());
    }

    @Test
    void login_unknownUsername_badCredentials() {
        when(localUserRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.login(LoginRequestDTO.builder()
                .identifier("ghost").password("any").build()))
                .isInstanceOf(BadCredentialsException.class);

        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void login_wrongPassword_badCredentials() {
        when(localUserRepository.findByUsername("juan")).thenReturn(Optional.of(owner));
        when(passwordEncoder.matches("wrong", "$2a$hashed")).thenReturn(false);

        assertThatThrownBy(() -> service.login(LoginRequestDTO.builder()
                .identifier("juan").password("wrong").build()))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void refresh_validToken_rotatesTokens() {
        RefreshToken row = RefreshToken.builder()
                .userId(userId).tokenHash(hash).revoked(false)
                .expiresAt(Instant.now().plus(Duration.ofDays(1))).build();

        when(jwtService.validateToken(presented)).thenReturn(true);
        when(jwtService.getTypFromToken(presented)).thenReturn(JwtService.TYP_REFRESH);
        when(refreshTokenRepository.findByTokenHash(hash)).thenReturn(Optional.of(row));
        when(localUserRepository.findById(userId)).thenReturn(Optional.of(owner));
        when(jwtService.generateAccessToken(userId, EdgeRole.OWNER)).thenReturn("NEW_ACCESS");
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
    void refresh_expiredRow_throws401() {
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
    void refresh_unknownTokenHash_throws401() {
        when(jwtService.validateToken(presented)).thenReturn(true);
        when(jwtService.getTypFromToken(presented)).thenReturn(JwtService.TYP_REFRESH);
        when(refreshTokenRepository.findByTokenHash(hash)).thenReturn(Optional.empty());

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
