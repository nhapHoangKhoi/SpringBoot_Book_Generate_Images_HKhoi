package com.hoangkhoi.springboot_book_generate_images.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;


public record SessionRequest(
        @NotBlank @Size(max = 100) String name,
        @NotBlank @Email @Size(max = 254) String email) {

    public SessionRequest {
        name = name == null ? null : name.trim();
        email = email == null ? null : email.trim().toLowerCase();
    }
}
