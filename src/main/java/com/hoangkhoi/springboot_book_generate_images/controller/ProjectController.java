package com.hoangkhoi.springboot_book_generate_images.controller;

import com.hoangkhoi.springboot_book_generate_images.dto.request.CreateProjectRequest;
import com.hoangkhoi.springboot_book_generate_images.dto.response.ProjectDetail;
import com.hoangkhoi.springboot_book_generate_images.dto.response.ProjectSummary;
import com.hoangkhoi.springboot_book_generate_images.exception.ProjectNotFoundException;
import com.hoangkhoi.springboot_book_generate_images.exception.UnauthorizedException;
import com.hoangkhoi.springboot_book_generate_images.model.Project;
import com.hoangkhoi.springboot_book_generate_images.repository.ProjectRepository;
import com.hoangkhoi.springboot_book_generate_images.response.ApiResponse;
import com.hoangkhoi.springboot_book_generate_images.response.SuccessMessages;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.List;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;


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

    @PostMapping
    @Operation(summary = "Create a project from a book's text")
    public ResponseEntity<ApiResponse<ProjectDetail>> createProject(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @Valid @RequestBody CreateProjectRequest request) {
        Project created = projectRepository.create(
                requireUser(userId), request.title().trim(), request.bookText());

        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(
                SuccessMessages.CREATE_PROJECT_SUCCESS, ProjectDetail.of(created, Instant.now())));
    }

    @GetMapping("/{projectId}")
    @Operation(summary = "Get a project's full pipeline state (polled while a step runs)")
    public ResponseEntity<ApiResponse<ProjectDetail>> getProjectById(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @PathVariable String projectId) {
        Project project = load(requireUser(userId), projectId);

        return ResponseEntity.ok(ApiResponse.ok(
                SuccessMessages.GET_PROJECT_SUCCESS, ProjectDetail.of(project, Instant.now())));
    }

    // The book text, separate from the detail payload because that one is polled
    @GetMapping("/{projectId}/book")
    @Operation(summary = "Get the project's full book text")
    public ResponseEntity<ApiResponse<String>> getBookText(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @PathVariable String projectId) {
        String user = requireUser(userId);
        load(user, projectId);

        return ResponseEntity.ok(ApiResponse.ok(
                SuccessMessages.GET_BOOK_TEXT_SUCCESS,
                projectRepository.readBookText(user, projectId)));
    }

    @GetMapping("/{projectId}/images/{fileName}")
    @Operation(summary = "Get a generated image")
    public ResponseEntity<Resource> getImage(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestParam(value = "userId", required = false) String userIdParam,
            @PathVariable String projectId,
            @PathVariable String fileName) {
        String caller = userId != null && !userId.isBlank() ? userId : userIdParam;

        return projectRepository.findImage(requireUser(caller), projectId, fileName)
                .<ResponseEntity<Resource>>map(path -> ResponseEntity.ok()
                        .contentType(MediaType.IMAGE_PNG)
                        .body(new FileSystemResource(path)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private String requireUser(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new UnauthorizedException();
        }
        return projectRepository.findUser(userId).orElseThrow(UnauthorizedException::new).getId();
    }

    private Project load(String userId, String projectId) {
        return projectRepository.find(userId, projectId)
                .orElseThrow(() -> new ProjectNotFoundException(userId, projectId));
    }
}
