package com.hoangkhoi.springboot_book_generate_images.controller;

import com.hoangkhoi.springboot_book_generate_images.dto.request.CreateProjectRequest;
import com.hoangkhoi.springboot_book_generate_images.dto.response.ProjectSummary;
import com.hoangkhoi.springboot_book_generate_images.exception.ProjectNotFoundException;
import com.hoangkhoi.springboot_book_generate_images.exception.UnauthorizedException;
import com.hoangkhoi.springboot_book_generate_images.model.Project;
import com.hoangkhoi.springboot_book_generate_images.repository.ProjectRepository;
import com.hoangkhoi.springboot_book_generate_images.response.ApiResponse;
import com.hoangkhoi.springboot_book_generate_images.response.SuccessMessages;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;


@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    private final ProjectRepository projectRepository;

    public ProjectController(ProjectRepository projectRepository) {
        this.projectRepository = projectRepository;
    }

    @GetMapping
    @Operation(summary = "Get list projects of the signed-in user")
    public ResponseEntity<ApiResponse<List<ProjectSummary>>> getAllProjects(
            @RequestHeader(value = "X-User-Id", required = false) String userId) {
        List<ProjectSummary> projects = projectRepository.findAll(requireUser(userId)).stream()
                .map(ProjectSummary::of)
                .toList();

        return ResponseEntity.ok(ApiResponse.ok(
                SuccessMessages.GET_ALL_PROJECTS_SUCCESS, projects));
    }



    private String requireUser(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new UnauthorizedException();
        }
        return projectRepository.findUser(userId).orElseThrow(UnauthorizedException::new).getId();
    }

}
