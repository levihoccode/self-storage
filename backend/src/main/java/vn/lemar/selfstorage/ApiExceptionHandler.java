package vn.lemar.selfstorage;

import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 400 cho lỗi đầu vào ở tầng controller — message chung; lỗi nghiệp vụ tự set message trong
 * exception của nó (vd {@code AuthExceptionHandler}).
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final String INVALID_REQUEST_MESSAGE = "Dữ liệu không hợp lệ";

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex) {
        return ResponseEntity.badRequest().body(ApiError.now(INVALID_REQUEST_MESSAGE));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadable(HttpMessageNotReadableException ex) {
        return ResponseEntity.badRequest().body(ApiError.now(INVALID_REQUEST_MESSAGE));
    }
}
