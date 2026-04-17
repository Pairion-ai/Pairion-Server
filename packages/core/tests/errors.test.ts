import { describe, it, expect } from 'vitest';
import {
  PairionError,
  NotImplementedError,
  UnauthorizedError,
  ForbiddenError,
  NotFoundError,
  BadRequestError,
  ConflictError,
} from '../src/index.js';

describe('PairionError', () => {
  it('carries code, message, statusCode, and details', () => {
    const err = new PairionError('test.error', 'test message', 418, { key: 'val' });
    expect(err.code).toBe('test.error');
    expect(err.message).toBe('test message');
    expect(err.statusCode).toBe(418);
    expect(err.details).toEqual({ key: 'val' });
    expect(err.name).toBe('PairionError');
  });

  it('defaults statusCode to 500', () => {
    const err = new PairionError('x', 'y');
    expect(err.statusCode).toBe(500);
  });

  it('serializes to OpenAPI Error schema via toJSON', () => {
    const err = new PairionError('c', 'm', 400, { d: 1 });
    expect(err.toJSON()).toEqual({ code: 'c', message: 'm', details: { d: 1 } });
  });

  it('omits details from JSON when undefined', () => {
    const err = new PairionError('c', 'm');
    const json = err.toJSON();
    expect(json).toEqual({ code: 'c', message: 'm' });
    expect('details' in json).toBe(false);
  });
});

describe('NotImplementedError', () => {
  it('has code server.not_implemented and status 501', () => {
    const err = new NotImplementedError('sessions');
    expect(err.code).toBe('server.not_implemented');
    expect(err.statusCode).toBe(501);
    expect(err.message).toContain('sessions');
    expect(err.name).toBe('NotImplementedError');
  });

  it('has a default message when no feature given', () => {
    const err = new NotImplementedError();
    expect(err.message).toBe('Not implemented');
  });
});

describe('UnauthorizedError', () => {
  it('has code auth.unauthorized and status 401', () => {
    const err = new UnauthorizedError();
    expect(err.code).toBe('auth.unauthorized');
    expect(err.statusCode).toBe(401);
    expect(err.name).toBe('UnauthorizedError');
  });

  it('accepts a custom message', () => {
    const err = new UnauthorizedError('bad token');
    expect(err.message).toBe('bad token');
  });
});

describe('ForbiddenError', () => {
  it('has code auth.forbidden and status 403', () => {
    const err = new ForbiddenError();
    expect(err.code).toBe('auth.forbidden');
    expect(err.statusCode).toBe(403);
    expect(err.name).toBe('ForbiddenError');
  });
});

describe('NotFoundError', () => {
  it('has code resource.not_found and status 404', () => {
    const err = new NotFoundError('User');
    expect(err.code).toBe('resource.not_found');
    expect(err.statusCode).toBe(404);
    expect(err.message).toBe('User not found');
    expect(err.name).toBe('NotFoundError');
  });

  it('defaults to Resource', () => {
    const err = new NotFoundError();
    expect(err.message).toBe('Resource not found');
  });
});

describe('BadRequestError', () => {
  it('has code request.bad_request and status 400', () => {
    const err = new BadRequestError('invalid field', { field: 'name' });
    expect(err.code).toBe('request.bad_request');
    expect(err.statusCode).toBe(400);
    expect(err.details).toEqual({ field: 'name' });
    expect(err.name).toBe('BadRequestError');
  });
});

describe('ConflictError', () => {
  it('has code resource.conflict and status 409', () => {
    const err = new ConflictError('already exists', { id: '1' });
    expect(err.code).toBe('resource.conflict');
    expect(err.statusCode).toBe(409);
    expect(err.details).toEqual({ id: '1' });
    expect(err.name).toBe('ConflictError');
  });
});
