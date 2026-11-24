package com.iot.platform.cloud.edge.access;

import com.iot.platform.cloud.edge.access.dto.InvitationDTO;
import com.iot.platform.cloud.edge.access.dto.InviteRequestDTO;
import com.iot.platform.common.shared.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/edges/{edgeId}/invitations")
@RequiredArgsConstructor
public class EdgeInvitationController {

    private final InvitationService invitationService;

    @PostMapping
    public ResponseEntity<ApiResponse<InvitationDTO>> invite(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID edgeId,
            @Valid @RequestBody InviteRequestDTO body) {
        InvitationDTO response = invitationService.invite(userId, edgeId, body.getEmail());
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("Invitation sent", response));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<InvitationDTO>>> listSent(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID edgeId) {
        List<InvitationDTO> response = invitationService.listSentInvitations(edgeId, userId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @DeleteMapping("/{invitationId}")
    public ResponseEntity<ApiResponse<Void>> cancel(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID edgeId,
            @PathVariable UUID invitationId) {
        invitationService.cancelInvitation(edgeId, invitationId, userId);
        return ResponseEntity.ok(ApiResponse.success("Invitation cancelled", null));
    }
}
