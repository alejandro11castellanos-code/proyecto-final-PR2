import express from 'express';
import rateLimit from 'express-rate-limit';
import { pool } from './db/pool.js';
import { createAuthRouter } from './routes/auth.js';
import { createUsuarioRouter } from './routes/usuarios.js';
import { createArtistaRouter } from './routes/artistas.js';
import { createLocalidadRouter } from './routes/localidades.js';
import { createConciertoRouter } from './routes/conciertos.js';
import { createInventarioRouter } from './routes/inventario.js';
import { createVentaRouter } from './routes/ventas.js';
import { createAccesoRouter } from './routes/accesos.js';
import { createAccesoWebRouter } from './routes/acceso-web.js';
import { createReporteRouter } from './routes/reportes.js';
import { createUserRepository } from './repositories/users.js';
import { createArtistaRepository } from './repositories/artistas.js';
import { createLocalidadRepository } from './repositories/localidades.js';
import { createConciertoRepository } from './repositories/conciertos.js';
import { createInventarioRepository } from './repositories/inventario.js';
import { createVentaRepository } from './repositories/ventas.js';
import { createAccesoRepository } from './repositories/accesos.js';
import { createReporteRepository } from './repositories/reportes.js';
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
  accesoRepository = createAccesoRepository(pool),
  reporteRepository = createReporteRepository(pool),
  qrProvider = createQrProvider(),
  mailer = createMailer(),
  jwtSecret = process.env.JWT_SECRET,
  // Intentos de login FALLIDOS permitidos por IP en cada ventana. Con el servidor público, sin
  // límite cualquiera podría probar contraseñas sin freno.
  loginRateLimit = { windowMs: 15 * 60 * 1000, limit: 20 },
} = {}) {
  const app = express();
  // Detrás del proxy de Render hay un salto: así req.ip es la IP real del cliente y no la del proxy.
  app.set('trust proxy', 1);
  app.use(express.json());

  app.use('/auth/login', rateLimit({
    ...loginRateLimit,
    skipSuccessfulRequests: true,
    standardHeaders: 'draft-7',
    legacyHeaders: false,
    handler: (_req, res) => res.status(429).json({
      error: 'Demasiados intentos fallidos. Intente de nuevo en unos minutos.',
    }),
  }));

  app.get('/health', (_req, res) => {
    res.json({
      status: 'ok',
      service: 'svb-gua-backend',
      timestamp: new Date().toISOString(),
    });
  });

  app.use('/auth', createAuthRouter({ userRepository, jwtSecret }));
  app.use('/usuarios', createUsuarioRouter({ userRepository, jwtSecret }));
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
  app.use('/accesos', createAccesoRouter({ accesoRepository, jwtSecret }));
  app.use('/acceso', createAccesoWebRouter());
  app.use('/reportes', createReporteRouter({ reporteRepository, jwtSecret }));

  // Express identifica los errores de parseo JSON antes de llegar a las rutas.
  app.use((error, _req, res, _next) => {
    if (error?.type === 'entity.parse.failed') {
      return res.status(400).json({ error: 'El cuerpo JSON no es válido.' });
    }
    return res.status(500).json({ error: 'Ocurrió un error interno.' });
  });

  return app;
}
