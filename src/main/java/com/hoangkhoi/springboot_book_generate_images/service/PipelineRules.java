package com.hoangkhoi.springboot_book_generate_images.service;

import com.hoangkhoi.springboot_book_generate_images.enums.ProjectStatus;
import com.hoangkhoi.springboot_book_generate_images.enums.RunRejection;
import com.hoangkhoi.springboot_book_generate_images.enums.Step;
import com.hoangkhoi.springboot_book_generate_images.enums.StepState;
import com.hoangkhoi.springboot_book_generate_images.model.Project;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;


public final class PipelineRules {

    public static final int MAX_CHARACTERS = 2;
    public static final int MAX_CHAPTERS = 1;


    public static final Duration STALE_AFTER = Duration.ofMinutes(5);

    private static final List<Step> ORDER = List.of(Step.values());

    private PipelineRules() {
    }

    public static Optional<Step> currentStep(ProjectStatus status) {
        int done = status.completedSteps();
        return done < ORDER.size() ? Optional.of(ORDER.get(done)) : Optional.empty();
    }

    public static boolean isStale(Project project, Instant now) {
        if (project.getStepState() != StepState.RUNNING) {
            return false;
        }
        Instant startedAt = project.getStepStartedAt();
        return startedAt == null || Duration.between(startedAt, now).compareTo(STALE_AFTER) > 0;
    }

    public static Optional<RunRejection> checkRun(Project project, Step step, Instant now) {
        Optional<Step> current = currentStep(project.getStatus());
        if (current.isEmpty()) {
            return Optional.of(RunRejection.PIPELINE_COMPLETE);
        }
        if (current.get() != step) {
            return Optional.of(RunRejection.OUT_OF_ORDER);
        }
        if (project.getStepState() == StepState.RUNNING) {
            return Optional.of(RunRejection.ALREADY_RUNNING);
        }
        return Optional.empty();
    }

    public static boolean canReset(Project project, Instant now) {
        return isStale(project, now);
    }

    public static <T> List<T> trimToCap(List<T> items, int cap) {
        return items.size() <= cap ? items : List.copyOf(items.subList(0, cap));
    }
}
