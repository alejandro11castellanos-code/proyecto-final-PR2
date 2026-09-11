import express from 'express';
import { pool } from './db/pool.js';
import { createAuthRouter } from './routes/auth.js';
import { createUserRepository } from './repositories/users.js';

/**
 * Builds the Express application. Kept separate from server startup so tests can
 * exercise routes without opening a port.
 */
export function createApp({
  userRepository = createUserRepository(pool),
  jwtSecret = process.env.JWT_SECRET,
} = {}) {
  const app = express();
  app.use(express.json());

  app.get('/health', (_req, res) => {
    res.json({
      status: 'ok',
      service: 'svb-gua-backend',
      timestamp: new Date().toISOString(),
    });
  });

  app.use('/auth', createAuthRouter({ userRepository, jwtSecret }));

  return app;
}
