package com.iot.platform.edge.cloudsync.outbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface EdgeOutboxRepository extends JpaRepository<EdgeOutbox, UUID> {

    List<EdgeOutbox> findTop100BySentAtIsNullOrderByCreatedAtAsc();
}
