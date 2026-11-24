package com.iot.platform.cloud.edge.access;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface EdgeInvitationRepository extends JpaRepository<EdgeInvitation, UUID> {

    List<EdgeInvitation> findByEdgeIdAndStatus(UUID edgeId, EdgeInvitation.Status status);

    List<EdgeInvitation> findByUserIdAndStatus(UUID userId, EdgeInvitation.Status status);

    boolean existsByUserIdAndEdgeIdAndStatus(
            UUID userId, UUID edgeId, EdgeInvitation.Status status);
}
