package com.hoangkhoi.springboot_book_generate_images.dto.request;

import jakarta.validation.constraints.Size;


public record RunStepRequest(@Size(max = 500) String style) {

    public String styleOrNull() {
        return style == null || style.isBlank() ? null : style.trim();
    }
}
