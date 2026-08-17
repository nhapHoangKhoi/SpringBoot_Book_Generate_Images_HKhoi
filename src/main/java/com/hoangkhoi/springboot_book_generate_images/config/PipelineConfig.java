package com.hoangkhoi.springboot_book_generate_images.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Where pipeline steps actually run. */
@Configuration
public class PipelineConfig {

    @Bean(destroyMethod = "shutdown")
    public ExecutorService pipelineExecutor() {
        return Executors.newFixedThreadPool(4);
    }
}
