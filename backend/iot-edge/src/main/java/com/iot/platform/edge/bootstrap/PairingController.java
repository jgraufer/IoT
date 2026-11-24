package com.iot.platform.edge.bootstrap;

import com.iot.platform.common.shared.ApiResponse;
import com.iot.platform.edge.bootstrap.dto.EdgeInfoDTO;
import com.iot.platform.edge.bootstrap.dto.PairingCodeDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@Slf4j
@RestController
@RequestMapping("/api/edge")
@RequiredArgsConstructor
public class PairingController {

    private final EdgeIdentityRepository edgeIdentityRepository;
    private final EdgeRegistrationService edgeRegistrationService;

    @GetMapping("/info")
    public ResponseEntity<ApiResponse<EdgeInfoDTO>> getInfo() {
        EdgeIdentity identity = edgeIdentityRepository.findSingleton()
                .orElseThrow(() -> new IllegalStateException(
                        "Edge identity not initialized — bootstrap did not run"));

        EdgeInfoDTO dto = EdgeInfoDTO.builder()
                .edgeId(identity.getEdgeId())
                .paired(identity.getPairedAt() != null)
                .build();

        return ResponseEntity.ok(ApiResponse.success(dto));
    }

    @GetMapping("/pairing-code")
    public ResponseEntity<ApiResponse<PairingCodeDTO>> getPairingCode() {
        EdgeIdentity identity = edgeIdentityRepository.findSingleton()
                .orElseThrow(() -> new IllegalStateException(
                        "Edge identity not initialized — bootstrap did not run"));

        if (identity.getPairedAt() != null) {
            return ResponseEntity
                    .status(404)
                    .body(ApiResponse.error("Edge is already paired"));
        }

        if (identity.getPairingCode() == null) {
            return ResponseEntity
                    .status(404)
                    .body(ApiResponse.error("Pairing code not available yet"));
        }

        Instant expiresAt = identity.getPairingCodeExpiresAt();
        if (expiresAt != null && !expiresAt.isAfter(Instant.now())) {
            edgeRegistrationService.publishIfNotPaired();
            return ResponseEntity
                    .status(404)
                    .body(ApiResponse.error("Pairing code expired; a new one has been requested, retry shortly"));
        }

        PairingCodeDTO dto = PairingCodeDTO.builder()
                .pairingCode(identity.getPairingCode())
                .expiresAt(identity.getPairingCodeExpiresAt())
                .build();

        return ResponseEntity.ok(ApiResponse.success(dto));
    }
}
