/**
 * @pairion/gateway — The network edge of the Pairion Server.
 *
 * @remarks
 * Owns the Fastify HTTP server (wired to the OpenAPI spec), the WebSocket
 * server (wired to the AsyncAPI spec), bearer-token auth middleware, and
 * request correlation ids. It never does business logic — it validates,
 * authenticates, dispatches, and formats.
 *
 * @packageDocumentation
 */

export { createServer, type ServerOptions, type ServerInstance } from './server.js';
export { createAuthHook } from './auth.js';
export { setupWebSocket } from './websocket.js';
export { registerRoutes } from './routes.js';
export { WebSocketSessionEmitter } from './ws-session-emitter.js';
