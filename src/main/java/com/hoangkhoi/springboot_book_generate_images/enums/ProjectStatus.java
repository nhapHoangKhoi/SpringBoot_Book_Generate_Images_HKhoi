package com.hoangkhoi.springboot_book_generate_images.enums;

public enum ProjectStatus {
    CREATED,
    STYLE_SET,
    CHARACTERS_GENERATED,
    PORTRAITS_GENERATED,
    CHAPTERS_GENERATED,
    DONE;

    // How many of the five steps have finished. CREATED = 0, DONE = 5
    public int completedSteps() {
        return ordinal();
    }
}
