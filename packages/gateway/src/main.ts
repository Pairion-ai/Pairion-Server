/**
 * Server entry point — boots the Pairion Server on port 18789.
 *
 * @remarks
 * Bootstraps the full adapter stack, skill registry, agent session
 * manager, and gateway. In M1: Anthropic LLM, Kokoro-MLX TTS,
 * Whisper-MLX STT, openWakeWord, and the weather skill.
 */

import { logger, EventBus } from '@pairion/core';
import {
  AdapterRegistry, FileSecretsStore, ensureDevToken,
  AnthropicLLMProvider, KokoroMLXTTSProvider,
  WhisperMLXSTTProvider, OpenWakeWordProvider,
} from '@pairion/adapters';
import { SkillRegistry, SkillInvoker, weatherSkill } from '@pairion/skills';
import { SessionManager, DEFAULT_AGENT_CONFIG, loadSystemPrompt } from '@pairion/agent';
import { createServer } from './server.js';

const PORT = 18789;
const HOST = '0.0.0.0';

async function main(): Promise<void> {
  const store = new FileSecretsStore();
  const devToken = ensureDevToken(store);

  // Set up adapter registry
  const registry = new AdapterRegistry();

  // LLM adapter — Anthropic Claude
  const apiKey = process.env['ANTHROPIC_API_KEY'] ?? store.get('anthropic.api_key');
  if (apiKey) {
    registry.register('llm', new AnthropicLLMProvider({ apiKey }));
    logger.info('Anthropic LLM adapter registered');
  } else {
    logger.warn('No Anthropic API key found. Set ANTHROPIC_API_KEY env var or run: security add-generic-password -a pairion -s anthropic.api_key -w <key>');
  }

  // TTS adapter — Kokoro-MLX
  registry.register('tts', new KokoroMLXTTSProvider());

  // STT adapter — Whisper-MLX
  registry.register('stt', new WhisperMLXSTTProvider());

  // Wake-word adapter — openWakeWord
  registry.register('wake-word', new OpenWakeWordProvider());

  // Set up skill registry
  const skillRegistry = new SkillRegistry();
  skillRegistry.register(weatherSkill);
  logger.info({ skillCount: skillRegistry.size }, 'Skills registered');

  // Set up agent
  const eventBus = new EventBus();
  const skillInvoker = new SkillInvoker(skillRegistry);
  const systemPrompt = loadSystemPrompt();

  const sessionManager = new SessionManager({
    registry,
    skillRegistry,
    skillInvoker,
    eventBus,
    config: DEFAULT_AGENT_CONFIG,
    systemPrompt,
  });

  // Create and start server
  const { app } = createServer({ devToken, port: PORT, host: HOST, sessionManager });

  await app.listen({ port: PORT, host: HOST });
  eventBus.emit('system.ready');
  logger.info({ port: PORT }, 'Pairion Server listening');
}

main().catch((err) => {
  logger.fatal({ err }, 'Failed to start Pairion Server');
  process.exit(1);
});
