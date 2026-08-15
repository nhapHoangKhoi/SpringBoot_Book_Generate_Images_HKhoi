package com.hoangkhoi.springboot_book_generate_images.exception;

/** No such project for that user — either a bad id, or someone else's project. Becomes a 404. */
public class ProjectNotFoundException extends RuntimeException {

    public ProjectNotFoundException(String userId, String projectId) {
        super("No project " + projectId + " for user " + userId);
    }
}
