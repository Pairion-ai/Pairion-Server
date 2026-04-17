# @pairion/gateway

The network edge of the Pairion Server. Owns the Fastify HTTP server (wired to the OpenAPI spec), the WebSocket server (wired to the AsyncAPI spec), bearer-token auth middleware, and request correlation ids.

## Public Surface

- `createServer(options)` — Creates a configured Fastify app with all routes and WebSocket support
- `createAuthHook(validToken)` — Creates a Fastify preHandler for bearer-token auth
- `setupWebSocket(server, devToken)` — Sets up the WebSocket server for AsyncAPI messaging
- `registerRoutes(app, devToken)` — Registers all REST routes

## Status

M0: `/health`, `/version`, `/v1/status` return real responses. All other routes return 501. WebSocket handles `DeviceIdentify`, `NodeIdentify`, `HeartbeatPing`, and `LogForward`; all other message types return `Error` with `server.not_implemented`.
