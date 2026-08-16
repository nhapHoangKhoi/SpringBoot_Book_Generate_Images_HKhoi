package com.hoangkhoi.springboot_book_generate_images.service;

import com.hoangkhoi.springboot_book_generate_images.model.IllustratedItem;
import com.hoangkhoi.springboot_book_generate_images.service.PipelineRules;
import com.hoangkhoi.springboot_book_generate_images.model.Project;
import com.hoangkhoi.springboot_book_generate_images.enums.RunRejection;
import com.hoangkhoi.springboot_book_generate_images.enums.Step;
import com.hoangkhoi.springboot_book_generate_images.enums.StepState;
import com.hoangkhoi.springboot_book_generate_images.service.GeminiClient;
import com.hoangkhoi.springboot_book_generate_images.exception.GeminiException;
import com.hoangkhoi.springboot_book_generate_images.model.GeneratedItem;
import com.hoangkhoi.springboot_book_generate_images.model.ImageTurn;
import com.hoangkhoi.springboot_book_generate_images.exception.ProjectNotFoundException;
import com.hoangkhoi.springboot_book_generate_images.exception.StepRejectedException;
import com.hoangkhoi.springboot_book_generate_images.repository.ProjectRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Runs one pipeline step at a time, on the server, without ever running it twice.
 */
@Service
public class PipelineService {

    private static final Logger log = LoggerFactory.getLogger(PipelineService.class);

    private final ProjectRepository repository;
    private final GeminiClient gemini;
    private final Executor executor;

    public PipelineService(ProjectRepository repository, GeminiClient gemini,
                           Executor pipelineExecutor) {
        this.repository = repository;
        this.gemini = gemini;
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
                case CHARACTERS -> runCharacters(userId, projectId);
                case CHAPTERS -> runChapters(userId, projectId);
                case PORTRAITS, ILLUSTRATIONS -> runImages(userId, projectId, step);
            }
            repository.update(userId, projectId, project -> {
                project.completeStep(step);
                return null;
            });
        } catch (RuntimeException e) {
            log.warn("Step {} failed for project {}", step, projectId, e);
            String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            boolean contextExpired =
                    e instanceof GeminiException gemini && gemini.isContextExpired();
            repository.update(userId, projectId, project -> {
                if (contextExpired) {
                    // Forget the dead conversation so the user's next retry sends the book again.
                    // Clearing it here rather than retrying now keeps every Gemini call one
                    // deliberate press
                    project.setGeminiContextRef(null);
                }
                project.failStep(message);
                return null;
            });
        }
    }

    private void runStyle(String userId, String projectId, String requestedStyle) {
        String style = requestedStyle != null
                ? requestedStyle
                : gemini.generateStyle(contextRef(userId, projectId));
        repository.update(userId, projectId, project -> {
            project.setStyle(style);
            return null;
        });
    }

    private void runCharacters(String userId, String projectId) {
        Project project = load(userId, projectId);
        List<GeneratedItem> generated = PipelineRules.trimToCap(
                gemini.generateCharacters(contextRef(userId, projectId), project.getStyle()),
                PipelineRules.MAX_CHARACTERS);
        repository.update(userId, projectId, p -> {
            p.setCharacters(toItems(generated));
            return null;
        });
    }

    private void runChapters(String userId, String projectId) {
        Project project = load(userId, projectId);
        List<String> characterNames = project.getCharacters().stream()
                .map(IllustratedItem::getName)
                .toList();
        List<GeneratedItem> generated = PipelineRules.trimToCap(
                gemini.generateChapters(
                        contextRef(userId, projectId), project.getStyle(), characterNames),
                PipelineRules.MAX_CHAPTERS);
        repository.update(userId, projectId, p -> {
            p.setChapters(toItems(generated));
            return null;
        });
    }

    /**
     * Draws one image per item, saving each before starting the next so the UI fills in rather
     * than waiting on the whole batch. Items that already have an image are skipped, which is what
     * makes a retry cost only the pictures that failed.
     */
    private void runImages(String userId, String projectId, Step step) {
        Project project = load(userId, projectId);
        boolean portraits = step == Step.PORTRAITS;
        List<IllustratedItem> items = portraits ? project.getCharacters() : project.getChapters();
        String prefix = portraits ? "portrait-" : "illustration-";
        String imageContext = imageContextRef(userId, projectId, project.getStyle());

        for (int index = 0; index < items.size(); index++) {
            IllustratedItem item = items.get(index);
            if (item.hasImage()) {
                continue;
            }
            int position = index;
            repository.update(userId, projectId, p -> {
                itemsOf(p, portraits).get(position).startImage();
                return null;
            });
            try {
                // Chapter illustrations continue the very conversation that drew the portraits,
                // which is what keeps a character recognisable between pictures.
                String instruction = portraits
                        ? "Create an illustration for " + item.getName()
                        + " following this description: " + item.getPrompt()
                        : "Create an illustration for " + item.getName()
                        + " using the previously generated characters, keeping them "
                        + "consistent, following this description: " + item.getPrompt();
                ImageTurn turn = gemini.generateImage(imageContext, instruction);
                imageContext = turn.interactionId();
                String fileName = repository.writeImage(
                        userId, projectId, prefix + position + ".png", turn.image());
                String advanced = imageContext;
                repository.update(userId, projectId, p -> {
                    itemsOf(p, portraits).get(position).completeImage(fileName);
                    p.setGeminiImageContextRef(advanced);
                    return null;
                });
            } catch (RuntimeException e) {
                String message =
                        e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                repository.update(userId, projectId, p -> {
                    itemsOf(p, portraits).get(position).failImage(message);
                    return null;
                });
                throw e;
            }
        }
    }

    /**
     * The image conversation, opened on first use with the style and standing rules.
     *
     * Persisted after every picture, so a retry of step 5 still has the portraits in its memory
     * even if the server restarted in between.
     */
    private String imageContextRef(String userId, String projectId, String style) {
        String existing = load(userId, projectId).getGeminiImageContextRef();
        if (existing != null) {
            return existing;
        }
        String created = gemini.openImageContext(style);
        repository.update(userId, projectId, project -> {
            project.setGeminiImageContextRef(created);
            return null;
        });
        return created;
    }

    private static List<IllustratedItem> itemsOf(Project project, boolean portraits) {
        return portraits ? project.getCharacters() : project.getChapters();
    }

    private static List<IllustratedItem> toItems(List<GeneratedItem> generated) {
        return generated.stream()
                .map(g -> new IllustratedItem(g.name(), g.prompt()))
                .collect(Collectors.toCollection(ArrayList::new));
    }

    /**
     * The book's handle at Gemini, uploaded on first use and reused by every later step.
     *
     * The upload happens outside the project lock; only the resulting handle is written under it.
     */
    private String contextRef(String userId, String projectId) {
        String existing = load(userId, projectId).getGeminiContextRef();
        if (existing != null) {
            return existing;
        }
        String created = gemini.openContext(repository.readBookText(userId, projectId));
        repository.update(userId, projectId, project -> {
            project.setGeminiContextRef(created);
            return null;
        });
        return created;
    }

    private Project load(String userId, String projectId) {
        return repository.find(userId, projectId)
                .orElseThrow(() -> new ProjectNotFoundException(userId, projectId));
    }
}
