package com.hoangkhoi.springboot_book_generate_images.service;

import com.hoangkhoi.springboot_book_generate_images.enums.ProjectStatus;
import com.hoangkhoi.springboot_book_generate_images.enums.RunRejection;
import com.hoangkhoi.springboot_book_generate_images.enums.Step;
import com.hoangkhoi.springboot_book_generate_images.enums.StepState;
import com.hoangkhoi.springboot_book_generate_images.model.Project;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;


class PipelineRulesTest {

    private static final Instant NOW = Instant.parse("2026-08-15T10:00:00Z");

    private static Project at(ProjectStatus status, StepState stepState, Instant startedAt) {
        Project p = Project.create("p1", "The Wind in the Willows", NOW);
        p.setStatus(status);
        p.setStepState(stepState);
        p.setStepStartedAt(startedAt);
        return p;
    }

    private static Project idleAt(ProjectStatus status) {
        return at(status, StepState.IDLE, null);
    }

    @Nested
    class CurrentStep {

        @Test
        void newProjectStartsAtStyle() {
            assertThat(PipelineRules.currentStep(ProjectStatus.CREATED)).contains(Step.STYLE);
        }

        /** Pins the two enums together: finishing step N must make step N+1 current. */
        @Test
        void eachStepBecomesCurrentOnlyAfterItsPredecessorCompletes() {
            ProjectStatus status = ProjectStatus.CREATED;
            for (Step step : Step.values()) {
                assertThat(PipelineRules.currentStep(status)).contains(step);
                status = step.completedStatus();
            }
            assertThat(status).isEqualTo(ProjectStatus.DONE);
        }

        @Test
        void completedPipelineHasNoCurrentStep() {
            assertThat(PipelineRules.currentStep(ProjectStatus.DONE)).isEmpty();
        }
    }

    @Nested
    class CheckRun {

        @Test
        void allowsTheCurrentStepWhenIdle() {
            assertThat(PipelineRules.checkRun(idleAt(ProjectStatus.STYLE_SET), Step.CHARACTERS, NOW))
                    .isEmpty();
        }

        /** Retry after a failure is the same call as the first attempt — no reset needed. */
        @Test
        void allowsTheCurrentStepAfterItFailed() {
            Project p = at(ProjectStatus.STYLE_SET, StepState.FAILED, null);

            assertThat(PipelineRules.checkRun(p, Step.CHARACTERS, NOW)).isEmpty();
        }

        @Test
        void rejectsAStepThatAlreadyCompleted() {
            assertThat(PipelineRules.checkRun(idleAt(ProjectStatus.STYLE_SET), Step.STYLE, NOW))
                    .contains(RunRejection.OUT_OF_ORDER);
        }

        @Test
        void rejectsAStepWhosePredecessorsHaveNotRun() {
            assertThat(PipelineRules.checkRun(idleAt(ProjectStatus.CREATED), Step.PORTRAITS, NOW))
                    .contains(RunRejection.OUT_OF_ORDER);
        }

        /** The double-click / second-tab case: the second call must not reach Gemini. */
        @Test
        void rejectsTheCurrentStepWhileItIsRunning() {
            Project p = at(ProjectStatus.STYLE_SET, StepState.RUNNING, NOW);

            assertThat(PipelineRules.checkRun(p, Step.CHARACTERS, NOW))
                    .contains(RunRejection.ALREADY_RUNNING);
        }

        /** A stale step needs an explicit reset first, so a slow-but-alive call is never doubled. */
        @Test
        void rejectsTheCurrentStepWhileItIsStale() {
            Project p = at(ProjectStatus.STYLE_SET, StepState.RUNNING, NOW);
            Instant later = NOW.plus(PipelineRules.STALE_AFTER).plusSeconds(1);

            assertThat(PipelineRules.checkRun(p, Step.CHARACTERS, later))
                    .contains(RunRejection.ALREADY_RUNNING);
        }

        @Test
        void rejectsEveryStepOnceThePipelineIsComplete() {
            for (Step step : Step.values()) {
                assertThat(PipelineRules.checkRun(idleAt(ProjectStatus.DONE), step, NOW))
                        .contains(RunRejection.PIPELINE_COMPLETE);
            }
        }
    }

    @Nested
    class Staleness {

        @Test
        void anIdleStepIsNeverStale() {
            assertThat(PipelineRules.isStale(idleAt(ProjectStatus.CREATED), NOW.plusSeconds(86_400)))
                    .isFalse();
        }

        @Test
        void aLongRunningImageStepIsNotStaleYet() {
            Project p = at(ProjectStatus.CHARACTERS_GENERATED, StepState.RUNNING, NOW);

            assertThat(PipelineRules.isStale(p, NOW.plusSeconds(90))).isFalse();
        }

        @Test
        void becomesStaleOnlyPastTheThreshold() {
            Project p = at(ProjectStatus.CHARACTERS_GENERATED, StepState.RUNNING, NOW);

            assertThat(PipelineRules.isStale(p, NOW.plus(PipelineRules.STALE_AFTER))).isFalse();
            assertThat(PipelineRules.isStale(p, NOW.plus(PipelineRules.STALE_AFTER).plusSeconds(1))).isTrue();
        }

        /** Corrupt state must stay recoverable rather than stranding the project forever. */
        @Test
        void runningWithNoStartTimeIsStale() {
            Project p = at(ProjectStatus.CREATED, StepState.RUNNING, null);

            assertThat(PipelineRules.isStale(p, NOW)).isTrue();
        }

        @Test
        void onlyAStrandedStepCanBeReset() {
            Project running = at(ProjectStatus.CREATED, StepState.RUNNING, NOW);

            assertThat(PipelineRules.canReset(running, NOW.plusSeconds(30))).isFalse();
            assertThat(PipelineRules.canReset(running, NOW.plus(PipelineRules.STALE_AFTER).plusSeconds(1)))
                    .isTrue();
            assertThat(PipelineRules.canReset(idleAt(ProjectStatus.CREATED), NOW)).isFalse();
        }
    }

    @Nested
    class Caps {

        @Test
        void trimsAnOverlongCharacterListToTheCap() {
            assertThat(PipelineRules.trimToCap(List.of("Mole", "Rat", "Toad"), PipelineRules.MAX_CHARACTERS))
                    .containsExactly("Mole", "Rat");
        }

        @Test
        void trimsAnOverlongChapterListToTheCap() {
            assertThat(PipelineRules.trimToCap(
                            List.of("The River Bank", "The Open Road"), PipelineRules.MAX_CHAPTERS))
                    .containsExactly("The River Bank");
        }

        @Test
        void leavesAListAlreadyWithinTheCapAlone() {
            assertThat(PipelineRules.trimToCap(List.of("Mole"), PipelineRules.MAX_CHARACTERS))
                    .containsExactly("Mole");
            assertThat(PipelineRules.<String>trimToCap(List.of(), PipelineRules.MAX_CHARACTERS)).isEmpty();
        }
    }
}
