/**
 * OpenAI-compatible LLM adapter for Pairion.
 *
 * <p>Implements {@link com.pairion.adapters.llm.spi.LlmAdapter} using the OpenAI Chat Completions
 * API over Server-Sent Events (SSE). Compatible with any OpenAI-compatible backend including LM
 * Studio, Ollama, OpenAI, xAI (Grok), Groq, DeepSeek, vLLM, and self-hosted models.
 *
 * <p>Activated by setting {@code pairion.adapters.llm=openaicompat}. The Anthropic adapter remains
 * the default when this property is absent.
 */
package com.pairion.adapters.llm.openaicompat;
