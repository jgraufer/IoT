package com.iot.platform.cloud.edge.dto;

import com.iot.platform.common.edge.EdgeRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EdgeDetailDTO {

    private UUID edgeId;
    private String name;
    private EdgeRole role;
    private Instant pairedAt;
    private Instant lastSeen;
    private List<MemberDTO> members;
}
