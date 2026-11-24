package com.iot.platform.common.edge;

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
public class EdgeRegisteredDTO {

    private UUID edgeId;

    private String pairingCode;

    private Instant pairingCodeExpiresAt;
}
