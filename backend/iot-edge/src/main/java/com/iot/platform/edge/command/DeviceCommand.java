package com.iot.platform.edge.command;

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
@Table(name = "device_command")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeviceCommand {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "device_id", nullable = false)
    private UUID deviceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "capability_type", nullable = false, length = 32)
    private CapabilityType capabilityType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private JsonNode value;

    @Column(name = "issued_by", nullable = false)
    private UUID issuedBy;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt;

    @PrePersist
    void onCreate() {
        if (issuedAt == null) issuedAt = Instant.now();
    }
}
