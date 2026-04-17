/**
 * Bearer-token authentication middleware.
 *
 * @remarks
 * Reads the dev token from the SecretsStore and validates incoming
 * `Authorization: Bearer <token>` headers. In M0, a single dev token
 * is the only auth mechanism.
 */

import type { FastifyRequest, FastifyReply } from 'fastify';
import { UnauthorizedError } from '@pairion/core';

/**
 * Creates a Fastify preHandler hook that validates bearer tokens.
 *
 * @param validToken - The expected bearer token.
 * @returns A Fastify preHandler function.
 */
export function createAuthHook(validToken: string) {
  return async (request: FastifyRequest, _reply: FastifyReply): Promise<void> => {
    const authHeader = request.headers.authorization;
    if (!authHeader?.startsWith('Bearer ')) {
      throw new UnauthorizedError('Missing or invalid Authorization header');
    }
    const token = authHeader.slice(7);
    if (token !== validToken) {
      throw new UnauthorizedError('Invalid bearer token');
    }
  };
}
