package vn.lemar.selfstorage.media.controller;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import vn.lemar.selfstorage.ApiError;
import vn.lemar.selfstorage.media.application.MediaService;
import vn.lemar.selfstorage.media.application.exception.InvalidUploadException;

/** Lỗi upload của module media — 400 với message cụ thể thay vì message chung của tầng khung. */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
public class MediaExceptionHandler {

    @ExceptionHandler(InvalidUploadException.class)
    public ResponseEntity<ApiError> handleInvalidUpload(InvalidUploadException ex) {
        return ResponseEntity.badRequest().body(ApiError.now(ex.getMessage()));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiError> handleTooLarge(MaxUploadSizeExceededException ex) {
        return ResponseEntity.badRequest().body(ApiError.now(MediaService.MAX_SIZE_MESSAGE));
    }
}
