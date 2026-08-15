package com.hoangkhoi.springboot_book_generate_images.exception;

/** Something went wrong reading or writing the data directory. Always a 500 — never expected. */
public class StorageException extends RuntimeException {

    public StorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
