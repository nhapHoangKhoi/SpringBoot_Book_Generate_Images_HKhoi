package com.hoangkhoi.springboot_book_generate_images.domain;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class PipelineStateTest {

    @Test
    void portraitsPendingPendingIsPending() {
        assertEquals(
                GenerationState.PENDING,
                PipelineState.derivePortraitsStatus(
                        items(
                                GenerationState.PENDING,
                                GenerationState.PENDING
                        )
                )
        );
    }

    @Test
    void portraitsCompletedPendingIsRunning() {
        assertEquals(
                GenerationState.RUNNING,
                PipelineState.derivePortraitsStatus(
                        items(
                                GenerationState.COMPLETED,
                                GenerationState.PENDING
                        )
                )
        );
    }

    @Test
    void portraitsRunningPendingIsRunning() {
        assertEquals(
                GenerationState.RUNNING,
                PipelineState.derivePortraitsStatus(
                        items(
                                GenerationState.RUNNING,
                                GenerationState.PENDING
                        )
                )
        );
    }

    @Test
    void portraitsCompletedRunningIsRunning() {
        assertEquals(
                GenerationState.RUNNING,
                PipelineState.derivePortraitsStatus(
                        items(
                                GenerationState.COMPLETED,
                                GenerationState.RUNNING
                        )
                )
        );
    }

    @Test
    void portraitsFailedPendingIsFailed() {
        assertEquals(
                GenerationState.FAILED,
                PipelineState.derivePortraitsStatus(
                        items(
                                GenerationState.FAILED,
                                GenerationState.PENDING
                        )
                )
        );
    }

    @Test
    void portraitsCompletedFailedIsFailed() {
        assertEquals(
                GenerationState.FAILED,
                PipelineState.derivePortraitsStatus(
                        items(
                                GenerationState.COMPLETED,
                                GenerationState.FAILED
                        )
                )
        );
    }

    @Test
    void portraitsRunningFailedIsFailed() {
        ItemState runningItem = new ItemState(GenerationState.RUNNING);
        ItemState failedItem = new ItemState(GenerationState.FAILED);

        GenerationState portraitsStatus =
                PipelineState.derivePortraitsStatus(
                        List.of(runningItem, failedItem)
                );

        assertEquals(
                GenerationState.FAILED,
                portraitsStatus
        );

        // The RUNNING item itself remains RUNNING/recoverable.
        assertEquals(
                GenerationState.RUNNING,
                runningItem.getState()
        );
    }

    @Test
    void portraitsFailedFailedIsFailed() {
        assertEquals(
                GenerationState.FAILED,
                PipelineState.derivePortraitsStatus(
                        items(
                                GenerationState.FAILED,
                                GenerationState.FAILED
                        )
                )
        );
    }

    @Test
    void portraitsCompletedCompletedIsCompleted() {
        assertEquals(
                GenerationState.COMPLETED,
                PipelineState.derivePortraitsStatus(
                        items(
                                GenerationState.COMPLETED,
                                GenerationState.COMPLETED
                        )
                )
        );
    }

    @Test
    void illustrationsPendingIsPending() {
        assertEquals(
                GenerationState.PENDING,
                PipelineState.deriveIllustrationsStatus(
                        new ItemState(GenerationState.PENDING)
                )
        );
    }

    @Test
    void illustrationsRunningIsRunning() {
        assertEquals(
                GenerationState.RUNNING,
                PipelineState.deriveIllustrationsStatus(
                        new ItemState(GenerationState.RUNNING)
                )
        );
    }

    @Test
    void illustrationsFailedIsFailed() {
        assertEquals(
                GenerationState.FAILED,
                PipelineState.deriveIllustrationsStatus(
                        new ItemState(GenerationState.FAILED)
                )
        );
    }

    @Test
    void illustrationsCompletedIsCompleted() {
        assertEquals(
                GenerationState.COMPLETED,
                PipelineState.deriveIllustrationsStatus(
                        new ItemState(GenerationState.COMPLETED)
                )
        );
    }

    private List<ItemState> items(
            GenerationState first,
            GenerationState second
    ) {
        return List.of(
                new ItemState(first),
                new ItemState(second)
        );
    }
}