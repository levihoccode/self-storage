package vn.lemar.selfstorage.booking.controller;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import vn.lemar.selfstorage.ApiError;
import vn.lemar.selfstorage.booking.application.exception.DepositNotAllowedException;
import vn.lemar.selfstorage.booking.application.exception.EmailNotVerifiedException;
import vn.lemar.selfstorage.booking.application.exception.InvoiceNotFoundException;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
public class BookingExceptionHandler {

    /** Không tồn tại và không phải của mình trả về như nhau — không lộ sự tồn tại của hóa đơn. */
    @ExceptionHandler(InvoiceNotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(InvoiceNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError.now(ex.getMessage()));
    }

    @ExceptionHandler(EmailNotVerifiedException.class)
    public ResponseEntity<ApiError> handleUnverified(EmailNotVerifiedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiError.now(ex.getMessage()));
    }

    /** 409: hóa đơn hợp lệ nhưng hoàn cảnh hiện tại không cho thanh toán. */
    @ExceptionHandler(DepositNotAllowedException.class)
    public ResponseEntity<ApiError> handleConflict(DepositNotAllowedException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError.now(ex.getMessage()));
    }
}
