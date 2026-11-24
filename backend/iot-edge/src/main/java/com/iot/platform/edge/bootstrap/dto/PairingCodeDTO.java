package com.iot.platform.edge.bootstrap.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PairingCodeDTO {

    private String pairingCode;

    private Instant expiresAt;
}
