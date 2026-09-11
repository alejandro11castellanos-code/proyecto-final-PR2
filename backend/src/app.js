import express from 'express';

/**
 * Builds the Express application. Kept separate from server startup so tests can
 * exercise routes without opening a port.
 */
export function createApp() {
  const app = express();
  app.use(express.json());

  app.get('/health', (_req, res) => {
    res.json({
      status: 'ok',
      service: 'svb-gua-backend',
      timestamp: new Date().toISOString(),
    });
  });

  return app;
}
