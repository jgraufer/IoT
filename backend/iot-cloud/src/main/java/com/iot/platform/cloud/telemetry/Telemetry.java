package com.iot.platform.cloud.telemetry;

import com.fasterxml.jackson.databind.JsonNode;
import com.iot.platform.common.device.CapabilityType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "telemetry")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Telemetry {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "device_id", nullable = false)
    private UUID deviceId;

    @Column(name = "edge_id", nullable = false)
    private UUID edgeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "capability_type", nullable = false, length = 32)
    private CapabilityType capabilityType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private JsonNode value;

    @Column(nullable = false)
    private Instant timestamp;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;
}
