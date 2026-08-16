package com.hoangkhoi.springboot_book_generate_images.service;

import com.hoangkhoi.springboot_book_generate_images.enums.RunRejection;
import com.hoangkhoi.springboot_book_generate_images.enums.Step;
import com.hoangkhoi.springboot_book_generate_images.enums.StepState;
import com.hoangkhoi.springboot_book_generate_images.exception.ProjectNotFoundException;
import com.hoangkhoi.springboot_book_generate_images.exception.StepRejectedException;
import com.hoangkhoi.springboot_book_generate_images.model.IllustratedItem;
import com.hoangkhoi.springboot_book_generate_images.model.Project;
import com.hoangkhoi.springboot_book_generate_images.repository.ProjectRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

/**
 * Runs one pipeline step at a time, on the server, without ever running it twice.
 */
@Service
public class PipelineService {

    private static final Logger log = LoggerFactory.getLogger(PipelineService.class);

    private final ProjectRepository repository;
    private final Executor executor;

    public PipelineService(ProjectRepository repository,
                           Executor pipelineExecutor) {
        this.repository = repository;
        this.executor = pipelineExecutor;
    }

    public Project run(String userId, String projectId, Step step, String requestedStyle) {
        // Claim and start are one atomic act under the lock. Everything downstream — the second
        // tab, the double-click, the impatient refresh — loses here rather than at Gemini.
        StepRejectedException rejection = repository.update(userId, projectId, project -> {
            Instant now = Instant.now();
            Optional<RunRejection> reason = PipelineRules.checkRun(project, step, now);
            if (reason.isEmpty()) {
                project.startStep(now);
                return null;
            }
            // Built here, thrown outside: the project is only readable while the lock is held.
            return new StepRejectedException(
                    reason.get(), PipelineRules.currentStep(project.getStatus()).orElse(null));
        });
        if (rejection != null) {
            throw rejection;
        }

        executor.execute(() -> execute(userId, projectId, step, requestedStyle));
        return repository.find(userId, projectId).orElseThrow();
    }

    private void execute(String userId, String projectId, Step step, String requestedStyle) {
        try {
            switch (step) {
                case STYLE -> runStyle(userId, projectId, requestedStyle);
                // case CHARACTERS -> runCharacters();
                // case CHAPTERS -> runChapters();
                // case PORTRAITS, ILLUSTRATIONS -> runImages();
            }
            repository.update(userId, projectId, project -> {
                project.completeStep(step);
                return null;
            });
        } catch (RuntimeException e) {
            log.warn("Step {} failed for project {}", step, projectId, e);
        }
    }

    private void runStyle(String userId, String projectId, String requestedStyle) {
        String style = requestedStyle;
        repository.update(userId, projectId, project -> {
            project.setStyle(style);
            return null;
        });
    }
}
