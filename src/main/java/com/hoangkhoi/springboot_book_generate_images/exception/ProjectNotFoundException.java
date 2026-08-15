package com.hoangkhoi.springboot_book_generate_images.exception;


public class ProjectNotFoundException extends RuntimeException {

    public ProjectNotFoundException(String userId, String projectId) {
        super("No project " + projectId + " for user " + userId);
    }
}
