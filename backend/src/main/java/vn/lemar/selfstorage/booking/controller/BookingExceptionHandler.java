package vn.lemar.selfstorage.booking.controller;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import vn.lemar.selfstorage.ApiError;
import vn.lemar.selfstorage.booking.application.exception.ProposalConflictException;
import vn.lemar.selfstorage.booking.application.exception.ProposalForbiddenException;
import vn.lemar.selfstorage.booking.application.exception.ProposalNotFoundException;
import vn.lemar.selfstorage.booking.application.exception.ProposalValidationException;
import vn.lemar.selfstorage.booking.application.exception.RentalOrderNotFoundException;
import vn.lemar.selfstorage.booking.application.exception.StorageUnitNotFoundException;

/** Đổi exception nghiệp vụ của booking thành {@code {message, timestamp}} đúng status code. */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
public class BookingExceptionHandler {

    @ExceptionHandler({ProposalNotFoundException.class, RentalOrderNotFoundException.class,
        StorageUnitNotFoundException.class})
    public ResponseEntity<ApiError> handleNotFound(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError.now(ex.getMessage()));
    }

    @ExceptionHandler(ProposalForbiddenException.class)
    public ResponseEntity<ApiError> handleForbidden(ProposalForbiddenException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiError.now(ex.getMessage()));
    }

    @ExceptionHandler(ProposalConflictException.class)
    public ResponseEntity<ApiError> handleConflict(ProposalConflictException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError.now(ex.getMessage()));
    }

    @ExceptionHandler(ProposalValidationException.class)
    public ResponseEntity<ApiError> handleValidation(ProposalValidationException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError.now(ex.getMessage()));
    }
}
