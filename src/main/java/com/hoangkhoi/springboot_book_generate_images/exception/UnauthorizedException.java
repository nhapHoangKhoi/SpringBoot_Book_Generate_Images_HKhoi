package com.hoangkhoi.springboot_book_generate_images.exception;

/** No usable X-User-Id on the request. Becomes a 401 — the client should send them to sign in. */
public class UnauthorizedException extends RuntimeException {

    public UnauthorizedException() {
        super("Sign in to continue");
    }
}
