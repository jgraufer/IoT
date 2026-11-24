package com.iot.platform.cloud.edge.access.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InvitationDTO {

    private UUID id;
    private UUID edgeId;
    private String edgeName;
    private UUID invitedUserId;
    private String invitedUsername;
    private String invitedEmail;
    private String invitedName;
    private UUID invitedBy;
    private Instant createdAt;
    private Instant expiresAt;
}
