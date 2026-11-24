package com.iot.platform.cloud.edge;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PairingCodeGeneratorTest {

    @Mock
    private EdgePairingCodeRepository repository;

    @InjectMocks
    private PairingCodeGenerator generator;

    @Test
    void generatedCode_matchesFormatAndAlphabet() {
        when(repository.findByCodeAndUsedFalse(anyString())).thenReturn(Optional.empty());

        String code = generator.generate();

        assertThat(code).matches("^[ABCDEFGHJKMNPQRSTUVWXYZ23456789]{4}-[ABCDEFGHJKMNPQRSTUVWXYZ23456789]{4}$");
    }

    @Test
    void manyCodes_neverContainExcludedChars() {
        when(repository.findByCodeAndUsedFalse(anyString())).thenReturn(Optional.empty());

        for (int i = 0; i < 200; i++) {
            String code = generator.generate();
            assertThat(code).doesNotContainAnyWhitespaces();
            assertThat(code).doesNotContain("0", "O", "1", "I", "L");
        }
    }

    @Test
    void persistentCollision_throwsAfterMaxAttempts() {
        EdgePairingCode existing = EdgePairingCode.builder().code("DUMMY").build();
        when(repository.findByCodeAndUsedFalse(anyString())).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> generator.generate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("non-colliding");
    }
}
