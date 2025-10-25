package com.example.traveler.exception;

import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.OptimisticLockException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;
import org.springframework.http.converter.HttpMessageNotReadableException;

import java.util.Map;
import java.util.stream.Collectors;
import java.time.format.DateTimeParseException;

@ControllerAdvice
public class GlobalExceptionHandler {

    // Обробка помилки "втраченого оновлення" [cite: 31]
    // Повертає 409 Conflict. (У ТЗ вказано "40G", що є одруківкою для 409)
    @ExceptionHandler({OptimisticLockException.class})
    public ResponseEntity<Object> handleOptimisticLockException(OptimisticLockException ex, WebRequest request) {
        return buildErrorResponse(HttpStatus.CONFLICT, ex.getMessage());
    }

    // Обробка помилки "не знайдено"
    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<Object> handleEntityNotFoundException(EntityNotFoundException ex, WebRequest request) {
        return buildErrorResponse(HttpStatus.NOT_FOUND, ex.getMessage()); // 404 Not Found
    }

    @ExceptionHandler(DateTimeParseException.class)
    public ResponseEntity<Object> handleDateTimeParseException(DateTimeParseException ex, WebRequest request) {
        return buildErrorResponse(HttpStatus.BAD_REQUEST, "Invalid date format. Please use YYYY-MM-DD.");
    }

    // Обробка помилок парсингу JSON (включаючи невалідні дати)
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex, WebRequest request) {
        // Повертає 400, як того вимагає тест
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                "status", HttpStatus.BAD_REQUEST.value(),
                "error", "Validation error",
                "message", ex.getMessage()
        ));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Object> handleIllegalStateException(IllegalStateException ex, WebRequest request) {
        // Повертаємо 400 Bad Request у форматі, який очікує тест
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                "status", HttpStatus.BAD_REQUEST.value(),
                "error", "Validation error",
                "message", ex.getMessage()
        ));
    }

    // Обробка помилок валідації (напр. @Size, @NotNull)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Object> handleValidationException(MethodArgumentNotValidException ex, WebRequest request) {
        Map<String, String> errors = ex.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(
                        fieldError -> fieldError.getField(),
                        fieldError -> fieldError.getDefaultMessage()
                ));

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                "status", HttpStatus.BAD_REQUEST.value(),
                "error", "Validation error", // Тести очікують "Validation error"
                "messages", errors
        ));
    }

    // Загальний обробник для інших помилок
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleGenericException(Exception ex, WebRequest request) {
        // Повертає 500 Internal Error
        ex.printStackTrace(); // Важливо для логування
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