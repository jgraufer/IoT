package com.iot.platform.cloud.exception;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.iot.platform.common.shared.ApiResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void expiredPairingCodeReturns400WithStableCode() throws Exception {
        ResponseEntity<ApiResponse<Void>> response =
                handler.handlePairingCodeExpired(new PairingCodeExpiredException());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(json(response).get("code").asText()).isEqualTo("PAIRING_CODE_EXPIRED");
    }

    @Test
    void invalidPairingCodeReturns400WithStableCode() throws Exception {
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleInvalidPairingCode(new InvalidPairingCodeException());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(json(response).get("code").asText()).isEqualTo("PAIRING_CODE_INVALID");
    }

    @Test
    void errorsWithoutCodeOmitTheField() throws Exception {
        JsonNode body = objectMapper.valueToTree(ApiResponse.error("boom"));

        assertThat(body.has("code")).isFalse();
    }

    private JsonNode json(ResponseEntity<ApiResponse<Void>> response) {
        return objectMapper.valueToTree(response.getBody());
    }
}
