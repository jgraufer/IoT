package com.iot.platform.cloud.edge;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "edge_pairing_codes")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EdgePairingCode {

    @Id
    @Column(length = 16)
    private String code;

    @Column(name = "edge_id", nullable = false)
    private UUID edgeId;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(nullable = false)
    @Builder.Default
    private Boolean used = false;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
