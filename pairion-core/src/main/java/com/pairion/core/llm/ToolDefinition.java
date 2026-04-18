package com.pairion.core.llm;

import java.util.Map;

/**
 * Definition of a tool that can be invoked by the LLM during generation.
 *
 * @param name the tool name
 * @param description human-readable description of what the tool does
 * @param inputSchema JSON Schema describing the tool's input parameters
 */
public record ToolDefinition(String name, String description, Map<String, Object> inputSchema) {}
