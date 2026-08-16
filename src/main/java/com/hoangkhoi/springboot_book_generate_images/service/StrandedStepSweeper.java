package com.hoangkhoi.springboot_book_generate_images.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Releases steps stranded by the last shutdown, once, at startup.
 */
@Component
public class StrandedStepSweeper implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(StrandedStepSweeper.class);

    private final PipelineService pipeline;

    public StrandedStepSweeper(PipelineService pipeline) {
        this.pipeline = pipeline;
    }

    @Override
    public void run(ApplicationArguments args) {
        int released = pipeline.releaseStrandedSteps();
        if (released > 0) {
            log.info("Released {} step(s) left running by the previous shutdown", released);
        }
    }
}
