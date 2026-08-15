package com.hoangkhoi.springboot_book_generate_images.enums;

public enum Step {
    STYLE(ProjectStatus.STYLE_SET),
    CHARACTERS(ProjectStatus.CHARACTERS_GENERATED),
    PORTRAITS(ProjectStatus.PORTRAITS_GENERATED),
    CHAPTERS(ProjectStatus.CHAPTERS_GENERATED),
    ILLUSTRATIONS(ProjectStatus.DONE);

    private final ProjectStatus completedStatus;

    Step(ProjectStatus completedStatus) {
        this.completedStatus = completedStatus;
    }

    public ProjectStatus completedStatus() {
        return completedStatus;
    }
}
