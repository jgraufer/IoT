package com.iot.platform.edge.bootstrap;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface EdgeIdentityRepository extends JpaRepository<EdgeIdentity, Integer> {

    default Optional<EdgeIdentity> findSingleton() {
        return findById(1);
    }
}
