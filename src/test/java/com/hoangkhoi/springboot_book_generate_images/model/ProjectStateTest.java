package com.hoangkhoi.springboot_book_generate_images.model;

import com.hoangkhoi.springboot_book_generate_images.enums.ItemState;
import com.hoangkhoi.springboot_book_generate_images.enums.ProjectStatus;
import com.hoangkhoi.springboot_book_generate_images.enums.Step;
import com.hoangkhoi.springboot_book_generate_images.enums.StepState;
import com.hoangkhoi.springboot_book_generate_images.service.PipelineRules;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ProjectStateTest {

    private static final Instant NOW = Instant.parse("2026-08-15T10:00:00Z");

    private static Project newProject() {
        return Project.create("p1", "The Wind in the Willows", NOW);
    }

    @Test
    void aNewProjectIsADraftWithNothingRunning() {
        Project p = newProject();

        assertThat(p.getStatus()).isEqualTo(ProjectStatus.CREATED);
        assertThat(p.getStepState()).isEqualTo(StepState.IDLE);
        assertThat(p.getStepStartedAt()).isNull();
        assertThat(p.getCharacters()).isEmpty();
    }

    @Test
    void startingAStepRecordsWhenItBegan() {
        Project p = newProject();

        p.startStep(NOW);

        assertThat(p.getStepState()).isEqualTo(StepState.RUNNING);
        assertThat(p.getStepStartedAt()).isEqualTo(NOW);
        assertThat(p.getStatus()).isEqualTo(ProjectStatus.CREATED);
    }

    @Test
    void completingAStepAdvancesProgressAndClearsLiveness() {
        Project p = newProject();
        p.startStep(NOW);

        p.completeStep(Step.STYLE);

        assertThat(p.getStatus()).isEqualTo(ProjectStatus.STYLE_SET);
        assertThat(p.getStepState()).isEqualTo(StepState.IDLE);
        assertThat(p.getStepStartedAt()).isNull();
    }

    /** The core resume guarantee: a failure costs you the step, never the work before it. */
    @Test
    void failingAStepKeepsEverythingEarlierIntact() {
        Project p = newProject();
        p.startStep(NOW);
        p.completeStep(Step.STYLE);
        p.setStyle("Warm hand-painted watercolour");
        p.startStep(NOW);
        p.completeStep(Step.CHARACTERS);
        p.setCharacters(List.of(new IllustratedItem("Mole", "a shy mole in a waistcoat")));

        p.startStep(NOW);
        p.failStep("Gemini image call timed out");

        assertThat(p.getStepState()).isEqualTo(StepState.FAILED);
        assertThat(p.getStepError()).isEqualTo("Gemini image call timed out");
        assertThat(p.getStepStartedAt()).isNull();
        assertThat(p.getStatus()).isEqualTo(ProjectStatus.CHARACTERS_GENERATED);
        assertThat(p.getStyle()).isEqualTo("Warm hand-painted watercolour");
        assertThat(p.getCharacters()).hasSize(1);
        assertThat(PipelineRules.currentStep(p.getStatus())).contains(Step.PORTRAITS);
    }

    @Test
    void retryingAfterAFailureClearsTheError() {
        Project p = newProject();
        p.failStep("boom");

        p.startStep(NOW.plusSeconds(5));

        assertThat(p.getStepState()).isEqualTo(StepState.RUNNING);
        assertThat(p.getStepError()).isNull();
    }

    @Test
    void runningEveryStepInOrderCompletesThePipeline() {
        Project p = newProject();

        for (Step step : Step.values()) {
            assertThat(PipelineRules.checkRun(p, step, NOW)).isEmpty();
            p.startStep(NOW);
            p.completeStep(step);
        }

        assertThat(p.getStatus()).isEqualTo(ProjectStatus.DONE);
        assertThat(PipelineRules.currentStep(p.getStatus())).isEmpty();
    }

    /** Per-item state is what lets a retry redraw only the portrait that failed. */
    @Test
    void oneItemFailingLeavesItsSiblingsImageAlone() {
        IllustratedItem mole = new IllustratedItem("Mole", "a shy mole in a waistcoat");
        IllustratedItem rat = new IllustratedItem("Rat", "a river rat with a picnic basket");

        mole.startImage();
        mole.completeImage("portrait-0.png");
        rat.startImage();
        rat.failImage("safety filter blocked the prompt");

        assertThat(mole.hasImage()).isTrue();
        assertThat(mole.getImageFile()).isEqualTo("portrait-0.png");
        assertThat(rat.hasImage()).isFalse();
        assertThat(rat.getImageState()).isEqualTo(ItemState.FAILED);
        assertThat(rat.getError()).isEqualTo("safety filter blocked the prompt");
    }

    @Test
    void regeneratingAFailedItemClearsItsError() {
        IllustratedItem rat = new IllustratedItem("Rat", "a river rat");
        rat.failImage("safety filter blocked the prompt");

        rat.startImage();
        rat.completeImage("portrait-1.png");

        assertThat(rat.getImageState()).isEqualTo(ItemState.DONE);
        assertThat(rat.getError()).isNull();
    }
}
