package com.ewallet.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.ServletWebRequest;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handleInsufficientFundsException_returnsBadRequestWithErrorCode() {
        InsufficientFundsException ex = new InsufficientFundsException("Not enough balance");

        ResponseEntity<ErrorResponse> response = handler.handleInsufficientFundsException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getErrorCode()).isEqualTo("INSUFFICIENT_FUNDS");
        assertThat(response.getBody().getMessage()).isEqualTo("Not enough balance");
    }

    @Test
    void handleNoSuchElementFoundException_returnsNotFound() {
        NoSuchElementFoundException ex = new NoSuchElementFoundException("Account not found");

        ResponseEntity<ErrorResponse> response = handler.handleNoSuchElementFoundException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getErrorCode()).isEqualTo("NOT_FOUND");
    }

    @Test
    void handleConcurrentOperationException_returnsConflict() {
        ConcurrentOperationException ex = new ConcurrentOperationException("Retry");

        ResponseEntity<ErrorResponse> response = handler.handleConcurrentOperationException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getErrorCode()).isEqualTo("CONCURRENT_MODIFICATION");
    }
}
