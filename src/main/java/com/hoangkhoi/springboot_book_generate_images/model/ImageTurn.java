package com.hoangkhoi.springboot_book_generate_images.model;

/**
 * One picture, plus the conversation id the next picture must continue from.
 *
 * The id travels back to the caller because the chain is what keeps characters consistent: drop
 * it and every illustration starts from a blank memory.
 */
public record ImageTurn(byte[] image, String interactionId) {
}
