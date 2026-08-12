package com.hoangkhoi.springboot_book_generate_images.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ItemStateTest {

    @Test
    void pendingCanStart() {
        ItemState item = new ItemState(GenerationState.PENDING);

        item.start();

        assertEquals(GenerationState.RUNNING, item.getState());
    }

    @Test
    void runningCanComplete() {
        ItemState item = new ItemState(GenerationState.RUNNING);

        item.complete();

        assertEquals(GenerationState.COMPLETED, item.getState());
    }

    @Test
    void runningCanFail() {
        ItemState item = new ItemState(GenerationState.RUNNING);

        item.fail();

        assertEquals(GenerationState.FAILED, item.getState());
    }

    @Test
    void failedCanBeRetried() {
        ItemState item = new ItemState(GenerationState.FAILED);

        item.retry();

        assertEquals(GenerationState.RUNNING, item.getState());
    }

    @Test
    void completedCannotBeRetried() {
        ItemState item = new ItemState(GenerationState.COMPLETED);

        assertThrows(
                IllegalStateException.class,
                item::retry
        );
    }

    @Test
    void pendingCannotBeCompletedDirectly() {
        ItemState item = new ItemState(GenerationState.PENDING);

        assertThrows(
                IllegalStateException.class,
                item::complete
        );
    }
}