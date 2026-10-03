package vn.lemar.selfstorage.identity.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import vn.lemar.selfstorage.identity.application.exception.AccountNotAllowedException;
import vn.lemar.selfstorage.identity.application.exception.EmailAlreadyExistsException;
import vn.lemar.selfstorage.identity.application.exception.InvalidCredentialsException;
import vn.lemar.selfstorage.identity.application.exception.InvalidOrExpiredTokenException;
import vn.lemar.selfstorage.identity.application.exception.UnauthenticatedException;

import java.time.Instant;
import java.util.Map;

@RestControllerAdvice
public class AuthExceptionHandler {

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidCredentials(InvalidCredentialsException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorBody(ex.getMessage()));
    }

    @ExceptionHandler(AccountNotAllowedException.class)
    public ResponseEntity<Map<String, Object>> handleAccountNotAllowed(AccountNotAllowedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(errorBody(ex.getMessage()));
    }

    @ExceptionHandler(EmailAlreadyExistsException.class)
    public ResponseEntity<Map<String, Object>> handleEmailExists(EmailAlreadyExistsException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(errorBody(ex.getMessage()));
    }

    @ExceptionHandler(InvalidOrExpiredTokenException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidToken(InvalidOrExpiredTokenException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorBody(ex.getMessage()));
    }

    @ExceptionHandler(UnauthenticatedException.class)
    public ResponseEntity<Map<String, Object>> handleUnauthenticated(UnauthenticatedException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorBody(ex.getMessage()));
    }

    private Map<String, Object> errorBody(String message) {
        return Map.of(
                "timestamp", Instant.now().toString(),
                "message", message
        );
    }
}