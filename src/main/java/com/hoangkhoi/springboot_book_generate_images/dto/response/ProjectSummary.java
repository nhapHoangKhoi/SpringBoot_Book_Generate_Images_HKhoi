package com.hoangkhoi.springboot_book_generate_images.dto.response;

import com.hoangkhoi.springboot_book_generate_images.enums.ProjectStatus;
import com.hoangkhoi.springboot_book_generate_images.enums.Step;
import com.hoangkhoi.springboot_book_generate_images.enums.StepState;
import com.hoangkhoi.springboot_book_generate_images.model.Project;
import com.hoangkhoi.springboot_book_generate_images.service.PipelineRules;

import java.time.Instant;


public record ProjectSummary(
        String id,
        String title,
        Instant createdAt,
        ProjectStatus status,
        StepState stepState,
        Step currentStep,
        int completedSteps) {

    public static ProjectSummary of(Project p) {
        return new ProjectSummary(
                p.getId(),
                p.getTitle(),
                p.getCreatedAt(),
                p.getStatus(),
                p.getStepState(),
                PipelineRules.currentStep(p.getStatus()).orElse(null),
                p.getStatus().completedSteps());
    }
}
