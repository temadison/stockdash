package com.temadison.stockdash.backend.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import org.junit.jupiter.api.Test;
import org.springframework.http.ProblemDetail;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ApiExceptionHandlerUnitTest {

    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    @Test
    void handlesConstraintViolationException() {
        @SuppressWarnings("unchecked")
        ConstraintViolation<Object> violation = (ConstraintViolation<Object>) mock(ConstraintViolation.class);
        Path propertyPath = mock(Path.class);
        when(propertyPath.toString()).thenReturn("portfolioController.dailyClosePriceHistory.symbol");
        when(violation.getPropertyPath()).thenReturn(propertyPath);
        when(violation.getMessage()).thenReturn("symbol is required.");

        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/api/portfolio/prices/history");

        ProblemDetail problem = handler.handleConstraintViolationException(
                new ConstraintViolationException(Set.of(violation)),
                request
        );

        assertThat(problem.getStatus()).isEqualTo(400);
        assertThat(problem.getTitle()).isEqualTo("Bad Request");
        assertThat(problem.getDetail()).contains("symbol is required");
        assertThat(problem.getProperties()).containsKey("errors");
    }

    @Test
    void handlesUnhandledExceptionAsInternalServerError() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/api/portfolio/unknown");

        ProblemDetail problem = handler.handleUnhandledException(new RuntimeException("boom"), request);

        assertThat(problem.getStatus()).isEqualTo(500);
        assertThat(problem.getTitle()).isEqualTo("Internal Server Error");
        assertThat(problem.getDetail()).isEqualTo("Unexpected server error.");
    }
}
