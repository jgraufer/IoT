package com.iot.platform.edge.device;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DeviceRepository extends JpaRepository<Device, UUID> {

    @Override
    @EntityGraph(attributePaths = "capabilities")
    List<Device> findAll();

    @Override
    @EntityGraph(attributePaths = "capabilities")
    Optional<Device> findById(UUID id);
}
