package com.memphisreo.platform.api;

import com.memphisreo.common.ForbiddenException;
import com.memphisreo.common.NotFoundException;
import com.memphisreo.common.ValidationException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import com.memphisreo.common.multitenancy.TenantIsolationViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    public record ErrorResponse(String message) {
    }

    /** Помилки по полях — фронт підсвічує кожне поле окремо. */
    public record ValidationErrorResponse(String message, java.util.List<ValidationException.FieldError> errors) {
    }

    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ValidationErrorResponse> handleValidation(ValidationException e) {
        return ResponseEntity.badRequest().body(new ValidationErrorResponse(e.getMessage(), e.getErrors()));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ValidationErrorResponse> handleUploadTooLarge(MaxUploadSizeExceededException e) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(new ValidationErrorResponse(
                "Файл завеликий", java.util.List.of(new ValidationException.FieldError("file", "fileTooLarge"))));
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(NotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse(e.getMessage()));
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ErrorResponse> handleForbidden(ForbiddenException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ErrorResponse(e.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleBadRequest(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorResponse(e.getMessage()));
    }

    /** Порушення ізоляції tenant-ів — клієнту як "не знайдено", нам — ERROR (ADR-001). */
    @ExceptionHandler(TenantIsolationViolationException.class)
    public ResponseEntity<ErrorResponse> handleTenantIsolationViolation(TenantIsolationViolationException e) {
        log.error("Порушення ізоляції tenant-ів заблоковано: {}", e.getMessage(), e);
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse("Не знайдено"));
    }
}
