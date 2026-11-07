package vn.lemar.selfstorage.payment.controller;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import vn.lemar.selfstorage.ApiError;
import vn.lemar.selfstorage.payment.application.exception.ManualReconciliationConflictException;
import vn.lemar.selfstorage.payment.application.exception.ManualReconciliationNotFoundException;
import vn.lemar.selfstorage.payment.application.exception.ManualReconciliationValidationException;
import vn.lemar.selfstorage.payment.application.exception.PendingPaymentFilterException;
import vn.lemar.selfstorage.payment.application.exception.RefundTransactionConflictException;
import vn.lemar.selfstorage.payment.application.exception.RefundTransactionNotFoundException;
import vn.lemar.selfstorage.payment.application.exception.RefundTransactionValidationException;
import vn.lemar.selfstorage.payment.application.exception.RefundQueueFilterException;
import vn.lemar.selfstorage.payment.application.exception.RefundRequestConflictException;
import vn.lemar.selfstorage.payment.application.exception.RefundRequestValidationException;

/** Lỗi riêng của endpoint đối soát thủ công, trước handler 500 chung. */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
public class PaymentAdminExceptionHandler {

    @ExceptionHandler(ManualReconciliationNotFoundException.class)
    public ResponseEntity<ApiError> notFound(ManualReconciliationNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError.now(ex.getMessage()));
    }

    @ExceptionHandler(ManualReconciliationConflictException.class)
    public ResponseEntity<ApiError> conflict(ManualReconciliationConflictException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError.now(ex.getMessage()));
    }

    @ExceptionHandler(ManualReconciliationValidationException.class)
    public ResponseEntity<ApiError> badRequest(ManualReconciliationValidationException ex) {
        return ResponseEntity.badRequest().body(ApiError.now(ex.getMessage()));
    }

    @ExceptionHandler(PendingPaymentFilterException.class)
    public ResponseEntity<ApiError> badPendingFilter(PendingPaymentFilterException ex) {
        return ResponseEntity.badRequest().body(ApiError.now(ex.getMessage()));
    }

    @ExceptionHandler(RefundTransactionNotFoundException.class)
    public ResponseEntity<ApiError> refundNotFound(RefundTransactionNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError.now(ex.getMessage()));
    }

    @ExceptionHandler(RefundTransactionConflictException.class)
    public ResponseEntity<ApiError> refundConflict(RefundTransactionConflictException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError.now(ex.getMessage()));
    }

    @ExceptionHandler(RefundTransactionValidationException.class)
    public ResponseEntity<ApiError> refundBadRequest(RefundTransactionValidationException ex) {
        return ResponseEntity.badRequest().body(ApiError.now(ex.getMessage()));
    }

    @ExceptionHandler(RefundQueueFilterException.class)
    public ResponseEntity<ApiError> refundQueueBadRequest(RefundQueueFilterException ex) {
        return ResponseEntity.badRequest().body(ApiError.now(ex.getMessage()));
    }

    @ExceptionHandler(RefundRequestConflictException.class)
    public ResponseEntity<ApiError> refundRequestConflict(RefundRequestConflictException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError.now(ex.getMessage()));
    }

    @ExceptionHandler(RefundRequestValidationException.class)
    public ResponseEntity<ApiError> refundRequestBadRequest(RefundRequestValidationException ex) {
        return ResponseEntity.badRequest().body(ApiError.now(ex.getMessage()));
    }
}
