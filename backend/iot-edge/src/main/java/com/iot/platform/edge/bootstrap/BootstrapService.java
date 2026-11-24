package com.iot.platform.edge.bootstrap;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class BootstrapService {

    private static final int JWT_SECRET_BYTES = 32;

    private final EdgeIdentityRepository repository;
    private final SecureRandom secureRandom = new SecureRandom();

    @PostConstruct
    public void initialize() {
        repository.findSingleton().ifPresentOrElse(
                this::logExisting,
                this::generateAndPersist
        );
    }

    private void logExisting(EdgeIdentity identity) {
        log.info("Edge identity already initialized: edgeId={}, pairedAt={}",
                identity.getEdgeId(), identity.getPairedAt());
    }

    private void generateAndPersist() {
        UUID edgeId = UUID.randomUUID();
        String jwtSecret = generateJwtSecret();

        EdgeIdentity identity = EdgeIdentity.builder()
                .id(1)
                .edgeId(edgeId)
                .jwtSecret(jwtSecret)
                .build();

        repository.save(identity);
        log.info("Edge identity initialized: edgeId={}", edgeId);
    }

    private String generateJwtSecret() {
        byte[] bytes = new byte[JWT_SECRET_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }
}
