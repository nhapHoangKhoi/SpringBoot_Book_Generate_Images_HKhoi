package com.hoangkhoi.springboot_book_generate_images.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;


public record CreateProjectRequest(
        @NotBlank @Size(max = 200) String title,
        @NotBlank String bookText) {
}
