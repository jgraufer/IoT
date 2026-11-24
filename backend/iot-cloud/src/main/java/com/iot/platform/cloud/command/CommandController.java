package com.iot.platform.cloud.command;

import com.iot.platform.cloud.command.dto.IssueCommandRequestDTO;
import com.iot.platform.common.device.CommandDTO;
import com.iot.platform.common.shared.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/edges/{edgeId}/devices/{deviceId}/commands")
@RequiredArgsConstructor
public class CommandController {

    private final CommandProxyService commandProxyService;

    @PostMapping
    public ResponseEntity<ApiResponse<CommandDTO>> issue(
            @PathVariable UUID edgeId,
            @PathVariable UUID deviceId,
            @Valid @RequestBody IssueCommandRequestDTO request,
            @AuthenticationPrincipal UUID issuedBy) {

        CommandDTO command = commandProxyService.proxyCommand(
                edgeId, deviceId, request.getCapability(), request.getValue(), issuedBy);
        return ResponseEntity
                .status(HttpStatus.ACCEPTED)
                .body(ApiResponse.success("Command accepted", command));
    }
}
