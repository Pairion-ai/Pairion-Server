import { describe, it, expect, beforeAll, afterAll } from 'vitest';
import { createServer } from '../src/server.js';
import type { ServerInstance } from '../src/server.js';

const DEV_TOKEN = 'test-token-for-testing';

describe('REST API', () => {
  let server: ServerInstance;

  beforeAll(async () => {
    server = createServer({ devToken: DEV_TOKEN, port: 0, host: '127.0.0.1' });
    await server.app.ready();
  });

  afterAll(async () => {
    server.wss.close();
    await server.app.close();
  });

  it('GET /health returns { status: "ok" }', async () => {
    const res = await server.app.inject({ method: 'GET', url: '/health' });
    expect(res.statusCode).toBe(200);
    expect(res.json()).toEqual({ status: 'ok' });
  });

  it('GET /version returns version info', async () => {
    const res = await server.app.inject({ method: 'GET', url: '/version' });
    expect(res.statusCode).toBe(200);
    const body = res.json();
    expect(body.version).toBe('1.0.0-alpha.1');
    expect(body.commit).toBeDefined();
    expect(body.buildTime).toBeDefined();
  });

  it('GET /v1/status requires auth', async () => {
    const res = await server.app.inject({ method: 'GET', url: '/v1/status' });
    expect(res.statusCode).toBe(401);
    const body = res.json();
    expect(body.code).toBe('auth.unauthorized');
  });

  it('GET /v1/status with valid token returns subsystem status', async () => {
    const res = await server.app.inject({
      method: 'GET',
      url: '/v1/status',
      headers: { authorization: `Bearer ${DEV_TOKEN}` },
    });
    expect(res.statusCode).toBe(200);
    const body = res.json();
    expect(body.setupComplete).toBe(false);
    expect(body.subsystems).toBeDefined();
    expect(body.subsystems.length).toBeGreaterThan(0);
    expect(body.subsystems[0].name).toBe('gateway');
    expect(body.subsystems[0].status).toBe('ready');
  });

  it('GET /v1/status with bad token returns 401', async () => {
    const res = await server.app.inject({
      method: 'GET',
      url: '/v1/status',
      headers: { authorization: 'Bearer wrong-token' },
    });
    expect(res.statusCode).toBe(401);
  });

  it('non-M0 route returns 501 with OpenAPI Error schema', async () => {
    const res = await server.app.inject({
      method: 'GET',
      url: '/v1/sessions',
      headers: { authorization: `Bearer ${DEV_TOKEN}` },
    });
    expect(res.statusCode).toBe(501);
    const body = res.json();
    expect(body.code).toBe('server.not_implemented');
    expect(body.message).toContain('Not implemented');
  });

  it('stub POST route returns 501', async () => {
    const res = await server.app.inject({
      method: 'POST',
      url: '/v1/sessions',
      headers: { authorization: `Bearer ${DEV_TOKEN}` },
      payload: {},
    });
    expect(res.statusCode).toBe(501);
  });

  it('stub DELETE route returns 501', async () => {
    const res = await server.app.inject({
      method: 'DELETE',
      url: '/v1/sessions/some-id',
      headers: { authorization: `Bearer ${DEV_TOKEN}` },
    });
    expect(res.statusCode).toBe(501);
  });

  it('stub PUT route returns 501', async () => {
    const res = await server.app.inject({
      method: 'PUT',
      url: '/v1/persona',
      headers: { authorization: `Bearer ${DEV_TOKEN}` },
      payload: {},
    });
    expect(res.statusCode).toBe(501);
  });

  it('stub PATCH route returns 501', async () => {
    const res = await server.app.inject({
      method: 'PATCH',
      url: '/v1/users/some-id',
      headers: { authorization: `Bearer ${DEV_TOKEN}` },
      payload: {},
    });
    expect(res.statusCode).toBe(501);
  });

  it('non-PairionError results in 500', async () => {
    // Use a separate server instance to register the error route before ready
    const { createServer } = await import('../src/server.js');
    const errServer = createServer({ devToken: DEV_TOKEN, port: 0, host: '127.0.0.1' });
    errServer.app.get('/test-internal-error', async () => {
      throw new Error('unexpected');
    });
    await errServer.app.ready();
    try {
      const res = await errServer.app.inject({ method: 'GET', url: '/test-internal-error' });
      expect(res.statusCode).toBe(500);
      expect(res.json().code).toBe('server.internal_error');
    } finally {
      errServer.wss.close();
      await errServer.app.close();
    }
  });
});
