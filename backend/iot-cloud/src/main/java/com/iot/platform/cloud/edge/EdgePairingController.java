package com.iot.platform.cloud.edge;

import com.iot.platform.cloud.edge.dto.PairRequestDTO;
import com.iot.platform.cloud.edge.dto.PairResponseDTO;
import com.iot.platform.common.shared.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/edges")
@RequiredArgsConstructor
public class EdgePairingController {

    private final EdgePairingService edgePairingService;

    @PostMapping("/pair")
    public ResponseEntity<ApiResponse<PairResponseDTO>> pair(
            @AuthenticationPrincipal UUID userId,
            @Valid @RequestBody PairRequestDTO request) {

        log.info("User {} attempting pairing with code {}", userId, request.getCode());
        PairResponseDTO response = edgePairingService.pair(userId, request.getCode());
        return ResponseEntity.ok(ApiResponse.success("Edge paired", response));
    }
}
