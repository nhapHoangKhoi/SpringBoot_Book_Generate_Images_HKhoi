package com.hoangkhoi.springboot_book_generate_images.exception;

import com.hoangkhoi.springboot_book_generate_images.enums.RunRejection;
import com.hoangkhoi.springboot_book_generate_images.enums.Step;

/**
 * A run request the server refused. Becomes a 409 carrying the reason.
 */
public class StepRejectedException extends RuntimeException {

    private final RunRejection rejection;
    private final Step currentStep;

    public StepRejectedException(RunRejection rejection, Step currentStep) {
        super(switch (rejection) {
            case PIPELINE_COMPLETE -> "Every step of this project is already done.";
            case OUT_OF_ORDER -> currentStep == null
                    ? "Finish the earlier steps first."
                    : "Run " + currentStep + " first.";
            case ALREADY_RUNNING -> currentStep == null
                    ? "That step is already running."
                    : currentStep + " is already running.";
        });
        this.rejection = rejection;
        this.currentStep = currentStep;
    }

    public RunRejection getRejection() {
        return rejection;
    }

    // The step the project is waiting on, or null once every step is done
    public Step getCurrentStep() {
        return currentStep;
    }
}
