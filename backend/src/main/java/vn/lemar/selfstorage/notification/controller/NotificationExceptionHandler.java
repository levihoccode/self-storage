package vn.lemar.selfstorage.notification.controller;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import vn.lemar.selfstorage.ApiError;
import vn.lemar.selfstorage.notification.application.exception.NotificationNotFoundException;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
public class NotificationExceptionHandler {

    @ExceptionHandler(NotificationNotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(NotificationNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError.now(ex.getMessage()));
    }
}
