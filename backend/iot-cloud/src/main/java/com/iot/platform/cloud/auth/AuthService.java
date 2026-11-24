package com.iot.platform.cloud.auth;

import com.iot.platform.cloud.exception.EmailAlreadyExistsException;
import com.iot.platform.cloud.exception.UsernameAlreadyExistsException;
import com.iot.platform.common.auth.AuthResponseDTO;
import com.iot.platform.common.auth.LoginRequestDTO;
import com.iot.platform.common.auth.RegisterRequestDTO;
import com.iot.platform.common.auth.UserDTO;
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

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenRepository refreshTokenRepository;

    @Transactional
    public AuthResponseDTO register(RegisterRequestDTO request) {
        log.info("Registro: username={}, email={}", request.getUsername(), request.getEmail());

        if (userRepository.existsByEmail(request.getEmail())) {
            throw new EmailAlreadyExistsException();
        }
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new UsernameAlreadyExistsException();
        }

        User user = User.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .name(request.getName())
                .isActive(true)
                .build();
        user = userRepository.save(user);

        return issueTokens(user);
    }

    @Transactional
    public AuthResponseDTO login(LoginRequestDTO request) {
        String identifier = request.getIdentifier();
        log.debug("Login: identifier={}", identifier);

        User user = identifier.contains("@")
                ? userRepository.findByEmail(identifier).orElse(null)
                : userRepository.findByUsername(identifier).orElse(null);

        if (user == null || !user.getIsActive()
                || !passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid credentials");
        }

        return issueTokens(user);
    }

    private AuthResponseDTO issueTokens(User user) {
        String accessToken = jwtService.generateAccessToken(user.getId());
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
                .isEdgeToken(false)
                .build();
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
