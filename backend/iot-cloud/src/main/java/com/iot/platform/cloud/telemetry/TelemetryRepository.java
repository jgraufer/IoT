package com.iot.platform.cloud.telemetry;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface TelemetryRepository
        extends JpaRepository<Telemetry, UUID>, TelemetryRepositoryCustom {

    @Query(value = """
            SELECT DISTINCT ON (capability_type) *
            FROM telemetry
            WHERE device_id = :deviceId AND edge_id = :edgeId
            ORDER BY capability_type, timestamp DESC
            """, nativeQuery = true)
    List<Telemetry> findLatestPerCapability(@Param("edgeId") UUID edgeId,
                                            @Param("deviceId") UUID deviceId);

    @Query(value = """
            SELECT extract(epoch FROM date_bin(
                       make_interval(mins => :bucketMinutes), timestamp, TIMESTAMPTZ 'epoch'))::bigint
                       AS bucket_epoch,
                   avg((value #>> '{}')::numeric)::double precision AS average,
                   count(*) AS sample_count
            FROM telemetry
            WHERE edge_id = :edgeId
              AND (CAST(:deviceId AS uuid) IS NULL OR device_id = :deviceId)
              AND capability_type = :capability
              AND timestamp >= :since
              AND timestamp <  :until
            GROUP BY bucket_epoch
            ORDER BY bucket_epoch
            """, nativeQuery = true)
    List<AggregatePointProjection> aggregate(@Param("edgeId") UUID edgeId,
                                             @Param("deviceId") UUID deviceId,
                                             @Param("capability") String capability,
                                             @Param("since") Instant since,
                                             @Param("until") Instant until,
                                             @Param("bucketMinutes") int bucketMinutes);
}
