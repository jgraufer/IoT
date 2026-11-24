package com.iot.platform.cloud.auth;

import com.iot.platform.cloud.auth.dto.LogoutRequestDTO;
import com.iot.platform.cloud.auth.dto.RefreshRequestDTO;
import com.iot.platform.common.auth.AuthResponseDTO;
import com.iot.platform.common.auth.LoginRequestDTO;
import com.iot.platform.common.auth.RegisterRequestDTO;
import com.iot.platform.common.shared.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final RefreshTokenService refreshTokenService;

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<AuthResponseDTO>> register(
            @Valid @RequestBody RegisterRequestDTO request) {
        AuthResponseDTO response = authService.register(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("Usuario registrado", response));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponseDTO>> login(
            @Valid @RequestBody LoginRequestDTO request) {
        AuthResponseDTO response = authService.login(request);
        return ResponseEntity.ok(ApiResponse.success("Login OK", response));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<AuthResponseDTO>> refresh(
            @Valid @RequestBody RefreshRequestDTO request) {
        AuthResponseDTO response = refreshTokenService.refresh(request.getRefreshToken());
        return ResponseEntity.ok(ApiResponse.success("Tokens refreshed", response));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            @Valid @RequestBody LogoutRequestDTO request) {
        refreshTokenService.logout(request.getRefreshToken());
        return ResponseEntity.ok(ApiResponse.success("Logged out"));
    }
}
