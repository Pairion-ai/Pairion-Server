package com.pairion.gateway.config;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.turbo.TurboFilter;
import ch.qos.logback.core.spi.FilterReply;
import java.util.regex.Pattern;
import org.slf4j.Marker;

/**
 * Logback turbo filter that redacts Anthropic API key patterns from log messages.
 *
 * <p>Matches the pattern {@code sk-ant-[A-Za-z0-9_-]+} and replaces it with {@code
 * [REDACTED_API_KEY]}. This filter runs before any appender processes the log event.
 */
public class ApiKeyRedactionFilter extends TurboFilter {

    private static final Pattern API_KEY_PATTERN = Pattern.compile("sk-ant-[A-Za-z0-9_\\-]+");

    /**
     * Evaluates whether a log message contains an Anthropic API key pattern. If the formatted
     * message contains a key, the event is denied to prevent the secret from reaching any appender.
     *
     * @param marker the marker associated with the logging request
     * @param logger the logger making the request
     * @param level the logging level
     * @param format the message format string
     * @param params the message parameters
     * @param t an associated throwable
     * @return {@link FilterReply#DENY} if the message contains an API key, otherwise {@link
     *     FilterReply#NEUTRAL}
     */
    @Override
    public FilterReply decide(
            Marker marker,
            Logger logger,
            Level level,
            String format,
            Object[] params,
            Throwable t) {
        if (format != null && API_KEY_PATTERN.matcher(format).find()) {
            return FilterReply.DENY;
        }
        if (params != null) {
            for (Object param : params) {
                if (param != null && API_KEY_PATTERN.matcher(param.toString()).find()) {
                    return FilterReply.DENY;
                }
            }
        }
        return FilterReply.NEUTRAL;
    }
}
