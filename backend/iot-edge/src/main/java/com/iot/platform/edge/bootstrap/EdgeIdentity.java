package com.iot.platform.edge.bootstrap;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "edge_identity")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EdgeIdentity {

    @Id
    @Builder.Default
    private Integer id = 1;

    @Column(name = "edge_id", nullable = false)
    private UUID edgeId;

    @Column(name = "jwt_secret", nullable = false)
    private String jwtSecret;

    @Column(name = "pairing_code", length = 16)
    private String pairingCode;

    @Column(name = "pairing_code_expires_at")
    private Instant pairingCodeExpiresAt;

    @Column(name = "paired_at")
    private Instant pairedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
