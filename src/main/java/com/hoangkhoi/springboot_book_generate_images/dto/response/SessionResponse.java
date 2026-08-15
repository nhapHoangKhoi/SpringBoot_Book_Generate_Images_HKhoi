package com.hoangkhoi.springboot_book_generate_images.dto.response;

import com.hoangkhoi.springboot_book_generate_images.model.User;


public record SessionResponse(String id, String name, String email) {

    public static SessionResponse of(User user) {
        return new SessionResponse(user.getId(), user.getName(), user.getEmail());
    }
}
