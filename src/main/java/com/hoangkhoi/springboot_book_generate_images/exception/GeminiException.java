package com.hoangkhoi.springboot_book_generate_images.exception;


public class GeminiException extends RuntimeException {

    private final boolean contextExpired;

    public GeminiException(String message) {
        this(message, false);
    }

    public GeminiException(String message, boolean contextExpired) {
        super(message);
        this.contextExpired = contextExpired;
    }

    /**
     * True when the stored conversation is gone and the book has to be sent again.
     *
     * The pipeline clears the stored handle on this, so the user's next retry re-uploads —
     * one deliberate press, never an automatic loop.
     */
    public boolean isContextExpired() {
        return contextExpired;
    }
}
