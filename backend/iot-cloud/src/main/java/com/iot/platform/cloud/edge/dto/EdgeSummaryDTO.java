package com.iot.platform.cloud.edge.dto;

import com.iot.platform.common.edge.EdgeRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EdgeSummaryDTO {

    private UUID edgeId;
    private String name;
    private EdgeRole role;
}
