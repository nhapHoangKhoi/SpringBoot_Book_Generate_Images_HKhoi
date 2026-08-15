package com.hoangkhoi.springboot_book_generate_images.controller;

import com.hoangkhoi.springboot_book_generate_images.dto.request.SessionRequest;
import com.hoangkhoi.springboot_book_generate_images.dto.response.SessionResponse;
import com.hoangkhoi.springboot_book_generate_images.model.User;
import com.hoangkhoi.springboot_book_generate_images.repository.ProjectRepository;
import com.hoangkhoi.springboot_book_generate_images.response.ApiResponse;
import com.hoangkhoi.springboot_book_generate_images.response.SuccessMessages;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Sign-in. An unknown email creates the user; a known one loads them (assessment §4.1). */
@RestController
@RequestMapping("/api/session")
public class SessionController {

    private final ProjectRepository projectRepository;

    public SessionController(ProjectRepository projectRepository) {
        this.projectRepository = projectRepository;
    }

    @PostMapping
    @Operation(summary = "Sign in with a name and email")
    public ResponseEntity<ApiResponse<SessionResponse>> signIn(
            @Valid @RequestBody SessionRequest request) {
        User user = projectRepository.saveUser(User.of(request.name(), request.email()));

        return ResponseEntity.ok(ApiResponse.ok(
                SuccessMessages.SIGN_IN_SUCCESS, SessionResponse.of(user)));
    }
}
