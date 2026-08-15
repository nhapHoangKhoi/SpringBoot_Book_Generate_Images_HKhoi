package com.hoangkhoi.springboot_book_generate_images.exception.handler;

import com.hoangkhoi.springboot_book_generate_images.exception.*;
import com.hoangkhoi.springboot_book_generate_images.response.ApiError;
import com.hoangkhoi.springboot_book_generate_images.response.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ApiResponse<ApiError>> handleUnauthorized(UnauthorizedException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.failed(
                ex.getMessage(), ApiError.of(ExceptionMessages.UNAUTHORIZED)));
    }

    @ExceptionHandler(ProjectNotFoundException.class)
    public ResponseEntity<ApiResponse<ApiError>> handleNotFound(ProjectNotFoundException ex) {
        logger.warn(ExceptionMessages.UNEXPECTED_ERROR, ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.failed(
                ExceptionMessages.PROJECT_NOT_FOUND_MESSAGE,
                ApiError.of(ExceptionMessages.PROJECT_NOT_FOUND)));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<ApiError>> handleInvalidRequest(
            MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(field -> field.getField() + ": " + field.getDefaultMessage())
                .orElse(ExceptionMessages.INVALID_REQUEST_MESSAGE);
        return ResponseEntity.badRequest().body(ApiResponse.failed(
                message, ApiError.of(ExceptionMessages.INVALID_REQUEST)));
    }

    @ExceptionHandler(StorageException.class)
    public ResponseEntity<ApiResponse<ApiError>> handleStorage(StorageException ex) {
        logger.error(ExceptionMessages.UNEXPECTED_ERROR, ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse.failed(
                ExceptionMessages.STORAGE_ERROR_MESSAGE,
                ApiError.of(ExceptionMessages.STORAGE_ERROR)));
    }
}
