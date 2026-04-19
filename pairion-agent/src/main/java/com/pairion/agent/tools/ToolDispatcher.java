package com.pairion.agent.tools;

import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Dispatches LLM tool call requests to the appropriate registered {@link AgentTool} implementation.
 *
 * <p>Each tool call from the LLM is routed by name to the matching tool. If no matching tool is
 * found, a structured error response is returned so the LLM can recover gracefully without crashing
 * the turn loop.
 *
 * <p>Tools are injected via Spring's list injection — any {@link AgentTool} bean is automatically
 * registered.
 */
@Component
public class ToolDispatcher {

    private static final Logger log = LoggerFactory.getLogger(ToolDispatcher.class);

    private final List<AgentTool> tools;

    /**
     * Constructs the dispatcher with all available agent tools.
     *
     * @param tools the list of registered tool implementations
     */
    public ToolDispatcher(List<AgentTool> tools) {
        this.tools = tools;
        log.info("ToolDispatcher initialized with {} tool(s): {}", tools.size(),
                tools.stream().map(AgentTool::name).toList());
    }

    /**
     * Dispatches a tool call by name and returns its result.
     *
     * <p>If no tool with the given name is registered, returns a structured error map with {@code
     * error: "unknown_tool"} so the LLM can handle the failure gracefully.
     *
     * @param toolName the name of the tool to invoke
     * @param input the input parameters parsed from the LLM's tool call
     * @return the tool's output as a string-keyed map
     */
    public Map<String, Object> dispatch(String toolName, Map<String, Object> input) {
        for (AgentTool tool : tools) {
            if (tool.name().equals(toolName)) {
                log.info("tool.dispatch: name={}", toolName);
                try {
                    Map<String, Object> result = tool.execute(input);
                    log.info("tool.result: name={}, keys={}", toolName, result.keySet());
                    return result;
                } catch (Exception e) {
                    log.error("tool.error: name={}, error={}", toolName, e.getMessage());
                    return Map.of("error", "tool_execution_failed", "message", e.getMessage());
                }
            }
        }
        log.warn("tool.unknown: name={}", toolName);
        return Map.of("error", "unknown_tool", "tool", toolName);
    }
}
