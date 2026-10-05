package vn.lemar.selfstorage.identity.controller;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import vn.lemar.selfstorage.ApiError;
import vn.lemar.selfstorage.identity.application.exception.AccountNotAllowedException;
import vn.lemar.selfstorage.identity.application.exception.InvalidCredentialsException;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
public class AuthExceptionHandler {

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ApiError> handleInvalidCredentials(InvalidCredentialsException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiError.now(ex.getMessage()));
    }

    @ExceptionHandler(AccountNotAllowedException.class)
    public ResponseEntity<ApiError> handleAccountNotAllowed(AccountNotAllowedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiError.now(ex.getMessage()));
    }
}
