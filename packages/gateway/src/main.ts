/**
 * Server entry point — boots the Pairion Server on port 18789.
 *
 * @remarks
 * This file is the `pnpm dev` target. It reads/generates the dev token,
 * creates the server, and starts listening.
 */

import { logger } from '@pairion/core';
import { FileSecretsStore, ensureDevToken } from '@pairion/adapters';
import { createServer } from './server.js';

const PORT = 18789;
const HOST = '0.0.0.0';

async function main(): Promise<void> {
  const store = new FileSecretsStore();
  const devToken = ensureDevToken(store);

  const { app } = createServer({ devToken, port: PORT, host: HOST });

  await app.listen({ port: PORT, host: HOST });
  logger.info({ port: PORT }, 'Pairion Server listening');
}

main().catch((err) => {
  logger.fatal({ err }, 'Failed to start Pairion Server');
  process.exit(1);
});
