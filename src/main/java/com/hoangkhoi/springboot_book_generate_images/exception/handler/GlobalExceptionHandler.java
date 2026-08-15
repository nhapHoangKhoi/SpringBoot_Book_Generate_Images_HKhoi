package com.hoangkhoi.springboot_book_generate_images.exception.handler;

import com.hoangkhoi.springboot_book_generate_images.exception.*;
import com.hoangkhoi.springboot_book_generate_images.response.ApiError;
import com.hoangkhoi.springboot_book_generate_images.response.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(StorageException.class)
    public ResponseEntity<ApiResponse<ApiError>> handleStorage(StorageException ex) {
        logger.error(ExceptionMessages.UNEXPECTED_ERROR, ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse.failed(
                ExceptionMessages.STORAGE_ERROR_MESSAGE,
                ApiError.of(ExceptionMessages.STORAGE_ERROR)));
    }
}
