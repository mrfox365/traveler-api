package com.example.traveler.exception;

import com.example.traveler.config.ShardingRoutingDataSource;
import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.OptimisticLockException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

import java.io.IOException;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice
@RequiredArgsConstructor
@Slf4j
public class GlobalExceptionHandler {

    private final ShardingRoutingDataSource shardingDataSource;

    // Обробка помилок БД і спроба ребалансування (оновлення мапінгу)
    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<?> handleDbException(DataAccessException ex, HttpServletRequest request) {
        log.error("Database error occurred: {}. Trying to refresh mapping...", ex.getMessage());

        try {
            shardingDataSource.refreshDataSources();
            // Повертаємо 503, щоб клієнт спробував ще раз
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .header("Retry-After", "1")
                    .body(Map.of("error", "Sharding map refreshed. Please try again."));
        } catch (IOException e) {
            log.error("Failed to refresh mapping", e);
            return ResponseEntity.internalServerError().body(Map.of("error", "Critical system error during rebalancing"));
        }
    }

    @ExceptionHandler({OptimisticLockException.class})
    public ResponseEntity<Object> handleOptimisticLockException(OptimisticLockException ex, WebRequest request) {
        return buildErrorResponse(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<Object> handleEntityNotFoundException(EntityNotFoundException ex, WebRequest request) {
        return buildErrorResponse(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(DateTimeParseException.class)
    public ResponseEntity<Object> handleDateTimeParseException(DateTimeParseException ex, WebRequest request) {
        return buildErrorResponse(HttpStatus.BAD_REQUEST, "Invalid date format. Please use YYYY-MM-DD.");
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex, WebRequest request) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                "status", HttpStatus.BAD_REQUEST.value(),
                "error", "Validation error",
                "message", ex.getMessage() != null ? ex.getMessage() : "Malformed JSON request"
        ));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Object> handleIllegalStateException(IllegalStateException ex, WebRequest request) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                "status", HttpStatus.BAD_REQUEST.value(),
                "error", "Validation error",
                "message", ex.getMessage()
        ));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Object> handleValidationException(MethodArgumentNotValidException ex, WebRequest request) {
        Map<String, String> errors = ex.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(
                        fieldError -> fieldError.getField(),
                        fieldError -> fieldError.getDefaultMessage(),
                        (existing, replacement) -> existing + "; " + replacement
                ));

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                "status", HttpStatus.BAD_REQUEST.value(),
                "error", "Validation error",
                "messages", errors
        ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleGenericException(Exception ex, WebRequest request) {
        log.error("Unexpected error", ex); // Логуємо повний стек-трейс
        return buildErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred.");
    }

    private ResponseEntity<Object> buildErrorResponse(HttpStatus status, String message) {
        return ResponseEntity
                .status(status)
                .body(Map.of(
                        "status", status.value(),
                        "error", status.getReasonPhrase(),
                        "message", message
                ));
    }
}