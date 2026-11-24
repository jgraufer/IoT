package com.iot.platform.cloud.device;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DeviceRepository extends JpaRepository<Device, UUID> {

    @EntityGraph(attributePaths = "capabilities")
    List<Device> findByEdgeId(UUID edgeId);

    @EntityGraph(attributePaths = "capabilities")
    Optional<Device> findByIdAndEdgeId(UUID id, UUID edgeId);

    boolean existsByIdAndEdgeId(UUID id, UUID edgeId);
}
