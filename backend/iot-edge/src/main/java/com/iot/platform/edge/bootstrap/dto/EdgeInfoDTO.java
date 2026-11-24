package com.iot.platform.edge.bootstrap.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EdgeInfoDTO {

    private UUID edgeId;

    private boolean paired;
}
