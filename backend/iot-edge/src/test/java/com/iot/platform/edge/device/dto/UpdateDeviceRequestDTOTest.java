package com.iot.platform.edge.device.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UpdateDeviceRequestDTOTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private boolean nameIsValid(String name) {
        return validator.validateProperty(UpdateDeviceRequestDTO.builder().name(name).build(), "name").isEmpty();
    }

    @Test
    void rejectsBlankName() {
        assertThat(nameIsValid("   ")).isFalse();
        assertThat(nameIsValid("\t")).isFalse();
    }

    @Test
    void acceptsNameWithText() {
        assertThat(nameIsValid("Luz salón")).isTrue();
        assertThat(nameIsValid("  a ")).isTrue();
    }

    @Test
    void acceptsNullNameInPartialPatch() {
        assertThat(nameIsValid(null)).isTrue();
    }
}
