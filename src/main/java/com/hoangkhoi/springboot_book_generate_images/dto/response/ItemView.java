package com.hoangkhoi.springboot_book_generate_images.dto.response;

import com.hoangkhoi.springboot_book_generate_images.enums.ItemState;
import com.hoangkhoi.springboot_book_generate_images.model.IllustratedItem;

/** One character or chapter card. {@code imageFile} is null until its image lands. */
public record ItemView(
        String name,
        String prompt,
        ItemState imageState,
        String imageFile,
        String error) {

    public static ItemView of(IllustratedItem item) {
        return new ItemView(
                item.getName(),
                item.getPrompt(),
                item.getImageState(),
                item.getImageFile(),
                item.getError());
    }
}
