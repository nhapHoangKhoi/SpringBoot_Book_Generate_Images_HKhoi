package com.hoangkhoi.springboot_book_generate_images.dto.response;

import com.hoangkhoi.springboot_book_generate_images.enums.ProjectStatus;
import com.hoangkhoi.springboot_book_generate_images.enums.Step;
import com.hoangkhoi.springboot_book_generate_images.enums.StepState;
import com.hoangkhoi.springboot_book_generate_images.model.Project;
import com.hoangkhoi.springboot_book_generate_images.service.PipelineRules;

import java.time.Instant;
import java.util.List;

/**
 * Everything the project screen needs except the book text, which has its own endpoint because
 * this one is polled every couple of seconds while a step runs.
 *
 * <p>{@code stale} is computed here rather than stored: whether a step has been running too long
 * depends on the time of the request.
 */
public record ProjectDetail(
        String id,
        String title,
        Instant createdAt,
        ProjectStatus status,
        StepState stepState,
        Step currentStep,
        int completedSteps,
        boolean stale,
        Instant stepStartedAt,
        String stepError,
        String style,
        List<ItemView> characters,
        List<ItemView> chapters) {

    public static ProjectDetail of(Project p, Instant now) {
        return new ProjectDetail(
                p.getId(),
                p.getTitle(),
                p.getCreatedAt(),
                p.getStatus(),
                p.getStepState(),
                PipelineRules.currentStep(p.getStatus()).orElse(null),
                p.getStatus().completedSteps(),
                PipelineRules.isStale(p, now),
                p.getStepStartedAt(),
                p.getStepError(),
                p.getStyle(),
                p.getCharacters().stream().map(ItemView::of).toList(),
                p.getChapters().stream().map(ItemView::of).toList());
    }
}
