package com.iot.platform.cloud.edge;

import com.iot.platform.cloud.edge.dto.EdgeDetailDTO;
import com.iot.platform.common.shared.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/edges/{edgeId}")
@RequiredArgsConstructor
public class EdgeDetailController {

    private final EdgeService edgeService;

    @GetMapping
    public ResponseEntity<ApiResponse<EdgeDetailDTO>> getEdgeDetail(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID edgeId) {
        return ResponseEntity.ok(ApiResponse.success(edgeService.getEdgeDetail(edgeId, userId)));
    }
}
