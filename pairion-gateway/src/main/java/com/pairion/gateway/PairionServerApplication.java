package com.pairion.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Spring Boot entry point for Pairion Server.
 *
 * <p>Launches the household-scale AI presence server on port 18789 with virtual threads enabled.
 */
@SpringBootApplication(scanBasePackages = "com.pairion")
public class PairionServerApplication {

    /**
     * Starts the Pairion Server.
     *
     * @param args command-line arguments
     */
    public static void main(String[] args) {
        SpringApplication.run(PairionServerApplication.class, args);
    }
}
