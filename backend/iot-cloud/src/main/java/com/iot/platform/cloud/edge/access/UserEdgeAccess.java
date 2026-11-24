package com.iot.platform.cloud.edge.access;

import com.iot.platform.common.edge.EdgeRole;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "user_edge_access",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "edge_id"})
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class    UserEdgeAccess {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "edge_id", nullable = false)
    private UUID edgeId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private EdgeRole role;

    @CreationTimestamp
    @Column(name = "granted_at", nullable = false, updatable = false)
    private Instant grantedAt;

    @Column(name = "granted_by")
    private UUID grantedBy;
}
