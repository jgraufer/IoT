package com.iot.platform.cloud.edge.dto;

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
public class MemberDTO {

    private UUID userId;
    private String name;
    private String username;
    private String email;
    private EdgeRole role;
    private Instant grantedAt;
}
