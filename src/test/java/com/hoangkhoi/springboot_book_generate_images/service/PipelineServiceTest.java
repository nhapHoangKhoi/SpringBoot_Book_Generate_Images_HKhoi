package com.hoangkhoi.springboot_book_generate_images.service;

import com.hoangkhoi.springboot_book_generate_images.enums.*;
import com.hoangkhoi.springboot_book_generate_images.exception.StepRejectedException;
import com.hoangkhoi.springboot_book_generate_images.model.IllustratedItem;
import com.hoangkhoi.springboot_book_generate_images.model.Project;
import com.hoangkhoi.springboot_book_generate_images.model.User;
import com.hoangkhoi.springboot_book_generate_images.repository.JsonStore;
import com.hoangkhoi.springboot_book_generate_images.repository.ProjectLocks;
import com.hoangkhoi.springboot_book_generate_images.repository.ProjectRepository;
import com.hoangkhoi.springboot_book_generate_images.service.FakeGeminiClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The rules that matter: order, exactly-once execution, retry, and cost.
 *
 * Most tests run steps on a same-thread executor so the assertion follows the work without
 * sleeping. The two that need a step to still be in flight use a real pool and a latch.
 */
class PipelineServiceTest {

    private static final String BOOK = "I.\nTHE RIVER BANK\n\nThe Mole had been working hard.";

    @TempDir
    Path dataDir;

    private ProjectRepository repository;
    private FakeGeminiClient gemini;
    private PipelineService pipeline;
    private String userId;
    private String projectId;
    private ExecutorService pool;

    @BeforeEach
    void setUp() {
        repository = new ProjectRepository(new JsonStore(), new ProjectLocks(), dataDir);
        gemini = new FakeGeminiClient();
        userId = repository.saveUser(User.of("Test A", "testa@gmail.com")).getId();
        projectId = repository.create(userId, "Hello A", BOOK).getId();
        // Runs steps on the calling thread, so a returned run() is a finished run().
        pipeline = new PipelineService(repository, gemini, Runnable::run);
    }

    @AfterEach
    void tearDown() {
        if (pool != null) {
            pool.shutdownNow();
        }
    }

    private Project reload() {
        return repository.find(userId, projectId).orElseThrow();
    }

    private void runAll() {
        for (Step step : Step.values()) {
            pipeline.run(userId, projectId, step, null);
        }
    }

    @Test
    void styleIsGeneratedFromTheBookWhenTheUserSuppliesNone() {
        pipeline.run(userId, projectId, Step.STYLE, null);

        Project p = reload();
        assertThat(p.getStyle()).contains("watercolour");
        assertThat(p.getStatus()).isEqualTo(ProjectStatus.STYLE_SET);
        assertThat(p.getStepState()).isEqualTo(StepState.IDLE);
    }

    /** A style the user typed is theirs — asking Gemini to invent one anyway would be a wasted call. */
    @Test
    void aUserSuppliedStyleIsUsedVerbatimAndCostsNoCall() {
        pipeline.run(userId, projectId, Step.STYLE, "Bold woodcut prints, two colours");

        assertThat(reload().getStyle()).isEqualTo("Bold woodcut prints, two colours");
        assertThat(gemini.openContextCalls()).isZero();
    }

    /** §4.3: the book goes to Gemini once and is referenced afterwards, never re-sent. */
    @Test
    void theBookIsSentToGeminiExactlyOnceAcrossTheWholePipeline() {
        runAll();

        assertThat(reload().getStatus()).isEqualTo(ProjectStatus.DONE);
        assertThat(gemini.openContextCalls()).isEqualTo(1);
        assertThat(reload().getGeminiContextRef()).isNotBlank();
    }

    /** The refusal reports the step actually waiting, so a confused client can self-correct. */
    @Test
    void aStepCannotRunBeforeItsPredecessorsAndTheRefusalNamesTheRealOne() {
        assertThatThrownBy(() -> pipeline.run(userId, projectId, Step.PORTRAITS, null))
                .isInstanceOf(StepRejectedException.class)
                .satisfies(e -> {
                    StepRejectedException rejected = (StepRejectedException) e;
                    assertThat(rejected.getRejection()).isEqualTo(RunRejection.OUT_OF_ORDER);
                    assertThat(rejected.getCurrentStep()).isEqualTo(Step.STYLE);
                });

        assertThat(reload().getStepState()).isEqualTo(StepState.IDLE);
    }

    @Test
    void aFinishedPipelineRefusesEverythingAndHasNoCurrentStep() {
        runAll();

        assertThatThrownBy(() -> pipeline.run(userId, projectId, Step.ILLUSTRATIONS, null))
                .isInstanceOf(StepRejectedException.class)
                .satisfies(e -> {
                    StepRejectedException rejected = (StepRejectedException) e;
                    assertThat(rejected.getRejection()).isEqualTo(RunRejection.PIPELINE_COMPLETE);
                    assertThat(rejected.getCurrentStep()).isNull();
                });
    }

    /**
     * The headline requirement: a second tab, or a double-click, must not buy a second image.
     * The fake blocks so the first run is genuinely still in flight when the second arrives.
     */
    @Test
    void aSecondRequestWhileAStepRunsDoesNotCallGeminiAgain() throws Exception {
        pipeline.run(userId, projectId, Step.STYLE, "Bold woodcut prints");
        pipeline.run(userId, projectId, Step.CHARACTERS, null);
        useBackgroundPool();
        CountDownLatch release = new CountDownLatch(1);
        gemini.blockImagesUntil(release);

        pipeline.run(userId, projectId, Step.PORTRAITS, null);
        assertThatThrownBy(() -> pipeline.run(userId, projectId, Step.PORTRAITS, null))
                .isInstanceOf(StepRejectedException.class)
                .extracting(e -> ((StepRejectedException) e).getRejection())
                .isEqualTo(RunRejection.ALREADY_RUNNING);

        assertThat(reload().getStepState()).isEqualTo(StepState.RUNNING);
        release.countDown();
        waitUntilIdle();
        assertThat(gemini.imageCalls()).isEqualTo(2);
    }

    @Test
    void aRunningStepIsVisibleAsRunningWithItsStartTime() throws Exception {
        pipeline.run(userId, projectId, Step.STYLE, "Bold woodcut prints");
        pipeline.run(userId, projectId, Step.CHARACTERS, null);
        useBackgroundPool();
        CountDownLatch release = new CountDownLatch(1);
        gemini.blockImagesUntil(release);

        pipeline.run(userId, projectId, Step.PORTRAITS, null);

        Project running = reload();
        assertThat(running.getStepState()).isEqualTo(StepState.RUNNING);
        assertThat(running.getStepStartedAt()).isNotNull();
        assertThat(PipelineRules.currentStep(running.getStatus())).contains(Step.PORTRAITS);
        release.countDown();
        waitUntilIdle();
    }

    @Test
    void aFailedStepKeepsEarlierResultsAndCanBeRetried() {
        pipeline.run(userId, projectId, Step.STYLE, "Bold woodcut prints");
        gemini.failWith(new IllegalStateException("Gemini returned 503"));

        pipeline.run(userId, projectId, Step.CHARACTERS, null);

        Project failed = reload();
        assertThat(failed.getStepState()).isEqualTo(StepState.FAILED);
        assertThat(failed.getStepError()).contains("503");
        assertThat(failed.getStatus()).isEqualTo(ProjectStatus.STYLE_SET);
        assertThat(failed.getStyle()).isEqualTo("Bold woodcut prints");

        gemini.failWith(null);
        pipeline.run(userId, projectId, Step.CHARACTERS, null);

        assertThat(reload().getStatus()).isEqualTo(ProjectStatus.CHARACTERS_GENERATED);
    }

    /** Retrying portraits must not pay for the one that already worked. */
    @Test
    void retryingPortraitsOnlyRedrawsTheOneThatFailed() {
        pipeline.run(userId, projectId, Step.STYLE, "Bold woodcut prints");
        pipeline.run(userId, projectId, Step.CHARACTERS, null);
        gemini.failImageFor("portrait prompt 1", new IllegalStateException("safety filter"));

        pipeline.run(userId, projectId, Step.PORTRAITS, null);

        Project afterFailure = reload();
        assertThat(afterFailure.getStepState()).isEqualTo(StepState.FAILED);
        assertThat(afterFailure.getCharacters().get(0).getImageState()).isEqualTo(ItemState.DONE);
        assertThat(afterFailure.getCharacters().get(1).getImageState()).isEqualTo(ItemState.FAILED);
        assertThat(gemini.imageCalls()).isEqualTo(2);

        gemini.failImageFor(null, null);
        pipeline.run(userId, projectId, Step.PORTRAITS, null);

        assertThat(reload().getStatus()).isEqualTo(ProjectStatus.PORTRAITS_GENERATED);
        assertThat(gemini.imageCalls()).isEqualTo(3);
    }

    /** §03 caps, enforced on the server: a chatty model does not get to cost extra images. */
    @Test
    void moreCharactersThanTheCapAreTrimmedBeforeAnyPortraitIsDrawn() {
        gemini.returnCharacters(5);
        pipeline.run(userId, projectId, Step.STYLE, "Bold woodcut prints");

        pipeline.run(userId, projectId, Step.CHARACTERS, null);
        pipeline.run(userId, projectId, Step.PORTRAITS, null);

        assertThat(reload().getCharacters()).hasSize(PipelineRules.MAX_CHARACTERS);
        assertThat(gemini.imageCalls()).isEqualTo(PipelineRules.MAX_CHARACTERS);
    }

    @Test
    void moreChaptersThanTheCapAreTrimmed() {
        gemini.returnChapters(4);
        pipeline.run(userId, projectId, Step.STYLE, "Bold woodcut prints");
        pipeline.run(userId, projectId, Step.CHARACTERS, null);
        pipeline.run(userId, projectId, Step.PORTRAITS, null);

        pipeline.run(userId, projectId, Step.CHAPTERS, null);

        assertThat(reload().getChapters()).hasSize(PipelineRules.MAX_CHAPTERS);
    }

    /**
     * Step 5 must continue the very conversation that drew the portraits, or characters drift
     * between pictures. Each call has to start from the id the previous call returned.
     */
    @Test
    void illustrationsContinueTheConversationThatDrewThePortraits() {
        runAll();

        assertThat(gemini.imageChain()).containsExactly("img-root", "img-1", "img-2");
        assertThat(reload().getGeminiImageContextRef()).isEqualTo("img-3");
    }

    /** The chain survives a restart, so a retried step 5 still remembers the portraits. */
    @Test
    void theImageConversationIsPersistedAfterEveryPicture() {
        pipeline.run(userId, projectId, Step.STYLE, "Bold woodcut prints");
        pipeline.run(userId, projectId, Step.CHARACTERS, null);

        pipeline.run(userId, projectId, Step.PORTRAITS, null);

        assertThat(reload().getGeminiImageContextRef()).isEqualTo("img-2");
    }

    @Test
    void generatedImagesAreOnDiskAndRecordedOnTheirItem() {
        runAll();

        Project done = reload();
        for (IllustratedItem character : done.getCharacters()) {
            assertThat(character.getImageFile()).isNotBlank();
            assertThat(repository.findImage(userId, projectId, character.getImageFile()))
                    .isPresent();
        }
        assertThat(done.getChapters().get(0).getImageFile()).isNotBlank();
    }

    @Test
    void aStepRunningNormallyCannotBeReset() throws Exception {
        pipeline.run(userId, projectId, Step.STYLE, "Bold woodcut prints");
        pipeline.run(userId, projectId, Step.CHARACTERS, null);
        useBackgroundPool();
        CountDownLatch release = new CountDownLatch(1);
        gemini.blockImagesUntil(release);
        pipeline.run(userId, projectId, Step.PORTRAITS, null);

        assertThatThrownBy(() -> pipeline.reset(userId, projectId))
                .isInstanceOf(StepRejectedException.class);

        release.countDown();
        waitUntilIdle();
    }

    @Test
    void aStrandedStepCanBeResetAndRetried() {
        strandTheCurrentStep();

        Project reset = pipeline.reset(userId, projectId);

        assertThat(reset.getStepState()).isEqualTo(StepState.FAILED);
        assertThat(reset.getStepError()).isNotBlank();
        assertThat(pipeline.run(userId, projectId, Step.STYLE, "Bold woodcut prints")).isNotNull();
        assertThat(reload().getStatus()).isEqualTo(ProjectStatus.STYLE_SET);
    }

    /** §4.3: a restart must not leave a project spinning forever with no way out. */
    @Test
    void startupReleasesStepsStrandedByARestart() {
        strandTheCurrentStep();

        int released = pipeline.releaseStrandedSteps();

        assertThat(released).isEqualTo(1);
        Project p = reload();
        assertThat(p.getStepState()).isEqualTo(StepState.FAILED);
        assertThat(p.getStepError()).containsIgnoringCase("restart");
    }

    @Test
    void startupLeavesHealthyProjectsAlone() {
        pipeline.run(userId, projectId, Step.STYLE, "Bold woodcut prints");

        assertThat(pipeline.releaseStrandedSteps()).isZero();
        assertThat(reload().getStepState()).isEqualTo(StepState.IDLE);
    }

    /** Mimics a process that died mid-step: the flag is set, but nothing is going to finish it. */
    private void strandTheCurrentStep() {
        repository.update(userId, projectId, p -> {
            p.startStep(Instant.now().minus(PipelineRules.STALE_AFTER).minusSeconds(60));
            return null;
        });
    }

    /**
     * Moves the step under test onto a real thread pool. Setup steps stay on the direct executor —
     * queueing them would race, since each needs its predecessor already finished.
     */
    private void useBackgroundPool() {
        pool = Executors.newFixedThreadPool(2);
        pipeline = new PipelineService(repository, gemini, pool);
    }

    private void waitUntilIdle() throws InterruptedException {
        for (int i = 0; i < 100 && reload().getStepState() == StepState.RUNNING; i++) {
            TimeUnit.MILLISECONDS.sleep(50);
        }
    }
}
