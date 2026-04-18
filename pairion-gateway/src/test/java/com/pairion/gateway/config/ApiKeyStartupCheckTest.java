package com.pairion.gateway.config;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.core.env.Environment;

/** Tests for {@link ApiKeyStartupCheck}. */
class ApiKeyStartupCheckTest {

    private final ApplicationReadyEvent event = mock(ApplicationReadyEvent.class);

    @Test
    void checkApiKeyWhenAbsent() {
        Environment env = mock(Environment.class);
        when(env.getProperty("ANTHROPIC_API_KEY")).thenReturn(null);
        ApiKeyStartupCheck check = new ApiKeyStartupCheck(env);
        check.checkApiKey(event);
    }

    @Test
    void checkApiKeyWhenBlank() {
        Environment env = mock(Environment.class);
        when(env.getProperty("ANTHROPIC_API_KEY")).thenReturn("  ");
        ApiKeyStartupCheck check = new ApiKeyStartupCheck(env);
        check.checkApiKey(event);
    }

    @Test
    void checkApiKeyWhenPresent() {
        Environment env = mock(Environment.class);
        when(env.getProperty("ANTHROPIC_API_KEY")).thenReturn("sk-ant-test-key");
        ApiKeyStartupCheck check = new ApiKeyStartupCheck(env);
        check.checkApiKey(event);
    }
}
