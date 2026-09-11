import express from 'express';
import { pool } from './db/pool.js';
import { createAuthRouter } from './routes/auth.js';
import { createArtistaRouter } from './routes/artistas.js';
import { createLocalidadRouter } from './routes/localidades.js';
import { createConciertoRouter } from './routes/conciertos.js';
import { createInventarioRouter } from './routes/inventario.js';
import { createVentaRouter } from './routes/ventas.js';
import { createUserRepository } from './repositories/users.js';
import { createArtistaRepository } from './repositories/artistas.js';
import { createLocalidadRepository } from './repositories/localidades.js';
import { createConciertoRepository } from './repositories/conciertos.js';
import { createInventarioRepository } from './repositories/inventario.js';
import { createVentaRepository } from './repositories/ventas.js';
import { createQrProvider } from './services/qrcode.js';
import { createMailer } from './services/mailer.js';

/**
 * Builds the Express application. Kept separate from server startup so tests can
 * exercise routes without opening a port.
 */
export function createApp({
  userRepository = createUserRepository(pool),
  artistaRepository = createArtistaRepository(pool),
  localidadRepository = createLocalidadRepository(pool),
  conciertoRepository = createConciertoRepository(pool),
  inventarioRepository = createInventarioRepository(pool),
  ventaRepository = createVentaRepository(pool),
  qrProvider = createQrProvider(),
  mailer = createMailer(),
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
  app.use('/artistas', createArtistaRouter({ artistaRepository, jwtSecret }));
  app.use('/localidades', createLocalidadRouter({ localidadRepository, jwtSecret }));
  app.use(
    '/conciertos',
    createConciertoRouter({ conciertoRepository, artistaRepository, jwtSecret }),
  );
  app.use(
    '/conciertos/:idConcierto/inventario',
    createInventarioRouter({ inventarioRepository, conciertoRepository, localidadRepository, jwtSecret }),
  );
  app.use('/ventas', createVentaRouter({ ventaRepository, qrProvider, mailer, jwtSecret }));

  // Express identifica los errores de parseo JSON antes de llegar a las rutas.
  app.use((error, _req, res, _next) => {
    if (error?.type === 'entity.parse.failed') {
      return res.status(400).json({ error: 'El cuerpo JSON no es válido.' });
    }
    return res.status(500).json({ error: 'Ocurrió un error interno.' });
  });

  return app;
}
