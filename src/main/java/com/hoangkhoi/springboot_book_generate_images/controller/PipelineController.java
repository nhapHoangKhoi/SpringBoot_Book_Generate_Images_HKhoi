package com.hoangkhoi.springboot_book_generate_images.controller;

import com.hoangkhoi.springboot_book_generate_images.dto.request.RunStepRequest;
import com.hoangkhoi.springboot_book_generate_images.dto.response.ProjectDetail;
import com.hoangkhoi.springboot_book_generate_images.enums.Step;
import com.hoangkhoi.springboot_book_generate_images.exception.UnauthorizedException;
import com.hoangkhoi.springboot_book_generate_images.model.Project;
import com.hoangkhoi.springboot_book_generate_images.repository.ProjectRepository;
import com.hoangkhoi.springboot_book_generate_images.response.ApiResponse;
import com.hoangkhoi.springboot_book_generate_images.response.SuccessMessages;
import com.hoangkhoi.springboot_book_generate_images.service.PipelineService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import java.time.Instant;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping("/api/projects/{projectId}/steps")
public class PipelineController {

    private final PipelineService pipelineService;
    private final ProjectRepository projectRepository;

    public PipelineController(PipelineService pipelineService,
                              ProjectRepository projectRepository) {
        this.pipelineService = pipelineService;
        this.projectRepository = projectRepository;
    }

    // Returns as soon as the step is claimed — the work continues in the background
    @PostMapping("/{step}/run")
    @Operation(summary = "Run one pipeline step")
    public ResponseEntity<ApiResponse<ProjectDetail>> runStep(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @PathVariable String projectId,
            @PathVariable Step step,
            @Valid @RequestBody(required = false) RunStepRequest request) {
        String user = requireUser(userId);
        String style = request == null ? null : request.styleOrNull();
        Project project = pipelineService.run(user, projectId, step, style);

        return ResponseEntity.ok(ApiResponse.ok(
                String.format(SuccessMessages.RUN_STEP_SUCCESS, step),
                ProjectDetail.of(project, Instant.now())));
    }

    @PostMapping("/{step}/reset")
    @Operation(summary = "Release a step stranded by a restart so it can be retried")
    public ResponseEntity<ApiResponse<ProjectDetail>> resetStep(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @PathVariable String projectId,
            @PathVariable Step step) {
        Project project = pipelineService.reset(requireUser(userId), projectId);

        return ResponseEntity.ok(ApiResponse.ok(
                String.format(SuccessMessages.RESET_STEP_SUCCESS, step),
                ProjectDetail.of(project, Instant.now())));
    }

    private String requireUser(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new UnauthorizedException();
        }
        return projectRepository.findUser(userId).orElseThrow(UnauthorizedException::new).getId();
    }
}
