package com.dq.example;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point of the example host.
 *
 * <p>It has a second job besides demonstrating the API: it is the first real consumer of
 * {@code dq-engine}. If wiring the engine up here were awkward, that would be a design
 * defect in the library rather than in this module.
 */
@SpringBootApplication
public class DataQualityApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(DataQualityApiApplication.class, args);
    }
}
