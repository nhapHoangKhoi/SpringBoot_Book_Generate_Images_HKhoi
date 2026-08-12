package com.hoangkhoi.springboot_book_generate_images.domain;

import java.util.List;
import java.util.Objects;

public final class PipelineState {

    private PipelineState() {
    }

    public static GenerationState derivePortraitsStatus(
            List<ItemState> characterItems
    ) {
        Objects.requireNonNull(characterItems, "Character items must not be null");

        boolean hasFailed = characterItems.stream()
                .anyMatch(item -> item.getState() == GenerationState.FAILED);

        if (hasFailed) {
            return GenerationState.FAILED;
        }

        boolean hasRunning = characterItems.stream()
                .anyMatch(item -> item.getState() == GenerationState.RUNNING);

        if (hasRunning) {
            return GenerationState.RUNNING;
        }

        boolean hasPending = characterItems.stream()
                .anyMatch(item -> item.getState() == GenerationState.PENDING);

        if (hasPending) {
            /*
             * This case only becomes RUNNING when at least one item
             * has already completed.
             *
             * The finalized model does not define zero-character
             * behavior, so we do not handle an empty list here.
             */
            boolean hasCompleted = characterItems.stream()
                    .anyMatch(item -> item.getState() == GenerationState.COMPLETED);

            return hasCompleted
                    ? GenerationState.RUNNING
                    : GenerationState.PENDING;
        }

        return GenerationState.COMPLETED;
    }

    public static GenerationState deriveIllustrationsStatus(
            ItemState chapterItem
    ) {
        Objects.requireNonNull(chapterItem, "Chapter item must not be null");

        return chapterItem.getState();
    }
}