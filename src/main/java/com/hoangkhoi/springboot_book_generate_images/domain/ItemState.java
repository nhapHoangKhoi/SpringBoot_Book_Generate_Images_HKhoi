package com.hoangkhoi.springboot_book_generate_images.domain;

public final class ItemState {

    private GenerationState state;

    public ItemState() {
        this(GenerationState.PENDING);
    }

    public ItemState(GenerationState state) {
        if (state == null) {
            throw new IllegalArgumentException("State must not be null");
        }

        this.state = state;
    }

    public GenerationState getState() {
        return state;
    }

    public void start() {
        transitionTo(GenerationState.RUNNING);
    }

    public void complete() {
        transitionTo(GenerationState.COMPLETED);
    }

    public void fail() {
        transitionTo(GenerationState.FAILED);
    }

    public void retry() {
        if (state != GenerationState.FAILED) {
            throw new IllegalStateException(
                    "Only FAILED items can be retried"
            );
        }

        state = GenerationState.RUNNING;
    }

    void transitionTo(GenerationState newState) {
        if (!isValidTransition(state, newState)) {
            throw new IllegalStateException(
                    "Invalid state transition: "
                            + state + " -> " + newState
            );
        }

        state = newState;
    }

    private boolean isValidTransition(
            GenerationState current,
            GenerationState next
    ) {
        return switch (current) {
            case PENDING ->
                    next == GenerationState.RUNNING;

            case RUNNING ->
                    next == GenerationState.COMPLETED
                            || next == GenerationState.FAILED;

            case FAILED ->
                    next == GenerationState.RUNNING;

            case COMPLETED ->
                    false;
        };
    }
}