import path from 'node:path';
import { createRequire } from 'node:module';
import { fileURLToPath } from 'node:url';
import express from 'express';
import QRCode from 'qrcode';

const require = createRequire(import.meta.url);
const carpetaPublica = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../../public/acceso');
const jsQR = require.resolve('jsqr/dist/jsQR.js');

// La página no usa scripts ni estilos en línea, así que la política puede ser estricta: solo carga
// recursos de este mismo servidor, y solo se conecta a este mismo servidor.
const POLITICA = [
  "default-src 'none'",
  "script-src 'self'",
  "style-src 'self'",
  "img-src 'self' data:",
  "media-src 'self' blob:",
  "connect-src 'self'",
  "base-uri 'none'",
  "form-action 'none'",
  "frame-ancestors 'none'",
].join('; ');

/**
 * Página web de control de acceso (/acceso/): se abre en el navegador del celular, usa la cámara
 * para leer el QR de cada boleto y llama a POST /accesos con la sesión del portero.
 */
export function createAccesoWebRouter() {
  const router = express.Router();

  router.use((_req, res, next) => {
    res.set({
      'Content-Security-Policy': POLITICA,
      'Permissions-Policy': 'camera=(self)',
      'X-Content-Type-Options': 'nosniff',
      'Referrer-Policy': 'no-referrer',
      'Cache-Control': 'no-cache',
    });
    next();
  });

  // La librería de lectura de QR se sirve desde node_modules: no hay copia suya en el repositorio.
  router.get('/jsqr.js', (_req, res) => res.type('js').sendFile(jsQR));

  // QR con la dirección de la propia página, para abrirla desde el celular con solo escanearlo.
  router.get('/enlace.png', async (req, res, next) => {
    try {
      const direccion = `${req.protocol}://${req.get('host')}/acceso/`;
      res.type('png').send(await QRCode.toBuffer(direccion, { margin: 1, width: 300 }));
    } catch (error) {
      next(error);
    }
  });

  router.use(express.static(carpetaPublica, { index: 'index.html' }));
  return router;
}
