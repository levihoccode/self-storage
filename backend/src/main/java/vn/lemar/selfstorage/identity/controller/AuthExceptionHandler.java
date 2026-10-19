package vn.lemar.selfstorage.identity.controller;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import vn.lemar.selfstorage.ApiError;
import vn.lemar.selfstorage.identity.application.exception.AccountNotAllowedException;
import vn.lemar.selfstorage.identity.application.exception.AccountNotFoundException;
import vn.lemar.selfstorage.identity.application.exception.EmailAlreadyExistsException;
import vn.lemar.selfstorage.identity.application.exception.EmailAlreadyVerifiedException;
import vn.lemar.selfstorage.identity.application.exception.EmailNotRegisteredException;
import vn.lemar.selfstorage.identity.application.exception.EmailNotVerifiedException;
import vn.lemar.selfstorage.identity.application.exception.FacilityNotFoundException;
import vn.lemar.selfstorage.identity.application.exception.ForbiddenException;
import vn.lemar.selfstorage.identity.application.exception.InvalidCredentialsException;
import vn.lemar.selfstorage.identity.application.exception.InvalidVerificationCodeException;
import vn.lemar.selfstorage.identity.application.exception.TooManyLoginAttemptsException;
import vn.lemar.selfstorage.identity.application.exception.TooManyVerificationAttemptsException;
import vn.lemar.selfstorage.identity.application.exception.UnauthenticatedException;
import vn.lemar.selfstorage.identity.application.exception.VerificationResendTooEarlyException;

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

    @ExceptionHandler(EmailNotVerifiedException.class)
    public ResponseEntity<ApiError> handleEmailNotVerified(EmailNotVerifiedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiError.now(ex.getMessage()));
    }

    @ExceptionHandler(AccountNotFoundException.class)
    public ResponseEntity<ApiError> handleAccountNotFound(AccountNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError.now(ex.getMessage()));
    }

    @ExceptionHandler(FacilityNotFoundException.class)
    public ResponseEntity<ApiError> handleFacilityNotFound(FacilityNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError.now(ex.getMessage()));
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ApiError> handleForbidden(ForbiddenException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiError.now(ex.getMessage()));
    }

    @ExceptionHandler(UnauthenticatedException.class)
    public ResponseEntity<ApiError> handleUnauthenticated(UnauthenticatedException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiError.now(ex.getMessage()));
    }

    @ExceptionHandler(EmailAlreadyExistsException.class)
    public ResponseEntity<ApiError> handleEmailAlreadyExists(EmailAlreadyExistsException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError.now(ex.getMessage()));
    }

    @ExceptionHandler(EmailNotRegisteredException.class)
    public ResponseEntity<ApiError> handleEmailNotRegistered(EmailNotRegisteredException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError.now(ex.getMessage()));
    }

    @ExceptionHandler(EmailAlreadyVerifiedException.class)
    public ResponseEntity<ApiError> handleEmailAlreadyVerified(EmailAlreadyVerifiedException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError.now(ex.getMessage()));
    }

    @ExceptionHandler(InvalidVerificationCodeException.class)
    public ResponseEntity<ApiError> handleInvalidVerificationCode(InvalidVerificationCodeException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError.now(ex.getMessage()));
    }

    @ExceptionHandler(TooManyVerificationAttemptsException.class)
    public ResponseEntity<ApiError> handleTooManyVerificationAttempts(TooManyVerificationAttemptsException ex) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(ApiError.now(ex.getMessage()));
    }

    @ExceptionHandler(VerificationResendTooEarlyException.class)
    public ResponseEntity<ApiError> handleVerificationResendTooEarly(VerificationResendTooEarlyException ex) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(ApiError.now(ex.getMessage()));
    }

    @ExceptionHandler(TooManyLoginAttemptsException.class)
    public ResponseEntity<ApiError> handleTooManyLoginAttempts(TooManyLoginAttemptsException ex) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(ApiError.now(ex.getMessage()));
    }
}
