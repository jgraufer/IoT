package com.iot.platform.cloud.edge.access;

import com.iot.platform.common.edge.EdgeRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserEdgeAccessRepository extends JpaRepository<UserEdgeAccess, UUID> {

    Optional<UserEdgeAccess> findByUserIdAndEdgeId(UUID userId, UUID edgeId);

    List<UserEdgeAccess> findByUserId(UUID userId);

    List<UserEdgeAccess> findByEdgeId(UUID edgeId);

    Optional<UserEdgeAccess> findByEdgeIdAndRole(UUID edgeId, EdgeRole role);
}
