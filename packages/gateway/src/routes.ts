/**
 * REST route registration for the Fastify HTTP server.
 *
 * @remarks
 * In M0, only `/health`, `/version`, and `/v1/status` return real responses.
 * Every other route defined in `openapi.yaml` is registered but returns
 * HTTP 501 with a response body matching the OpenAPI `Error` schema.
 */

import type { FastifyInstance } from 'fastify';
import { NotImplementedError } from '@pairion/core';
import { createAuthHook } from './auth.js';

/** Server version info. */
const VERSION_INFO = {
  version: '1.0.0-alpha.1',
  commit: 'development',
  buildTime: new Date().toISOString(),
};

/**
 * Registers all REST routes on the Fastify instance.
 *
 * @param app - The Fastify instance.
 * @param devToken - The dev bearer token for authentication.
 */
export function registerRoutes(app: FastifyInstance, devToken: string): void {
  const authHook = createAuthHook(devToken);

  // ── System (no auth) ─────────────────────────────────────────────
  app.get('/health', async () => ({ status: 'ok' }));

  app.get('/version', async () => VERSION_INFO);

  // ── System (auth required) ───────────────────────────────────────
  app.get('/v1/status', { preHandler: authHook }, async () => ({
    setupComplete: false,
    activeProfile: null,
    activeAdapters: {},
    subsystems: [
      { name: 'gateway', status: 'ready' },
      { name: 'core', status: 'ready' },
      { name: 'adapters', status: 'ready', detail: 'No adapters registered (M0)' },
      { name: 'logs', status: 'ready' },
      { name: 'agent', status: 'offline', detail: 'Not implemented (M0)' },
      { name: 'household', status: 'offline', detail: 'Not implemented (M0)' },
      { name: 'speaker-id', status: 'offline', detail: 'Not implemented (M0)' },
      { name: 'memory', status: 'offline', detail: 'Not implemented (M0)' },
      { name: 'skills', status: 'offline', detail: 'Not implemented (M0)' },
      { name: 'node-mgmt', status: 'offline', detail: 'Not implemented (M0)' },
      { name: 'proactive', status: 'offline', detail: 'Not implemented (M0)' },
      { name: 'actions', status: 'offline', detail: 'Not implemented (M0)' },
      { name: 'authoring', status: 'offline', detail: 'Not implemented (M0)' },
    ],
  }));

  // ── 501 stub routes (all require auth) ───────────────────────────
  const stubRoutes: Array<{ method: 'get' | 'post' | 'put' | 'patch' | 'delete'; path: string }> = [
    // Setup
    { method: 'get', path: '/v1/setup/state' },
    { method: 'get', path: '/v1/setup/recommendations' },
    { method: 'post', path: '/v1/setup/complete' },
    { method: 'post', path: '/v1/setup/reset' },
    // Persona
    { method: 'get', path: '/v1/persona' },
    { method: 'put', path: '/v1/persona' },
    { method: 'post', path: '/v1/persona/voice-preview' },
    // Profiles
    { method: 'get', path: '/v1/profiles' },
    { method: 'post', path: '/v1/profiles' },
    { method: 'get', path: '/v1/profiles/active' },
    { method: 'put', path: '/v1/profiles/active' },
    { method: 'get', path: '/v1/profiles/:id' },
    { method: 'put', path: '/v1/profiles/:id' },
    { method: 'delete', path: '/v1/profiles/:id' },
    // Adapters
    { method: 'get', path: '/v1/adapters' },
    { method: 'get', path: '/v1/adapters/:kind' },
    { method: 'post', path: '/v1/adapters/:kind' },
    { method: 'get', path: '/v1/adapters/:kind/active' },
    { method: 'put', path: '/v1/adapters/:kind/active' },
    { method: 'get', path: '/v1/adapters/:kind/:id' },
    { method: 'put', path: '/v1/adapters/:kind/:id' },
    { method: 'delete', path: '/v1/adapters/:kind/:id' },
    { method: 'post', path: '/v1/adapters/:kind/:id/test' },
    { method: 'get', path: '/v1/adapters/tts/voices' },
    // Skills
    { method: 'get', path: '/v1/skills' },
    { method: 'post', path: '/v1/skills' },
    { method: 'get', path: '/v1/skills/:id' },
    { method: 'patch', path: '/v1/skills/:id' },
    { method: 'delete', path: '/v1/skills/:id' },
    { method: 'post', path: '/v1/skills/authoring/sessions' },
    { method: 'get', path: '/v1/skills/authoring/sessions/:id' },
    { method: 'delete', path: '/v1/skills/authoring/sessions/:id' },
    // Sessions
    { method: 'get', path: '/v1/sessions' },
    { method: 'post', path: '/v1/sessions' },
    { method: 'get', path: '/v1/sessions/:id' },
    { method: 'delete', path: '/v1/sessions/:id' },
    { method: 'post', path: '/v1/sessions/:id/messages' },
    { method: 'get', path: '/v1/sessions/:id/history' },
    // Memory
    { method: 'post', path: '/v1/memory/search' },
    { method: 'get', path: '/v1/memory/episodic' },
    { method: 'get', path: '/v1/memory/episodic/:id' },
    { method: 'delete', path: '/v1/memory/episodic/:id' },
    { method: 'get', path: '/v1/memory/preferences' },
    { method: 'get', path: '/v1/memory/preferences/:key' },
    { method: 'put', path: '/v1/memory/preferences/:key' },
    { method: 'delete', path: '/v1/memory/preferences/:key' },
    // Proactive
    { method: 'get', path: '/v1/proactive/rules' },
    { method: 'post', path: '/v1/proactive/rules' },
    { method: 'get', path: '/v1/proactive/rules/:id' },
    { method: 'put', path: '/v1/proactive/rules/:id' },
    { method: 'delete', path: '/v1/proactive/rules/:id' },
    { method: 'get', path: '/v1/proactive/history' },
    // Screen
    { method: 'get', path: '/v1/screen/permissions' },
    { method: 'put', path: '/v1/screen/permissions' },
    // Actions
    { method: 'get', path: '/v1/actions/pending' },
    { method: 'get', path: '/v1/actions/:id' },
    { method: 'post', path: '/v1/actions/:id/approve' },
    { method: 'post', path: '/v1/actions/:id/reject' },
    // Channels
    { method: 'get', path: '/v1/channels' },
    { method: 'get', path: '/v1/channels/:id' },
    { method: 'patch', path: '/v1/channels/:id' },
    { method: 'post', path: '/v1/channels/:id/pair' },
    // Devices
    { method: 'get', path: '/v1/devices' },
    { method: 'post', path: '/v1/devices/pair' },
    { method: 'post', path: '/v1/devices/pair/:code/approve' },
    { method: 'get', path: '/v1/devices/pair/:code/poll' },
    { method: 'get', path: '/v1/devices/:id' },
    { method: 'delete', path: '/v1/devices/:id' },
    // HUD
    { method: 'get', path: '/v1/hud/config' },
    { method: 'put', path: '/v1/hud/config' },
    { method: 'get', path: '/v1/hud/themes' },
    // Logs
    { method: 'get', path: '/v1/logs' },
    // Users
    { method: 'get', path: '/v1/users' },
    { method: 'post', path: '/v1/users' },
    { method: 'get', path: '/v1/users/me' },
    { method: 'get', path: '/v1/users/:id' },
    { method: 'patch', path: '/v1/users/:id' },
    { method: 'delete', path: '/v1/users/:id' },
    { method: 'post', path: '/v1/users/:id/enroll/voice' },
    { method: 'get', path: '/v1/users/:id/enroll/status' },
    { method: 'delete', path: '/v1/users/:id/enroll/status' },
    // Nodes
    { method: 'get', path: '/v1/nodes' },
    { method: 'post', path: '/v1/nodes/pair' },
    { method: 'post', path: '/v1/nodes/pair/:code/approve' },
    { method: 'get', path: '/v1/nodes/pair/:code/poll' },
    { method: 'get', path: '/v1/nodes/:id' },
    { method: 'patch', path: '/v1/nodes/:id' },
    { method: 'delete', path: '/v1/nodes/:id' },
    { method: 'get', path: '/v1/nodes/:id/offline-policy' },
    { method: 'put', path: '/v1/nodes/:id/offline-policy' },
    // Voice ID
    { method: 'post', path: '/v1/voice-id/identify' },
    { method: 'get', path: '/v1/voice-id/config' },
    { method: 'put', path: '/v1/voice-id/config' },
    // Household
    { method: 'get', path: '/v1/household' },
    { method: 'patch', path: '/v1/household' },
    { method: 'get', path: '/v1/household/policy' },
    { method: 'put', path: '/v1/household/policy' },
    { method: 'post', path: '/v1/household/transfer-ownership' },
  ];

  for (const route of stubRoutes) {
    app[route.method](route.path, { preHandler: authHook }, async () => {
      throw new NotImplementedError(route.path);
    });
  }
}
