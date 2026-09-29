package com.sufiyan.moviebooking.exception;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;

import java.sql.SQLIntegrityConstraintViolationException;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Failure modes that are hard to provoke end-to-end (races, bugs) still map to the standard error shape. */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
    private final MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/shows/1/holds");

    @Test
    void uniqueConstraintRace_is409_withoutLeakingSqlDetails() {
        var ex = new DataIntegrityViolationException("could not execute statement",
                new SQLIntegrityConstraintViolationException("Duplicate entry '5-9' for key 'uk_show_seats_show_seat'"));

        var response = handler.handleDataIntegrity(ex, request);

        assertThat(response.getStatusCode().value()).isEqualTo(409);
        assertThat(response.getBody().error()).isEqualTo("DATA_CONFLICT");
        assertThat(response.getBody().message()).doesNotContain("uk_show_seats").doesNotContain("Duplicate entry");
        assertThat(response.getBody().path()).isEqualTo("/api/shows/1/holds");
    }

    @Test
    void optimisticAndPessimisticLockFailures_are409_retryable() {
        var optimistic = handler.handleLocking(new ObjectOptimisticLockingFailureException("ShowSeat", 5L), request);
        var pessimistic = handler.handleLocking(new PessimisticLockingFailureException("lock timeout"), request);

        assertThat(optimistic.getStatusCode().value()).isEqualTo(409);
        assertThat(optimistic.getBody().error()).isEqualTo("CONCURRENT_MODIFICATION");
        assertThat(pessimistic.getBody().error()).isEqualTo("CONCURRENT_MODIFICATION");
    }

    @Test
    void unexpectedErrors_are500_withAGenericMessage() {
        var response = handler.handleUnexpected(new IllegalStateException("secret internal detail"), request);

        assertThat(response.getStatusCode().value()).isEqualTo(500);
        assertThat(response.getBody().error()).isEqualTo("INTERNAL_ERROR");
        assertThat(response.getBody().message()).doesNotContain("secret");
    }

    @Test
    void constraintViolations_listTheFields() {
        ConstraintViolation<?> violation = mock(ConstraintViolation.class);
        Path path = mock(Path.class);
        when(path.toString()).thenReturn("search.size");
        when(violation.getPropertyPath()).thenReturn(path);
        when(violation.getMessage()).thenReturn("must be less than or equal to 100");

        var response = handler.handleConstraintViolation(new ConstraintViolationException(Set.of(violation)), request);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody().fieldErrors()).singleElement()
                .satisfies(f -> assertThat(f.field()).isEqualTo("search.size"));
    }

    @Test
    void accessDeniedFromMethodSecurity_is403() {
        var response = handler.handleAccessDenied(new AccessDeniedException("nope"), request);

        assertThat(response.getStatusCode().value()).isEqualTo(403);
        assertThat(response.getBody().error()).isEqualTo("FORBIDDEN");
    }
}
