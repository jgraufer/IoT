package com.iot.platform.cloud.edge;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;

@Component
@RequiredArgsConstructor
public class PairingCodeGenerator {

    static final String ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
    static final int SEGMENT_LENGTH = 4;
    static final int MAX_ATTEMPTS = 5;

    private final EdgePairingCodeRepository edgePairingCodeRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    public String generate() {
        for (int i = 0; i < MAX_ATTEMPTS; i++) {
            String candidate = generateCandidate();
            if (edgePairingCodeRepository.findByCodeAndUsedFalse(candidate).isEmpty()) {
                return candidate;
            }
        }
        throw new IllegalStateException(
                "Could not generate non-colliding pairing code after " + MAX_ATTEMPTS + " attempts");
    }

    private String generateCandidate() {
        StringBuilder sb = new StringBuilder(2 * SEGMENT_LENGTH + 1);
        appendSegment(sb);
        sb.append('-');
        appendSegment(sb);
        return sb.toString();
    }

    private void appendSegment(StringBuilder sb) {
        for (int i = 0; i < SEGMENT_LENGTH; i++) {
            sb.append(ALPHABET.charAt(secureRandom.nextInt(ALPHABET.length())));
        }
    }
}
