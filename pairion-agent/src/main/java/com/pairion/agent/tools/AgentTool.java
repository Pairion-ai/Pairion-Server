package com.pairion.agent.tools;

import java.util.Map;

/**
 * Service provider interface for agent tools that the LLM can invoke.
 *
 * <p>Implementations are discovered via Spring's component scan. Each tool registers itself with a
 * unique name matching the tool definition provided to the LLM in {@link
 * com.pairion.agent.session.AgentSession}.
 */
public interface AgentTool {

    /**
     * Returns the unique tool name as registered in the LLM's tool definition list.
     *
     * @return the tool name (e.g. {@code "get_current_weather"})
     */
    String name();

    /**
     * Executes the tool with the given input parameters.
     *
     * @param input the input parameters parsed from the LLM tool call
     * @return the tool result as a string-keyed map; must never return null
     * @throws Exception if execution fails; the dispatcher will convert this to an error response
     */
    Map<String, Object> execute(Map<String, Object> input) throws Exception;
}
