package com.iot.platform.edge.auth;

import com.iot.platform.common.auth.AuthResponseDTO;
import com.iot.platform.common.auth.LoginRequestDTO;
import com.iot.platform.common.auth.UserDTO;
import com.iot.platform.edge.exception.InvalidRefreshTokenException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final LocalUserRepository localUserRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    @Transactional
    public AuthResponseDTO login(LoginRequestDTO request) {
        String identifier = request.getIdentifier();
        log.debug("Local login attempt: identifier={}", identifier);

        LocalUser user = (identifier != null && identifier.contains("@")
                ? localUserRepository.findByEmail(identifier)
                : localUserRepository.findByUsername(identifier))
                .orElse(null);
        if (user == null
                || !passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid credentials");
        }

        return issueTokens(user);
    }

    @Transactional(noRollbackFor = InvalidRefreshTokenException.class)
    public AuthResponseDTO refresh(String presentedToken) {
        if (!jwtService.validateToken(presentedToken)) {
            throw new InvalidRefreshTokenException();
        }
        if (!JwtService.TYP_REFRESH.equals(jwtService.getTypFromToken(presentedToken))) {
            throw new InvalidRefreshTokenException();
        }

        String tokenHash = TokenHashUtil.sha256Hex(presentedToken);
        RefreshToken row = refreshTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(InvalidRefreshTokenException::new);

        if (Boolean.TRUE.equals(row.getRevoked())) {
            log.warn("Refresh token reuse detected for userId={}; revoking all sessions",
                    row.getUserId());
            refreshTokenRepository.revokeAllByUserId(row.getUserId());
            throw new InvalidRefreshTokenException();
        }

        if (row.getExpiresAt().isBefore(Instant.now())) {
            throw new InvalidRefreshTokenException();
        }

        LocalUser user = localUserRepository.findById(row.getUserId())
                .orElseThrow(InvalidRefreshTokenException::new);

        row.setRevoked(true);
        refreshTokenRepository.save(row);

        return issueTokens(user);
    }

    @Transactional
    public void logout(String presentedToken) {
        String tokenHash = TokenHashUtil.sha256Hex(presentedToken);
        refreshTokenRepository.findByTokenHash(tokenHash).ifPresent(row -> {
            if (!Boolean.TRUE.equals(row.getRevoked())) {
                row.setRevoked(true);
                refreshTokenRepository.save(row);
            }
        });
    }

    private AuthResponseDTO issueTokens(LocalUser user) {
        String accessToken = jwtService.generateAccessToken(user.getId(), user.getRole());
        String refreshToken = jwtService.generateRefreshToken(user.getId());

        RefreshToken row = RefreshToken.builder()
                .userId(user.getId())
                .tokenHash(TokenHashUtil.sha256Hex(refreshToken))
                .expiresAt(Instant.now().plus(jwtService.refreshTokenTtl()))
                .revoked(false)
                .build();
        refreshTokenRepository.save(row);

        return AuthResponseDTO.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .user(mapToDTO(user))
                .isEdgeToken(true)
                .build();
    }

    private UserDTO mapToDTO(LocalUser user) {
        return UserDTO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .name(user.getName())
                .build();
    }
}
