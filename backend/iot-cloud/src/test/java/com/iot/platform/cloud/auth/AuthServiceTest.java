package com.iot.platform.cloud.auth;

import com.iot.platform.cloud.exception.EmailAlreadyExistsException;
import com.iot.platform.cloud.exception.UsernameAlreadyExistsException;
import com.iot.platform.common.auth.AuthResponseDTO;
import com.iot.platform.common.auth.LoginRequestDTO;
import com.iot.platform.common.auth.RegisterRequestDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtService jwtService;
    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @InjectMocks
    private AuthService service;

    private RegisterRequestDTO validRegister;

    @BeforeEach
    void setUp() {
        validRegister = RegisterRequestDTO.builder()
                .username("juan")
                .email("juan@example.com")
                .password("supersecret")
                .name("Juan Perez")
                .build();
    }

    @Test
    void register_success() {
        when(userRepository.existsByEmail("juan@example.com")).thenReturn(false);
        when(userRepository.existsByUsername("juan")).thenReturn(false);
        when(passwordEncoder.encode("supersecret")).thenReturn("$2a$hashed");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(UUID.randomUUID());
            return u;
        });
        when(jwtService.generateAccessToken(any())).thenReturn("ACCESS");
        when(jwtService.generateRefreshToken(any())).thenReturn("REFRESH");
        when(jwtService.refreshTokenTtl()).thenReturn(java.time.Duration.ofDays(7));

        AuthResponseDTO response = service.register(validRegister);

        assertThat(response.getAccessToken()).isEqualTo("ACCESS");
        assertThat(response.getRefreshToken()).isEqualTo("REFRESH");
        assertThat(response.getUser().getUsername()).isEqualTo("juan");
        assertThat(response.getUser().getEmail()).isEqualTo("juan@example.com");
        verify(refreshTokenRepository).save(any(RefreshToken.class));
    }

    @Test
    void register_emailDuplicate_throws409() {
        when(userRepository.existsByEmail("juan@example.com")).thenReturn(true);

        assertThatThrownBy(() -> service.register(validRegister))
                .isInstanceOf(EmailAlreadyExistsException.class);

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void register_usernameDuplicate_throws409() {
        when(userRepository.existsByEmail("juan@example.com")).thenReturn(false);
        when(userRepository.existsByUsername("juan")).thenReturn(true);

        assertThatThrownBy(() -> service.register(validRegister))
                .isInstanceOf(UsernameAlreadyExistsException.class);

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void login_byUsername_success() {
        UUID userId = UUID.randomUUID();
        User user = User.builder()
                .id(userId).username("juan").email("juan@example.com")
                .passwordHash("$2a$hashed").isActive(true).build();
        when(userRepository.findByUsername("juan")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("supersecret", "$2a$hashed")).thenReturn(true);
        when(jwtService.generateAccessToken(userId)).thenReturn("ACCESS");
        when(jwtService.generateRefreshToken(userId)).thenReturn("REFRESH");
        when(jwtService.refreshTokenTtl()).thenReturn(java.time.Duration.ofDays(7));

        AuthResponseDTO response = service.login(LoginRequestDTO.builder()
                .identifier("juan").password("supersecret").build());

        assertThat(response.getAccessToken()).isEqualTo("ACCESS");
        assertThat(response.getRefreshToken()).isEqualTo("REFRESH");
        verify(userRepository, never()).findByEmail(anyString());
    }

    @Test
    void login_byEmail_success() {
        UUID userId = UUID.randomUUID();
        User user = User.builder()
                .id(userId).username("juan").email("juan@example.com")
                .passwordHash("$2a$hashed").isActive(true).build();
        when(userRepository.findByEmail("juan@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("supersecret", "$2a$hashed")).thenReturn(true);
        when(jwtService.generateAccessToken(userId)).thenReturn("ACCESS");
        when(jwtService.generateRefreshToken(userId)).thenReturn("REFRESH");
        when(jwtService.refreshTokenTtl()).thenReturn(java.time.Duration.ofDays(7));

        AuthResponseDTO response = service.login(LoginRequestDTO.builder()
                .identifier("juan@example.com").password("supersecret").build());

        assertThat(response.getAccessToken()).isEqualTo("ACCESS");
        verify(userRepository, never()).findByUsername(anyString());
    }

    @Test
    void login_unknownIdentifier_badCredentials() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.login(LoginRequestDTO.builder()
                .identifier("ghost").password("any").build()))
                .isInstanceOf(BadCredentialsException.class);

        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void login_wrongPassword_badCredentials() {
        User user = User.builder()
                .id(UUID.randomUUID()).username("juan").email("juan@example.com")
                .passwordHash("$2a$hashed").isActive(true).build();
        when(userRepository.findByUsername("juan")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "$2a$hashed")).thenReturn(false);

        assertThatThrownBy(() -> service.login(LoginRequestDTO.builder()
                .identifier("juan").password("wrong").build()))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void login_inactiveUser_badCredentials() {
        User user = User.builder()
                .id(UUID.randomUUID()).username("juan").email("juan@example.com")
                .passwordHash("$2a$hashed").isActive(false).build();
        when(userRepository.findByUsername("juan")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.login(LoginRequestDTO.builder()
                .identifier("juan").password("any").build()))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void register_persistsBcryptHashNotPlaintext() {
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(userRepository.existsByUsername(anyString())).thenReturn(false);
        when(passwordEncoder.encode("supersecret")).thenReturn("$2a$hashed");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(jwtService.generateAccessToken(any())).thenReturn("A");
        when(jwtService.generateRefreshToken(any())).thenReturn("R");
        when(jwtService.refreshTokenTtl()).thenReturn(java.time.Duration.ofDays(7));

        service.register(validRegister);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getPasswordHash()).isEqualTo("$2a$hashed");
        assertThat(captor.getValue().getPasswordHash()).isNotEqualTo("supersecret");
    }
}
