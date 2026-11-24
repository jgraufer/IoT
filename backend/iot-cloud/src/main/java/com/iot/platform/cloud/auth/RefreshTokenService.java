package com.iot.platform.cloud.auth;

import com.iot.platform.cloud.exception.InvalidRefreshTokenException;
import com.iot.platform.common.auth.AuthResponseDTO;
import com.iot.platform.common.auth.UserDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;
    private final JwtService jwtService;

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

        User user = userRepository.findById(row.getUserId())
                .orElseThrow(InvalidRefreshTokenException::new);
        if (!user.getIsActive()) {
            throw new InvalidRefreshTokenException();
        }

        row.setRevoked(true);
        refreshTokenRepository.save(row);

        String newAccess = jwtService.generateAccessToken(user.getId());
        String newRefresh = jwtService.generateRefreshToken(user.getId());
        RefreshToken next = RefreshToken.builder()
                .userId(user.getId())
                .tokenHash(TokenHashUtil.sha256Hex(newRefresh))
                .expiresAt(Instant.now().plus(jwtService.refreshTokenTtl()))
                .revoked(false)
                .build();
        refreshTokenRepository.save(next);

        return AuthResponseDTO.builder()
                .accessToken(newAccess)
                .refreshToken(newRefresh)
                .tokenType("Bearer")
                .user(mapToDTO(user))
                .isEdgeToken(false)
                .build();
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

    private UserDTO mapToDTO(User user) {
        return UserDTO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .name(user.getName())
                .isActive(user.getIsActive())
                .createdAt(user.getCreatedAt())
                .build();
    }
}
