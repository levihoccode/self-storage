package vn.lemar.selfstorage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Lỗi ở tầng controller: 400 validate/JSON hỏng dùng message chung; lỗi khung (404/405/415…) giữ
 * đúng status; mọi exception không lường trước (bug) trả 500 với message chung — chi tiết chỉ vào
 * log, không lộ ra response.
 */
@Order(Ordered.LOWEST_PRECEDENCE)
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(ApiExceptionHandler.class);

    private static final String INVALID_REQUEST_MESSAGE = "Dữ liệu không hợp lệ";
    private static final String NOT_FOUND_MESSAGE = "Không tìm thấy tài nguyên";
    private static final String METHOD_NOT_SUPPORTED_MESSAGE = "Phương thức không được hỗ trợ";
    private static final String MEDIA_TYPE_NOT_SUPPORTED_MESSAGE = "Định dạng nội dung không được hỗ trợ";
    private static final String FALLBACK_CLIENT_MESSAGE = "Yêu cầu không hợp lệ";
    private static final String INTERNAL_ERROR_MESSAGE = "Internal server error";

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex) {
        return ResponseEntity.badRequest().body(ApiError.now(INVALID_REQUEST_MESSAGE));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadable(HttpMessageNotReadableException ex) {
        return ResponseEntity.badRequest().body(ApiError.now(INVALID_REQUEST_MESSAGE));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex) {
        if (ex instanceof ErrorResponse frameworkError) {
            HttpStatusCode status = frameworkError.getStatusCode();
            return ResponseEntity.status(status)
                    .headers(frameworkError.getHeaders())
                    .body(ApiError.now(messageFor(status)));
        }
        LOG.error("Unhandled exception", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiError.now(INTERNAL_ERROR_MESSAGE));
    }

    private static String messageFor(HttpStatusCode status) {
        return switch (status.value()) {
            case 400 -> INVALID_REQUEST_MESSAGE;
            case 404 -> NOT_FOUND_MESSAGE;
            case 405 -> METHOD_NOT_SUPPORTED_MESSAGE;
            case 415 -> MEDIA_TYPE_NOT_SUPPORTED_MESSAGE;
            default -> FALLBACK_CLIENT_MESSAGE;
        };
    }
}
