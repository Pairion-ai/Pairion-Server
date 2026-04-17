/**
 * Server factory — creates and configures the Fastify application with
 * all routes, error handling, and WebSocket support.
 */

import Fastify, { type FastifyInstance } from 'fastify';
import { randomUUID } from 'node:crypto';
import { createSubsystemLogger, PairionError } from '@pairion/core';
import { registerRoutes } from './routes.js';
import { setupWebSocket } from './websocket.js';
import type { WebSocketServer } from 'ws';

const log = createSubsystemLogger('gateway');

/** Options for creating the Pairion server. */
export interface ServerOptions {
  /** The dev bearer token for authentication. */
  readonly devToken: string;
  /** Port to listen on. */
  readonly port: number;
  /** Host to bind to. */
  readonly host: string;
}

/** The created server instance with its WebSocket server. */
export interface ServerInstance {
  /** The Fastify application. */
  readonly app: FastifyInstance;
  /** The WebSocket server. */
  readonly wss: WebSocketServer;
}

/**
 * Creates and configures the Pairion server.
 *
 * @param options - Server configuration options.
 * @returns The configured Fastify app and WebSocket server (not yet listening).
 */
export function createServer(options: ServerOptions): ServerInstance {
  const app = Fastify({
    logger: false,
    genReqId: () => randomUUID(),
    disableRequestLogging: true,
  });

  // Request logging with correlation id
  app.addHook('onRequest', async (request) => {
    log.info({ requestId: request.id, method: request.method, url: request.url }, 'Incoming request');
  });

  // Error handler — maps PairionError to proper HTTP responses
  app.setErrorHandler((error, _request, reply) => {
    if (error instanceof PairionError) {
      return reply.status(error.statusCode).send(error.toJSON());
    }
    log.error({ err: error }, 'Unhandled error');
    return reply.status(500).send({
      code: 'server.internal_error',
      message: 'Internal server error',
    });
  });

  // Register REST routes
  registerRoutes(app, options.devToken);

  // Set up WebSocket on the underlying HTTP server
  const wss = setupWebSocket(app.server, options.devToken);

  return { app, wss };
}
