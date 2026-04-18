/**
 * Anthropic Claude LLM adapter implementation.
 *
 * <p>This is the <strong>only</strong> package permitted to import {@code com.anthropic.*} classes.
 * The ArchUnit vendor-SDK isolation rule enforces this boundary at build time.
 *
 * <p>Uses the official Anthropic Java SDK ({@code com.anthropic:anthropic-java}) for streaming
 * message generation with tool-use support.
 */
package com.pairion.adapters.llm.anthropic;
