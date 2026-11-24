package com.iot.platform.cloud.edge;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EdgePairingCodeRepository extends JpaRepository<EdgePairingCode, String> {

    Optional<EdgePairingCode> findByCodeAndUsedFalse(String code);

    boolean existsByEdgeIdAndUsedFalseAndExpiresAtAfter(UUID edgeId, Instant now);
}
