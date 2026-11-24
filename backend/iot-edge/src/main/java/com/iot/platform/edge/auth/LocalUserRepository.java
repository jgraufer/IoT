package com.iot.platform.edge.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface LocalUserRepository extends JpaRepository<LocalUser, UUID> {

    Optional<LocalUser> findByUsername(String username);

    Optional<LocalUser> findByEmail(String email);
}
