package com.hoangkhoi.springboot_book_generate_images.exception;


public class UnauthorizedException extends RuntimeException {

    public UnauthorizedException() {
        super("Sign in to continue");
    }
}
