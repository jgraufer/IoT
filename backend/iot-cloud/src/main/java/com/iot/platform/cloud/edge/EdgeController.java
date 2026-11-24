package com.iot.platform.cloud.edge;

import com.iot.platform.cloud.edge.dto.EdgeSummaryDTO;
import com.iot.platform.common.shared.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/users/me")
@RequiredArgsConstructor
public class EdgeController {

    private final EdgeService edgeService;

    @GetMapping("/edges")
    public ResponseEntity<ApiResponse<List<EdgeSummaryDTO>>> listMyEdges(
            @AuthenticationPrincipal UUID userId) {
        return ResponseEntity.ok(ApiResponse.success(edgeService.listMyEdges(userId)));
    }
}
