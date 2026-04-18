package com.pairion.gateway.config;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.core.spi.FilterReply;
import org.junit.jupiter.api.Test;

/** Tests for {@link ApiKeyRedactionFilter}. */
class ApiKeyRedactionFilterTest {

    private final ApiKeyRedactionFilter filter = new ApiKeyRedactionFilter();

    @Test
    void deniesMessageContainingApiKey() {
        FilterReply reply =
                filter.decide(null, null, Level.INFO, "Key is sk-ant-abc123_DEF-456", null, null);
        assertThat(reply).isEqualTo(FilterReply.DENY);
    }

    @Test
    void allowsNormalMessage() {
        FilterReply reply = filter.decide(null, null, Level.INFO, "Normal log message", null, null);
        assertThat(reply).isEqualTo(FilterReply.NEUTRAL);
    }

    @Test
    void deniesApiKeyInParams() {
        FilterReply reply =
                filter.decide(
                        null, null, Level.INFO, "Key: {}", new Object[] {"sk-ant-abc123"}, null);
        assertThat(reply).isEqualTo(FilterReply.DENY);
    }

    @Test
    void allowsNullFormat() {
        FilterReply reply = filter.decide(null, null, Level.INFO, null, null, null);
        assertThat(reply).isEqualTo(FilterReply.NEUTRAL);
    }

    @Test
    void allowsNullParams() {
        FilterReply reply = filter.decide(null, null, Level.INFO, "message", null, null);
        assertThat(reply).isEqualTo(FilterReply.NEUTRAL);
    }

    @Test
    void allowsParamWithNullValue() {
        FilterReply reply =
                filter.decide(null, null, Level.INFO, "message {}", new Object[] {null}, null);
        assertThat(reply).isEqualTo(FilterReply.NEUTRAL);
    }

    @Test
    void allowsParamWithoutApiKey() {
        FilterReply reply =
                filter.decide(
                        null, null, Level.INFO, "message {}", new Object[] {"safe-value"}, null);
        assertThat(reply).isEqualTo(FilterReply.NEUTRAL);
    }
}
