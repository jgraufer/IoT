package com.iot.platform.edge.command;

import com.iot.platform.common.device.DeviceCommandDTO;
import com.iot.platform.common.shared.ApiResponse;
import com.iot.platform.edge.command.dto.IssueCommandRequestDTO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/devices/{deviceId}/commands")
@RequiredArgsConstructor
public class CommandController {

    private final CommandService commandService;

    @PostMapping
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','MEMBER')")
    public ResponseEntity<ApiResponse<DeviceCommandDTO>> issue(
            @PathVariable UUID deviceId,
            @Valid @RequestBody IssueCommandRequestDTO request,
            @AuthenticationPrincipal UUID issuedBy) {

        DeviceCommandDTO command = commandService.issueCommand(
                deviceId, request.getCapability(), request.getValue(), issuedBy);
        return ResponseEntity
                .status(HttpStatus.ACCEPTED)
                .body(ApiResponse.success("Command accepted", command));
    }
}
