package com.hoangkhoi.springboot_book_generate_images.controller;

import com.hoangkhoi.springboot_book_generate_images.enums.RunRejection;
import com.hoangkhoi.springboot_book_generate_images.enums.Step;
import com.hoangkhoi.springboot_book_generate_images.exception.StepRejectedException;
import com.hoangkhoi.springboot_book_generate_images.model.Project;
import com.hoangkhoi.springboot_book_generate_images.model.User;
import com.hoangkhoi.springboot_book_generate_images.repository.ProjectRepository;
import com.hoangkhoi.springboot_book_generate_images.service.PipelineService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** How a refused or accepted run reaches the client. The pipeline itself is mocked. */
@WebMvcTest(PipelineController.class)
class PipelineControllerTest {

    private static final String USER_ID = User.idFor("testa@gmail.com");
    private static final Instant CREATED = Instant.parse("2026-08-15T10:00:00Z");

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private PipelineService pipeline;

    @MockitoBean
    private ProjectRepository repository;

    @BeforeEach
    void signedIn() {
        when(repository.findUser(USER_ID))
                .thenReturn(Optional.of(User.of("Test A Name", "testa@gmail.com")));
    }

    @Test
    void runningAStepReturnsTheClaimedState() throws Exception {
        Project claimed = Project.create("tlv2u592", "Hello A", CREATED);
        claimed.startStep(CREATED);
        when(pipeline.run(USER_ID, "tlv2u592", Step.STYLE, null)).thenReturn(claimed);

        mvc.perform(post("/api/projects/tlv2u592/steps/STYLE/run").header("X-User-Id", USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.stepState").value("RUNNING"))
                .andExpect(jsonPath("$.data.currentStep").value("STYLE"));
    }

    @Test
    void anOptionalStyleIsPassedThroughForStepOne() throws Exception {
        when(pipeline.run(any(), any(), any(), any()))
                .thenReturn(Project.create("tlv2u592", "Hello A", CREATED));

        mvc.perform(post("/api/projects/tlv2u592/steps/STYLE/run")
                        .header("X-User-Id", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"style":"  Bold woodcut prints  "}"""))
                .andExpect(status().isOk());

        verify(pipeline).run(USER_ID, "tlv2u592", Step.STYLE, "Bold woodcut prints");
    }

    /** A blank style means "let Gemini choose", not "the style is an empty string". */
    @Test
    void aBlankStyleIsTreatedAsNoStyle() throws Exception {
        when(pipeline.run(any(), any(), any(), any()))
                .thenReturn(Project.create("tlv2u592", "Hello A", CREATED));

        mvc.perform(post("/api/projects/tlv2u592/steps/STYLE/run")
                        .header("X-User-Id", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"style":"   "}"""))
                .andExpect(status().isOk());

        verify(pipeline).run(eq(USER_ID), eq("tlv2u592"), eq(Step.STYLE), isNull());
    }

    /** What the second tab sees: a 409 naming the reason, not a second Gemini call. */
    @Test
    void aStepAlreadyRunningIsRefusedWithItsReason() throws Exception {
        when(pipeline.run(any(), any(), any(), any()))
                .thenThrow(new StepRejectedException(RunRejection.ALREADY_RUNNING, Step.PORTRAITS));

        mvc.perform(post("/api/projects/tlv2u592/steps/PORTRAITS/run").header("X-User-Id", USER_ID))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.data.code").value("ALREADY_RUNNING"))
                .andExpect(jsonPath("$.data.currentStep").value("PORTRAITS"));
    }

    /** The refusal names the step to run instead, so the client needn't poll to find out. */
    @Test
    void anOutOfOrderStepIsRefusedAndNamesTheStepToRun() throws Exception {
        when(pipeline.run(any(), any(), any(), any()))
                .thenThrow(new StepRejectedException(RunRejection.OUT_OF_ORDER, Step.CHARACTERS));

        mvc.perform(post("/api/projects/tlv2u592/steps/ILLUSTRATIONS/run")
                        .header("X-User-Id", USER_ID))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.data.code").value("OUT_OF_ORDER"))
                .andExpect(jsonPath("$.data.currentStep").value("CHARACTERS"))
                .andExpect(jsonPath("$.message").value("Run CHARACTERS first."));
    }

    /** Nothing is left to run once the pipeline is done, so the field is absent rather than null. */
    @Test
    void aCompletedPipelineReportsNoCurrentStep() throws Exception {
        when(pipeline.run(any(), any(), any(), any()))
                .thenThrow(new StepRejectedException(RunRejection.PIPELINE_COMPLETE, null));

        mvc.perform(post("/api/projects/tlv2u592/steps/STYLE/run").header("X-User-Id", USER_ID))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.data.code").value("PIPELINE_COMPLETE"))
                .andExpect(jsonPath("$.data.currentStep").doesNotExist());
    }

    /** The other error shapes stay clean — no stray null field on a 401 or 404. */
    @Test
    void errorsThatAreNotStepRejectionsOmitTheField() throws Exception {
        mvc.perform(post("/api/projects/tlv2u592/steps/STYLE/run"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.data.currentStep").doesNotExist());
    }

    @Test
    void anUnknownStepNameIsARequestError() throws Exception {
        mvc.perform(post("/api/projects/tlv2u592/steps/DANCING/run").header("X-User-Id", USER_ID))
                .andExpect(status().is4xxClientError());

        verify(pipeline, never()).run(any(), any(), any(), any());
    }

    @Test
    void resettingAStrandedStepReturnsItReadyToRetry() throws Exception {
        Project reset = Project.create("tlv2u592", "Hello A", CREATED);
        reset.failStep("This step was interrupted and never finished.");
        when(pipeline.reset(USER_ID, "tlv2u592")).thenReturn(reset);

        mvc.perform(post("/api/projects/tlv2u592/steps/STYLE/reset").header("X-User-Id", USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.stepState").value("FAILED"))
                .andExpect(jsonPath("$.data.stepError").isNotEmpty());
    }

    @Test
    void withoutTheUserHeaderNoStepCanBeStarted() throws Exception {
        mvc.perform(post("/api/projects/tlv2u592/steps/STYLE/run"))
                .andExpect(status().isUnauthorized());

        Mockito.verifyNoInteractions(pipeline);
    }
}
