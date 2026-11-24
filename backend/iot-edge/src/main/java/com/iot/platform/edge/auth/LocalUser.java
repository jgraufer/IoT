package com.iot.platform.edge.auth;

import com.iot.platform.common.edge.EdgeRole;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "local_user")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LocalUser {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 64)
    private String username;

    @Column(nullable = false, unique = true)
    private String email;

    @Column
    private String name;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private EdgeRole role;

    @Column(name = "last_synced_at", nullable = false)
    private Instant lastSyncedAt;
}
