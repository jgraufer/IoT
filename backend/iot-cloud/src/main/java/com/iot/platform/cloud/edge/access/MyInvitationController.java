package com.iot.platform.cloud.edge.access;

import com.iot.platform.cloud.edge.access.dto.InvitationDTO;
import com.iot.platform.common.shared.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/users/me/invitations")
@RequiredArgsConstructor
public class MyInvitationController {

    private final InvitationService invitationService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<InvitationDTO>>> listMine(
            @AuthenticationPrincipal UUID userId) {
        return ResponseEntity.ok(ApiResponse.success(invitationService.listMyInvitations(userId)));
    }

    @PostMapping("/{invitationId}/accept")
    public ResponseEntity<ApiResponse<InvitationDTO>> accept(

            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID invitationId) {
        InvitationDTO response = invitationService.accept(userId, invitationId);
        return ResponseEntity.ok(ApiResponse.success("Invitation accepted", response));
    }

    @PostMapping("/{invitationId}/reject")
    public ResponseEntity<ApiResponse<InvitationDTO>> reject(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID invitationId) {
        InvitationDTO response = invitationService.reject(userId, invitationId);
        return ResponseEntity.ok(ApiResponse.success("Invitation rejected", response));
    }
}
