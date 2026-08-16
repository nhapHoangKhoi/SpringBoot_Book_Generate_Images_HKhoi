package com.hoangkhoi.springboot_book_generate_images.model;

/** A name + image prompt pair, as returned by the structured-output steps (2 and 4). */
public record GeneratedItem(String name, String prompt) {
}
