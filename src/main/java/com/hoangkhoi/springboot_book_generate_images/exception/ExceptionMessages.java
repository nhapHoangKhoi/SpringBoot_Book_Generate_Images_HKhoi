package com.hoangkhoi.springboot_book_generate_images.exception;

public class ExceptionMessages {

    /* Codes */
    public static final String UNAUTHORIZED = "UNAUTHORIZED";
    public static final String PROJECT_NOT_FOUND = "PROJECT_NOT_FOUND";
    public static final String INVALID_REQUEST = "INVALID_REQUEST";
    public static final String GEMINI_ERROR = "GEMINI_ERROR";
    public static final String STORAGE_ERROR = "STORAGE_ERROR";

    /* Messages */
    public static final String UNAUTHORIZED_MESSAGE = "Sign in to continue.";
    public static final String PROJECT_NOT_FOUND_MESSAGE = "That project no longer exists.";
    public static final String INVALID_REQUEST_MESSAGE = "Check the values you entered.";
    public static final String STORAGE_ERROR_MESSAGE = "Could not read or write project data.";
    public static final String UNEXPECTED_ERROR = "Unexpected error: {}";

    private ExceptionMessages() {
    }
}
