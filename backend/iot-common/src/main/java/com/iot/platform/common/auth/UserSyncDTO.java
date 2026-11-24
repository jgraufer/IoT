package com.iot.platform.common.auth;

import com.iot.platform.common.edge.EdgeRole;
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
public class UserSyncDTO {

    private UserSyncOperation operation;

    private UUID userId;

    private String username;

    private String email;

    private String name;

    private String passwordHash;

    private EdgeRole role;

    private Instant timestamp;

    private UUID previousOwnerId;

    private UUID newOwnerId;
}
