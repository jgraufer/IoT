package com.iot.platform.cloud.edge;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "edges")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Edge {

    @Id
    private UUID id;

    @Column
    private String name;

    @Column(name = "paired_at")
    private Instant pairedAt;

    @Column(name = "last_seen")
    private Instant lastSeen;

    @Column(name = "local_url")
    private String localUrl;
}
