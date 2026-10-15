package vn.lemar.selfstorage.booking.controller;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import vn.lemar.selfstorage.ApiError;
import vn.lemar.selfstorage.booking.application.exception.FacilityNotFoundException;
import vn.lemar.selfstorage.booking.application.exception.RentalRequestAlreadyHandledException;
import vn.lemar.selfstorage.booking.application.exception.RentalRequestDuplicateUnitException;
import vn.lemar.selfstorage.booking.application.exception.RentalRequestNotFoundException;
import vn.lemar.selfstorage.booking.application.exception.RentalRequestValidationException;
import vn.lemar.selfstorage.booking.application.exception.StorageUnitUnavailableException;
import vn.lemar.selfstorage.booking.application.exception.UnitTypeNotFoundException;

/**
 * Lỗi nghiệp vụ của booking — phải HIGHEST_PRECEDENCE vì ApiExceptionHandler
 * (LOWEST) sẽ đè lên bằng catch-all 500 nếu thiếu @Order (xem error-handling.md).
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
public class BookingExceptionHandler {

    @ExceptionHandler(RentalRequestValidationException.class)
    public ResponseEntity<ApiError> handleValidation(RentalRequestValidationException ex) {
        return ResponseEntity.badRequest().body(ApiError.now(ex.getMessage()));
    }

    @ExceptionHandler(UnitTypeNotFoundException.class)
    public ResponseEntity<ApiError> handleUnitTypeNotFound(UnitTypeNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError.now(ex.getMessage()));
    }

    @ExceptionHandler(FacilityNotFoundException.class)
    public ResponseEntity<ApiError> handleFacilityNotFound(FacilityNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError.now(ex.getMessage()));
    }

    @ExceptionHandler(RentalRequestNotFoundException.class)
    public ResponseEntity<ApiError> handleRentalRequestNotFound(RentalRequestNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError.now(ex.getMessage()));
    }

    @ExceptionHandler(RentalRequestAlreadyHandledException.class)
    public ResponseEntity<ApiError> handleAlreadyHandled(RentalRequestAlreadyHandledException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError.now(ex.getMessage()));
    }

    @ExceptionHandler(RentalRequestDuplicateUnitException.class)
    public ResponseEntity<ApiError> handleDuplicateUnit(RentalRequestDuplicateUnitException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError.now(ex.getMessage()));
    }

    @ExceptionHandler(StorageUnitUnavailableException.class)
    public ResponseEntity<ApiError> handleUnitUnavailable(StorageUnitUnavailableException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError.now(ex.getMessage()));
    }
}
