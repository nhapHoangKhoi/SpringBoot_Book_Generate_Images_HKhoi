package com.hoangkhoi.springboot_book_generate_images.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.hoangkhoi.springboot_book_generate_images.enums.Step;


@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(String code, Step currentStep) {

    public static ApiError of(String code) {
        return new ApiError(code, null);
    }
}
