package com.iot.platform.cloud.edgesync.outbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CloudOutboxRepository extends JpaRepository<CloudOutbox, UUID> {

    List<CloudOutbox> findTop100BySentAtIsNullOrderByCreatedAtAsc();
}
