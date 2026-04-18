package com.pairion.gateway.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Checks for the {@code ANTHROPIC_API_KEY} environment variable at startup.
 *
 * <p>Logs a warning if the key is absent (non-fatal) or an info message acknowledging its presence.
 */
@Component
public class ApiKeyStartupCheck {

    private static final Logger log = LoggerFactory.getLogger(ApiKeyStartupCheck.class);

    private final Environment environment;

    /**
     * Constructs the startup check with the given Spring environment.
     *
     * @param environment the Spring environment for property resolution
     */
    public ApiKeyStartupCheck(Environment environment) {
        this.environment = environment;
    }

    /**
     * Inspects the environment for the Anthropic API key when the application is ready.
     *
     * @param event the application ready event
     */
    @EventListener(ApplicationReadyEvent.class)
    public void checkApiKey(ApplicationReadyEvent event) {
        String key = environment.getProperty("ANTHROPIC_API_KEY");
        if (key == null || key.isBlank()) {
            log.warn(
                    "ANTHROPIC_API_KEY environment variable is not set — LLM adapter will not"
                            + " function until configured");
        } else {
            log.info("ANTHROPIC_API_KEY detected — Anthropic LLM adapter available");
        }
    }
}
